(ns main
  (:require
    [reagent.core :as r]
    [reagent.dom :as rdom]
    [clojure.core :refer [read-string]]))

(defonce state
  (r/atom
    {:code "(ns example)\n\n(print (+ 2 3))"
     :config "{:dots true\n :filename \"example.cljs\"\n :language \"clojure\"}"
     :show-config false
     :ui {:dots true
          :filename "example.cljs"
          :language "clojure"}}))

(defonce cm-instances
  (atom {:code nil
         :config nil}))

(defn parse-config [config-str]
  (try
    (read-string config-str)
    (catch js/Error e
      (js/console.error "Failed to parse config:" e)
      nil)))

(defn update-ui-from-config [config-str]
  (when-let [config (parse-config config-str)]
    (swap! state update :ui merge config)))

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
                   (.on cm "change" (fn [_ _]
                                      (let [new-value (.getValue cm)]
                                        (swap! state assoc :config new-value)
                                        (update-ui-from-config new-value))))
                   (.refresh cm)
                   (swap! cm-instances assoc :config cm))))))}])

(defn codemirror-editor [state]
  (let [ui (:ui @state)]
    [:div
     {:class (when (:dots ui) "threedots")
      :style {"--filename" (str "\"" (:filename ui) "\"")}}
     [:div.editor-container
      {:ref (fn [el]
              (when el ; el is the DOM node
                (let [cm-options #js {:value (:code @state)
                                      :mode (:language ui)
                                      :theme "seti"
                                      :lineNumbers false
                                      :matchBrackets true
                                      :autoCloseBrackets true
                                      :lineWrapping true ; Wrap long lines
                                      ; Render all lines for auto height
                                      :viewportMargin js/Infinity}]
                  (if-let [cm (:code @cm-instances)]
                    (do
                      (.setOption cm "mode" (:language ui))
                      (.refresh cm))
                    (let [cm (js/CodeMirror el cm-options)]
                      ;; Optionally refresh to ensure proper layout
                      (.refresh cm)
                      (swap! cm-instances assoc :code cm))))))}]]))

(defn app [state]
  [:div.app-container
   {:on-mouse-enter #(swap! state assoc :show-config true)
    :on-mouse-leave #(swap! state assoc :show-config false)}
   [config-editor state]
   [codemirror-editor state]])

;; Initialize UI from config on startup
(update-ui-from-config (:config @state))

(rdom/render [app state] (.getElementById js/document "app"))
