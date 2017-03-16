(use '[clojure.java.shell :only (sh)])
(require '[clojure.string :as string])

(defn git-ref
  []
  (or (System/getenv "GIT_COMMIT")
      (string/trim (:out (sh "git" "rev-parse" "HEAD")))
      ""))

(defproject org.cyverse/info-typer "2.8.1-SNAPSHOT"
  :description "An AMQP based info type detection service for iRODS"
  :url "https://github.com/cyverse-de/info-typer"
  :license {:name "BSD"
            :url "http://iplantcollaborative.org/sites/default/files/iPLANT-LICENSE.txt"}
  :manifest {"Git-Ref" ~(git-ref)}
  :uberjar-name "info-typer-standalone.jar"
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [com.novemberain/langohr "3.6.1"]
                 [me.raynes/fs "1.4.6"]
                 [org.cyverse/clj-jargon "2.8.0"
                   :exclusions [[org.slf4j/slf4j-log4j12]
                                [log4j]]]
                 [org.cyverse/clojure-commons "2.8.0" :exclusions [commons-logging]]
                 [org.cyverse/common-cli "2.8.1"]
                 [org.cyverse/heuristomancer "2.8.2"]
                 [org.cyverse/service-logging "2.8.0"]
                 [org.cyverse/event-messages "0.0.1"]]
  :eastwood {:exclude-namespaces [:test-paths]
             :linters [:wrong-arity :wrong-ns-form :wrong-pre-post :wrong-tag :misplaced-docstrings]}
  :main ^:skip-aot info-typer.core
  :profiles {:dev     {:resource-paths ["conf/test"]}
             :uberjar {:aot :all}}
  :plugins [[jonase/eastwood "0.2.3"]
            [test2junit "1.1.3"]]
  :uberjar-exclusions [#"LICENSE" #"NOTICE"]
  :jvm-opts ["-Dlogback.configurationFile=/etc/iplant/de/logging/info-typer-logging.xml"])
