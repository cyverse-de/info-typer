(ns info-typer.util.irods
  (:require [clj-jargon.init :as init]
            [info-typer.config :as cfg]))

(def jargon-cfg
  "The iRODS connection settings, built once.

   Memoized because the HTTP API builds one per request and the AMQP consumer holds one for
   the life of the process; deriving it each time would re-read the configuration on every
   file-type lookup. It lives here rather than in core so that both callers reach the same
   one."
  (memoize
   (fn []
     (init/init (cfg/irods-host)
                (cfg/irods-port)
                (cfg/irods-user)
                (cfg/irods-pass)
                (cfg/irods-home)
                (cfg/irods-zone)
                (cfg/irods-resc)
                :max-retries (cfg/irods-max-retries)
                :retry-sleep (cfg/irods-retry-sleep)
                :use-trash   (cfg/irods-use-trash)))))

(defmacro with-jargon-exceptions
  "Opens an iRODS connection, translating jargon's exceptions into the DE's error envelope."
  [[cm-sym] & body]
  `(init/with-jargon (jargon-cfg) [~cm-sym]
     ~@body))
