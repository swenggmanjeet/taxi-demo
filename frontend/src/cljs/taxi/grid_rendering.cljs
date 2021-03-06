
;; ******************************************************************************
;; Copyright (C) 2016 Push Technology Ltd.
;;
;; Licensed under the Apache License, Version 2.0 (the "License");
;; you may not use this file except in compliance with the License.
;; You may obtain a copy of the License at
;; http://www.apache.org/licenses/LICENSE-2.0
;;
;; Unless required by applicable law or agreed to in writing, software
;; distributed under the License is distributed on an "AS IS" BASIS,
;; WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
;; See the License for the specific language governing permissions and
;; limitations under the License.
;; *******************************************************************************

(ns ^:figwheel-always taxi.grid-rendering
    (:require
     [taxi.world :as world]
     [taxi.util :as util]
     [om.core :as om :include-macros true]
     [om.dom :as dom :include-macros true]))

(enable-console-print!)

(def horizontal-blocks
  (dec (+ world/grid-border world/grid-border world/grid-width)))

(def vertical-blocks
  (dec (+ world/grid-border world/grid-border world/grid-height)))

(def layout-size
  (let
    [grid-width (+ world/grid-border world/grid-border world/grid-width)
     grid-height (+ world/grid-border world/grid-border world/grid-height)
     x-fragments (+ (* (dec grid-width) world/road-ratio) grid-width)
     y-fragments (+ (* (dec grid-height) world/road-ratio) grid-height)
     x-fragment-size (/ (* world/grid-ratio (.-innerWidth js/window)) x-fragments)
     y-fragment-size (/ (* world/grid-ratio (.-innerHeight js/window)) y-fragments)
     x-block-size (* x-fragment-size world/road-ratio)
     y-block-size (* y-fragment-size world/road-ratio)]
    (if (< x-block-size y-block-size)
      {:block-size (* x-fragment-size world/road-ratio)
       :road-width x-fragment-size
       :grid-height (* y-fragments x-fragment-size)
       :grid-width (* x-fragments x-fragment-size)
       :taxi-size (* x-fragment-size world/taxi-ratio)}
      {:block-size (* y-fragment-size world/road-ratio)
       :road-width y-fragment-size
       :grid-height (* y-fragments y-fragment-size)
       :grid-width (* x-fragments y-fragment-size)
       :taxi-size (* x-fragment-size world/taxi-ratio)})))

(def block-size
  (:block-size layout-size))

(def road-width
  (:road-width layout-size))

(def grid-height
  (:grid-height layout-size))

(def grid-width
  (:grid-width layout-size))

(def taxi-size
  (:taxi-size layout-size))

(def border-offset
  (* world/grid-border (+ block-size road-width)))

(defn- block-offset
  "Calculate the offset for a block from an index."
  [i]
  (+ road-width (* i (+ block-size road-width))))

(defn- taxi-offset
  "Calculate the offset for a taxi from an index."
  [i]
  (let [centering-offset (- (/ road-width 2) (/ taxi-size 2))
        grid-offset (* i (+ block-size road-width))]
      (+ centering-offset grid-offset border-offset)))

(defn- block
  "Render a single block.
  Returns a component that represents a block."

  [{:keys [x y]}]
  (dom/div
   #js {:style #js {:left x
                    :top y
                    :height block-size
                    :width block-size}
        :className "block"}
   nil))

(defn- grid
  "Returns a sequence containing all the React components that represent a
  block in the grid."
  [state]

  (let [block-list (for [i (range horizontal-blocks) j (range vertical-blocks)]
                {:x (block-offset i)
                 :y (block-offset j)})]
    (map block block-list)))

(defn- location
  "Render a single named location.
  Returns a React component that represents a named location."
  [{:keys [image x y height width name colour]}]

  (reify om/IRender
    (render
     [_]
     (let [style (if (nil? image)
                   #js {:left x
                       :top y
                       :height height
                       :width width}
                   #js {:backgroundImage (get world/images image)
                       :left x
                       :top y
                       :height height
                       :width width})]
       (dom/div
        #js {:className "location"
             :style style}
        (dom/span
         #js { :style #js {:position "absolute"
                           :bottom 0
                           :color colour
                           :textAlign "center"
                           :width width}}
         name))))))

(defn- locations
  "Returns a sequence containing all the React components that represent a
  location"
  [state]

  (om/build-all
   location
   world/locations
   {:fn (fn [{:keys [x y ratio] :as location}]
          (assoc location
            :x (block-offset (+ world/grid-border x))
            :y (block-offset (+ world/grid-border y))
            :height (* ratio block-size)
            :width (* ratio block-size)))}))

(defn- taxi-info-facts
  "Return a sequence of dom elements based on the requsted facts about the taxi"
  [taxi facts]

  (map (fn [[key title format]]
         (let [value (get taxi key)]
           (if (nil? value)
             nil
             (dom/tr nil
                     (dom/td nil (str title ":"))
                     (dom/td nil (format value))))))
       facts))

(defn- taxi-info
  "Return a taxi info div."
  [taxi]

  (dom/div #js {:className "taxiInfo"
                :style #js {:top taxi-size
                            :left taxi-size}}
           (apply dom/table
                  nil
                  (taxi-info-facts taxi [[:display-name "Name" identity]
                                         [:direction "Direction" util/direction-to-string]
                                         [:estimated-speed "Estimated speed" (partial util/float-to-string 2)]
                                         [:position "Position" util/location-to-string]
                                         [:known-position "Last known position" util/location-to-string]
                                         [:age "Age" identity]]))))

(defn- taxi
  "Render a single taxi."
  [{:keys [x y width height direction] :as taxi}]

  (reify om/IRender
    (render
     [_]
     (dom/div #js {:className "taxiContainer"
                   :style #js {:position "absolute"
                               :top y
                               :left x}}
              (dom/div #js {:className "taxi"
                            :style #js {:backgroundImage (get world/images direction)
                                        :width width
                                        :height height}})
              (taxi-info taxi)))))

(defn- taxis
  "Returns a sequence containing all the React components that represent a
  taxi"
  [state]

  (om/build-all
   taxi
   (vals (:all-taxis state))
   {:fn  (fn [{:keys [position] :as taxi}]
           (assoc taxi :x (taxi-offset (nth position 0))
                       :y (taxi-offset (nth position 1))
                       :direction (if (= 2 (count position)) :up (nth position 2))
                       :width taxi-size
                       :height taxi-size))}))

(defn- passenger-info [passenger]
  "Return a passenger info div."
  (dom/div #js {:className "passengerInfo"
                :style #js {:left taxi-size
                            :position "absolute"}}
           (dom/div nil (dom/span nil
                                  (str (:display-name passenger)
                                       " wants to go to "
                                       (util/location-to-string (:destination (:journey passenger))))))
           (if (not (nil? (:bid passenger)))
             (dom/div nil (dom/span nil
                                    (str "Current bid "
                                         (util/money-to-string (:bid passenger))))))))

(defn- waiting-passenger [passenger]
  "Render a single waiting passenger."
  (reify om/IRender
    (render
     [_]
     (dom/div #js {:className "passenger"
                   :style #js {:height taxi-size
                               :width taxi-size
                               :left (block-offset
                                      (nth (:location (:journey passenger)) 0))
                               :top  (block-offset
                                      (nth (:location (:journey passenger)) 1))}}
              (passenger-info passenger)))))

(defn- gather-journey-info
  "Gather the information needed to display journeys on the world view."
  [state]

  (let [passengers (filter #(= (:journey-state %) :pending) (vals (:global-journeys state)))]
    (map (fn [journey]
           (let [auctions-for-journey (filter #(= (:journey-id journey) (:journey-id %)) (vals (:auctions state)))]
             (if
               (first auctions-for-journey)
               ; Found an auction for the journey add current bid information
               (assoc journey :bid (:bid (first auctions-for-journey)))
               ; Did not find an auction for the journey
               journey)))
         passengers)))

(defn- waiting-passengers
  "Returns a sequence containing all the React components that represent
  pending journeys."
  [state]
  (om/build-all
   waiting-passenger
   (gather-journey-info state)))

(defn- passenger-arrival [passenger]
  "Return a passenger arrival div."
  (dom/div #js {:className "passengerInfo"
                :style #js {:left taxi-size
                            :position "absolute"}}
           (dom/div nil (dom/span nil
                                  (str (:display-name passenger)
                                       " has arrived at "
                                       (util/location-to-string (:destination (:journey passenger))))))))

(defn- arrived-passenger [passenger]
  "Render a single waiting passenger."
  (reify om/IRender
    (render
     [_]
     (dom/div #js {:className "passenger"
                   :style #js {:height taxi-size
                               :width taxi-size
                               :left (block-offset
                                      (nth (:destination (:journey passenger)) 0))
                               :top  (block-offset
                                      (nth (:destination (:journey passenger)) 1))}}
              (passenger-arrival passenger)))))

(defn- arrived-passengers
  "Returns a sequence containing all the React components that represent
  completed journeys."
  [state]
  (om/build-all
   arrived-passenger
   (filter #(= (:journey-state %) :complete) (vals (:global-journeys state)))))

(defn view
  [state owner]
  (reify om/IRender
    (render
      [_]
      (dom/div nil
       (apply dom/div #js {:className "grid"
                           :style #js {:height grid-height
                                       :width grid-width}}
        (concat
          (grid state)
          (taxis state)
          (locations state)
          (waiting-passengers state)
          (arrived-passengers state)
          ))))))

