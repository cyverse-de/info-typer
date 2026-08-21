(ns info-typer.service
  (:require [cheshire.core :as cheshire]
            [clojure-commons.error-codes :as ce]))

(def ^:private default-content-type
  "application/json; charset=utf-8")

(defn unrecognized-path-response
  "The body sent for a path this service does not serve.

   Deliberately not the error_code envelope: data-info answers an unrecognised path with this
   shape, and the DE's clients already know it."
  []
  (cheshire/encode {:success false :reason "unrecognized service path"}))

(defn success-response
  [body]
  {:status  200
   :body    body
   :headers {"Content-Type" default-content-type}})

(defn trap
  "Runs a service call, rendering a thrown error map as the DE's error envelope."
  [action func & args]
  (ce/trap action #(success-response (apply func args))))
