(ns prometheus.core-test
  (:require
    [clojure.test :refer :all]
    [prometheus.alpha :as prom :refer [defcounter defhistogram]]))

(defcounter
  :counter
  "Counter.")

(defcounter
  :my/counter
  "Counter with namespace.")

(defcounter
  :counter/two
  "Counter with namespace.")

(defcounter
  :counter/one_label
  "Counter with one label."
  "label_name_1")

(defhistogram
  :histogram
  "Histogram."
  [0.0 1.0]
  "test")

(use-fixtures
  :each
  (fn [f]
    (prom/clear! :counter)
    (prom/clear! :my/counter)
    (prom/clear! :counter/two)
    (prom/clear! :counter/one_label)
    (prom/clear! :histogram)
    (f)))

(deftest counter-test
  (testing "Increment counter without namespace."
    (prom/inc! :counter)
    (is (= 1.0 (prom/get :counter))))

  (testing "Increment counter with namespace."
    (prom/inc! :my/counter)
    (is (= 1.0 (prom/get :my/counter))))

  (testing "Increment counter by two."
    (prom/inc! :counter/two 2)
    (is (= 2.0 (prom/get :counter/two))))

  (testing "Increment counter with labels."
    (prom/inc! :counter/one_label "label-1")
    (is (= 1.0 (prom/get :counter/one_label "label-1")))))

(deftest histogram-test
  (testing "Use timer on histogram and close if through with-open."
    (with-open [_ (prom/timer :histogram "t1")]
      (inc 1))
    (is (pos? (prom/sum :histogram "t1"))))

  (testing "Use timer on histogram and close if through with-open."
    (let [timer (prom/timer :histogram "t2")]
      (inc 1)
      (prom/observe-duration timer))
    (is (pos? (prom/sum :histogram "t2")))))
