(defproject org.clojars.akiel/prometheus-clj.alpha "0.2-SNAPSHOT"
  :description "Clojure wrappers for the Prometheus Java client."
  :url "https://github.com/alexanderkiel/prometheus-clj.alpha"

  :license {:name "The Apache Software License, Version 2.0"
            :url  "http://www.apache.org/licenses/LICENSE-2.0.txt"}

  :dependencies [[io.prometheus/simpleclient "0.0.26"]
                 [io.prometheus/simpleclient_hotspot "0.0.26"]
                 [io.prometheus/simpleclient_common "0.0.26"]]

  :min-lein-version "2.4.3"

  :profiles {:dev {:dependencies [[org.clojure/clojure "1.8.0"]]}
             :1.7 {:dependencies [[org.clojure/clojure "1.7.0"]]}
             :1.6 {:dependencies [[org.clojure/clojure "1.6.0"]]}})
