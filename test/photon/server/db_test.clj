(ns photon.server.db-test
  (:require [clojure.test :refer :all]
            [photon.server.db :as db]
            [rethinkdb.query :as r]))

(def author-atom (atom {}))

(def conn (r/connect))

;; most of this stuff is only for development. Maybe it should go in test?
;; Create our tables.
(defn setup []
  (when (some #{"authors"} (-> (r/table-list) (r/run conn)))
    (-> (r/table-drop "authors")
        (r/run conn)))
  (-> (r/db "test")
      (r/table-create "authors")
      (r/run conn)))

(defn watch-authors-async
  []
  (db/start-async-changefeed author-atom
                             (r/table "authors")))

(deftest defchange-fails-with-non-namespace-qualified-name
  (try
    (db/defchange :dummy {})
    (is (= 0 1))
    (catch Exception e
      (is (= 1 1)))))

(deftest defchange-works
  (let [query {:query :val}] ;; replace this with an actual query
    (db/defchange ::sample-change query)
    (let [a (get-in @photon.server.db/change-registry [::sample-change :feed])
          b (photon.server.db.PhotonChangeFeed. query (atom {:active? false}))]
      (is (= @(:conn a) @(:conn b)))
      (is (= (:query a) (:query b))))))

(deftest starting-a-changefeed-fails-unless-connection-is-configured
  (let [query (r/table "authors")]
    (db/defchange ::authors query)
    (let [changefeed (get-in @photon.server.db/change-registry [::authors :feed])]
      (try
        (photon.server.db/start changefeed)
        (is (= 0 1))
        (catch Exception e
          (is (= 0 0)))))))

(deftest we-can-start-and-stop-a-working-changefeed
  (setup)
  (photon.server.db/configure!)
  (let [query (r/table "authors")]
    (db/defchange ::authors query)
    (let [changefeed (get-in @photon.server.db/change-registry [::authors :feed])
          conn (r/connect)]
      (photon.server.db/start changefeed)
      ;; verify that the changefeed picks this up.
      (-> (r/table "authors")
          (r/insert {:demo "test-value-1"})
          (r/run conn))
      (is (= 0 1))
      (photon.server.db/stop changefeed)
      ;; verify that the (now closed) changefeed doesn't do anything with this
      (-> (r/table "authors")
          (r/insert {:demo "test-value-2"})
          (r/run conn)))))

(deftest subscribe!-fails-when-feed-is-not-defined
  (try
    (db/subscribe! ::authors (str (java.util.UUID/randomUUID)))
    (is (= 0 1))
    (catch Exception e
      (is (= (ex-data e)
             {:error :feed-not-found
              :feed ::authors})))))

(deftest subscribe!-adds-the-UUID-when-feed-is-defined
  (setup)
  (photon.server.db/configure!)
  (let [query (r/table "authors")
        uuid (str (java.util.UUID/randomUUID))]
    (db/defchange ::authors query)
    (db/subscribe! ::authors uuid)
    (is (= (-> @db/change-registry
               ::authors
               :subscribers)
           #{uuid}))
    (db/stop (-> @db/change-registry ::authors :feed)) ;; teardown
    ))
