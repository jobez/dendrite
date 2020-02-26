(ns dendrite.core
  (:require [clojure.core.async :as async]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure-zulip.core :as zulip])
  (:import [java.io StringWriter]
           [java.net Socket]))

;; * tcp socketry

(defn %send-request
  "Sends an HTTP GET request to the specified host, port, and path"
  [host port path]
  (with-open [sock (Socket. host port)
              writer (io/writer sock)
              reader (io/reader sock)
              response (StringWriter.)]
    (.append writer (str  path "\n"))
    (.flush writer)
    (.readLine reader)))

(def send-request (partial %send-request
                            (System/getenv "CYC_SERVER")
                            (some-> (System/getenv "CYC_PORT")
                                    (Integer/parseInt )) ))

  ;; * zulip

(def conn
  (zulip/connection
   {:username
    (System/getenv "ZULIP_USERNAME")
    :api-key
    (System/getenv "ZULIP_APIKEY")
    :base-url
    (System/getenv "ZULIP_BASEURL") }))

(clojure.pprint/pprint conn)

(defn dispatch-msg! [event]
  (let [message (:message event)
        {stream :display_recipient
         message-type :type
         sender :sender_email
         :keys [sender_full_name subject content]} message]
    (clojure.pprint/pprint event)
    (when (str/starts-with? content "!cyc ")
      (zulip/send-stream-message conn stream subject
                                 (->> content
                                  (drop 5)
                                  (apply str)
                                  (send-request))))))

(defn handle-event
  "Check whether event contains a message starting with '!echo' if yes,
  reply (either in private or on stream) with the rest of the message."
  [conn event]
  (let [message (:message event)
        {stream :display_recipient
         message-type :type
         sender :sender_email
         :keys [subject content]} message]
    (dispatch-msg! event)))


(defn mk-handler-channel
  "Create channel that calls `handle-event` on input with `conn`"
  [conn]
  (let [c (async/chan)]
    (async/go-loop []
      (handle-event conn (async/<! c))
      (recur))
    c))


(defn -main [& args]
  (let [register-response (zulip/sync*
                            (zulip/register
                             conn))

         event-channel (zulip/subscribe-events conn register-response)
         handler-channel (mk-handler-channel conn)]

     (async/pipe event-channel handler-channel)))
