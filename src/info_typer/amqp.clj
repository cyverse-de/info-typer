(ns info-typer.amqp
  (:require [clojure.tools.logging       :as log]
            [langohr.core                :as rmq]
            [langohr.basic               :as lb]
            [langohr.channel             :as lch]
            [langohr.exchange            :as le]
            [langohr.queue               :as lq]
            [langohr.consumers           :as lc]
            [info-typer.config           :as cfg])
  (:import [java.net SocketException]))


(defn- attempt-connect
  [conn-map]
  (try
    (let [conn (rmq/connect conn-map)]
      (log/info "Connected to the AMQP broker.")
      conn)
    (catch SocketException e
      (log/warn "Failed to connect to the AMQP broker."))))


(defn get-connection
  "Sets the amqp-conn ref if necessary and returns it."
  [conn-map]
  (if-let [conn (attempt-connect conn-map)]
    conn
    (do
      (Thread/sleep (cfg/amqp-retry-sleep))
      (recur conn-map))))


(defn- exchange?
  "Returns a boolean indicating whether an exchange exists."
  [channel exchange]
  (try
    (le/declare-passive channel exchange)
    true
    (catch java.io.IOException _ false)))


(defn declare-exchange
  "Declares an exchange if it doesn't already exist."
  [channel exchange type & {:keys [durable auto-delete]
                            :or {durable     false
                                 auto-delete false}}]
  (when-not (exchange? channel exchange)
    (le/declare channel exchange type {:durable durable :auto-delete auto-delete}))
  channel)


(defn declare-queue
  "Declares a queue by name, returning its name."
  [channel queue-name]
  (:queue (lq/declare channel queue-name {:durable true :auto-delete false :exclusive false})))


(defn bind
  "Binds a queue to an exchange."
  [channel queue exchange routing-key]
  (lq/bind channel queue exchange {:routing-key routing-key})
  channel)


(defn subscribe
  "Registers a callback function that fires every time a message enters the specified queue."
  [channel queue msg-fn & {:keys [auto-ack]
                           :or   {auto-ack true}}]
  (lc/subscribe channel queue msg-fn {:auto-ack auto-ack})
  channel)


(defn channel
  [cfg-map]
  (let [ch (lch/open (get-connection (select-keys cfg-map [:uri])))]
    (lb/qos ch (:qos cfg-map))
    ch))

(defn queue
  [chan cfg-map]
  (lq/declare chan
              (:queue-name cfg-map)
              {:exclusive   (:queue-exclusive? cfg-map)
               :durable     (:queue-durable? cfg-map)
               :auto-delete (:queue-auto-delete? cfg-map)}))

(defn configure
  "Sets up a channel, exchange, and queue, with the queue bound to the exchange
   and 'msg-fn' registered as the callback."
  [msg-fn cfg-map topics]
  (log/info "configuring events AMQP connection")
  (let [chan (channel cfg-map)
        q    (queue chan cfg-map)]
    (declare-exchange
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

    (subscribe chan (:queue-name cfg-map) msg-fn :auto-ack false)))
