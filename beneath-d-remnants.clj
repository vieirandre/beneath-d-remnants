#!/usr/bin/env bb

(ns beneath-d-remnants
  (:require [clojure.string :as str]))
  
(defn normalize [s]
  (str/lower-case (str/trim s)))

(def target "MSI Afterburner")

(println "beneath-d-remnants")
(println "Target:" (normalize target))
