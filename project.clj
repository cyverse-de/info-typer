(use '[clojure.java.shell :only (sh)])
(require '[clojure.string :as string])

(defn git-ref
  []
  (or (System/getenv "GIT_COMMIT")
      (string/trim (:out (sh "git" "rev-parse" "HEAD")))
      ""))

(defproject org.cyverse/info-typer "2.12.0-SNAPSHOT"
  :description "An AMQP based info type detection service for iRODS"
  :url "https://github.com/cyverse-de/info-typer"
  :license {:name "BSD"
            :url "http://iplantcollaborative.org/sites/default/files/iPLANT-LICENSE.txt"}
  :manifest {"Git-Ref" ~(git-ref)}
  :uberjar-name "info-typer-standalone.jar"
  :dependencies [[org.clojure/clojure "1.11.1"]
                 [com.novemberain/langohr "3.6.1"]
                 [me.raynes/fs "1.4.6"]
                 [org.cyverse/clj-jargon "3.1.0"
                   :exclusions [[org.slf4j/slf4j-log4j12]
                                [log4j]]]
                 [org.cyverse/clojure-commons "3.0.7" :exclusions [commons-logging]]
                 [org.cyverse/common-cli "2.8.1"]
                 [org.cyverse/heuristomancer "2.8.6"]
                 [org.cyverse/service-logging "2.8.3"]
                 [org.cyverse/otel "0.2.5"]
                 [net.logstash.logback/logstash-logback-encoder "7.4"]
                 [org.cyverse/event-messages "0.0.1"]]
  :eastwood {:exclude-namespaces [:test-paths]
             :linters [:wrong-arity :wrong-ns-form :wrong-pre-post :wrong-tag :misplaced-docstrings]}
  :main ^:skip-aot info-typer.core
  :profiles {:dev     {:resource-paths ["conf/test"]
                       :jvm-opts ["-Dotel.javaagent.enabled=false"]}
             :uberjar {:aot :all}}
  :plugins [[jonase/eastwood "1.4.2"]
            [lein-ancient "0.7.0"]
            [test2junit "1.1.3"]]
  :uberjar-exclusions [#"LICENSE" #"NOTICE"]
  :jvm-opts ["-Dlogback.configurationFile=/etc/iplant/de/logging/info-typer-logging.xml"
             "-javaagent:./opentelemetry-javaagent.jar"
             "-Dotel.resource.attributes=service.name=info-typer"])
