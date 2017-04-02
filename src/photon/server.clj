(ns photon.server)

(defonce state
  (atom
   {:initialized? false

    :ajax-post-fn nil
    :ajax-get-or-ws-handshake-fn nil

    ; ChannelSocket's receive channel -- "ch-chsk"
    :ch-recv nil

    ; ChannelSocket's send API fn -- "chsk-send!"
    :send-fn nil

    ; watchable, read-only atom
    :connected-uids nil}))
