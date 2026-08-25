(ns info-typer.amqp-test
  (:require [clojure.test :refer :all]
            [info-typer.amqp :as amqp])
  (:import [com.novemberain.langohr Connection]
           [com.rabbitmq.client ConnectionFactory]))

(deftest recovery-is-left-to-the-supervisor
  ;; The connection is built but never initialised, so nothing dials a broker. Asserting
  ;; against the connection rather than the settings map is the point: a misspelled option key
  ;; would leave langohr's own recovery on while the map still looked right.
  ;;
  ;; It has to stay off. While langohr recovers, the channel it handed out reports itself
  ;; closed, which is how info-typer.core decides the consumer has died -- so both would
  ;; reconnect, stranding a connection and adding a second consumer on every blip.
  (let [settings (#'amqp/connection-settings {:uri "amqp://guest:guest@rabbit:5672/" :qos 100})
        conn     (Connection. (ConnectionFactory.) settings)]
    (is (false? (.automaticRecoveryEnabled conn)))
    (is (false? (.automaticTopologyRecoveryEnabled conn)))
    (is (= "amqp://guest:guest@rabbit:5672/" (:uri settings)))
    (is (nil? (:qos settings)) "the qos is a channel setting and does not belong here")))
