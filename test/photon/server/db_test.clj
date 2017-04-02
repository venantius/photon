(ns photon.server.db-test
  (:require [clojure.test :refer :all]
            [photon.server.db :as db]
            [rethinkdb.query :as r]))

(def author-atom (atom {}))

;; most of this stuff is only for development. Maybe it should go in test?
;; Create our tables.
(defn setup []
  (-> (r/db "test")
      (r/table-create "authors")))

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
          b (photon.server.db.PhotonChangeFeed. query (atom {:active? false}))]
      (is (= @(:conn a) @(:conn b)))
      (is (= (:query a) (:query b))))))

(deftest starting-a-changefeed-fails-unless-connection-is-configured
  (let [query (r/table "authors")]
    (db/defchange ::authors query)
    (let [changefeed (::authors @photon.server.db/change-registry)]
      (try
        (photon.server.db/start changefeed)
        (is (= 0 1))
        (catch Exception e
          (is (= 0 0)))))))

(deftest we-can-start-a-changefeed
  (photon.server.db/configure!)
  (let [query (r/table "authors")]
    (db/defchange ::authors query)
    (let [changefeed (::authors @photon.server.db/change-registry)]
      (photon.server.db/start changefeed)
      (let [conn (r/connect)]
        (-> (r/table "authors")
            (r/insert {:demo "test"})
            (r/run conn))))))
