(ns ola-clojure.ola-client
  "Interface to the automatically generated protocol buffer
  communication classes which communicate with the Open Lighting
  Architecture daemon, olad."
  (:require [flatland.protobuf.core :refer [protobuf protobuf-load protobuf-dump]]
            [clojure.java.io :as io]
            [clojure.core.cache :as cache]
            [clojure.core.async :refer [chan go go-loop <! >!! close!]]
            [clojure.stacktrace :refer [root-cause]]
            [ola-clojure.rpc-messages :refer [RpcMessage]]
            [taoensso.timbre :as timbre])
  (:import [java.net Socket InetSocketAddress]
           [java.nio ByteBuffer ByteOrder]
           [java.io InputStream]
           [flatland.protobuf PersistentProtocolBufferMap]
           [com.google.protobuf ByteString]))

;; Values needed to construct proper protocol headers for communicating with OLA server
(def ^:private protocol-version 1)
(def ^:private version-mask 0xf0000000)
(def ^:private version-masked (bit-and (bit-shift-left protocol-version 28) version-mask))
(def ^:private size-mask 0x0fffffff)

(defonce olad-port
  ^{:doc "The port on which to contact the OLA server. Unless you have
  changed your OLA configuration, this should work."}
  (atom 9010))

(defonce olad-host
  ^{:doc "The host on your OLA server is running. For best performance,
  run it on the same machine, and leave this setting alone.
  If you are on Windows, and so need to run OLA on a different machine,
  set this atom to the name or IP address of the machine running olad."}
  (atom "localhost"))

(def ^:private
  socket-timeout
  "How many milliseconds to wait when trying to open the socket to the
  OLA daemon. The default is two seconds, which is ridiculously long
  since Afterglow is designed to work with the OLA server on a local
  socket. But this was added in order to support Windows use where OLA
  cannot run locally. Hopefully this will be more than long enough."
  2000)

(def ^:private
  request-cache-ttl
  "How long to cache handlers to be called when OLA server responds.
  If an hour has gone by, we can be sure that request is never going to
  see a response."
  (.convert java.util.concurrent.TimeUnit/MILLISECONDS
            1 java.util.concurrent.TimeUnit/HOURS))

(defonce ^:private
  ^{:doc "The channel used to communicate with the thread that talks to the OLA server"}
  channel 
  (atom nil))

(defn- next-request-id
  "Assign the sequence number for a new request, wrapping at the
  protocol limit. Will be safe because it will take far longer than an
  hour to wrap, and stale requests will thus be long gone."
  [request-counter]
  (swap! request-counter #(if (= % Integer/MAX_VALUE)
                            1
                            (inc %))))

(defn- disconnect-server
  "Disconnects any active OLA server connection, and returns the new
  value the @connection atom should hold."
  [conn]
  (when (:socket conn)
    (try
      (.close (:socket conn))
      (catch Exception e
        (timbre/info e "Issue closing OLA server socket"))))
  nil)

(defn- connect-server
  "Establishes a new connection to the OLA server, returning the value
  the @connection atom should hold for using it. Takes the current
  connection, if any, as its argument, in case cleanup is needed."
  [conn]
  (when (:socket conn)
    (disconnect-server conn)) ; Clean up any old connection, e.g. if a read failed
  (try
    (let [addr (InetSocketAddress. @olad-host @olad-port)
          sock (Socket.)]
      (.connect sock addr socket-timeout)
      ;; Set TCP_NODELAY option on the socket, for important throughput reasons, as documented
      ;; at http://docs.openlighting.org/ola/doc/latest/rpc_system.html#sec_RPCHeader
      (.setTcpNoDelay sock true)
      (try
        (let [in (io/input-stream sock)
              out (io/output-stream sock)]
          {:socket sock :in in :out out})
        (catch Exception e
          (timbre/warn e "Problem opening olad server streams; discarding connection")
          (try
            (.close sock)
            (catch Exception e
              (timbre/info e "Further exception trying to clean up failed olad connection"))))))
    (catch Exception e
      (timbre/warn e "Unable to connect to olad server, is it running?"))))

(defn- build-header
  "Calculates the correct 4-byte header value for an OLA request of
  the specified length."
  [length]
  (bit-or (bit-and length size-mask) version-masked))

(defn- parse-header
  "Returns the length encoded by a header value, after validating the
  protocol version."
  [header]
  (if (= (bit-and header version-mask) version-masked)
    (bit-and header size-mask)
    (throw (Exception. (str "Unsupported OLA protocol version, "
                            (bit-shift-right (bit-and header version-mask) 28))))))

(defn- read-fully
  "Will fill the buffer to capacity, or throw an exception. Returns
  the number of bytes read." ^long [^InputStream input ^bytes buf]
  (loop [off 0 len (alength buf)]
    (let [in-size (.read input buf off len)]
      (cond
        (== in-size len) (+ off in-size)
        (neg? in-size) (throw (Exception. (str "Only able to read " off " of " (alength buf) " expected bytes.")))
        :else (recur (+ off in-size) (- len in-size))))))

(defn- write-safely-internal
  "Recursive portion of write-safely, try to write a message to the
  olad server, reopen connection and recur if that fails and it is the
  first failure. Returns true if writing was successful."
  [^bytes header-and-message ^Boolean first-try  ^clojure.lang.Atom connection]
  (try
    (.write (:out @connection) header-and-message)
    (try
      (.flush (:out @connection))
      true
      (catch Exception e
        (timbre/warn e "Problem flushing message to olad server; not retrying.")))
    (catch Exception e
      (timbre/warn e "Problem writing message to olad server")
      (when first-try
        (timbre/info "Reopening connection and retrying...")
        (swap! connection connect-server)
        (write-safely-internal header-and-message false connection)))))

(defn- write-safely
  "Try to write a message to the olad server, reopen connection and
  retry once if that failed. Returns true if writing swas successful."
  [^bytes header ^bytes message ^clojure.lang.Atom connection]
  ;; Combine the header and message into a single write, for important throughput reasons, as documented
  ;; at http://docs.openlighting.org/ola/doc/latest/rpc_system.html#sec_RPCHeader
  (let [combined (byte-array (+ (count header) (count message)))]
    (System/arraycopy header 0 combined 0 (count header))
    (System/arraycopy message 0 combined (count header) (count message))
    (write-safely-internal combined true connection)))

(defn- store-handler
  "Record a handler in the cache so it will be ready to call when the
  OLA server responds."
  [request-id response-type handler request-cache]
  (if (cache/has? @request-cache request-id)
    (do
      (swap! request-cache #(cache/hit % request-id))
      (timbre/warn "Collision for request id" request-id))
    (swap! request-cache #(cache/miss % request-id {:response-type response-type :handler handler}))))

(defn- find-handler
  "Look up the handler details for a request id, removing them from
  the cache."
  [request-id request-cache]
  (when-let [entry (cache/lookup @request-cache request-id)]
    (swap! request-cache #(cache/evict % request-id))
    entry))

(defn- handle-response
  "Look up the handler associated with an OLA server response and call
  it with the RPC response."
  [wrapper request-cache]
  (try
    (if-let [handler-entry (find-handler (:id wrapper) request-cache)]
      (let [response-length (.size (:buffer wrapper))
            response-buffer (.asReadOnlyByteBuffer (:buffer wrapper))
            response-bytes (byte-array response-length)]
        (.get response-buffer response-bytes)
        (when-let [handler (:handler handler-entry)]
          (handler {:response (protobuf-load (:response-type handler-entry) response-bytes)})))
      (timbre/warn "Cannot find handler for response, too old?" wrapper))
    (catch Throwable t
      (timbre/error t "Problem delivering OLA response to handler"))))

(defn- handle-failure
  "Look up the handler associated with an OLA server response and call
  it with an error description and possibly throwable. Can also be
  called with the three argument arity if the handler is already
  known, to simply deliver the failure information."
  ([wrapper request-cache message thrown]
   (if-let [handler-entry (find-handler (:id wrapper) request-cache)]
     (handle-failure (:handler handler-entry) message thrown)
     (timbre/warn "Cannot find handler for response, too old?" wrapper)))
  ([handler message thrown]
   (when handler
     (try
       (handler (merge {:failed message} (when thrown {:thrown thrown})))
       (catch Throwable t
         (timbre/error "Problem delivering RPC failure to handler"))))))

(defn- channel-loop
  "Reads from the internal request channel until it closes, formatting
  messages to be sent to the OLA server socket. Can be run on an
  unbuffered core.async channel because this is fast, local async
  I/O."
  [channel connection request-cache]
  (let [request-counter (atom 0)
        header-buffer (.order (ByteBuffer/allocate 4) (ByteOrder/nativeOrder))
        header-bytes (byte-array 4)]
    (go
      (loop [request (<! channel)]
        (when-let [[name message response-type response-handler] request]
          (let [msg-bytes (ByteString/copyFrom (protobuf-dump message))
                request-id (next-request-id request-counter)
                request (protobuf RpcMessage :type :REQUEST :id request-id :name name :buffer msg-bytes)
                request-bytes (protobuf-dump request)]
            (store-handler request-id response-type response-handler request-cache)
            (doto header-buffer
              (.clear)
              (.putInt (.intValue (build-header (count request-bytes))))
              (.flip)
              (.get header-bytes))
            (or
              (write-safely header-bytes request-bytes connection)
              (handle-failure request request-cache
                              (str "Unable to write " name " message to OLA."
                                   (when-not @connection " Is it running?")))))
          (recur (<! channel))))
      ;; The channel has been closed, so signal the main thread to shut down as well
      (swap! connection disconnect-server))))

(declare shutdown)

(defn- process-requests
  "Set up loops to read requests on our local channel and send them to
  the OLA server, and read its responses and dispatch them to the
  proper handlers. Needs to be called in a future since it uses
  blocking reads from the server socket."
  [channel]

  (let [connection (atom (connect-server nil))
        request-cache (atom (cache/ttl-cache-factory {} :ttl request-cache-ttl))
        header-bytes (byte-array 4)
        header-buffer (.order (ByteBuffer/allocate 4) (ByteOrder/nativeOrder))]
    (if @connection
      ;; Run core.async loop which takes requests on the internal channel and writes them to the OLA server socket
      (channel-loop channel connection request-cache)
      ;; We were not able to obtain a connection, so shut down.
      (close! channel))
    
    ;; An ordinary loop which reads from the OLA server socket and dispatches responses to their handlers
    (while @connection
      (try
        (read-fully (:in @connection) header-bytes)
        (doto header-buffer
          (.clear)
          (.put header-bytes)
          (.flip))
        (let [wrapper-length (parse-header (.getInt header-buffer))
              wrapper-bytes (byte-array wrapper-length)]
          (read-fully (:in @connection) wrapper-bytes)
          (let [wrapper (protobuf-load RpcMessage wrapper-bytes)]
            (if (= (:type wrapper) :RESPONSE)
              (handle-response wrapper request-cache)
              (if (= (:type wrapper) :RESPONSE_FAILED)
                (let [error-length (.size (:buffer wrapper))
                      error-buffer (.asReadOnlyByteBuffer (:buffer wrapper))
                      error-bytes (byte-array error-length)]
                  (.get error-buffer error-bytes)
                  (let [error-message (str "OLA RpcMessage failed: " (String. error-bytes "UTF-8"))]
                    (handle-failure wrapper request-cache error-message nil)
                    (timbre/warn error-message)))
                (do (handle-failure wrapper request-cache
                                    (str "Unrecognized OLA RpcMessage type: " (:type wrapper)) nil)
                    (timbre/warn "Unrecognized OLA RpcMesssage response:" wrapper))))))
        (catch Exception e
          (when @connection
            (timbre/warn e "Problem reading from olad, trying to reconnect...")
            (swap! connection connect-server)
            (when-not @connection
              (timbre/error "Unable to reconnect to OLA server, shutting down ola_client")
              (shutdown))))))
    (timbre/info "OLA request processor terminating.")
    (shutdown)))

(defn- create-channel
  [old-channel]
  (or old-channel
      (let [c (chan)]
        (timbre/info "Created OLA request processor.")
        (future (process-requests c))
        c)))

(defn- destroy-channel
  [c]
  (when c
    (close! c))
  nil)

(defn start
  "Explicitly start event handling thread and OLA server connection;
  generally unnecessary, as send-request will call if necessary."
  []
  (swap! channel create-channel))

(defn shutdown
  "Stop the event handling thread and close the OLA server connection,
  if they exist."
  []
  (swap! channel destroy-channel))

(defn send-request
  "Send a request to the OLA server. You will generally not call this
  directly; instead, you will use one of the automatically generated
  RPC functions that does the work of marshaling the request
  parameters into the proper Protobuf object an then calls this on
  your behalf.

  `name` is the name of the OLA RPC method to be invoked. `message` is
  a Protobuf object of the correct type to contain the arguments of
  the specified OLA RPC method. `response-type` is the Protobuf type
  that a successful response will contain. `response-handler` is the
  function to be called when a response is received.

  The response handler is called with a single map as its argument. If
  the RPC was successful, the map will contain a single key
  `:response` whose value holds a map expanded from the Protobuf
  response object.

  If the RPC failed, the map will contain the key `:failed`, and its
  value will be a string describing the failure. If the failure
  resulted from an `Exception` or other `Throwable`, the map will also
  contain that, under the key `:thrown`.

  If `response-handler` is `nil`, no callback will be performed to
  report the result of the RPC attempt."
  {:doc/format :markdown}
  [name message response-type response-handler]
  (start)
  (>!! @channel [name message response-type response-handler]))

(defn wrap-message-if-needed
  "Checks if a message is already a protobuf, and if not constructs
  one of the appropriate type."
  [message message-type]
  (if (= (type message) flatland.protobuf.PersistentProtocolBufferMap)
    message
    (protobuf message-type message)))
