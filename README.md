# ola-clojure

<image align="right" width="275"
src="doc/assets/ola-clojure-padded-left.png"> A
[Clojure](http://clojure.org) library for communicating with the
[Open Lighting Architecture](https://www.openlighting.org/ola/).

[![License](https://img.shields.io/github/license/brunchboy/ola-clojure.svg)](#license)

[Protocol Buffers](https://developers.google.com/protocol-buffers/docs/overview)
are used to efficiently communicate with the `olad` daemon via its
[RPC Service](https://docs.openlighting.org/doc/latest/rpc_system.html).

This project was extracted from
[Afterglow](https://github.com/brunchboy/afterglow#afterglow), so that
other Clojure projects could communicate with OLA without having to
pull in all of Afterglow.

### Overview

ola-clojure uses the
[OLA RPC system](http://docs.openlighting.org/ola/doc/latest/rpc_system.html#sec_RPCHeader)
to communicate with the Open Lighting Architecture. Its build process
scans the Protobuf
[specification file](https://github.com/brunchboy/ola-clojure/blob/master/resources/proto/Ola.proto)
that defines the OLA methods exported by `olad`, and uses a code
generator to create Clojure functions which marshal maps you pass them
into properly formatted Protocol Buffers, open the `olad` connection
if necessary, wrap an RPC message telling `olad` what you want to do,
and send it. When a response is received (or if the send attempt
fails), a callback function that you supply will be invoked with
either the response or a failure indication.

This means that in the most common configuration, of talking to `olad`
on the default port `9010` on the local machine, you need to write
almost no code. But you can also configure ola-clojure to talk to
other ports and other machines, as described
[below](#connection-configuration).

## Usage

1. If you haven't already,
   [Install OLA](https://www.openlighting.org/ola/getting-started/downloads/).
   (On the Mac I recommend using [Homebrew](http://brew.sh) which lets
   you simply `brew install ola`). Once you launch the `olad` server
   you can interact with its embedded
   [web server](http://localhost:9090/ola.html), which is very helpful
   in seeing whether anything is working; you can even watch live DMX
   values changing.

2. Set up a Clojure project using [Leiningen](http://leiningen.org) or [Boot](https://github.com/boot-clj/boot#boot--).

3. Add this project as a dependency:
   [![Clojars Project](https://img.shields.io/clojars/v/ola-clojure.svg)](https://clojars.org/ola-clojure)

4. In the namespace from which you want to communinicate with `olad`,
   add this to the `:require` section of the `ns` form:

   `[ola-clojure.ola-service :as ola]`

And you are ready to invoke methods in `olad`. The methods which are
exported by `ola-service` are parsed from the `OlaServerService`
section of
[Ola.proto](https://github.com/brunchboy/ola-clojure/blob/master/resources/proto/Ola.proto#L374-L402).
The messages which the methods use as parameters are defined earlier in
the file. Consider for example the `GetUniverseInfo`
[method](https://github.com/brunchboy/ola-clojure/blob/master/resources/proto/Ola.proto#L385):

```protobuf
// RPCs handled by the OLA Server
service OlaServerService {
  // ...
  rpc GetUniverseInfo (OptionalUniverseRequest) returns (UniverseInfoReply);
  // ...
}
```

The request message, `OptionalUniverseRequest` is specified
[earlier](https://github.com/brunchboy/ola-clojure/blob/master/resources/proto/Ola.proto#L257-L260)
in the file:

```protobuf
// request info about a universe
message OptionalUniverseRequest {
  optional int32 universe = 1;
}
```

Armed with this information, and having properly required and aliased
`ola-service` (if you are just experimenting interactively at the
REPL, `(require '[ola-clojure.ola-service :as ola])` at this point),
we can obtain a list of universes from `olad` like so:

```clojure
(ola/GetUniverseInfo #(clojure.pprint/pprint %))
```

The only required argument to the wrapper functions is a callback
function that will be invoked with the results. In this case, we are
passing an anonymous function which simply uses the standard Clojure
pretty-printer to format the map we get back. You may have to hit
<kbd>Enter</kbd> again to see the output, because connecting to `olad`
and sending the RPC happens asynchronously, and the call to
`GetUniverseInfo` returns immediately, as that process is just
beginning. But once you do, you will see something like this:

```clojure
{:response
 {:universe
  [{:universe 0,
    :name "Dummy Universe",
    :merge_mode :LTP,
    :input_port_count 0,
    :output_port_count 1,
    :rdm_devices 6,
    :output_ports
    [{:port_id 0,
      :priority_capability 0,
      :description "Dummy Port",
      :universe 0,
      :active true,
      :supports_rdm true}]}
   {:universe 1,
    :name "DMX USB Out 1",
    :merge_mode :LTP,
    :input_port_count 0,
    :output_port_count 1,
    :rdm_devices 0,
    :output_ports
    [{:port_id 0,
      :priority_capability 0,
      :description "Serial #: 02615032, firmware 4.8",
      :universe 1,
      :active true,
      :supports_rdm true}]}
   {:universe 2,
    :name "DMX USB Out 2",
    :merge_mode :LTP,
    :input_port_count 0,
    :output_port_count 1,
    :rdm_devices 0,
    :output_ports
    [{:port_id 1,
      :priority_capability 0,
      :description "Serial #: 02615032, firmware 4.8",
      :universe 2,
      :active true,
      :supports_rdm true}]}
   {:universe 3,
    :name "DMX USB In 1",
    :merge_mode :LTP,
    :input_port_count 1,
    :output_port_count 0,
    :rdm_devices 0,
    :input_ports
    [{:port_id 0,
      :priority_capability 1,
      :description "Serial #: 02615032, firmware 4.8",
      :universe 3,
      :active true,
      :priority_mode 1,
      :priority 100,
      :supports_rdm false}]}]}}
```

> If you are sending a "fire and forget" message and you don't care
> about the result, you can pass `nil` for your callback function.

This is exactly what we would expect from looking at the
[specifications](https://github.com/brunchboy/ola-clojure/blob/master/resources/proto/Ola.proto#L257-L275)
of the reply message:

```protobuf
message UniverseInfo {
  required int32 universe = 1;
  required string name = 2;
  required MergeMode merge_mode = 3;
  required int32 input_port_count = 4;
  required int32 output_port_count = 5;
  required int32 rdm_devices = 6;
  repeated PortInfo input_ports = 7;
  repeated PortInfo output_ports = 8;
}
 
message UniverseInfoReply {
  repeated UniverseInfo universe = 1;
}
```

In the response, the Protocol Buffer structures have all been expanded
into ordinary Clojure data structures for convenient access.

As we saw in its specification above, the `GetUniverseInfo` takes an
optional parameter identifying a specific universe of interest. To
pass parameters using `ola-clojure`, you simply supply a normal
Clojure map with keys and values corresponding to the message
definitions within the Protobuf specification. In this case, the
parameter is named `universe` and takes an integer, so the following
variant gets information about a single universe:

```clojure
(ola/GetUniverseInfo {:universe 1} #(clojure.pprint/pprint %))
```
As expected:

```clojure
{:response
 {:universe
  [{:universe 1,
    :name "DMX USB Out 1",
    :merge_mode :LTP,
    :input_port_count 0,
    :output_port_count 1,
    :rdm_devices 0,
    :output_ports
    [{:port_id 0,
      :priority_capability 0,
      :description "Serial #: 02615032, firmware 4.8",
      :universe 1,
      :active true,
      :supports_rdm true}]}]}}
```

What happens if we ask for a universe that doesn't exist?

```clojure
(ola/GetUniverseInfo {:universe 42} #(clojure.pprint/pprint %))
```

This call initially returns `true` just like the others, but yields different output:

```clojure
{:failed "OLA RpcMessage failed: Universe doesn't exist"}
```

Notice that instead of a `:response` key, there is a `:failed` key
containing a description of the problem. This is how you can tell that
something went wrong. If there is an exception that provides more
context to the problem, it will be present under the key `:thrown`.

In addition to the result map printed by our callback function, you
will see a different line of output:

```
15-Aug-12 10:34:02 Alacrity.local WARN [ola-clojure.ola-client] -
                   OLA RpcMessage failed: Universe doesn't exist
```

This comes from the logging mechanism used by ola-clojure. See
[below](#logging-configuration) for information about how that can be
configured.

Other methods work in the same way, like
[GetPlugins](https://github.com/brunchboy/ola-clojure/blob/master/resources/proto/Ola.proto#L376):

```clojure
(ola/GetPlugins #(clojure.pprint/pprint %))
```

```clojure
{:response
 {:plugin
  [{:plugin_id 1, :name "Dummy", :active true, :enabled true}
   {:plugin_id 2, :name "ArtNet", :active true, :enabled true}
   {:plugin_id 3, :name "ShowNet", :active true, :enabled true}
   {:plugin_id 4, :name "ESP Net", :active true, :enabled true}
   {:plugin_id 5, :name "Serial USB", :active true, :enabled true}
   {:plugin_id 6, :name "Enttec Open DMX", :active true, :enabled true}
   {:plugin_id 7, :name "SandNet", :active false, :enabled false}
   {:plugin_id 8, :name "StageProfi", :active false, :enabled false}
   {:plugin_id 9, :name "Pathport", :active true, :enabled true}
   {:plugin_id 11, :name "E1.31 (sACN)", :active true, :enabled true}
   {:plugin_id 12, :name "USB", :active true, :enabled true}
   {:plugin_id 14, :name "OSC", :active true, :enabled true}
   {:plugin_id 16, :name "KiNET", :active true, :enabled true}
   {:plugin_id 17, :name "KarateLight", :active false, :enabled false}
   {:plugin_id 18,
    :name "Milford Instruments",
    :active false,
    :enabled false}
   {:plugin_id 19, :name "Renard", :active true, :enabled true}
   {:plugin_id 21,
    :name "Open Pixel Control",
    :active true,
    :enabled true}
   {:plugin_id 22, :name "GPIO", :active true, :enabled true}]}}
```

Or, extracting and just printing the most important part of the
response to
[GetPluginDescription](https://github.com/brunchboy/ola-clojure/blob/master/resources/proto/Ola.proto#L378-L379):

```clojure
(ola/GetPluginDescription {:plugin_id 5}
                          #(print (get-in % [:response :description])))
```

```
Serial USB Plugin
----------------------------
 
This plugin supports DMX USB devices that emulate a serial port. This 
includes:
 - Arduino RGB Mixer
 - DMX-TRI & RDM-TRI
 - DMXking USB DMX512-A, Ultra DMX, Ultra DMX Pro
 - DMXter4 & mini DMXter
 - Enttec DMX USB Pro
 - Robe Universe Interface
 
See http://opendmx.net/index.php/USB_Protocol_Extensions for more info.
 
--- Config file : ola-usbserial.conf ---
 
device_dir = /dev
The directory to look for devices in.
 
device_prefix = ttyUSB
The prefix of filenames to consider as devices. Multiple keys are allowed.
 
ignore_device = /dev/ttyUSB
Ignore the device matching this string. Multiple keys are allowed.
 
pro_fps_limit = 190
The max frames per second to send to a Usb Pro or DMXKing device.
 
tri_use_raw_rdm = [true|false]
Bypass RDM handling in the {DMX,RDM}-TRI widgets.
 
ultra_fps_limit = 40
The max frames per second to send to a Ultra DMX Pro device.
 
uucp_lock_path = /var/lock
Path to check for UUCP Lock files.
```

Finally, as an example of a more interesting RPC you will likely want
to call, here is the section of Afterglow's `show` namespace which
sends an updated set of DMX control values to one of the show's
universes:

```clojure
(let [levels (get buffers universe)]
  (ola/UpdateDmxData {:universe universe :data (ByteString/copyFrom levels)}
                     response-handler))
```

In this example, `universe` contains the ID of a universe that the
show is controlling, and `levels` is a Java `byte` array containing
the desired DMX channel values for the universe. This invocation
causes `olad` to send those values to whatever plugin and interface is
controlling that universe.

These examples, combined with the
[Ola.proto](https://github.com/brunchboy/ola-clojure/blob/master/resources/proto/Ola.proto#L374-L402)
specification which creates the wrapper functions, will hopefully
enable you to figure out how to send whatever messages you need to
`olad`.
 
### Connection Configuration

If you need to talk to `olad` on a different port or address, you can
do so by configuring the
[ola-client](https://github.com/brunchboy/ola-clojure/blob/master/src/ola_clojure/ola_client.clj)
namespace which manages the connection on behalf of the RPC wrapper
functions:

```clojure
(require '[ola-clojure.ola-client :as ola-client])
(reset! ola-client/olad-host "172.30.246.32")
(reset! ola-client/olad-port 9200)
```

The `ola-client` namespace also provides a `shutdown` function which
you can call if you ever want to explicitly close the `olad` connection:

```clojure
(ola-client/shutdown)
```

There is also a `start` function to open the connection again, but
there is no real need to call this, as the RPC wrapper functions will
all call it if necessary.

### Logging Configuration

Like Afterglow, ola-clojure uses the excellent
[Timbre](https://github.com/ptaoussanis/timbre) logging framework. If
you do nothing, log messages above the `debug` level will be written
to the standard output. But you can configure it however you would
like, as described in its
[documentation](https://github.com/ptaoussanis/timbre#configuration).

## Status

This has been used without any changes since the beginning of the
Afterglow project, although it was only recently separated into its
own project, and cleaned up slightly in the process.

## Bugs

Although there are none known as of the time of this release, please
log [issues](https://github.com/brunchboy/ola-clojure/issues) as you
encounter them!

### References

* Clojure implementation of Protocol Buffers via
  [lein-protobuf](https://github.com/flatland/lein-protobuf) and
  [clojure-protobuf](https://github.com/flatland/clojure-protobuf).
* The
  [Java OLA client](https://github.com/OpenLightingProject/ola/tree/master/java).

## License

<img align="right" alt="Deep Symmetry" src="doc/assets/DS-logo-bw-200-padded-left.png">
Copyright © 2015 [Deep Symmetry, LLC](http://deepsymmetry.org)

Distributed under the
[Eclipse Public License 1.0](http://opensource.org/licenses/eclipse-1.0.php),
the same as Clojure. By using this software in any fashion, you are
agreeing to be bound by the terms of this license. You must not remove
this notice, or any other, from this software. A copy of the license
can be found in
[doc/epl-v10.html](https://cdn.rawgit.com/brunchboy/ola-clojure/master/doc/epl-v10.html)
within this project.

The OLA RPC Protobuf specification files, like the rest of the Open
Lighting Architecture, are distributed under the GNU Lesser General
Public License,
[version 2.1](http://www.gnu.org/licenses/old-licenses/lgpl-2.1.en.html).
