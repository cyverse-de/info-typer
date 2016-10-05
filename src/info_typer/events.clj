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

(defn events-config-map
  "Constructs a amqp configuration map that can be passed to
   the configure function. Returns a map in the format:
   {
     :uri                   string
     :exchange              string
     :exchange-type         string
     :exchange-durable?     bool
     :exchange-auto-delete? bool
     :queue-name            string
     :queue-exclusive?      bool
     :queue-auto-delete?    bool
     :qos                   integer
   }"
  []
  {:uri                   (cfg/amqp-events-uri)
   :exchange              (cfg/amqp-events-exchange)
   :exchange-type         (cfg/amqp-events-exchange-type)
   :exchange-durable?     (cfg/amqp-events-exchange-durable?)
   :exchange-auto-delete? (cfg/amqp-events-exchange-auto-delete?)
   :queue-name            ""
   :queue-exclusive?      true
   :queue-auto-delete?    true
   :qos                   (cfg/amqp-qos)})

(defn channel
  [cfg-map]
  (let [ch (lch/open (amqp/get-connection (select-keys cfg-map [:uri])))]
    (lb/qos ch (:qos cfg-map))
    ch))

(defn queue
  [chan cfg-map]
  (lq/declare chan
              (:queue-name cfg-map)
              {:exclusive   (:queue-exclusive? cfg-map)
               :auto-delete (:queue-auto-delete? cfg-map)}))

(defn configure
  "Sets up a channel, exchange, and queue, with the queue bound to the exchange
   and 'msg-fn' registered as the callback."
  [msg-fn cfg-map topics]
  (log/info "configuring events AMQP connection")
  (let [chan (channel cfg-map)
        q    (queue chan cfg-map)]
    (amqp/declare-exchange
     chan
     (:exchange cfg-map)
     (:exchange-type cfg-map)
     :durable (:exchange-durable? cfg-map)
     :auto-delete (:exchange-auto-delete? cfg-map))

    (doseq [topic topics]
      (lq/bind
       chan
       (:queue-name cfg-map)
       (:exchange cfg-map)
       {:routing-key topic}))

    (amqp/subscribe chan (:queue-name cfg-map) msg-fn :auto-ack false)))

(defn receive
  "Configures the AMQP connection. This is wrapped in a function because we want
   to start the connection in a new thread."
  []
  (try
    (configure message-router (events-config-map) (keys routing-functions))
    (catch Exception e
      (log/error "[amqp/messaging-initialization]" (ce/format-exception e)))))
