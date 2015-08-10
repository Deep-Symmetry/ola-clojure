(ns ola-clojure.{{classname|lower}}-service
    (:require [flatland.protobuf.core :refer :all]
              [ola-clojure.{{classname|lower}}-messages :refer :all]
              [ola-clojure.ola-client :refer [send-request wrap-message-if-needed]]))

{% for item in rpcs %}
(defn {{item.method}}
  ([handler]
   ({{item.method}} {} handler))
  ([message handler]
   (let [wrapped (wrap-message-if-needed message {{item.takes}})]
     (send-request "{{item.method}}" wrapped {{item.returns}} handler))))
{% endfor %}
