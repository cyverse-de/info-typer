(ns info-typer.util.irods
  (:require [clj-jargon.init :as init]
            [clojure-commons.error-codes :as error]
            [clojure.tools.logging :as log]
            [info-typer.config :as cfg]
            [slingshot.slingshot :refer [throw+ try+]])
  (:import [java.io IOException]
           [org.irods.jargon.core.exception JargonException]))

(def jargon-cfg
  "The iRODS connection settings, built once."
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
  "Renders an unreachable iRODS as ERR_UNAVAILABLE rather than an unchecked exception."
  [& body]
  `(try+
    (do ~@body)
    (catch JargonException e#
      (if (instance? IOException (.getCause e#))
        (do (log/error e# "iRODS is unavailable; this usually means the iRODS server is down,"
                       "unreachable from this service, or refusing new connections")
            (throw+ {:error_code error/ERR_UNAVAILABLE
                     :reason     (str "iRODS is unavailable: " e#)}))
        (throw+)))))

(defmacro with-jargon-exceptions
  "Opens an iRODS connection, translating jargon's exceptions into the DE's error envelope."
  [[cm-sym] & body]
  `(catch-jargon-io-exceptions
    (init/with-jargon (jargon-cfg) [~cm-sym]
      ~@body)))
