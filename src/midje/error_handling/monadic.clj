;; -*- indent-tabs-mode: nil -*-

(ns midje.error-handling.monadic
  (:use
    [clojure.algo.monads :only [defmonad domonad with-monad m-lift]]
    [clojure.test :only [report]]
    [midje.internal-ideas.file-position :only [form-position]]
    [midje.util.form-utils :only [named?]]
    [utilize.seq :only (find-first)]))

(defn- as-validation-error [form]
  (vary-meta form assoc :midje-validation-error true))

(defn validation-error-form? [form]
  (:midje-validation-error (meta form)))

(defn report-validation-error [form & notes]
  (as-validation-error `(report {:type :user-error
                                 :notes '~notes
                                 :position '~(form-position form)})))

(defmacro simple-report-validation-error [form & notes]
  `(report-validation-error ~form ~@notes (str ~form)))





(defmonad midje-maybe-m
   "Monad describing form processing with possible failures. Failure
   is represented by any form with metadata :midje-validation-error"
   [m-result identity
    m-bind   (fn [mv f] (if (validation-error-form? mv) mv (f mv)))
    ])

(defmacro valid-let [let-vector & body]
  `(domonad midje-maybe-m [~@let-vector] ~@body))

(defmacro safely [fn & body]
  `( (with-monad midje-maybe-m (m-lift ~(count body) ~fn))
     ~@body))

(defn- spread-validation-error [collection]
  (or (find-first validation-error-form? collection)
      collection))

;; This is a pretty dubious addition. Not using it now - found
;; a better way - but might need it later.
(defmacro with-valid [symbol & body]
  `(let [~symbol (#'spread-validation-error ~symbol)]
     (if (validation-error-form? ~symbol)
       (eval ~symbol)
       (do ~@body))))


(defmulti validate (fn [form] 
                     (if (named? (first form)) 
                       (name (first form)) 
                       :validate-seq)))

(defmethod validate :validate-seq [form] 
  (spread-validation-error (map validate form)))

(defmethod validate :default [form] (rest form))

(defmacro when-valid [validatable-form-or-forms & body-to-execute-if-valid]
  `(let [result# (validate ~validatable-form-or-forms)]
     (if (validation-error-form? result#)
       result#
       (do ~@body-to-execute-if-valid))))