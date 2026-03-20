#!/usr/bin/env bb

(ns beneath-d-remnants
  (:require [babashka.cli :as cli]
            [babashka.fs :as fs]
            [clojure.string :as str]
            [clojure.java.io :as io]))

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
  {:names  {:desc "Names to match during cleanup"
            :coerce parse-names}
   :delete {:desc "Delete matches (default is dry-run)"
            :default false}})

(def opts
  (cli/parse-opts *command-line-args* {:spec cli-spec}))

(def names (:names opts))
(def delete? (boolean (:delete opts)))

(when (or (nil? names) (empty? names))
  (binding [*out* *err*]
    (println "Missing required argument: --names \"name1,name2\""))
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

(defn recursive-roots []
  (->> [(some-> (envv "ProgramData") (str "\\Microsoft\\Windows\\Start Menu"))
        (some-> (envv "APPDATA") (str "\\Microsoft\\Windows\\Start Menu"))
        (envv "LOCALAPPDATA")
        (envv "APPDATA")]
       (remove nil?)
       (map fs/file)
       (filter fs/exists?)
       distinct))

(defn matches-any-name?
  [text names]
  (let [t (normalize text)]
    (boolean
     (and t
          (some (fn [name]
                  (str/includes? t (normalize name)))
                names)))))

(defn path->match
  [path dir?]
  {:path (str path)
   :kind (if dir? :dir :file)})

(defn unique-matches
  [matches]
  (->> matches
       distinct
       (sort-by :path)
       vec))

(defn collect-matches
  [paths names name-fn dir?-fn]
  (->> paths
       (filter (fn [path]
                 (matches-any-name? (name-fn path) names)))
       (map (fn [path]
              (path->match path (dir?-fn path))))
       unique-matches))

(defn root-result
  [root matches]
  (when (seq matches)
    {:root root
     :matches matches}))

(defn scan-top-level
  [names]
  (reduce
   (fn [acc root]
     (let [entries (try (fs/list-dir root) (catch Exception _ []))
           matches (collect-matches entries names fs/file-name fs/directory?)]
       (if-let [result (root-result root matches)]
         (conj acc result)
         acc)))
   []
   (candidate-roots)))

(defn safe-file-seq
  [root]
  (try
    (seq (file-seq (io/file (str root))))
    (catch Exception _
      nil)))

(defn scan-recursive
  [names]
  (reduce
   (fn [acc root]
     (let [paths (or (safe-file-seq root) [])
           matches (collect-matches paths names #(.getName %) #(.isDirectory %))]
       (if-let [result (root-result root matches)]
         (conj acc result)
         acc)))
   []
   (recursive-roots)))

(defn print-results [label results]
  (println label)
  (if (empty? results)
    (println "No matches found")
    (doseq [{:keys [root matches]} results]
      (println "Matches under:" (str root))
      (doseq [{:keys [path kind]} matches]
        (println " -" (str "[" (name kind) "]") path)))))

(defn collect-delete-targets
  [results]
  (->> results
       (mapcat :matches)
       distinct
       (sort-by (fn [{:keys [path]}] (count path)) >)
       vec))

(defn delete-match!
  [{:keys [path kind]}]
  (try
    (when (fs/exists? path)
      (if (= kind :dir)
        (fs/delete-tree path)
        (fs/delete path)))
    (println "Deleted:" path)
    (catch Exception e
      (binding [*out* *err*]
        (println "Failed to delete:" path "-" (.getMessage e))))))

(let [top-level-results (scan-top-level names)
      recursive-results (scan-recursive names)
      all-results (concat top-level-results recursive-results)]
  (println "Mode:" (if delete? "DELETE" "DRY-RUN"))
  (println "Names:" (str/join ", " names))

  (print-results "Top-level scan:" top-level-results)
  (print-results "Recursive scan:" recursive-results)

  (when delete?
    (let [targets (collect-delete-targets all-results)]
      (println "Deleting filesystem matches...")
      (if (empty? targets)
        (println "No matches found")
        (doseq [target targets]
          (delete-match! target))))))
