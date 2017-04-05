(ns photon.server.db-test
  (:require [clojure.core.async :as async]
            [clojure.test :refer :all]
            [bond.james :as bond :refer [with-spy]]
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
    (let [a (::sample-change @photon.server.db/change-registry)
          b (photon.server.db.PhotonChangeFeed.
             query (atom {:active? false}) #{})]
      (is (= @(:feed a) @(:feed b)))
      (is (= (:query a) (:query b))))))

(deftest starting-a-changefeed-fails-unless-connection-is-configured
  (let [query (r/table "authors")]
    (db/defchange ::authors query)
    (let [changefeed (get-in @photon.server.db/change-registry [::authors])]
      (try
        (photon.server.db/start changefeed)
        (is (= 0 1))
        (catch Exception e
          (is (= 0 0)))))))

(deftest we-can-start-and-stop-a-working-changefeed
  (setup)
  (photon.server.db/configure!)
  ;; This runs async 
  (async/<!!
   (async/go
     (let [query (r/table "authors")]
       (db/defchange ::authors query)
       (let [changefeed (::authors @db/change-registry)
             conn (r/connect)]
         (with-spy [db/emit!]
           (db/start changefeed)
           ;; verify that the changefeed picks this up.

           ;; Sleep for 2 seconds to ensure the changefeed starts before inserting
           ;; a change.
           ;; TODO: Figure out a way to actually check and verify that the changefeed
           ;; has started rather than just sleeping the thread for 2s.
           (Thread/sleep 2000)
           (-> (r/table "authors")
               (r/insert {:demo "test-value-1"})
               (r/run conn))
           ;; Loop until we get the emit! call. This is a bit lazy in that it
           ;; will block forever if we don't get a message.
           (loop []
             (when-not (= (-> db/emit! bond/calls count) 1)
               (recur)))
           (db/stop changefeed)
           (is (= (-> db/emit! bond/calls count) 1))
           ;; verify that the (now closed) changefeed doesn't do anything with this
           (-> (r/table "authors")
               (r/insert {:demo "test-value-2"})
               (r/run conn))
           ;; No additional calls to db/emit!
           (is (= (-> db/emit! bond/calls count) 1))))))))

(deftest subscribe!-fails-when-feed-is-not-defined
  (try
    (db/subscribe! ::authors (str (java.util.UUID/randomUUID)))
    (is (= 0 1))
    (catch Exception e
      (is (= (ex-data e)
             {:error :feed-not-found
              :feed ::authors})))))

(deftest subscribe!-adds-the-UUID-when-feed-is-defined-and-starts-the-feed
  (setup)
  (photon.server.db/configure!)
  (let [query (r/table "authors")
        uuid (str (java.util.UUID/randomUUID))]
    (db/defchange ::authors query)
    (db/subscribe! ::authors uuid)
    (testing "that the UID is added"
      (is (= (-> @db/change-registry
                 ::authors
                 :subscribers)
             #{uuid})))
    (testing "that the feed is active"
      (is (db/active? (-> @db/change-registry
                          ::authors))))
    (db/stop (-> @db/change-registry ::authors)) ;; teardown
))

#_(deftest unsubscribe!-stops-the-feed-only-when-no-uids-are-left
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
