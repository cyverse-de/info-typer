(ns info-typer.routes
  (:use [common-swagger-api.schema]
        [common-swagger-api.schema.data :only [DataIdPathParam]]
        [common-swagger-api.schema.filetypes]
        [compojure.api.middleware :only [wrap-exceptions]]
        [service-logging.middleware :only [log-validation-errors add-user-to-context]])
  (:require [clojure-commons.exception :as cx]
            [clojure-commons.lcase-params :refer [wrap-lcase-params]]
            [clojure-commons.query-params :refer [wrap-query-params]]
            [compojure.route :as route]
            [info-typer.config :as config]
            [info-typer.filetypes :as filetypes]
            [info-typer.service :as svc]
            [ring.middleware.keyword-params :as params]
            [schema.core :as s]))

(s/defschema DetectedType
  {:type (describe String "The detected file type, or \"unknown\"")})

;; Repeated from the route below because the middleware that needs it runs before compojure
;; has routed the request. detect-reads-the-raw-request-body fails if the two drift apart.
(def ^:private detect-uri "/file-types/detect")

(defn- wrap-unparsed-detect-body
  "Takes the content type off a detect request so that nothing parses its body.

   compojure-api's parameter and format middleware both consume :body before a handler runs,
   and both sit outside the API handler, so the label has to come off out here."
  [handler]
  (fn [request]
    (if (and (= :post (:request-method request))
             (= detect-uri (:uri request)))
      (handler (-> request
                   (update :headers dissoc "content-type")
                   (dissoc :content-type)))
      (handler request))))

(defapi ^:private api-routes
  (swagger-routes
   {:ui   "/docs"
    :data {:info {:title       "Discovery Environment Info Typer API"
                  :description "Documentation for the Discovery Environment file type API"
                  :version     "1.0.0"}
           :tags [{:name "service-info" :description "Service Information"}
                  {:name "filetypes" :description "File Type Metadata"}
                  {:name "data-by-id" :description "File Type Operations (by ID)"}]}})

  (context "/" []
    :middleware [add-user-to-context
                 wrap-query-params
                 wrap-lcase-params
                 params/wrap-keyword-params
                 [wrap-exceptions cx/exception-handlers]
                 log-validation-errors]

    (GET "/" []
      :tags ["service-info"]
      :summary "Service Information"
      :description "Reports that the service is running, and whether its AMQP consumer is
                    currently connected. The AMQP connection is reported but does not decide
                    the response: the file type endpoints work without it, and failing here
                    would take them out of service too."
      (svc/success-response (assoc svc/svc-info :amqp (config/amqp-connected?))))

    (GET "/admin/config" []
      :tags ["service-info"]
      :summary "Service Configuration"
      :description "Lists the service's configuration, with anything secret masked."
      (svc/success-response (config/masked-config)))

    (GET "/file-types" [:as {uri :uri}]
      :tags ["filetypes"]
      :return TypesList
      :summary "List File Types"
      :description "Lists available file types supported by the underlying library heuristomancer."
      (svc/trap uri filetypes/type-list))

    (POST "/file-types/detect" []
      :tags ["filetypes"]
      :return DetectedType
      :summary "Detect a File Type"
      :description "Identifies the type of the raw bytes in the request body. Nothing is read
                    from or written to the data store, and no user is involved -- this exists
                    so that a caller holding the bytes can ask what they are.

                    The body is never parsed, whatever content type it carries."
      ;; Declared through :swagger rather than with :body, which would coerce the bytes
      ;; against the schema. The declaration matters: without it a client generated from this
      ;; spec sends no body at all, and inherits a JSON content type from the API-wide
      ;; consumes list.
      :swagger {:consumes   ["application/octet-stream"]
                :parameters {:body (describe (s/maybe s/Str) "The bytes to identify.")}}
      (fn [request]
        (svc/success-response {:type (filetypes/detect-type (:body request))})))

    (context "/data/:data-id" []
      :path-params [data-id :- DataIdPathParam]
      :tags ["data-by-id"]

      (PUT "/type" [:as {uri :uri}]
        :query [params StandardUserQueryParams]
        :body [body (describe FileType "The type to set.")]
        :return FileTypeReturn
        :summary "Set File Type"
        :description "Set a file's type to a specific value, or unset it with an empty string.

                      Error codes: ERR_NOT_OWNER, ERR_BAD_OR_MISSING_FIELD, ERR_DOES_NOT_EXIST,
                      ERR_NOT_A_USER, ERR_NOT_A_FILE"
        (svc/trap uri filetypes/set-file-type (:user params) data-id (:type body)))

      (POST "/type" [:as {uri :uri}]
        :query [params StandardUserQueryParams]
        :return FileTypeReturn
        :summary "Detect and Set File Type"
        :description "Re-detects a file's type from its contents and records it, overwriting
                      whatever type it already had. This is not quite what the AMQP consumer
                      does on upload: that one leaves a file alone if it is already typed, and
                      records a different unit to say so.

                      Error codes: ERR_NOT_OWNER, ERR_DOES_NOT_EXIST, ERR_NOT_A_USER,
                      ERR_NOT_A_FILE"
        (svc/trap uri filetypes/detect-and-set-file-type (:user params) data-id)))

    (undocumented (route/not-found (svc/unrecognized-path-response)))))

(def app
  "The service's ring handler."
  (wrap-unparsed-detect-body api-routes))
