(ns info-typer.core
  (:gen-class)
  (:require [me.raynes.fs :as fs]
            [common-cli.core :as ccli]
            [info-typer.config :as cfg]
            [info-typer.messaging :as messages]
            [info-typer.routes :as routes]
            [info-typer.util.irods :as irods]
            [ring.adapter.jetty :as jetty]
            [service-logging.thread-context :as tc]))


(def ^:private svc-info
  {:desc     "DE message handling service for file info type detection"
   :app-name "info-typer"
   :group-id "org.cyverse"
   :art-id   "info-typer"
   :service  "info-typer"})


(defn- cli-options
  []
  [["-c" "--config PATH" "Path to the config file"
    :default "/etc/iplant/de/info-typer.properties"]
   ["-v" "--version" "Print out the version number."]
   ["-h" "--help"]])


(defn- start-consumer
  "Starts the AMQP consumer on its own thread.

   It used to own the main thread, and the process only stayed alive because langohr's
   consumer thread is not a daemon. Jetty owns the main thread now, so this is started first
   and explicitly: a broker that is unreachable must not stop the HTTP API from coming up,
   because the file type endpoints answer without it."
  []
  (.start (Thread. ^Runnable #(messages/receive (irods/jargon-cfg)) "info-typer-amqp")))


(defn -main
  [& args]
  (tc/with-logging-context svc-info
    (let [{:keys [options]} (ccli/handle-args svc-info args cli-options)]
      (when-not (fs/exists? (:config options))
        (ccli/exit 1 "The config file does not exist."))
      (when-not (fs/readable? (:config options))
        (ccli/exit 1 "The config file is not readable."))
      (cfg/load-config-from-file (:config options))

      (start-consumer)
      (jetty/run-jetty routes/app {:port (cfg/listen-port) :join? true}))))
