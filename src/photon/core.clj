(ns photon.core
  (:require [rethinkdb.query :as r]
            [compojure.core :refer [defroutes GET POST]]
            [compojure.route :as route]

            [photon.config :as config]
            [photon.messages :as messages]
            [photon.server :as server]
            [photon.server.db :as db]

            [taoensso.sente :as sente]
            [taoensso.sente.server-adapters.immutant :refer [get-sch-adapter]]))

(reset! sente/debug-mode?_ true) ; Uncomment for extra debug info

;; Router.

(defmulti event-msg-handler
  "Server-side Photon event router. Routes on the message :id."
  :id)

(defmethod event-msg-handler :default
  [{:keys [id ?data event] :as ev-msg}]
  ; don't do anything.
)

(defmethod event-msg-handler messages/subscribe
  [{:keys [id ?data event uid] :as ev-msg}]
  (let [feed (:name ?data)]
    (println "SUBSCRIBER:" uid id ?data)
    (db/subscribe! feed uid)))

(defonce router_ (atom nil))
(defn  stop-router! [] (when-let [stop-fn @router_] (stop-fn)))
(defn start-router! []
  (stop-router!)
  (reset! router_
          (sente/start-server-chsk-router!
           (:ch-recv @server/state) event-msg-handler)))

;; Initializer

(defn initialize!
  "Initialize the Sente channel socket and start the server-side Photon message
  router."
  []
  (when (false? (:initialized? @server/state))
    (swap! server/state merge
           (assoc
            (sente/make-channel-socket!
             (get-sch-adapter))
            :initialized? true))
    (start-router!)))

(defn push-to-client
  []
  (let [connected-uids @(:connected-uids @server/state)]
    (doall
     (map
      #((:send-fn @server/state)
        %
        [:fast-push/could-be-anything (str "hello !!")])
      (:any connected-uids)))))
