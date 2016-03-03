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


(cc/defprop-str environment-name
  "The short name of the environment info-typer is running in. Used for defining the name of the queue it listens on."
  [props config-valid configs]
  "info-typer.environment-name")


(cc/defprop-str garnish-type-attribute
  "The value that goes in the attribute column for AVUs that define a file type."
  [props config-valid configs]
  "info-typer.type-attribute")


(cc/defprop-long filetype-read-amount
  "The size, in bytes as a long, of the sample read from iRODS"
  [props config-valid configs]
  "info-typer.filetype-read-amount")


(cc/defprop-str irods-host
  "Returns the iRODS hostname/IP address."
  [props config-valid configs]
  "info-typer.irods.host")


(cc/defprop-str irods-port
  "Returns the iRODS port."
  [props config-valid configs]
  "info-typer.irods.port")


(cc/defprop-str irods-user
  "Returns the user that porklock should connect as."
  [props config-valid configs]
  "info-typer.irods.user")


(cc/defprop-str irods-pass
  "Returns the iRODS user's password."
  [props config-valid configs]
  "info-typer.irods.pass")


(cc/defprop-str irods-zone
  "Returns the iRODS zone."
  [props config-valid configs]
  "info-typer.irods.zone")


(cc/defprop-str irods-home
  "Returns the path to the home directory in iRODS. Usually /iplant/home"
  [props config-valid configs]
  "info-typer.irods.home")


(cc/defprop-optstr irods-resc
  "Returns the iRODS resource."
  [props config-valid configs]
  "info-typer.irods.resc")


(cc/defprop-int irods-max-retries
  "The number of retries for failed operations."
  [props config-valid configs]
  "info-typer.irods.max-retries")


(cc/defprop-int irods-retry-sleep
  "The number of milliseconds to sleep between retries."
  [props config-valid configs]
  "info-typer.irods.retry-sleep")


(cc/defprop-boolean irods-use-trash
  "Toggles whether to move deleted files to the trash first."
  [props config-valid configs]
  "info-typer.irods.use-trash")


(cc/defprop-str amqp-host
  "The hostname for the AMQP broker"
  [props config-valid configs]
  "info-typer.amqp.host")


(cc/defprop-int amqp-port
  "The port for the AMQP broker"
  [props config-valid configs]
  "info-typer.amqp.port")


(cc/defprop-str amqp-user
  "The username for AMQP broker"
  [props config-valid configs]
  "info-typer.amqp.user")


(cc/defprop-str amqp-pass
  "The password for AMQP broker"
  [props config-valid configs]
  "info-typer.amqp.pass")


(cc/defprop-int amqp-retry-sleep
  "The number of milliseconds to sleep between connection retries."
  [props config-valid configs]
  "info-typer.amqp.retry-sleep")


(cc/defprop-str amqp-exchange
  "The exchange to listen to for iRODS updates."
  [props config-valid configs]
  "info-typer.amqp.exchange")


(cc/defprop-str amqp-exchange-type
  "The exchange type for the iRODS updates"
  [props config-valid configs]
  "info-typer.amqp.exchange.type")


(cc/defprop-boolean amqp-exchange-durable?
  "Toggles whether or not the rabbitmq exchange is durable."
  [props config-valid configs]
  "info-typer.amqp.exchange.durable")


(cc/defprop-boolean amqp-exchange-auto-delete?
  "Toggles whether to auto-delete the exchange or not."
  [props config-valid configs]
  "info-typer.amqp.exchange.auto-delete")

(cc/defprop-int amqp-qos
  "The number of messages to allow to be delivered to this client at once without acknowledgement."
  [props config-valid configs]
  "info-typer.amqp.qos")

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
