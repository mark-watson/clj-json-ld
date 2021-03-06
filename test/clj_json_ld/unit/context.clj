(ns clj-json-ld.unit.context
  "
  Test the context processing as defined here: http://www.w3.org/TR/json-ld-api/#context-processing-algorithm
  "
  (:require [clojure.string :as s]
            [midje.sweet :refer :all]
            [clj-json-ld.json-ld :as json-ld]
            [clj-json-ld.context :refer (update-with-local-context)]
            [clj-json-ld.iri :refer (expand-iri)]
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
        (doseq [type-value ["@id" "@vocab" "bar" "foo:bar" fcms-iri]]
          (let [expanded-type-value (expand-iri active-context type-value {
                                      :vocab true
                                      :document-relative false
                                      :local-context {}
                                      :defined {}})]
          (update-with-local-context active-context {"foo" {"@type" type-value}}) =>
            (assoc active-context "foo" {"@type" expanded-type-value "@id" "http://vocab.com/foo"}))))

      (facts "a term defined with a invalid @type mapping is an invalid type mapping"
        (doseq [type (concat not-strings [blank-node-identifier "@foo" "@container"])]
          (update-with-local-context active-context {"foo" {"@type" type}}) => (throws clojure.lang.ExceptionInfo))))

    (facts "about @reverse values in a defined term"

      (facts "a term defined with a valid @reverse adds the term and the expanded @reverse mapping to the term definition in the active context"
        (doseq [reverse-value [blank-node-identifier "bar" "foo:bar" fcms-iri]]
          (update-with-local-context active-context {"foo" {"@reverse" "bar"}}) =>
            (assoc active-context "foo" {:reverse true "@reverse" "http://vocab.com/bar" "@id" "http://vocab.com/foo"})))

      (fact "a term defined with @reverse and a valid @container of @index or @set, adds them to the active context"
        (doseq [container-value ["@index", "@set"]]
          (update-with-local-context active-context {"foo" {"@reverse" fcms-iri "@container" container-value}}) =>
            (assoc active-context "foo" {
              :reverse true
              "@reverse" fcms-iri
              "@container" container-value
              "@id" "http://vocab.com/foo"})))

      (fact "a term defined with @reverse and @id is an invalid reverse property"
        (update-with-local-context active-context {"foo" {"@reverse" "foo" "@id" "foo"}}) => (throws clojure.lang.ExceptionInfo))

      (fact "a term defined with @reverse and @container that is not @index, @set or nil is an invalid reverse property"
        (doseq [container-value (concat not-strings ["foo" "@id"])]
          (update-with-local-context active-context {"foo" {"@reverse" "foo" "@container" container-value}}) =>
            (throws clojure.lang.ExceptionInfo)))

      (fact "a term defined with an @reverse value that's not a string is an invalid IRI mapping"
        (doseq [value not-strings]
          (update-with-local-context active-context {"foo" {"@reverse" value}}) => (throws clojure.lang.ExceptionInfo))))

    (facts "about iri mapping"

      ;; 13.x
      (facts "when term has an @id that isn't the term"

        (facts "a term defined with a valid @id adds the expanded IRI to the term definition in the active context"
          (doseq [id-value ["bar" "foo:bar" fcms-iri]]
            (update-with-local-context active-context {"foo" {"@id" id-value}}) =>
              (assoc active-context "foo"
                {"@id" (expand-iri active-context id-value {
                  :vocab true
                  :document-relative false
                  :local-context {}
                  :defined {}})})))

        (facts "and the value of @id is not a string it is an invalid IRI mapping"
          (doseq [id-value not-strings]
            (update-with-local-context active-context {"foo" {"@id" id-value}}) =>
              (throws clojure.lang.ExceptionInfo)))

        (facts "and the value of @id is not a JSON-LD keyword, an absolute IRI, or a blank node identifier it is an invalid IRI mapping"
          (update-with-local-context (dissoc active-context "@vocab") {"foo" {"@id" "bar"}}) =>
              (throws clojure.lang.ExceptionInfo))

        (facts "and the value of @id is @context it is an invalid IRI mapping"
          (update-with-local-context active-context {"foo" {"@id" "@context"}}) =>
              (throws clojure.lang.ExceptionInfo)))

      ;; 14.x
      (facts "when term has an @id that is the term and it contains a colon"

        ;; 14.1 
        (fact "and the term's prefix has a term definition in the local context"
          (update-with-local-context active-context {"a:bar" {"@id" "a:bar"}
                                                     "a" {"@id" "http://foo.com/"}}) =>
            (-> active-context 
              (assoc "a" {"@id" "http://foo.com/"})
              (assoc "a:bar" {"@id" "http://foo.com/bar"})))

        ;; 1 cyclic iri mapping
        ;; TODO not able to set one of these up yet, and pretty sure we aren't handling it properly
        ;; How is this triggered by spec test error 10
        ; (fact "and the term's prefix has a term definition in the local context"
        ;   (update-with-local-context active-context {"a" {"@id" "a:a"}}) =>
        ;     (throws clojure.lang.ExceptionInfo))

        ;; 14.2
        (fact "and the term's prefix has a term definition in active context"
          (let [active-context-with-term-definition (assoc active-context "foo" {"@id" "http://foo.com/"})]
            (update-with-local-context active-context-with-term-definition {"foo:bar" {"@id" "foo:bar"}}) =>
              (assoc active-context-with-term-definition "foo:bar" {"@id" "http://foo.com/bar"})))

        ;; 14.3
        (fact "and the term has no term definition in the active context"
          (update-with-local-context active-context {"foo:bar" {"@id" "foo:bar"}}) =>
            (assoc active-context "foo:bar" {"@id" "foo:bar"})))

      ;; 15
      (facts "when term has an @id that is the term and it doesn't contain a colon"

        (fact "and the term has a vocabulary mapping"
          (update-with-local-context active-context {"foo" {"@id" "foo"}}) =>
            (assoc active-context "foo" {"@id" "http://vocab.com/foo"}))


        (fact "and the term doesn't have a vocabulary mapping it is an invalid IRI mapping"
          (update-with-local-context (dissoc active-context "@vocab") {"foo" {"@id" "foo"}}) =>
            (throws clojure.lang.ExceptionInfo))))


    (facts "about @container values in a defined term"

      (facts "a term defined with a valid @container adds the term and the container mapping to the term definition in the active context"
        (doseq [container-value ["@list" "@set" "@index" "@language"]]
          (update-with-local-context active-context {"foo" {"@container" container-value}}) =>
            (assoc active-context "foo" {"@container" container-value "@id" "http://vocab.com/foo"})))

      (fact "a term defined with @container that is not @list, @set, @index or @language is an invalid container mapping"
        (doseq [container-value (concat not-strings ["foo" "@id"])]
          (update-with-local-context active-context {"foo" {"@container" container-value}}) =>
            (throws clojure.lang.ExceptionInfo))))

    (facts "about @language values in a defined term"

      (facts "a term defined with a valid @language adds the @language mapping to the term definition in the active context"
        (doseq [language-value [language another-language nil]]
          (update-with-local-context active-context {"foo" {"@language" language-value}}) =>
            (assoc active-context "foo" {
              "@language" (if language-value (s/lower-case language-value) nil)
              "@id" "http://vocab.com/foo"})))

      (facts "a term defined with a valid @language and a @type skips adding the @language mapping to the active context"
        (doseq [language-value [language another-language nil]]
          (update-with-local-context active-context {"foo" {"@type" "@id" "@language" language-value}}) =>
            (assoc active-context "foo" {"@type" "@id" "@id" "http://vocab.com/foo"})))

      (facts "a term defined with @language that is not a string or null"
        (doseq [language-value not-strings]
          (update-with-local-context active-context {"foo" {"@language" language-value}}) =>
            (throws clojure.lang.ExceptionInfo))))))