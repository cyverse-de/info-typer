(ns info-typer.core-test
  (:require [clojure.test :refer :all]
            [info-typer.config :as config]
            [info-typer.core :refer :all]))

(defn with-empty-config [f]
  (require 'info-typer.config :reload)
  (config/load-config-from-file "conf/test/empty.properties")
  (f))

(use-fixtures :once with-empty-config)

(deftest test-config-defaults
  (testing "default configuration settings"
    (is (= (config/environment-name) "docker-compose"))
    (is (= (config/garnish-type-attribute) "ipc-filetype"))
    (is (= (config/filetype-read-amount) 1024))
    (is (= (config/irods-host) "irods"))
    (is (= (config/irods-port) "1247"))
    (is (= (config/irods-user) "rods"))
    (is (= (config/irods-pass) "notprod"))
    (is (= (config/irods-zone) "iplant"))
    (is (= (config/irods-home) "/iplant/home"))
    (is (= (config/irods-resc) ""))
    (is (= (config/irods-max-retries) 10))
    (is (= (config/irods-retry-sleep) 1000))
    (is (true? (config/irods-use-trash)))
    (is (= (config/amqp-uri) "amqp://guest:guest@rabbit:5672/"))
    (is (= (config/amqp-retry-sleep) 10000))
    (is (= (config/amqp-exchange) "irods"))
    (is (= (config/amqp-exchange-type) "topic"))
    (is (true? (config/amqp-exchange-durable?)))
    (is (false? (config/amqp-exchange-auto-delete?)))))
