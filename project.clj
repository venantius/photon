(defproject photon "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [org.clojure/clojurescript "1.9.494"]

                 [org.clojure/core.async    "0.2.395"]
                 [compojure "1.5.2"]
                 [ring "1.5.0"]

                 [org.immutant/web "2.1.6"
                  :exclusions [[ch.qos.logback/logback-classic]
                               [ch.qos.logback/logback-core]]]
                 [org.slf4j/slf4j-log4j12 "1.7.21"] ;; move to dev.
                 [com.apa512/rethinkdb "0.15.26"]
                 [com.taoensso/sente "1.11.0"]
                 ]
  
  :plugins [[lein-figwheel "0.5.9"]]

  :clean-targets  [:target-path "out"]
  :cljsbuild {
    :builds [{:id "dev"
              :source-paths ["src"]
              :figwheel true
              :compiler {:main "photon.core"
                         :output-to "resources/public/js/main.js"
                         :output-dir "resources/public/js/photon/out"
                         :asset-path "/js/photon/out"}}]}
  )
