(ns info-typer.routes-test
  (:require [cheshire.core :as json]
            [clojure.test :refer :all]
            [common-swagger-api.schema.filetypes :as filetype-schema]
            [info-typer.config :as config]
            [info-typer.filetypes :as filetypes]
            [info-typer.routes :as routes]
            [ring.mock.request :as mock]
            [schema.core :as s]))

(defn with-empty-config [f]
  (require 'info-typer.config :reload)
  (config/load-config-from-file "conf/test/empty.properties")
  (f))

(use-fixtures :once with-empty-config)

(defn- body-of
  "Decodes a response body.

   compojure-api's format middleware serialises the handler's map and hands back a stream, so
   a test that treats the body as a map silently sees nil for every key."
  [response]
  (let [body (:body response)]
    (cond
      (string? body)                  (json/parse-string body true)
      (instance? java.io.InputStream body) (json/parse-string (slurp body) true)
      :else                           body)))

;; This one is first because it is the case most likely to break without anyone noticing:
;; compojure-api's parameter and format middleware sit outside every route and both consume
;; :body before a handler runs. A form-encoded body -- what curl sends by default -- was
;; slurped for parameters, leaving the detector an exhausted stream and answering "unknown"
;; for everything, and a body labelled application/json that is not JSON was rejected with a
;; 400 before the handler ran. The content type is ignored here so that neither can happen.
(deftest detect-reads-the-raw-request-body
  (doseq [content-type ["application/octet-stream"
                        "application/x-www-form-urlencoded"
                        "application/json"
                        "application/x-yaml"
                        "text/plain"]]
    (testing (str "a CSV sample is identified from bytes labelled " content-type)
      (let [response (routes/app (-> (mock/request :post "/file-types/detect")
                                     (mock/content-type content-type)
                                     (mock/body "a,b,c\n1,2,3\n4,5,6\n")))]
        (is (= 200 (:status response)))
        (is (= "csv" (:type (body-of response)))))))

  (testing "bytes nothing recognises are reported as unknown rather than as a failure"
    (let [response (routes/app (-> (mock/request :post "/file-types/detect")
                                   (mock/content-type "application/octet-stream")
                                   (mock/body (String. (byte-array (repeat 64 (byte 0)))))))]
      (is (= 200 (:status response)))
      (is (= "unknown" (:type (body-of response))))))

  (testing "an empty body is unknown, not an error"
    (let [response (routes/app (-> (mock/request :post "/file-types/detect")
                                   (mock/content-type "application/octet-stream")
                                   (mock/body "")))]
      (is (= 200 (:status response)))
      (is (= "unknown" (:type (body-of response)))))))

(deftest the-detect-operation-documents-its-raw-body
  ;; Without both of these a client generated from the spec sends no body at all and labels
  ;; the request as JSON, inheriting the API-wide consumes list -- which is precisely the
  ;; request that used to come back wrong.
  (let [op (-> (routes/app (mock/request :get "/swagger.json"))
               body-of
               (get-in [:paths (keyword "/file-types/detect") :post]))]
    (is (= ["application/octet-stream"] (:consumes op)))
    (is (= ["body"] (map :in (:parameters op))))))

(deftest file-types-are-listed
  (let [response (routes/app (mock/request :get "/file-types"))
        types    (:types (body-of response))]
    (is (= 200 (:status response)))
    (is (seq types) "the list is empty, so heuristomancer reported no formats")
    (is (= (sort types) types) "the list is not sorted, and callers render it as-is")
    (is (some #{"csv"} types))))

(deftest service-info-reports-amqp-without-depending-on-it
  (config/set-amqp-connected! false)
  (let [response (routes/app (mock/request :get "/"))
        body     (body-of response)]
    ;; The status is 200 with the broker down on purpose. The file type endpoints answer
    ;; without it, so failing the probe here would take them out of service as well.
    (is (= 200 (:status response)))
    (is (= "info-typer" (:service body)))
    (is (false? (:amqp body))))

  (config/set-amqp-connected! true)
  (is (true? (:amqp (body-of (routes/app (mock/request :get "/")))))))

(deftest the-configuration-is-masked
  ;; Loaded from a config that actually sets these, not from the empty one the other tests
  ;; use. mask-config only reports properties that are present, so asserting against an empty
  ;; config passes whether the mask works or not.
  (config/load-config-from-file "conf/test/secrets.properties")
  (try
    (let [response (routes/app (mock/request :get "/admin/config"))
          body     (body-of response)
          rendered (json/generate-string body)]
      (is (= 200 (:status response)))

      ;; Both properties are in the response at all -- without this the rest is vacuous.
      (is (contains? body (keyword "info-typer.irods.pass")))
      (is (contains? body (keyword "info-typer.amqp.uri")))

      ;; The iRODS password, which mask-config catches by property name.
      (is (not (re-find #"notprod" rendered)))

      ;; The broker password, which it catches only because the URI is named explicitly:
      ;; mask-config matches names, and nothing about info-typer.amqp.uri says its value
      ;; carries a credential. That is exactly how data-info publishes its own.
      (is (not (re-find #"SUPERSECRETBROKERPW" rendered))
          "the AMQP URI's password is in the response"))
    (finally
      (config/load-config-from-file "conf/test/empty.properties"))))

(deftest an-unrecognised-path-keeps-the-shape-clients-know
  (let [response (routes/app (mock/request :get "/nothing-here"))
        body     (body-of response)]
    (is (= 404 (:status response)))
    (is (false? (:success body)))))

(deftest setting-a-type-requires-a-known-one
  (testing "a type the library does not know is refused before iRODS is touched"
    (let [response (routes/app (-> (mock/request :put "/data/00000000-0000-0000-0000-000000000000/type?user=someone")
                                   (mock/json-body {:type "not-a-real-type"})))]
      (is (= 400 (:status response))))))

(deftest unset-is-recorded-as-unknown-rather-than-removed
  (testing "\"unknown\" is a type the schema accepts"
    ;; The DE distinguishes a file nobody has typed from one typed as unidentifiable, so
    ;; unsetting records "unknown" rather than removing the attribute.
    (is (contains? (set filetype-schema/ValidInfoTypes) "unknown")))

  (testing "the empty string is accepted on input, because it is how a type is unset"
    (is (contains? (set filetype-schema/ValidInfoTypes) "unknown"))
    (is (nil? (s/check filetype-schema/ValidInfoTypesEnumPlusBlank "")))))

(deftest a-malformed-data-id-is-refused-before-irods-is-dialled
  ;; The path parameter is coerced, so an id that is not a UUID fails schema validation. Left
  ;; as a plain string it reached UUID/fromString inside the iRODS connection, which cost a
  ;; connection and answered 500 where data-info answers 400.
  (let [response (routes/app (-> (mock/request :put "/data/not-a-uuid/type?user=someone")
                                 (mock/json-body {:type "csv"})))]
    (is (= 400 (:status response)))))

(deftest detecting-nothing-is-unknown
  ;; Guards the handler's contract directly, not only through the route: a nil body reaching
  ;; heuristomancer throws, and the route would answer 500 for a question that has an answer.
  (is (= "unknown" (filetypes/detect-type nil))))
