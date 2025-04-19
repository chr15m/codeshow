(ns main
  (:require
    [reagent.core :as r]
    [reagent.dom :as rdom]))

(defonce state
  (r/atom
    {:code "(ns example)\n\n(print (+ 2 3))"
     :config "{:dots true\n :filename \"example.cljs\"\n :language \"clojure\"}"
     :show-config false}))

(defonce cm-instances
  (atom {:code nil
         :config nil}))

(defn config-editor [state]
  [:div.config-editor
   {:class (when (not (:show-config @state)) "hidden")
    :ref (fn [el]
           (when el ; el is the DOM node
             (let [cm-options #js {:value (:config @state)
                                   :mode "clojure"
                                   :theme "seti"
                                   :lineNumbers false
                                   :matchBrackets true
                                   :autoCloseBrackets true
                                   :lineWrapping true
                                   :viewportMargin js/Infinity}]
               (when-not (:config @cm-instances)
                 (let [cm (js/CodeMirror el cm-options)]
                   (.refresh cm)
                   (swap! cm-instances assoc :config cm))))))}])

(defn codemirror-editor [state]

  [:div.threedots
   {:ref (fn [el]
           (when el ; el is the DOM node
             (let [cm-options #js {:value (:code @state)
                                   :mode "clojure"
                                   :theme "seti"
                                   :lineNumbers false
                                   :matchBrackets true
                                   :autoCloseBrackets true
                                   :lineWrapping true ; Wrap long lines
                                   ; Render all lines for auto height
                                   :viewportMargin js/Infinity}]
               (when-not (:code @cm-instances)
                 (let [cm (js/CodeMirror el cm-options)]
                   ;; Optionally refresh to ensure proper layout
                   (.refresh cm)
                   (swap! cm-instances assoc :code cm))))))}])

(defn app [state]
  [:div.app-container
   {:on-mouse-enter #(swap! state assoc :show-config true)
    :on-mouse-leave #(swap! state assoc :show-config false)}
   [config-editor state]
   [codemirror-editor state]])

(rdom/render [app state] (.getElementById js/document "app"))
