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
      (cfg/set-amqp-connected! true)
      conn)
    (catch SocketException e
      (cfg/set-amqp-connected! false)
      (log/warn "Failed to connect to the AMQP broker; retrying. Until it succeeds, uploaded"
                "files will not be typed automatically, though the HTTP API still answers."))))


(defn- get-connection
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


(defn- declare-exchange
  "Declares an exchange if it doesn't already exist."
  [channel exchange type & {:keys [durable auto-delete]
                            :or {durable     false
                                 auto-delete false}}]
  (when-not (exchange? channel exchange)
    (le/declare channel exchange type {:durable durable :auto-delete auto-delete}))
  channel)


(defn- subscribe
  "Registers a callback function that fires every time a message enters the specified queue."
  [channel queue msg-fn & {:keys [auto-ack]
                           :or   {auto-ack true}}]
  (lc/subscribe channel queue msg-fn {:auto-ack auto-ack})
  channel)


(defn- connection-settings
  "Broker settings, with langohr's automatic recovery turned off.

   Recovery is this service's job, not langohr's. While langohr reconnects, the channel it
   handed out reports itself closed, which the supervisor reads as a dead consumer -- it then
   opens a second connection alongside the one langohr is quietly restoring, and both stay
   subscribed to the same queue."
  [cfg-map]
  (assoc (select-keys cfg-map [:uri])
         :automatically-recover          false
         :automatically-recover-topology false))


(defn- channel
  "Opens a connection and a channel on it, returning both."
  [cfg-map]
  (let [conn (get-connection (connection-settings cfg-map))
        ch   (lch/open conn)]
    (lb/qos ch (:qos cfg-map))
    [conn ch]))

(defn- queue
  [chan cfg-map]
  (lq/declare chan
              (:queue-name cfg-map)
              {:exclusive   (:queue-exclusive? cfg-map)
               :durable     (:queue-durable? cfg-map)
               :auto-delete (:queue-auto-delete? cfg-map)}))

(defn close-connection
  "Closes a broker connection, logging rather than throwing if it has already gone away."
  [conn]
  (try
    (rmq/close conn)
    (catch Exception e
      (log/warn e "failed to close the AMQP connection; it had probably already been torn down"
                "by the broker or by the network"))))


(defn configure
  "Sets up a channel, exchange, and queue, with the queue bound to the exchange
   and 'msg-fn' registered as the callback.

   Returns the connection and the channel. The caller owns both and has to close the
   connection when it is done with them."
  [msg-fn cfg-map topics]
  (log/info "configuring events AMQP connection")
  (let [[conn chan] (channel cfg-map)]
    (try
      (queue chan cfg-map)
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

      (subscribe chan (:queue-name cfg-map) msg-fn :auto-ack false)
      {:connection conn :channel chan}
      (catch Throwable t
        ;; Setup can fail with the connection already open -- a passive declare for an
        ;; exchange that does not exist takes the channel down, and the broker leaves the
        ;; connection running. The caller has nothing to close in that case, so it closes here
        ;; or not at all, and the supervisor's next attempt would strand another one.
        (close-connection conn)
        (throw t)))))
