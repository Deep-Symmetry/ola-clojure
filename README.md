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

### Documentation Overview

To be fleshed out.

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

## Status

This has been used without any changes since the beginning of the
Afterglow project, although it was only recently separated into its
own project.

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
