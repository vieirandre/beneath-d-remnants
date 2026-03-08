#!/usr/bin/env bb

(ns beneath-d-remnants
  (:require [babashka.cli :as cli]
            [babashka.fs :as fs]
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
  {:apps   {:desc "Names of the target apps"
            :coerce parse-names}
   :delete {:desc "Delete matches (default is dry-run)"
            :default false}})

(def opts
  (cli/parse-opts *command-line-args* {:spec cli-spec}))

(def target-apps (:apps opts))
(def delete? (boolean (:delete opts)))

(when (or (nil? target-apps) (empty? target-apps))
  (binding [*out* *err*]
    (println "Missing required argument: --apps \"name1,name2\""))
  (System/exit 2))

(when-not (windows?)
  (binding [*out* *err*]
    (println "This script is intended for Windows"))
  (System/exit 1))

(defn envv [k] (System/getenv k))

(defn candidate-roots []
  (->> [(envv "ProgramFiles")
        (envv "ProgramFiles(x86)")
        (envv "ProgramData")
        (envv "LOCALAPPDATA")
        (envv "APPDATA")]
       (remove nil?)
       (map fs/file)
       (filter fs/exists?)
       distinct))

(defn matches-any-app?
  [text apps]
  (let [t (normalize text)]
    (boolean
     (and t
          (some (fn [app]
                  (str/includes? t (normalize app)))
                apps)))))

(defn scan-top-level
  [apps]
  (reduce
   (fn [acc root]
     (let [entries (try (fs/list-dir root) (catch Exception _ []))
           matches (filter (fn [p]
                             (matches-any-app? (fs/file-name p) apps))
                           entries)]
       (if (seq matches)
         (conj acc {:root root :matches matches})
         acc)))
   []
   (candidate-roots)))

(println "Mode:" (if delete? "DELETE" "DRY-RUN"))
(println "Target apps:" (str/join ", " target-apps))

(doseq [{:keys [root matches]} (scan-top-level target-apps)]
  (println "Matches under:" (str root))
  (doseq [p matches]
    (println " -" (str p))))
