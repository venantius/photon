(ns photon.core
  (:require [rethinkdb.query :as r]
            [compojure.core :refer [defroutes GET POST]]
            [compojure.route :as route]

            [immutant.web :as web]

            [ring.middleware.params]
            [ring.middleware.keyword-params]
            [ring.middleware.session]

            [taoensso.sente :as sente]
            [taoensso.sente.server-adapters.immutant :refer [get-sch-adapter]]))

(reset! sente/debug-mode?_ true) ; Uncomment for extra debug info

(let [{:keys [ch-recv send-fn connected-uids
              ajax-post-fn ajax-get-or-ws-handshake-fn]}
      (sente/make-channel-socket!
       (get-sch-adapter))]

  (def ring-ajax-post                ajax-post-fn)
  (def ring-ajax-get-or-ws-handshake ajax-get-or-ws-handshake-fn)
  (def ch-chsk                       ch-recv) ; ChannelSocket's receive channel
  (def chsk-send!                    send-fn) ; ChannelSocket's send API fn
  (def connected-uids                connected-uids) ; Watchable, read-only atom
)

(defn add-random-uid
  [req]
  (assoc-in req [:session :uid] (str (java.util.UUID/randomUUID))))

(defroutes my-app-routes
  ;; <other stuff>

  ;;; Add these 2 entries: --->
  (GET  "/chsk" req (ring-ajax-get-or-ws-handshake (add-random-uid req)))
  (POST "/chsk" req (ring-ajax-post (add-random-uid req)))
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
  (web/run #'my-app {:host "127.0.0.1" :path "/" :port "5000"}))

(defn push-to-client
  []
  (photon.core/chsk-send!
   :taoensso.sente/nil-uid
   [:fast-push/could-be-anything (str "hello !!")]))
