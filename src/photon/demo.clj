(ns photon.demo
  (:require [compojure.core :refer [defroutes GET POST]]
            [compojure.route :as route]
            [immutant.web :as web]
            [photon.core :as photon]
            [photon.server.routes :as photon-routes]
            [ring.middleware.params]
            [ring.middleware.keyword-params]
            [ring.middleware.session]))

(defroutes my-app-routes
  ;; <other stuff>

  ;;; Add these 2 entries: --->
  photon-routes/photon-routes
  (route/resources "/")
  (route/not-found "<h1>Resource not found</h1>"))

(def my-app
  (-> my-app-routes
      ;; Add necessary Ring middleware:
      ring.middleware.keyword-params/wrap-keyword-params
      ring.middleware.params/wrap-params
      ring.middleware.session/wrap-session))

(defn run
  []
  (photon/initialize!)
  (web/run #'my-app {:host "127.0.0.1" :path "/" :port "5000"}))
