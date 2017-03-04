(ns photon.core
  (:require [rethinkdb.query :as r]
            [compojure.core :refer [defroutes GET POST]]
            [compojure.route :as route]

            [immutant.web :as web]

            [ring.middleware.params]
            [ring.middleware.keyword-params]

            [taoensso.sente :as sente]
            [taoensso.sente.server-adapters.immutant :refer [get-sch-adapter]]))

(reset! sente/debug-mode?_ true) ; Uncomment for extra debug info

(let [{:keys [ch-recv send-fn connected-uids
              ajax-post-fn ajax-get-or-ws-handshake-fn]}
      (sente/make-channel-socket! (get-sch-adapter) {})]

  (def ring-ajax-post                ajax-post-fn)
  (def ring-ajax-get-or-ws-handshake ajax-get-or-ws-handshake-fn)
  (def ch-chsk                       ch-recv) ; ChannelSocket's receive channel
  (def chsk-send!                    send-fn) ; ChannelSocket's send API fn
  (def connected-uids                connected-uids) ; Watchable, read-only atom
)

(defroutes my-app-routes
  ;; <other stuff>

  ;;; Add these 2 entries: --->
  (GET  "/chsk" req (ring-ajax-get-or-ws-handshake req))
  (POST "/chsk" req (ring-ajax-post                req))
  (route/resources "/"))

(def my-app
  (-> my-app-routes
      ;; Add necessary Ring middleware:
      ring.middleware.keyword-params/wrap-keyword-params
      ring.middleware.params/wrap-params))

(defn run
  []
  (web/run my-app {:host "127.0.0.1" :path "/" :port "5000"}))

(def conn (r/connect :host "127.0.0.1" :port 28015 :db "test"))

(defn setup []
  (-> (r/db "test")
      (r/table-create "authors")))

(defn prn-author-updates
  []
  (doall
   (-> (r/db "test")
       (r/table "authors")
       (r/changes {:include-initial true})
       (r/run conn))))

(defonce author-atom (atom #{}))

(defn watch-author-changes
  []
  (reset! author-atom (set (-> (r/db "test") (r/table "authors") (r/run conn))))
  (doall
   (map
    (fn [v]
      (let [{:keys [old_val new_val]} v]
        (if old_val
          (swap! author-atom (fn [a] (-> a
                                         (disj old_val)
                                         (conj new_val))))
          (swap! author-atom #(conj % new_val)))))
    (-> (r/db "test")
        (r/table "authors")
        (r/changes)
        (r/run conn)))))
