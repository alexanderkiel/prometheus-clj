(ns prometheus.alpha
  (:require
    [clojure.string :as str])
  (:import
    [clojure.lang Keyword]
    [io.prometheus.client Counter Histogram Counter$Child Histogram$Child CollectorRegistry Gauge Gauge$Child Collector SimpleCollector SimpleCollector$Builder Histogram$Timer Summary Summary$Timer]
    [io.prometheus.client.exporter.common TextFormat]
    [io.prometheus.client.hotspot DefaultExports]
    [java.io StringWriter])
  (:refer-clojure :exclude [get]))

(set! *warn-on-reflection* true)

(defn register-default-exports! []
  (DefaultExports/initialize))

(defonce ^:private registry (atom {}))

(defn collector? [x]
  (instance? Collector x))

(defn counter? [x]
  (instance? Counter x))

(defn gauge? [x]
  (instance? Gauge x))

(defn histogram? [x]
  (instance? Histogram x))

(defn summary? [x]
  (instance? Summary x))

(defprotocol ToCollector
  (collector- [_]))

(extend-protocol ToCollector
  Collector
  (collector- [c] c)

  Keyword
  (collector- [k] (clojure.core/get @registry k))

  Object
  (collector- [_]))

(defn collector [x]
  (collector- x))

(defn- register! [c k]
  (swap! registry assoc k c)
  (.register (CollectorRegistry/defaultRegistry) c))

(defn- unregister! [c k]
  (.unregister (CollectorRegistry/defaultRegistry) c)
  (swap! registry dissoc k c))

(defn- clear-registry! []
  (.clear (CollectorRegistry/defaultRegistry))
  (reset! registry {}))

(defn- ^SimpleCollector$Builder with-namespace
  [^SimpleCollector$Builder builder keyword]
  (if-let [namespace (namespace keyword)]
    (.namespace builder namespace)
    builder))

(defn defcounter
  "Given a namespace-qualified keyword, a help text and label-names, registers
  a counter in the default registry."
  [keyword help & label-names]
  (when-let [counter (collector keyword)]
    (unregister! counter keyword))
  (-> (Counter/build)
      (with-namespace keyword)
      (.name (name keyword))
      (.labelNames (into-array String label-names))
      (.help help)
      (.create)
      (register! keyword)))

(defn defgauge
  "Given a namespace-qualified keyword, a help text and label-names, registers
  a gauge in the default registry."
  [keyword help & label-names]
  (-> (Gauge/build)
      (with-namespace keyword)
      (.name (name keyword))
      (.labelNames (into-array String label-names))
      (.help help)
      (.create)
      (register! keyword)))

(defn defhistogram
  "Given a namespace-qualified keyword, a help text, label-names and buckets,
  registers a histogram in the default registry."
  [keyword help buckets & label-names]
  (when-let [histogram (collector keyword)]
    (unregister! histogram keyword))
  (-> (Histogram/build)
      (.buckets (double-array buckets))
      (with-namespace keyword)
      (.name (name keyword))
      (.help help)
      (.labelNames (into-array String label-names))
      (.create)
      (register! keyword)))

(defn clear!
  "Clears the collector. Only SimpleCollector are clearable."
  [x]
  (if-let [collector (collector x)]
    (if (instance? SimpleCollector collector)
      (.clear ^SimpleCollector collector)
      (throw (ex-info "Not a clearable collector." {:type (type collector)})))
    (throw (ex-info "Unknown collector." {:collector collector}))))

(defn- ^Counter$Child counter-with-labels [^Counter counter label-array]
  (.labels counter (into-array String label-array)))

(defn- ^Gauge$Child gauge-with-labels [^Gauge gauge labels]
  (.labels gauge (into-array String labels)))

(defn- ^Histogram$Child histogram-with-labels [^Histogram histogram label-array]
  (.labels histogram (into-array String label-array)))

(defn inc!
  "Increment the counter by the given amount."
  {:arglists '([counter] [counter & labels] [counter & labels amount])}
  [counter & more]
  (if-let [counter (collector counter)]
    (if (counter? counter)
      (let [last (last more)
            amount (if (number? last) (double last) 1.0)
            labels (if (number? last) (butlast more) more)]
        (-> (counter-with-labels counter labels)
            (.inc amount)))
      (throw (ex-info "Not a counter." {:type (type counter)})))
    (throw (ex-info "Unknown counter." {:counter counter}))))

(defn get
  "Gets the current value of a counter."
  [counter & labels]
  (if-let [counter (collector counter)]
    (if (counter? counter)
      (-> (counter-with-labels counter labels)
          (.get))
      (throw (ex-info "Not a counter." {:type (type counter)})))
    (throw (ex-info "Unknown counter." {:counter counter}))))

(defn set-gauge!
  "Set the gauge to the given value."
  ([gauge value] (set-gauge! gauge [] value))
  ([gauge labels value]
   (if-let [gauge (collector gauge)]
     (if (gauge? gauge)
       (-> (gauge-with-labels gauge labels)
           (.set value))
       (throw (ex-info "Not a gauge." {:type (type gauge)})))
     (throw (ex-info "Unknown gauge." {:gauge gauge})))))

(defn timer
  "Returns a timer of the histogram with optional labels.

  The timer is closeable and observes the time at close. It can be used with
  with-open."
  [histogram & labels]
  (if-let [histogram (collector histogram)]
    (if (histogram? histogram)
      (-> (histogram-with-labels histogram labels)
          (.startTimer))
      (throw (ex-info "Not a histogram." {:type (type histogram)})))
    (throw (ex-info "Unknown histogram." {:histogram histogram}))))

(defprotocol Timer
  (observe-duration- [_]))

(extend-protocol Timer
  Histogram$Timer
  (observe-duration- [t] (.observeDuration t))

  Summary$Timer
  (observe-duration- [t] (.observeDuration t)))

(defn observe-duration!
  "Observes the duration of a histogram or summary timer."
  [timer]
  (observe-duration- timer))

(defn sum
  "Gets the current sum of a histogram."
  [histogram & labels]
  (if-let [histogram (collector histogram)]
    (if (histogram? histogram)
      (-> (histogram-with-labels histogram labels)
          (.get)
          (.-sum))
      (throw (ex-info "Not a histogram." {:type (type histogram)})))
    (throw (ex-info "Unknown histogram." {:histogram histogram}))))

(defn dump-metrics
  "Dumps metrics of the default registry using simple client's text format."
  []
  (let [registry (CollectorRegistry/defaultRegistry)
        writer (StringWriter.)]
    (TextFormat/write004 writer (.metricFamilySamples registry))
    {:status 200
     :headers {"Content-Type" TextFormat/CONTENT_TYPE_004}
     :body (.toString writer)}))

(comment
  (enumeration-seq (.metricFamilySamples (CollectorRegistry/defaultRegistry)))
  (clear-registry!)
  )
