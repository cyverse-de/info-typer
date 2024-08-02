(use '[clojure.java.shell :only (sh)])
(require '[clojure.string :as string])

(defn git-ref
  []
  (or (System/getenv "GIT_COMMIT")
      (string/trim (:out (sh "git" "rev-parse" "HEAD")))
      ""))

(defproject org.cyverse/info-typer "3.0.1-SNAPSHOT"
  :description "An AMQP based info type detection service for iRODS"
  :url "https://github.com/cyverse-de/info-typer"
  :license {:name "BSD"
            :url "http://iplantcollaborative.org/sites/default/files/iPLANT-LICENSE.txt"}
  :manifest {"Git-Ref" ~(git-ref)}
  :uberjar-name "info-typer-standalone.jar"
  :dependencies [[org.clojure/clojure "1.11.3"]
                 [com.novemberain/langohr "3.6.1"]
                 [me.raynes/fs "1.4.6"]
                 [org.cyverse/clj-jargon "3.1.1"
                   :exclusions [[org.slf4j/slf4j-log4j12]
                                [log4j]]]
                 [org.cyverse/clojure-commons "3.0.9" :exclusions [commons-logging]]
                 [org.cyverse/common-cli "2.8.2"]
                 [org.cyverse/heuristomancer "2.8.7"]
                 [org.cyverse/service-logging "2.8.4"]
                 [org.cyverse/otel "0.2.6"]]
  :eastwood {:exclude-namespaces [:test-paths]
             :linters [:wrong-arity :wrong-ns-form :wrong-pre-post :wrong-tag :misplaced-docstrings]}
  :main ^:skip-aot info-typer.core
  :profiles {:dev     {:resource-paths ["conf/test"]
                       :jvm-opts ["-Dotel.javaagent.enabled=false"]}
             :uberjar {:aot :all}}
  :plugins [[jonase/eastwood "1.4.3"]
            [lein-ancient "0.7.0"]
            [test2junit "1.4.4"]]
  :uberjar-exclusions [#"LICENSE" #"NOTICE"]
  :jvm-opts ["-Dlogback.configurationFile=/etc/iplant/de/logging/info-typer-logging.xml"
             "-javaagent:./opentelemetry-javaagent.jar"
             "-Dotel.resource.attributes=service.name=info-typer"])
