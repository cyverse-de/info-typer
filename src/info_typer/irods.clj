(ns info-typer.irods
  (:use [clj-jargon.item-ops :only [input-stream]])
  (:require [heuristomancer.core :as hm]
            [clojure.tools.logging :as log]
            [info-typer.config :as cfg])
  (:import [java.io ByteArrayInputStream InputStream]
           [java.util Arrays]))


(defn- sample
  "Reads up to limit bytes from a stream, or nil when there are none."
  [^InputStream stream ^long limit]
  (let [buffer (byte-array limit)]
    (loop [filled 0]
      (if (>= filled limit)
        buffer
        (let [read (.read stream buffer filled (- limit filled))]
          (if (pos? read)
            (recur (+ filled read))
            (when (pos? filled) (Arrays/copyOf buffer filled))))))))


(defn identify-stream
  "Identifies the type of a stream's contents, or nil when nothing recognises it.

   The sample is read here rather than handed to heuristomancer directly, because
   heuristomancer throws on a stream with nothing in it -- a zero-byte data object, or a
   caller asking about no bytes at all. Neither is an error: they are simply unidentifiable."
  [^InputStream stream]
  (when stream
    (when-let [bytes (sample stream (cfg/filetype-read-amount))]
      (when-let [result (hm/identify (ByteArrayInputStream. bytes) (cfg/filetype-read-amount))]
        (name result)))))


(defn- get-file-type
  "Uses heuristomancer to determine a the file type of a file."
  [cm path]
  ;; Hinted java.io.InputStream on purpose. clj-jargon declares input-stream as returning
  ;; IRODSFileInputStream but hands back a PackingIrodsInputStream, and the two are siblings
  ;; rather than one extending the other -- so without the hint the local takes the declared
  ;; type, the close that with-open compiles gets a checkcast the value can never satisfy, and
  ;; every typing attempt dies with a ClassCastException.
  (with-open [^InputStream stream (input-stream cm path)]
    (identify-stream stream)))


(defn content-type
  "Determines the filetype of path. Reads in a chunk, writes it to a temp file, runs it
   against the configured script. If the script can't identify it, it's passed to Tika."
  [cm path]

  (let [info-type (get-file-type cm path)]
    (log/info "Data object at" path "identified as type" info-type)
    (if (or (nil? info-type) (empty? info-type))
      ""
      info-type)))
