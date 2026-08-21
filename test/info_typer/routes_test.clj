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

;; The raw-body case is first because it is the one most likely to break without anyone
;; noticing: compojure-api installs format middleware that will happily consume an
;; octet-stream body before the handler sees it, leaving the detector reading an empty stream
;; and reporting "unknown" for everything.
(deftest detect-reads-the-raw-request-body
  (testing "a CSV sample is identified from the bytes in the body"
    (let [response (routes/app (-> (mock/request :post "/file-types/detect")
                                   (mock/content-type "application/octet-stream")
                                   (mock/body "a,b,c\n1,2,3\n4,5,6\n")))]
      (is (= 200 (:status response)))
      (is (= "csv" (:type (body-of response))))))

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
  (let [body (body-of (routes/app (mock/request :get "/admin/config")))
        rendered (json/generate-string body)]
    (is (= 200 (:status (routes/app (mock/request :get "/admin/config")))))
    ;; The test config's iRODS password. Its presence here would mean the mask does not cover
    ;; what data-info's left exposed.
    (is (not (re-find #"notprod" rendered)))))

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

(deftest detecting-nothing-is-unknown
  ;; Guards the handler's contract directly, not only through the route: a nil body reaching
  ;; heuristomancer throws, and the route would answer 500 for a question that has an answer.
  (is (= "unknown" (filetypes/detect-type nil))))
