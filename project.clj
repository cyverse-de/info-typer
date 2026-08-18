(require '[clojure.java.shell :refer (sh)])
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
  ;; Fail the build on a new dependency conflict rather than printing a
  ;; warning nobody reads.
  :pedantic? :abort
  ;; Records versions Leiningen already resolves, read off the resolved
  ;; classpath rather than copied from lein's "Consider using these
  ;; :managed-dependencies" hint -- that hint names the version that LOST the
  ;; conflict, so pasting it would be a silent upgrade.
  ;;
  ;; The jackson-* entries hold the family where clj-jargon puts it. jargon-core
  ;; is pinned :upgrade false at 4.3.7.0-RELEASE for iRODS compatibility and
  ;; brings jackson 2.14.1. jackson-core sits higher because cheshire needs it
  ;; there -- cheshire 6 throws at runtime against jackson-core 2.14.1. This
  ;; asymmetry is deliberate and matches what main already resolved; do not
  ;; "tidy" it by dropping core to match the rest. Unifying means moving jargon.
  :managed-dependencies [[cheshire "5.13.0"]
                         [clj-http "3.13.0"]
                         [com.fasterxml.jackson.core/jackson-annotations "2.14.1"]
                         [com.fasterxml.jackson.core/jackson-core "2.17.0"]
                         [com.fasterxml.jackson.core/jackson-databind "2.14.1"]
                         [com.fasterxml.jackson.dataformat/jackson-dataformat-cbor "2.14.1"]
                         [com.fasterxml.jackson.dataformat/jackson-dataformat-smile "2.14.1"]
                         [com.google.guava/guava "16.0.1"]
                         [commons-codec "1.16.1"]
                         [org.apache.commons/commons-compress "1.8"]
                         [prismatic/schema "1.1.12"]
                         [ring/ring-codec "1.1.0"]
                         [ring/ring-core "1.6.3"]]
  :dependencies [[org.clojure/clojure "1.12.5"]
                 [com.novemberain/langohr "5.6.0" :exclusions [org.slf4j/slf4j-api]]
                 [me.raynes/fs "1.4.6"]
                 [org.cyverse/clj-jargon "3.1.6"
                   :exclusions [[org.slf4j/slf4j-log4j12]
                                [log4j]]]
                 [org.cyverse/clojure-commons "3.0.13" :exclusions [commons-logging]]
                 [org.cyverse/common-cli "2.8.3"]
                 [org.cyverse/heuristomancer "2.8.8"]
                 [org.cyverse/service-logging "2.8.6"]
                 [org.slf4j/slf4j-api "2.0.18"]]
  :eastwood {:exclude-namespaces [:test-paths]
             :linters [:wrong-arity :wrong-ns-form :wrong-pre-post :wrong-tag :misplaced-docstrings]}
  :main ^:skip-aot info-typer.core
  :profiles {:dev     {:resource-paths ["conf/test"]}
             :uberjar {:aot :all}}
  :plugins [[jonase/eastwood "1.4.3"]
            [lein-ancient "1.0.0"]
            [test2junit "1.4.4"]]
  :uberjar-exclusions [#"LICENSE" #"NOTICE"]
  :jvm-opts ["-Dlogback.configurationFile=/etc/iplant/de/logging/info-typer-logging.xml"])
