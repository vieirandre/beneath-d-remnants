#!/usr/bin/env bb

(ns beneath-d-remnants
  (:require [clojure.string :as str]))

(defn normalize [s]
  (str/lower-case (str/trim s)))

(defn windows?
  []
  (str/includes? (normalize (System/getProperty "os.name")) "windows"))

(def target "MSI Afterburner")

(when-not (windows?)
  (binding [*out* *err*]
    (println "this scrpt is intended for windows"))
  (System/exit 1))

(println "Target:" (normalize target))
