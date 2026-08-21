(ns info-typer.util.irods
  (:require [clj-jargon.init :as init]
            [clojure-commons.error-codes :as error]
            [info-typer.config :as cfg]
            [slingshot.slingshot :refer [throw+ try+]])
  (:import [java.io IOException]
           [org.irods.jargon.core.exception JargonException]))

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

(defmacro catch-jargon-io-exceptions
  "Renders an unreachable iRODS as ERR_UNAVAILABLE rather than an unchecked exception.

   Copied from data-info's macro of the same name, wording included. These endpoints moved
   out of that service and terrain still branches on the code they return, so an iRODS outage
   -- much the most common way they fail -- has to keep reporting itself the same way."
  [& body]
  `(try+
    (do ~@body)
    (catch JargonException e#
      (if (instance? IOException (.getCause e#))
        (throw+ {:error_code error/ERR_UNAVAILABLE
                 :reason     (str "iRODS is unavailable: " e#)})
        (throw+)))))

(defmacro with-jargon-exceptions
  "Opens an iRODS connection, translating jargon's exceptions into the DE's error envelope."
  [[cm-sym] & body]
  `(catch-jargon-io-exceptions
    (init/with-jargon (jargon-cfg) [~cm-sym]
      ~@body)))
