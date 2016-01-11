(defproject ola-clojure "0.1.3"
  :description "A Clojure library for communicating with the Open Lighting Architecture."
  :url "https://github.com/brunchboy/ola-clojure"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.7.0"]
                 [org.clojure/core.cache "0.6.4"]
                 [org.clojure/core.async "0.2.374"]
                 [org.clojars.brunchboy/protobuf "0.8.3"]
                 [selmer "0.9.9"
                  :exclusions [com.google.protobuf/protobuf-java]]
                 [com.taoensso/timbre "4.2.0"]]
  :source-paths ["src" "generated"]
  :prep-tasks [["with-profile" "+gen,+dev" "run" "-m" "ola-clojure.src-generator"] "protobuf" "javac" "compile"]

  :target-path "target/%s"

  :profiles {:dev {:source-paths ["dev_src"]
                   :resource-paths ["dev_resources"]
                   :env {:dev true}}

             :gen {:prep-tasks ^:replace ["protobuf" "javac" "compile"]}}
  :plugins [[org.clojars.brunchboy/lein-protobuf "0.4.3"
             :exclusions [leinjacker]]
            [lein-codox "0.9.0"]]

  :aliases {"gen" ["with-profile" "+gen,+dev" "run" "-m" "ola-clojure.src-generator"]}

  :codox {:source-uri "http://github.com/brunchboy/ola-clojure/blob/master/{filepath}#L{line}"
          :output-path "target/doc"
          :metadata {:doc/format :markdown}}
  :min-lein-version "2.0.0")
