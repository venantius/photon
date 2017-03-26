(ns photon.server.db
  (:require
   [photon.core :as photon]
   [rethinkdb.query :as r]))

;; rethinkdb connection
(def conn (r/connect :host "127.0.0.1" :port 28015 :db "test"))

;; most of this stuff is only for development. Maybe it should go in test?
;; Create our tables.
(defn setup []
  (-> (r/db "test")
      (r/table-create "authors")))

;; A working changefeed
(defn prn-author-updates
  []
  (doall
   (-> (r/db "test")
       (r/table "authors")
       (r/changes {:include-initial true})
       (r/run conn))))

(defonce author-atom (atom #{}))

(defn watch-changefeed
  "Given an atom and a query, store the initial results of the query in the atom
  and then wait for any changes and also store them in the atom."
  [a q]
  (reset! a (set (r/run q conn)))
  (doall
   (map
     ;; TODO -- handle deletions!
     ;; TODO -- make this non-blocking, move it to a core async channel.
    (fn [{:keys [old_val new_val] :as v}]
      (if old_val
        ;; update
        (swap! a (fn [x] (-> x
                             (disj old_val)
                             (conj new_val))))
        ;; create
        (swap! author-atom #(conj % new_val)))
      ;; Now, let's broadcast to any connected clients!
      (doall
       (map
        #(photon/chsk-send! % [:photon.server.db/change v])
        (:any @photon/connected-uids))))
    (-> q
        (r/changes)
        (r/run conn)))))

(defn watch-authors
  []
  (watch-changefeed author-atom
                    (r/table "authors")))
