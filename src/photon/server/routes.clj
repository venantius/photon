(ns photon.server.routes
  (:require [compojure.core :refer [defroutes GET POST]]
            [compojure.route :as route]
            [photon.config :as config]
            [photon.server :as server]))

(defn add-random-uid
  "Add a random UID to the user's session."
  [req]
  (assoc-in req [:session :uid] (str (java.util.UUID/randomUUID))))

;; Add this to your app's routes.
(defroutes photon-routes
  (GET config/photon-endpoint req
    ((:ajax-get-or-ws-handshake-fn @server/state)
     (add-random-uid req)))
  (POST config/photon-endpoint req
    ((:ajax-post-fn @server/state)
     (add-random-uid req))))
