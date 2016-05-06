(ns taxi.auction
  (:use
   [clojure.core.async :only [>! <! <!! close! go go-loop chan timeout]]
   [clojure.edn :as edn :only [read-string]])
  (:require [taxi.communication :as diffusion])
  (:import com.pushtechnology.diffusion.client.Diffusion
           com.pushtechnology.diffusion.client.session.Session
           com.pushtechnology.diffusion.client.session.Session$Listener
           com.pushtechnology.diffusion.client.session.Session$State
           com.pushtechnology.diffusion.client.features.control.topics.MessagingControl
           com.pushtechnology.diffusion.client.features.control.topics.MessagingControl$MessageHandler
           com.pushtechnology.diffusion.client.features.control.topics.MessagingControl$SendCallback
           com.pushtechnology.diffusion.client.features.control.topics.TopicControl
           com.pushtechnology.diffusion.client.features.control.topics.TopicControl$AddCallback
           com.pushtechnology.diffusion.client.features.control.topics.TopicControl$RemoveCallback
           com.pushtechnology.diffusion.client.features.Topics
           com.pushtechnology.diffusion.client.features.Topics$CompletionCallback
           com.pushtechnology.diffusion.client.features.control.topics.TopicUpdateControl
           com.pushtechnology.diffusion.client.features.control.topics.TopicUpdateControl$Updater$UpdateCallback
           com.pushtechnology.diffusion.client.features.Topics$TopicStream
           com.pushtechnology.diffusion.client.topics.details.TopicType
           com.pushtechnology.diffusion.client.callbacks.TopicTreeHandler

           java.lang.Thread
           java.security.KeyStore
           javax.net.ssl.SSLContext
           javax.net.ssl.TrustManagerFactory))

(def auction-time-ms (* 20 1000))
(def auction-keep-alive-ms (* 2 60 1000))

(defn- close-bidding
  "Update the state of the auction to prevent further bids being accepted."
  [state auction-id]

  (let [{:keys [bid auction-state] :as auction} (get-in state [:auctions auction-id])]

    (if (= :open auction-state)
      (let [offer (assoc auction :auction-state :offered)]
        (assoc-in state [:auctions auction-id] offer))

      state)))

(defn- close-auction [app-state auction-id auction-chan]
  (let [state (swap! app-state close-bidding auction-id)
        auction (get-in state [:auctions auction-id])]
    (println "Close auction" auction-id "bid" (:bid auction))
    (diffusion/update-topic (:session @app-state) auction-chan (str "controller/auctions/" auction-id) auction)
    (go
     (<! (timeout auction-keep-alive-ms))
     (diffusion/remove-topics (:session @app-state) auction-chan (str ">controller/auctions/" auction-id)))
    ))

(defn- claim-auction
  "Update the app-state with a new auction."
  [{:keys [last-auction-id session] :as state} request]

  (let [auction-id (inc last-auction-id)
        auction {:id auction-id :journey request :auction-state :open}]
    (-> state
        (assoc-in [:auctions auction-id] auction)
        (assoc :last-auction-id auction-id))))

(defn- start-auction
  "Process new :journey message event."
  [request auction-chan app-state]

  (let [new-state (swap! app-state claim-auction request)
        auction-id (:last-auction-id new-state)
        auction (get-in new-state [:auctions auction-id])]

    (println "Starting auction" auction-id new-state)

    (diffusion/add-topic (:session @app-state) auction-chan (str "controller/auctions/" auction-id) auction)

    (go
      (<! (timeout auction-time-ms))
      (close-auction app-state auction-id auction-chan))))

(defn- update-auction-state
  "Update the auction with a new bid if the auction is still open."
  [state auction session-id]

  (let [{:keys [id bid]} auction
        {old-bid :bid auction-state :auction-state} (get-in state [:auctions id])]
    (if (and (= :open auction-state)
             (or (nil? old-bid) (> old-bid bid)))
      (assoc-in state [:auctions id] auction)

      state)))

(defn- update-auction
  "Process new :bid message event."
  [{:keys [id] :as auction} session-id auction-chan app-state]

  (let [new-state (swap! app-state update-auction-state auction session-id)
        current-auction (get-in new-state [:auctions id])]
    (diffusion/update-topic (:session @app-state) auction-chan (str "controller/auctions/" id) current-auction)))

(defn- journey-accepted
  ""
  [value])

(defn- taxi-arrived-to-collect
  ""
  [value])

(defn process-message
  "Process message events taken from the channel.
  Dispatches based on the type of message received."
  [app-state auction-chan session-id {:keys [type value] :as request}]

  (condp = type
    :journey  (start-auction value auction-chan app-state)
    :bid      (update-auction value session-id auction-chan app-state)
    :accept-journey (journey-accepted value)
    :collection-arrival (taxi-arrived-to-collect value)
    (println "Ignoring" request)))