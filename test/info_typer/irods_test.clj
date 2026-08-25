(ns info-typer.irods-test
  (:require [clj-jargon.item-ops :as item-ops]
            [clojure.test :refer :all]
            [info-typer.config :as config]
            [info-typer.irods :as irods])
  (:import [java.io ByteArrayInputStream]))

(defn with-empty-config [f]
  (require 'info-typer.config :reload)
  (config/load-config-from-file "conf/test/empty.properties")
  (f))

(use-fixtures :once with-empty-config)

(deftest typing-does-not-depend-on-jargons-declared-stream-type
  ;; clj-jargon declares input-stream as returning IRODSFileInputStream but hands back a
  ;; PackingIrodsInputStream, and the two are siblings under java.io.InputStream rather than
  ;; one extending the other. A local bound to that call picks up the declared type, so the
  ;; close that with-open compiles gets a checkcast to a class the value never is -- which took
  ;; out every AMQP typing attempt and POST /data/:id/type with a ClassCastException.
  ;;
  ;; The stub returns a plain ByteArrayInputStream for the same reason: like the real value, it
  ;; is not an IRODSFileInputStream, so it fails the moment that cast comes back.
  (are [content expected] (= expected
                             (with-redefs [item-ops/input-stream
                                           (fn [_ _] (ByteArrayInputStream. (.getBytes ^String content "UTF-8")))]
                               (irods/content-type nil "/cyverse/home/someone/file")))
    "a,b,c\n1,2,3\n4,5,6\n"           "csv"
    ">seq1\nACGTACGTACGTACGT\n"       "fasta"
    ""                                ""
    (String. (byte-array (repeat 64 (byte 0)))) ""))
