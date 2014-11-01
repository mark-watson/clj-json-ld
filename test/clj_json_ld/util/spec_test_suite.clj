(ns clj-json-ld.util.spec-test-suite
  (:require [clojure.java.io :as io]
            [clojure.walk :refer (keywordize-keys)]
            [cheshire.core :refer (parse-string, parse-stream)]))

;; JSON-LD.org git repo may be in this repo or parent and may be called json-ld.org or spec
(def possible-spec-dirs ["../json-ld.org/" "../spec/" "./json-ld.org/" "./spec/"])
(def spec-location (first (filter #(.isDirectory (io/file %)) possible-spec-dirs)))
;; test files are in the /test-suite/tests dir of the JSON-LD.org repo
(def tests-location (str spec-location "test-suite/tests/"))

(defn- load-manifest 
  "Given a manifest file name, load it from the tests dir"
  [manifest-file]
  (parse-stream (clojure.java.io/reader (str tests-location manifest-file))))

(defn tests-from-manifest
  "Load the :sequence vector from the manifest file and replace
  the :input, :expect and :context values in each test case with the
  JSON string contents of the file they point to."
  [manifest-file]
  (->> (:sequence (keywordize-keys (load-manifest manifest-file)))
    (map #(assoc % :input (slurp (str tests-location (:input %)))))
    (map #(assoc % :expect (slurp (str tests-location (:expect %)))))
    (map #(assoc % :context (if (:context %) (slurp (str tests-location (:context %))) nil)))))

(defn print-test
  "Print output explaining the test case."
  [test-type test-case]
  (println (str "\n" test-type) "Test:" (:name test-case))
  (println "Input:\n" (:input test-case))
  (println "Expected:\n" (:expect test-case)))