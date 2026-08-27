(ns info-typer.filetypes
  (:require [clj-jargon.by-uuid :as by-uuid]
            [clj-jargon.metadata :as meta]
            [clojure-commons.file-utils :as ft]
            [clojure.string :as string]
            [heuristomancer.core :as hm]
            [info-typer.config :as cfg]
            [info-typer.irods :as irods]
            [info-typer.util.irods :as util-irods]
            [info-typer.validators :as validators])
  (:import [java.io InputStream]))

;; The AVU unit every typed file in the data store carries. It is not the same as the unit the
;; AMQP consumer writes ("ipc-info-typer"), and that difference is deliberate: it records
;; which of the two paths typed a file, and things that read the unit already rely on it.
(def ^:private http-type-unit "ipc-data-info")

(def ^:private script-types (sort (hm/supported-formats)))

(defn type-list
  "Lists the types heuristomancer can identify."
  []
  {:types script-types})

(defn detect-type
  "Identifies the type of a sample of a file's contents.

   A missing or unrecognized body is \"unknown\" rather than a failure."
  [^InputStream stream]
  (let [result (irods/identify-stream stream)]
    (if (string/blank? result)
      "unknown"
      result)))

(defn- set-type
  "Records a file's type, replacing whatever was there."
  [cm path type]
  (meta/set-metadata cm path (cfg/garnish-type-attribute) type http-type-unit))

(defn- validated-path
  "Resolves a data id to a path the given user may set the type on."
  [cm user data-id]
  ;; Collection paths come back from jargon with a trailing slash that the AVU must not carry.
  (let [path (ft/rm-last-slash (by-uuid/get-path cm data-id))]
    (validators/uuid-exists path data-id)
    (validators/path-exists cm path)
    (validators/user-exists cm user)
    (validators/user-owns-path cm user path)
    path))

(defn set-file-type
  "Sets a file's type, or unsets it by recording \"unknown\".

   A blank type means \"unset\", which is recorded as \"unknown\" rather than by removing the
   AVU -- the DE distinguishes a file nobody has typed from one typed as unidentifiable, and
   removing the attribute would collapse the two.

   Unsetting skips the is-a-file check that setting performs, so that a folder that somehow
   acquired a type can still have it cleared."
  [user data-id type]
  (util-irods/with-jargon-exceptions [cm]
    (let [path (validated-path cm user data-id)]
      (if-not (string/blank? type)
        (do (validators/path-is-file cm path)
            (set-type cm path type)
            {:path path :type type :user user})
        (do (set-type cm path "unknown")
            {:path path :type "unknown" :user user})))))

(defn detect-and-set-file-type
  "Identifies a file's type from its contents and records it."
  [user data-id]
  (util-irods/with-jargon-exceptions [cm]
    (let [path (validated-path cm user data-id)]
      (validators/path-is-file cm path)
      (let [type (irods/content-type cm path)
            type (if (string/blank? type) "unknown" type)]
        (set-type cm path type)
        {:path path :type type :user user}))))
