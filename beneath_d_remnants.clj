#!/usr/bin/env bb

(ns beneath-d-remnants
  (:require [babashka.cli :as cli]
            [clojure.string :as str]))

(defn normalize [s]
  (str/lower-case (str/trim s)))

(defn windows?
  []
  (str/includes? (normalize (System/getProperty "os.name")) "windows"))

(def cli-spec
  {:target {:desc "Name of the target app"}})

(def opts
  (cli/parse-opts *command-line-args* {:spec cli-spec}))

(def target (:target opts))

(when (str/blank? target)
  (binding [*out* *err*]
    (println "Missing required argument: --target"))
  (System/exit 2))

(when-not (windows?)
  (binding [*out* *err*]
    (println "This script is intended for Windows"))
  (System/exit 1))

(println "Target:" target)
