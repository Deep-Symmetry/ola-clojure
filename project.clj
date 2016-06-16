(defproject ola-clojure "0.1.7-SNAPSHOT"
  :description "A Clojure library for communicating with the Open Lighting Architecture."
  :url "https://github.com/brunchboy/ola-clojure"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [org.clojure/core.cache "0.6.5"]
                 [org.clojure/core.async "0.2.382"]
                 [org.clojars.brunchboy/protobuf "0.8.3"]
                 [selmer "1.0.4"
                  :exclusions [com.google.protobuf/protobuf-java]]
                 [com.taoensso/timbre "4.4.0"]]
  :source-paths ["src" "generated"]
  :prep-tasks [["with-profile" "+gen,+dev" "run" "-m" "ola-clojure.src-generator"] "protobuf" "javac" "compile"]

  :target-path "target/%s"

  :profiles {:dev {:source-paths ["dev_src"]
                   :resource-paths ["dev_resources"]
                   :env {:dev true}}

             :gen {:prep-tasks ^:replace ["protobuf" "javac" "compile"]}}
  :plugins [[org.clojars.brunchboy/lein-protobuf "0.4.3"
             :exclusions [leinjacker]]
            [lein-codox "0.9.4"]]

  :aliases {"gen" ["with-profile" "+gen,+dev" "run" "-m" "ola-clojure.src-generator"]}

  :codox {:source-uri "http://github.com/brunchboy/ola-clojure/blob/master/{filepath}#L{line}"
          :output-path "target/doc"
          :metadata {:doc/format :markdown}}
  :min-lein-version "2.0.0")
