(defproject ola-clojure "0.1.1-SNAPSHOT"
  :description "A Clojure library for communicating with the Open Lighting Architecture."
  :url "https://github.com/brunchboy/ola-clojure"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.7.0"]
                 [org.clojure/core.cache "0.6.4"]
                 [org.clojure/core.async "0.1.346.0-17112a-alpha"]
                 [org.clojars.brunchboy/protobuf "0.8.3"]
                 [selmer "0.9.1"]
                 [com.taoensso/timbre "4.1.1"]]
  :source-paths ["src" "generated"]
  :prep-tasks [["with-profile" "+gen,+dev" "run" "-m" "ola-clojure.src-generator"] "protobuf" "javac" "compile"]

  :target-path "target/%s"

  :profiles {:dev {:source-paths ["dev_src"]
                   :resource-paths ["dev_resources"]
                   :env {:dev true}}

             :gen {:prep-tasks ^:replace ["protobuf" "javac" "compile"]}}
  :plugins [[org.clojars.brunchboy/lein-protobuf "0.4.3" :exclusions [leinjacker]]
            [codox "0.8.13"]
            [lein-ancient "0.6.7"]]

  :aliases {"gen" ["with-profile" "+gen,+dev" "run" "-m" "ola-clojure.src-generator"]}

  :codox {:src-dir-uri "http://github.com/brunchboy/ola-clojure/blob/master/"
          :src-linenum-anchor-prefix "L"
          :output-dir "target/doc"}
  :min-lein-version "2.0.0")
