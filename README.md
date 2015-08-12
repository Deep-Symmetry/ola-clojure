# ola-clojure

<image align="right" width="275"
src="doc/assets/ola-clojure-padded-left.png"> A
[Clojure](http://clojure.org) library for communicating with the
[Open Lighting Architecture](https://www.openlighting.org/ola/).

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
other ports and other machines, as described below.

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
   [![Clojars Project](https://clojars.org/ola-clojure/latest-version.svg)](https://clojars.org/ola-clojure)

4. In the namespace from which you want to communinicate with `olad`,
   add this to the `:require` section of the `ns` form:

   `[ola-clojure.ola-service :as ola]`

And you are ready to invoke methods in `olad`. The methods which are
exported by `ola-service` are parsed from the `OlaServerService`
section of
[Ola.proto](https://github.com/brunchboy/ola-clojure/blob/master/resources/proto/Ola.proto#L374-L402).
The messages which the methods use as paramters are defined earlier in
the file. Consider for example the `GetUniverseInfo`
[method](https://github.com/brunchboy/ola-clojure/blob/master/resources/proto/Ola.proto#L385):

```protobuf
rpc GetUniverseInfo (OptionalUniverseRequest) returns (UniverseInfoReply);
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
      :supports_rdm true}]}]}}
```

> If you are sending a "fire and forget" message and you don't care
> about the result, you can pass `nil` for your callback function.

As seen in its specification above, the `GetUniverseInfo` takes an
optional parameter identifying a specific universe of interest. To
pass parameters using `ola-clojure`, you simply supply a normal
Clojure map with keys and values corresponding to the message
definitions within the Protobuf specification. In this case, the
pareamteter is named `universe` and takes an integer, so the following
variant would return the same result as above in the current
configuration, but would select just that universe if there were more
than one configured:

```clojure
(ola/GetUniverseInfo {:universe 0} #(clojure.pprint/pprint %))
```

What happens if we ask for a universe that doesn't exist?

```clojure
(ola/GetUniverseInfo {:universe 1} #(clojure.pprint/pprint %))
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
15-Aug-12 10:34:02 alacrity.singlewire.lan WARN [ola-clojure.ola-client] - OLA RpcMessage failed: Universe doesn't exist
```

This comes from the logging mechanism used by ola-clojure. See
[below](#logging-configuration) for information about how that can be
configured.

### Connection Configuration

### Logging Configuration

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
