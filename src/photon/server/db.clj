(ns photon.server.db
  (:require [clojure.core.async :as async]
            [photon.server :as server]
            [rethinkdb.query :as r]))

(defonce _config (atom nil))

;; structure:
;; {:your.ns/changefeed-name
;;  {:subscribers #{"id1" "id2"}
;;   :feed #PhotonChangeFeed{:query {}
;;                           :feed atom{:active? bool
;;                                      :connection atom{}}}}}

(defonce change-registry (atom {}))

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

(defn emit!
  [uid]
  ;; do this later.
)

(defn start-async-changefeed
  "Start an async changefeed for the query q. This changefeed will return
  results in a channel, which will be "
  [a q conn]
  ;; (reset! a (set (r/run q conn)))
  (let [chan (-> q
                 (r/changes)
                 (r/run conn {:async? true}))]
    (async/go-loop []
      (when-let [msg (async/<! chan)]
        ;; (update-atom! a msg)
        (emit! :asdf)
        (println msg)
        (recur)))))

(defprotocol IChangeFeed
  (active? [this])
  (start [this])
  (stop [this]))

(defrecord PhotonChangeFeed
           [query
            ^clojure.lang.Atom feed
            ^clojure.lang.PersistentHashSet subscribers]
  IChangeFeed
  (active? [this] (:active? @feed))
  (start [this]
    (if (nil? @_config)
      (throw (ex-info "RethinkDB server connection is not configured." {}))
      (let [{:keys [host port db]} @_config
            connection (r/connect :host host :port port :db db)]
        (reset! feed {:active? true
                      :connection connection
                      :channel (start-async-changefeed (atom nil) query connection)}))))
  (stop [this]
    (.close (:connection @feed))
    (swap! feed assoc :active? false)))

(defn- add-uid
  "Add the uid to the set of subscribers."
  [subscribers uid]
  (conj subscribers uid))

(defn subscribe!
  "Subscribe a specific user to a changefeed."
  [feed-id uid]
  ;; first, check to see if that changefeed is defined. if not, throw an error.
  (if-let [feed (feed-id @change-registry)]
    (do
      ;; If the feed is defined, add the subscriber uid to the feed's set of
      ;; subscribers
      (swap! change-registry update-in [feed-id :subscribers] add-uid uid)
      ;; Next, see if this server is currently listening to that changefeed.
      ;; If not, start listening.
      (when-not (active? feed)
        (start feed)))
    (throw (ex-info "Feed not found" {:feed feed-id
                                      :error :feed-not-found}))))

(defn- remove-uid
  "Given x, which will either be a set or nil, either remove x from the set or
  return nil"
  [{:keys [subscribers feed] :as change} uid]
  (if (nil? subscribers)
    nil
    (conj subscribers uid)))

(defn unsubscribe!
  "Unsubscribe a specific user from a specific changefeed."
  [feed-id uid]
  ;; first, check to see if that changefeed is defined. if not, throw an error.
  (if-let [feed (:feed (feed-id @change-registry))]
    (do
      ;; If the feed is defined, remove the subscriber uid from the feed's set of
      ;; subscribers
      (swap! change-registry update-in [feed-id :subscribers] add-uid uid)
      ;; Next, see if this server is currently listening to that changefeed.
      ;; If not, start listening.
      (when-not (active? feed)
        (start feed)))
    (throw (ex-info "Feed not found" {:feed feed-id
                                      :error :feed-not-found}))))

(defn defchange
  "Define a RethinkDB changefeed for Photon. The changename should be a
  namespace-qualified keyword, and the query should be the query you want to
  run.

  Do not actually invoke `run` on the query before passing it in here;
  Photon will automatically start the changefeed when a client subscribes to it
  and will automatically disconnect the changefeed when no more clients are
  subscribed."
  [changename query]
  (if (nil? (namespace changename))
    (throw (Exception. "changename must be namespace-qualified."))
    (let [feed (map->PhotonChangeFeed {:query query
                                       :feed (atom {:active? false})
                                       :subscribers #{}})]
      (swap! change-registry assoc changename feed))))
