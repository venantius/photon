(ns photon.core
  (:require [rethinkdb.query :as r]
            [compojure.core :refer [defroutes GET POST]]
            [compojure.route :as route]

            [photon.config :as config]
            [photon.server :as server]

            [taoensso.sente :as sente]
            [taoensso.sente.server-adapters.immutant :refer [get-sch-adapter]]))

(reset! sente/debug-mode?_ true) ; Uncomment for extra debug info

(defn initialize!
  []
  (when (nil? @server/state)
    (swap! server/state merge
           (sente/make-channel-socket!
            (get-sch-adapter)))))

(defn add-random-uid
  [req]
  (assoc-in req [:session :uid] (str (java.util.UUID/randomUUID))))

;; Add this to your app's routes.
(defroutes photon-routes
  (GET  config/photon-endpoint req
    ((:ajax-get-or-ws-handshake-fn @server/state) (add-random-uid req)))
  (POST config/photon-endpoint req
    ((:ajax-post-fn @server/state) (add-random-uid req))))

(defn push-to-client
  []
  (let [connected-uids @(:connected-uids @server/state)]
    (doall
     (map
      #(photon.core/chsk-send!
        %
        [:fast-push/could-be-anything (str "hello !!")])
      (:any connected-uids)))))
