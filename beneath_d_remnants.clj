#!/usr/bin/env bb

(ns beneath-d-remnants
  (:require [babashka.cli :as cli]
            [clojure.string :as str]))

(defn normalize [s]
  (str/lower-case (str/trim s)))

(defn windows?
  []
  (str/includes? (normalize (System/getProperty "os.name")) "windows"))

(defn parse-names [s]
  (->> (str/split (or s "") #",")
       (map str/trim)
       (remove str/blank?)
       vec))

(def cli-spec
  {:apps {:desc "Names of the target apps"
          :coerce parse-names}})

(def opts
  (cli/parse-opts *command-line-args* {:spec cli-spec}))

(def target-apps (:apps opts))

(when (or (nil? target-apps) (empty? target-apps))
  (binding [*out* *err*]
    (println "Missing required argument: --apps \"name1,name2\""))
  (System/exit 2))

(when-not (windows?)
  (binding [*out* *err*]
    (println "This script is intended for Windows"))
  (System/exit 1))

(println "Target apps:" (str/join ", " target-apps))
