(ns info-typer.messaging
  (:require [clojure.string :as string]
            [clojure.tools.logging :as log]
            [cheshire.core :as json]
            [slingshot.slingshot :refer [throw+ try+]]
            [clj-jargon.by-uuid :as uuid]
            [clj-jargon.init :refer [with-jargon]]
            [clj-jargon.metadata :as meta]
            [service-logging.thread-context :as tc]
            [clojure-commons.error-codes :as ce]
            [langohr.basic :as lb]
            [info-typer.amqp :as amqp]
            [info-typer.config :as cfg]
            [info-typer.irods :as irods])
  (:import [clojure.lang IPersistentMap]
           [java.util UUID]))


(defn- get-file-id
  [payload]
  (try+
    (-> payload (json/parse-string true) :entity UUID/fromString)
    (catch Throwable _
      (throw+ {:error_code "ERR_INVALID_JSON" :payload payload}))))

(defn- filetype-message-handler
  "Handle a message. Retry once in the case of missing files or exceptions."
  [irods-cfg channel {:keys [delivery-tag redelivery?] :as metadata} payload]
  (try+
   (with-jargon irods-cfg [cm]
     (let [id   (get-file-id payload)
           path (uuid/get-path cm id)]
       (if (nil? path)
         (do
           (lb/reject channel delivery-tag (not redelivery?))
           (log/error "file" id "does not exist"))
         (do (if (meta/attribute? cm path (cfg/garnish-type-attribute))
               (log/warn "file" id "already has an attribute called" (cfg/garnish-type-attribute))
               (let [detected-type (irods/content-type cm path)
                     ctype (if (or (nil? detected-type) (string/blank? detected-type))
                             (do (log/debug "type was not detected for file" id ", adding type unknown") "unknown") detected-type)]
                    ; Double-check an attribute hasn't been added during the time it took for us to detect.
                 (when-not (meta/attribute? cm path (cfg/garnish-type-attribute))
                   (log/info "adding type" ctype "to file" id)
                   (meta/add-metadata cm path (cfg/garnish-type-attribute) ctype "ipc-info-typer")
                   (log/debug "done adding type" ctype "to file" id))))
             (lb/ack channel delivery-tag)))))
   (catch ce/error? err
     (lb/reject channel delivery-tag (not redelivery?))
     (log/error (ce/format-exception (:throwable &throw-context))))
   (catch Exception e
     (lb/reject channel delivery-tag (not redelivery?))
     (log/error (ce/format-exception e)))))


(defn- dataobject-added
  "Event handler for 'data-object.added' events."
  [irods-cfg channel metadata ^bytes payload]
  (filetype-message-handler irods-cfg channel metadata (String. payload "UTF-8")))


(def routing-functions
  {"data-object.add" dataobject-added})

(defn- message-handler
  "A langohr compatible message callback. This will push out message handling to other functions
   based on the value of the routing-key. This will allow us to pull in the full iRODS event
   firehose later and delegate execution to handlers without having to deal with AMQPs object
   hierarchy.

   The payload is passed to handlers as a byte stream. That should theoretically give us the
   ability to handle binary data arriving in messages, even though that doesn't seem likely."
  [irods-cfg channel {:keys [routing-key content-type delivery-tag type] :as metadata} ^bytes payload]
  (tc/with-logging-context {:amqp-delivery-tag delivery-tag}
    (log/info (format "[amqp/message-handler] [%s] [%s]" routing-key (String. payload "UTF-8")))
    (when-let [handler (get routing-functions routing-key)]
      (handler irods-cfg channel metadata payload))))

(defn typer-config-map
  "Constructs a amqp configuration map that can be passed to
   the configure function. Returns a map in the format:
   {
     :uri                   string
     :exchange              string
     :exchange-type         string
     :exchange-durable?     bool
     :exchange-auto-delete? bool
     :queue-name            string
     :queue-durable?        bool
     :queue-exclusive?      bool
     :queue-auto-delete?    bool
     :qos                   integer
   }"
  []
  {:uri                   (cfg/amqp-uri)
   :exchange              (cfg/amqp-exchange)
   :exchange-type         (cfg/amqp-exchange-type)
   :exchange-durable?     (cfg/amqp-exchange-durable?)
   :exchange-auto-delete? (cfg/amqp-exchange-auto-delete?)
   :queue-name            (str "info-typer." (cfg/environment-name))
   :queue-durable?        true
   :queue-exclusive?      false
   :queue-auto-delete?    false
   :qos                   (cfg/amqp-qos)})


(defn receive
  "Configures the AMQP connection. This is wrapped in a function because we want to start
   the connection in a new thread."
  [^IPersistentMap irods-cfg]
  (try
    (amqp/configure (partial message-handler irods-cfg) (typer-config-map) (keys routing-functions))
    (catch Exception e
      (log/error "[amqp/messaging-initialization]" (ce/format-exception e)))))
