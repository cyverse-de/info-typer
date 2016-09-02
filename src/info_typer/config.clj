(ns info-typer.config
  (:use [slingshot.slingshot :only [throw+]])
  (:require [clojure-commons.config :as cc]
            [clojure-commons.error-codes :as ce]))


(def ^:private config-valid
  "A ref for storing a configuration validity flag."
  (ref true))


(def ^:private configs
  "A ref for storing the symbols used to get configuration settings."
  (ref []))


(def ^:private props
  "A ref for storing the configuration properties."
  (ref nil))


(cc/defprop-optstr environment-name
  "The short name of the environment info-typer is running in. Used for defining the name of the queue it listens on."
  [props config-valid configs]
  "info-typer.environment-name" "docker-compose")


(cc/defprop-optstr garnish-type-attribute
  "The value that goes in the attribute column for AVUs that define a file type."
  [props config-valid configs]
  "info-typer.type-attribute" "ipc-filetype")


(cc/defprop-optlong filetype-read-amount
  "The size, in bytes as a long, of the sample read from iRODS"
  [props config-valid configs]
  "info-typer.filetype-read-amount" 1024)


(cc/defprop-optstr irods-host
  "Returns the iRODS hostname/IP address."
  [props config-valid configs]
  "info-typer.irods.host" "irods")


(cc/defprop-optstr irods-port
  "Returns the iRODS port."
  [props config-valid configs]
  "info-typer.irods.port" "1247")


(cc/defprop-optstr irods-user
  "Returns the user that porklock should connect as."
  [props config-valid configs]
  "info-typer.irods.user" "rods")


(cc/defprop-optstr irods-pass
  "Returns the iRODS user's password."
  [props config-valid configs]
  "info-typer.irods.pass" "notprod")


(cc/defprop-optstr irods-zone
  "Returns the iRODS zone."
  [props config-valid configs]
  "info-typer.irods.zone" "iplant")


(cc/defprop-optstr irods-home
  "Returns the path to the home directory in iRODS. Usually /iplant/home"
  [props config-valid configs]
  "info-typer.irods.home" "/iplant/home")


(cc/defprop-optstr irods-resc
  "Returns the iRODS resource."
  [props config-valid configs]
  "info-typer.irods.resc" "")


(cc/defprop-optint irods-max-retries
  "The number of retries for failed operations."
  [props config-valid configs]
  "info-typer.irods.max-retries" 10)


(cc/defprop-optint irods-retry-sleep
  "The number of milliseconds to sleep between retries."
  [props config-valid configs]
  "info-typer.irods.retry-sleep" 1000)


(cc/defprop-optboolean irods-use-trash
  "Toggles whether to move deleted files to the trash first."
  [props config-valid configs]
  "info-typer.irods.use-trash" true)


(cc/defprop-optstr amqp-host
  "The hostname for the AMQP broker"
  [props config-valid configs]
  "info-typer.amqp.host" "rabbit")


(cc/defprop-optint amqp-port
  "The port for the AMQP broker"
  [props config-valid configs]
  "info-typer.amqp.port" 5672)


(cc/defprop-optstr amqp-user
  "The username for AMQP broker"
  [props config-valid configs]
  "info-typer.amqp.user" "guest")


(cc/defprop-optstr amqp-pass
  "The password for AMQP broker"
  [props config-valid configs]
  "info-typer.amqp.pass" "guest")


(cc/defprop-optint amqp-retry-sleep
  "The number of milliseconds to sleep between connection retries."
  [props config-valid configs]
  "info-typer.amqp.retry-sleep" 10000)


(cc/defprop-optstr amqp-exchange
  "The exchange to listen to for iRODS updates."
  [props config-valid configs]
  "info-typer.amqp.exchange" "irods")


(cc/defprop-optstr amqp-exchange-type
  "The exchange type for the iRODS updates"
  [props config-valid configs]
  "info-typer.amqp.exchange.type" "topic")


(cc/defprop-optboolean amqp-exchange-durable?
  "Toggles whether or not the rabbitmq exchange is durable."
  [props config-valid configs]
  "info-typer.amqp.exchange.durable" true)


(cc/defprop-optboolean amqp-exchange-auto-delete?
  "Toggles whether to auto-delete the exchange or not."
  [props config-valid configs]
  "info-typer.amqp.exchange.auto-delete" false)

(cc/defprop-optint amqp-qos
  "The number of messages to allow to be delivered to this client at once without acknowledgement."
  [props config-valid configs]
  "info-typer.amqp.qos" 100)

(defn- exception-filters
  []
  (remove nil? [(irods-pass) (irods-user)]))


(defn- validate-config
  "Validates the configuration settings after they've been loaded."
  []
  (when-not (cc/validate-config configs config-valid)
    (throw+ {:error_code ce/ERR_CONFIG_INVALID})))


(defn load-config-from-file
  "Loads the configuration settings from a file."
  [cfg-path]
  (cc/load-config-from-file cfg-path props)
  (cc/log-config props :filters [#"irods\.user"])
  (validate-config)
  (ce/register-filters (exception-filters)))
