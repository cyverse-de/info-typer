(ns info-typer.routes
  (:use [common-swagger-api.schema]
        [common-swagger-api.schema.filetypes]
        [compojure.api.middleware :only [wrap-exceptions]]
        [service-logging.middleware :only [log-validation-errors add-user-to-context]])
  (:require [clojure-commons.error-codes :as ce]
            [clojure-commons.exception :as cx]
            [clojure-commons.lcase-params :refer [wrap-lcase-params]]
            [clojure-commons.query-params :refer [wrap-query-params]]
            [compojure.route :as route]
            [info-typer.config :as config]
            [info-typer.filetypes :as filetypes]
            [info-typer.service :as svc]
            [ring.middleware.keyword-params :as params]
            [schema.core :as s]))

(def ^:private svc-info
  {:desc     "DE service for file info type detection"
   :app-name "info-typer"
   :group-id "org.cyverse"
   :art-id   "info-typer"
   :service  "info-typer"})

(s/defschema DetectedType
  {:type (describe String "The detected file type, or \"unknown\"")})

(defapi app
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
      (svc/success-response (assoc svc-info :amqp (config/amqp-connected?))))

    (GET "/admin/config" []
      :tags ["service-info"]
      :summary "Service Configuration"
      :description "Lists the service's configuration, with anything secret masked."
      (svc/success-response (config/masked-config)))

    (GET "/file-types" []
      :tags ["filetypes"]
      :return TypesList
      :summary "List File Types"
      :description "Lists available file types supported by the underlying library heuristomancer."
      (svc/trap "/file-types" filetypes/type-list))

    (POST "/file-types/detect" []
      :tags ["filetypes"]
      :return DetectedType
      :summary "Detect a File Type"
      :description "Identifies the type of the raw bytes in the request body. Nothing is read
                    from or written to the data store, and no user is involved -- this exists
                    so that a caller holding the bytes can ask what they are."
      (fn [request]
        (svc/success-response {:type (filetypes/detect-type (:body request))})))

    (context "/data/:data-id" []
      :path-params [data-id :- (describe String "The data item's UUID")]
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
        :description "Identifies a file's type from its contents and records it, which is what
                      the AMQP consumer does when a file is uploaded. This is the same
                      operation on demand.

                      Error codes: ERR_NOT_OWNER, ERR_DOES_NOT_EXIST, ERR_NOT_A_USER,
                      ERR_NOT_A_FILE"
        (svc/trap uri filetypes/detect-and-set-file-type (:user params) data-id)))

    (undocumented (route/not-found (svc/unrecognized-path-response)))))
