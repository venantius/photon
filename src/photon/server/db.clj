(ns photon.server.db
  (:require [clojure.core.async :as async]
            [photon.server :as server]
            [rethinkdb.query :as r]))

(defonce _config (atom nil))

(defonce subscribers (atom {}))

(defonce change-registry (atom {}))

(defn- add-uid
  "Given x, which will either be a set or nil, either add x to the set or return
  a new set with x as its element."
  [x uid]
  (if (nil? x)
    #{uid}
    (conj x uid)))

(defn subscribe!
  "Subscribe a specific user to a changefeed."
  [feed uid]
  ;; first, check to see if that changefeed is defined. if not, throw an error.

  ;; second, see if this server is currently listening to that changefeed.
  ;; if not, start listening

  ;; finally, update the subscribers atom to include the UID.
  (swap! subscribers update feed add-uid uid))

;; rethinkdb connection
(def conn (r/connect :host "127.0.0.1" :port 28015 :db "test"))

(defn configure!
  "Set the default connection configuration. Does not actually open a database
  connection."
  ([] (configure! {}))
  ([{:keys [host port db] :or {host "127.0.0.1" port 28015 db "test"} :as opts}]
   (when (nil? @_config)
     (reset! _config {:host host :port port :db db}))))

;; NOTE: Is this a pattern we actually want to follow on the server? Maybe it
;; is in a debug mode but otherwise it just seems memory-heavy. We should
;; push this stuff to the client.
(defn update-atom!
  "Update the atom a containing a set of values."
  [a {:keys [old_val new_val] :as v}]
  (if old_val
        ;; update
    (swap! a (fn [x] (-> x
                         (disj old_val)
                         (conj new_val))))
        ;; create
    (swap! a #(conj % new_val)))
  ;; todo: broadcast to conected clients.
)

(defn start-async-changefeed
  "Given an atom and a query, store the initial results of the query in the atom
  and then wait for any changes and also store them in the atom."
  [a q conn]
  (reset! a (set (r/run q conn)))
  (let [chan (-> q
                 (r/changes)
                 (r/run conn {:async? true}))]
    (async/go-loop []
      (when-let [msg (async/<! chan)]
        (update-atom! a msg)
        (println msg)
        (recur)))))

(defprotocol IChangeFeed
  (active? [this])
  (start [this])
  (stop [this]))

(defrecord PhotonChangeFeed [query ^clojure.lang.Atom conn]
  IChangeFeed
  (active? [this] (:active? @conn))
  (start [this]
    (if (nil? @_config)
      (throw (ex-info "RethinkDB server connection is not configured." {}))
      (let [{:keys [host port db]} @_config
            connection (r/connect :host host :port port :db db)]
        (reset! conn {:active? true
                      :connection connection})
        (start-async-changefeed (atom nil) query connection))))
  (stop [this]
    (.close (:connection @conn))
    (reset! conn {:active? false})))

(defn defchange
  [changename query]
  (if (nil? (namespace changename))
    (throw (Exception. "changename must be namespace-qualified."))
    (let [feed (map->PhotonChangeFeed {:query query
                                       :conn (atom {:active? false})})]
      (swap! change-registry assoc changename feed))))
