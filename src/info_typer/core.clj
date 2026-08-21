(ns info-typer.core
  (:gen-class)
  (:require [me.raynes.fs :as fs]
            [clojure.tools.logging :as log]
            [common-cli.core :as ccli]
            [info-typer.config :as cfg]
            [info-typer.messaging :as messages]
            [info-typer.routes :as routes]
            [info-typer.service :as svc]
            [info-typer.util.irods :as irods]
            [ring.adapter.jetty :as jetty]
            [service-logging.thread-context :as tc]))


(defn- cli-options
  []
  [["-c" "--config PATH" "Path to the config file"
    :default "/etc/iplant/de/info-typer.properties"]
   ["-v" "--version" "Print out the version number."]
   ["-h" "--help"]])


(def ^:private consumer-retry-ms
  "How long to wait before starting the AMQP consumer again after it stops."
  30000)

(defn- consume-forever
  "Runs the AMQP consumer, restarting it if it ever stops.

   The supervision is the point. amqp/attempt-connect only retries SocketException, so an
   unresolvable broker host, a rejected credential or a bad vhost all escape it -- and before
   this service had an HTTP API, that killed the last non-daemon thread and the process
   exited, which Kubernetes noticed and retried. Jetty holds the process open now, so without
   this the pod would stay up and healthy while silently typing nothing."
  []
  (loop []
    (try
      (messages/receive (irods/jargon-cfg))
      (catch Throwable t
        (log/error t "the AMQP consumer stopped; uploaded files will not be typed until it"
                   "reconnects. This usually means the broker is unreachable or the"
                   "credentials in info-typer.amqp.uri are wrong.")))
    (cfg/set-amqp-connected! false)
    (Thread/sleep consumer-retry-ms)
    (recur)))

(defn- start-consumer
  "Starts the AMQP consumer on its own supervised thread.

   Started before jetty and separately from it: a broker that is unreachable must not stop the
   HTTP API coming up, because the file type endpoints answer without it."
  []
  (.start (Thread. ^Runnable consume-forever "info-typer-amqp")))


(defn -main
  [& args]
  (tc/with-logging-context svc/svc-info
    (let [{:keys [options]} (ccli/handle-args svc/svc-info args cli-options)]
      (when-not (fs/exists? (:config options))
        (ccli/exit 1 "The config file does not exist."))
      (when-not (fs/readable? (:config options))
        (ccli/exit 1 "The config file is not readable."))
      (cfg/load-config-from-file (:config options))

      (start-consumer)
      (jetty/run-jetty routes/app {:port (cfg/listen-port) :join? true}))))
