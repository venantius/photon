(ns photon.core
  (:require-macros
   [cljs.core.async.macros :as asyncm :refer (go go-loop)])
  (:require
   ;; <other stuff>
   [clojure.string  :as str]

   [cljs.core.async :as async :refer (<! >! put! chan)]

   [photon.config :as config]

   [taoensso.encore :as encore :refer-macros (have have?)]

   [taoensso.sente  :as sente :refer (cb-success?)] ; <--- Add this
  ))

;;; Add this: --->
(let [{:keys [chsk ch-recv send-fn state]}
      (sente/make-channel-socket! config/photon-endpoint ; Note the same path as before
       {:type :auto ; e/o #{:auto :ajax :ws}
       })]
  (def chsk       chsk)
  (def ch-chsk    ch-recv) ; ChannelSocket's receive channel
  (def chsk-send! send-fn) ; ChannelSocket's send API fn
  (def chsk-state state)   ; Watchable, read-only atom
  )

(.log js/console "Photon JS loaded!")

(defonce router (atom nil))
(defn stop-router!
  []
  (when-let [stop-f @router]
    (stop-f)))

(defmulti recv-event-handler
  "Multimethod to handle the `?data` from `:chsk/recv` messages."
  (fn [x] (first x)) ; dispatch on the event key
  )

(defmethod recv-event-handler
  :default
  [data]
  (println "Called!")
  (println data))

(defmethod recv-event-handler
  :photon.server.db/change
  [data]
  (println "DB CHANGE!!"))

(defmulti -event-msg-handler
  "Multimethod to handle Sente `event-msg`s"
  :id ; Dispatch on event-id
  )

(defn event-msg-handler
  "Wraps `-event-msg-handler` with logging, error catching, etc."
  [{:as ev-msg :keys [id ?data event]}]
  (-event-msg-handler ev-msg))

(defmethod -event-msg-handler
  :default ; Default/fallback case (no other matching handler)
  [{:as ev-msg :keys [event]}]
  (println "Unhandled event: %s" event))

(defmethod -event-msg-handler :chsk/state
  [{:as ev-msg :keys [?data]}]
  (let [[old-state-map new-state-map] (have vector? ?data)]
    (if (:first-open? new-state-map)
      (println "Channel socket successfully established!: %s" new-state-map)
      (println "Channel socket state change: %s"              new-state-map))))

(defmethod -event-msg-handler :chsk/recv
  [{:as ev-msg :keys [?data]}]
  (recv-event-handler ?data)
  (println "Push event from server: " ?data))

(defmethod -event-msg-handler :chsk/handshake
  [{:as ev-msg :keys [?data]}]
  (let [[?uid ?csrf-token ?handshake-data] ?data]
    (println "Handshake: " ?data)))

(defn start-router! []
  (stop-router!)
  (reset! router
    (sente/start-client-chsk-router!
      ch-chsk event-msg-handler)))

;;;; Init stuff

(defn start! [] (start-router!))

(defonce _start-once (start!))
