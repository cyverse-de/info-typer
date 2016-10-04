(ns info-typer.events
  (:require [info-typer.amqp             :as amqp]
            [clojure.tools.logging       :as log]
            [langohr.core                :as rmq]
            [langohr.basic               :as lb]
            [langohr.channel             :as lch]
            [langohr.exchange            :as le]
            [langohr.queue               :as lq]
            [langohr.consumers           :as lc]
            [info-typer.config           :as cfg]
            [service-logging.thread-context :as tc]
            [clojure-commons.error-codes :as ce])
  (:import [org.cyverse.events.ping PingMessages
                                    PingMessages$Ping
                                    PingMessages$Pong]
           [com.google.protobuf.util JsonFormat]))

(def pong-key "events.info-typer.pong")

(defn ping-handler
  [channel metadata payload]
  (lb/publish channel (cfg/amqp-events-exchange) pong-key
    (.print (JsonFormat/printer)
      (.. (PingMessages$Pong/newBuilder)
        (setPongFrom "info-typer")
        (build)))))

(def routing-functions
  {"events.info-typer.ping" ping-handler})

(defn message-router
  [channel {:keys [routing-key content-type delivery-tag type] :as metadata} ^bytes payload]
  (tc/with-logging-context {:amqp-delivery-tag delivery-tag}
    (log/info (format "[events/message-router] [%s] [%s]" routing-key (String. payload "UTF-8")))

    (when-let [handler (get routing-functions routing-key)]
      (handler channel metadata payload))))

(defn- connection-map
  "Returns a configuration map for the RabbitMQ connection."
  []
  {:host     (cfg/amqp-events-host)
   :port     (cfg/amqp-events-port)
   :username (cfg/amqp-events-user)
   :password (cfg/amqp-events-pass)})

(defn configure
  "Sets up a channel, exchange, and queue, with the queue bound to the exchange
   and 'msg-fn' registered as the callback."
  [msg-fn topics]
  (log/info "configuring events AMQP connection")
  (let [chan (lch/open (amqp/get-connection (connection-map)))
        q    (amqp/declare-queue chan (str "info-typer.events." (cfg/environment-name)))]
    (lb/qos chan (cfg/amqp-qos))

    (amqp/declare-exchange chan
     (cfg/amqp-events-exchange)
     (cfg/amqp-events-exchange-type)
     :durable (cfg/amqp-events-exchange-durable?)
     :auto-delete (cfg/amqp-events-exchange-auto-delete?))

    (doseq [topic topics]
      (amqp/bind chan q (cfg/amqp-events-exchange) topic))

    (amqp/subscribe chan q msg-fn :auto-ack false)))

(defn receive
  "Configures the AMQP connection. This is wrapped in a function because we want
   to start the connection in a new thread."
  []
  (try
    (configure message-router (keys routing-functions))
    (catch Exception e
      (log/error "[amqp/messaging-initialization]" (ce/format-exception e)))))
