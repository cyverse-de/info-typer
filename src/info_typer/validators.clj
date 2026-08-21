(ns info-typer.validators
  (:use [slingshot.slingshot :only [throw+]])
  (:require [clj-jargon.item-info :as item]
            [clj-jargon.permissions :as perm]
            [clj-jargon.users :as user]
            [clojure-commons.error-codes :as error]))

;; These reproduce data-info.util.validators for the endpoints moving here. The error codes
;; and the keys beside them are what terrain and the DE's UI read, so they are copied rather
;; than rewritten: an endpoint that moves between services must not change its failures.

(defn path-exists
  [cm path]
  (when-not (item/exists? cm path)
    (throw+ {:error_code error/ERR_DOES_NOT_EXIST
             :path       path})))

(defn path-is-file
  [cm path]
  (when-not (item/is-file? cm path)
    (throw+ {:error_code error/ERR_NOT_A_FILE
             :path       path})))

(defn user-exists
  [cm username]
  (when-not (user/user-exists? cm username)
    (throw+ {:error_code error/ERR_NOT_A_USER
             :user       username})))

(defn user-owns-path
  [cm username path]
  (when-not (perm/owns? cm username path)
    (throw+ {:error_code error/ERR_NOT_OWNER
             :user       username
             :path       path})))

(defn uuid-exists
  [path uuid]
  (when (nil? path)
    (throw+ {:error_code error/ERR_DOES_NOT_EXIST
             :uuid       uuid})))
