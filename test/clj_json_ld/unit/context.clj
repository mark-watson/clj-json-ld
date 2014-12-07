(ns clj-json-ld.unit.context
  "
  Test the context processing as defined here: http://www.w3.org/TR/json-ld-api/#context-processing-algorithm
  "
  (:require [clojure.string :as s]
            [midje.sweet :refer :all]
            [clj-json-ld.json-ld :as json-ld]
            [clj-json-ld.context :refer (update-with-local-context)]
            [clojurewerkz.urly.core :as u]))

(def fcms-iri "http://falkland-cms.com/")
(def falklandsophile-iri "http://falklandsophile.com/")
(def snootymonkey-iri "http://snootymonkey.com")
(def relative-iri "/foo/bar")

(def vocab-iri "http://vocab.org/")
(def another-vocab-iri "http://vocab.net/")

(def language "x-l337-5p34k")
(def another-language "en-Kelper")

(def blank-node-identifier "_:foo")

(def not-strings [42 3.14 [] {} () #{}])

(def active-context {
  "@base" fcms-iri
  "@vocab" "http://vocab.com/"
  "@language" "x-pig-latin"
  "@foo" :bar
})

(facts "about updating active context with local contexts"

  (facts "about invalid local contexts"

    (facts "as a scalar"
      (update-with-local-context active-context 1) => (throws clojure.lang.ExceptionInfo)
      (update-with-local-context active-context [{} "" nil 1]) => (throws clojure.lang.ExceptionInfo)
      (update-with-local-context active-context 1.1) => (throws clojure.lang.ExceptionInfo)
      (update-with-local-context active-context [{} "" nil 1.1]) => (throws clojure.lang.ExceptionInfo))

    (facts "as a keyword"
      (update-with-local-context active-context :foo) => (throws clojure.lang.ExceptionInfo)
      (update-with-local-context active-context [{} "" nil :foo]) => (throws clojure.lang.ExceptionInfo)
      (update-with-local-context active-context :foo) => (throws clojure.lang.ExceptionInfo)
      (update-with-local-context active-context [{} "" nil :foo]) => (throws clojure.lang.ExceptionInfo))

    (facts "as a sequential"
      (update-with-local-context active-context [[]]) => (throws clojure.lang.ExceptionInfo)
      (update-with-local-context active-context [{} "" nil []]) => (throws clojure.lang.ExceptionInfo)
      (update-with-local-context active-context [()]) => (throws clojure.lang.ExceptionInfo)
      (update-with-local-context active-context [{} "" nil ()]) => (throws clojure.lang.ExceptionInfo)))

  (facts "about nil local contexts"

    (fact "result in a newly-initialized active context"
      (update-with-local-context active-context nil) => {}
      (update-with-local-context active-context [nil]) => {}
      (update-with-local-context active-context [{:blat "bloo"} nil]) => {}))

  (future-facts "about local contexts as remote strings")
        
  (facts "about @base in local contexts"

    (facts "@base of nil removes the @base"
      (update-with-local-context active-context {"@base" nil}) => (dissoc active-context "@base"))

    (facts "@base of an absolute IRI makes the IRI the @base"
      
      (doseq [local-context [
                {"@base" falklandsophile-iri}     
                [{} {"@base" falklandsophile-iri}]   
                [{"@base" falklandsophile-iri} {}]
                [{"@base" snootymonkey-iri} {"@base" falklandsophile-iri} {}]
                [{"@base" snootymonkey-iri} {"@base" fcms-iri} {} {"@base" nil} {"@base" falklandsophile-iri}]
              ]]
        (update-with-local-context active-context local-context) =>
          (assoc active-context "@base" falklandsophile-iri)))
      
    (facts "@base of an relative IRI merges with the @base of the active-context"

      (update-with-local-context active-context {"@base" "foo/bar"}) =>
        (assoc active-context "@base" (u/resolve fcms-iri "foo/bar"))

      (update-with-local-context active-context [{} {"@base" "foo/bar"} {}]) =>
        (assoc active-context "@base" (u/resolve fcms-iri "foo/bar"))

      (update-with-local-context active-context [{"@base" "foo/bar"} {"@base" "bloo/blat"}]) =>
        (assoc active-context "@base" (-> fcms-iri (u/resolve "foo/bar") (u/resolve "bloo/blat"))))

    (facts "@base of a relative IRI without an @base in the active-context is an invalid base IRI error"

      (doseq [local-context (concat 
              (map #(hash-map "@base" %) not-strings)
              [
                {"@base" "foo/bar"}
                [{"@base" nil} {"@base" "foo/bar"}]
                [{} {"@base" "foo/bar"}]
                [{"@base" "foo/bar"} {"@base" falklandsophile-iri}]
              ])]
        (update-with-local-context {} local-context) => (throws clojure.lang.ExceptionInfo))))

  (facts "about @vocab in local contexts"

    (facts "@vocab of nil removes the @vocab"
      (update-with-local-context active-context {"@vocab" nil}) => (dissoc active-context "@vocab"))

    (facts "@vocab of an absolute IRI makes the IRI the @vocab"

      (doseq [local-context [
                {"@vocab" vocab-iri}
                [{} {"@vocab" vocab-iri}]
                [{"@vocab" vocab-iri} {}]
                [{"@vocab" another-vocab-iri} {"@vocab" vocab-iri} {}]
                [{"@vocab" another-vocab-iri} {} {"@vocab" nil} {"@vocab" vocab-iri}]
              ]]
        (update-with-local-context active-context local-context) =>
          (assoc active-context "@vocab" vocab-iri)))

    (facts "@vocab of a blank node identifier makes the blank node identifier the @vocab"
      (doseq [local-context [
                {"@vocab" blank-node-identifier}
                [{} {"@vocab" blank-node-identifier}]
                [{"@vocab" blank-node-identifier} {}]
                [{"@vocab" another-vocab-iri} {"@vocab" vocab-iri} {"@vocab" blank-node-identifier} {}]
                [{"@vocab" another-vocab-iri} {"@vocab" vocab-iri} {} {"@vocab" nil} {"@vocab" blank-node-identifier}]
              ]]
        (update-with-local-context active-context local-context) =>
          (assoc active-context "@vocab" blank-node-identifier)))

    (facts "@vocab of anything else is an invalid vocab mapping"

      (doseq [local-context (concat
                (map #(hash-map "@base" %) not-strings)
                [
                  {"@vocab" "foo"}
                  {"@vocab" "foo/bar"}
                  [{"@vocab" nil} {"@vocab" "foo/bar"}]
                  [{} {"@vocab" "foo/bar"}]
                  [{"@base" vocab-iri} {"@vocab" "foo/bar"} {"@base" another-vocab-iri}]
                ])]
        (update-with-local-context {} local-context) => (throws clojure.lang.ExceptionInfo))))

  (facts "about @language in local contexts"

    (facts "@language of nil removes the @language"
      (update-with-local-context active-context {"@language" nil}) => (dissoc active-context "@language"))

    (facts "@language of any string makes the string the @language"

      (doseq [local-context [
                {"@language" language}
                [{} {"@language" language}]
                [{"@language" language} {}]
                [{"@language" another-language} {"@language" language} {}]
                [{"@language" another-language} {} {"@language" nil} {"@language" language}]
              ]]
        (update-with-local-context active-context local-context) =>
          (assoc active-context "@language" language))

      (fact "@language string is lower-cased to makes the @language"
        (update-with-local-context active-context {"@language" another-language}) => 
          (assoc active-context "@language" (s/lower-case another-language))))

    (facts "@language of any non-string invalid default language"
      (doseq [value not-strings]
        (update-with-local-context {} {"@language" value}) => (throws clojure.lang.ExceptionInfo))))
  
  (facts "about additional terms in local contexts"

    (facts "a term defined as nil in the local context is nil in the active context"
      (update-with-local-context active-context {"@bar" nil}) => (assoc active-context "@bar" nil)
      (update-with-local-context active-context {"@bar" {"@id" nil "foo" "bar"}}) =>
        (assoc active-context "@bar" nil))

    (facts "a term in the local context that's a JSON-LD keyword is a keyword redefinition error"
      (doseq [json-ld-keyword (disj json-ld/keywords "@base" "@vocab" "@language")]
        (update-with-local-context active-context {json-ld-keyword "http://abs.com"}) =>
          (throws clojure.lang.ExceptionInfo)))

    (facts "a term defined as a string in the local context is a JSON object with an @id in the active context"
      (update-with-local-context active-context {"@foo" "bar"}) => (assoc active-context "@foo" {"@id" "bar"})
      (update-with-local-context active-context {"@foo" "bar" "@blat" "bloo"}) =>
        (assoc active-context "@foo" {"@id" "bar"} "@blat" {"@id" "bloo"}))

    (facts "a term defined as anything but a string or a JSON object is an invalid term definition"
      (doseq [value (remove not-strings {})]
        (update-with-local-context active-context {"@foo" value}) => (throws clojure.lang.ExceptionInfo)))
  
    (facts "about @type values in a defined term"

      (facts "a term defined with a valid @type mapping adds the term and the @type mapping to the active context"
        (update-with-local-context active-context {"foo" {"@type" "@id"}}) => (assoc active-context "foo" {"@type" "@id"})
        (update-with-local-context active-context {"foo" {"@type" "@vocab"}}) => (assoc active-context "foo" {"@type" "@vocab"})
        (update-with-local-context active-context {"foo" {"@type" fcms-iri}}) => (assoc active-context "foo" {"@type" fcms-iri}))

      (facts "a term defined with a invalid @type mapping is an invalid type mapping"
        (doseq [type (concat not-strings [blank-node-identifier "@foo" "@container"])]
          (update-with-local-context active-context {"foo" {"@type" type}}) => (throws clojure.lang.ExceptionInfo))))

    (facts "about @reverse values in a defined term"

      (facts "a term defined with a valid @reverse adds the term and the @reverse mapping to the active context"
        (update-with-local-context active-context {"foo" {"@reverse" blank-node-identifier}}) => (assoc active-context "foo" {"@reverse" blank-node-identifier})
        (update-with-local-context active-context {"foo" {"@reverse" fcms-iri}}) => (assoc active-context "foo" {"@reverse" fcms-iri}))

      (fact "a term defined with @reverse and @id is an invalid reverse property"
        (update-with-local-context active-context {"foo" {"@reverse" "foo" "@id" "foo"}}) => (throws clojure.lang.ExceptionInfo))
      
      (fact "a term defined with an @reverse value that's not a string is an invalid IRI mapping"
        (doseq [value not-strings]
          (update-with-local-context active-context {"foo" {"@reverse" value}}) => (throws clojure.lang.ExceptionInfo))))))