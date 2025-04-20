(ns main
  (:require
    [reagent.core :as r]
    [reagent.dom :as rdom]
    [clojure.core :refer [read-string]]))

(defn edn-to-str [e]
  (-> e pr-str (.replaceAll " :" "\n :") (.replaceAll ",\n" "\n")))

(def initial-config
  {:dots true
   :filename "example.cljs"
   :language "clojure"})

(defonce state
  (r/atom
    {:code "(ns example)\n\n(print (+ 2 3))"
     :config (edn-to-str initial-config)
     :show-config false ;; Start hidden
     :ui initial-config}))

(defonce cm-instances
  (atom {:code nil
         :config nil}))

(defn parse-config [config-str]
  (try
    (read-string config-str)
    (catch js/Error e
      (js/console.error "Failed to parse config:" e)
      nil)))

(defn save-state-to-storage [state-val]
  (try
    (let [to-save (select-keys state-val [:code :config :ui])
          edn-str (pr-str to-save)]
      (.setItem js/localStorage "code-editor-state" edn-str))
    (catch js/Error e
      (js/console.error "Failed to save state to localStorage:" e))))

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
               (if-let [cm (:config @cm-instances)]
                 ;; If instance exists, just ensure value is up-to-date
                 (when (not= (.getValue cm) (:config @state))
                   (.setValue cm (:config @state)))
                 ;; Else, create the instance
                 (let [cm (js/CodeMirror el cm-options)]
                   (.on cm "change" (fn [_ _]
                                      (let [new-value (.getValue cm)]
                                        (swap! state assoc :config new-value)
                                        (update-ui-from-config new-value)
                                        (save-state-to-storage @state))))
                   (.refresh cm)
                   (swap! cm-instances assoc :config cm))))))}])

(defn codemirror-editor [state]
  (let [ui (:ui @state)
        has-filename (seq (:filename ui))
        top-padding (if (or (:dots ui) has-filename) "2em" "1em")
        filename-display (if (empty? (:filename ui)) "none" "block")]
    [:div
     {:class (when (:dots ui) "threedots")
      :style {"--filename" (str "\"" (:filename ui) "\"")
              "--top-padding" top-padding
              "--filename-display" filename-display}}
     [:div.editor-container
      {:on-mouse-enter #(swap! state assoc :show-config false)
       :on-mouse-leave #(swap! state assoc :show-config true)
       :ref (fn [el]
              (when el
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
                      ;; Update mode if it changed
                      (when (not= (.getOption cm "mode") (:language ui))
                        (.setOption cm "mode" (:language ui)))
                      ;; Refresh needed if mode changes or other updates occur
                      (.refresh cm))
                    ;; Else, create the instance
                    (let [cm (js/CodeMirror el cm-options)]
                      ;; Set up change handler to save state
                      (.on cm "change" (fn [_ _]
                                         (let [new-value (.getValue cm)]
                                           (swap! state assoc :code new-value)
                                           (save-state-to-storage @state))))
                      ;; Optionally refresh to ensure proper layout
                      (.refresh cm)
                      (swap! cm-instances assoc :code cm))))))}]]))

(defn app [state]
  [:div.app-container
   [config-editor state]
   [codemirror-editor state]])

(rdom/render [app state] (.getElementById js/document "app"))

(try
  (when-let [saved-state (.getItem js/localStorage "code-editor-state")]
    (print (read-string saved-state))
    (swap! state merge (read-string saved-state))
    (some-> (:code @cm-instances) .getDoc (.setValue (:code @state))))
  (catch :default _e nil))

(update-ui-from-config (:config @state))

(defonce event-handlers
  (let [body (.-body js/document)]
    (.addEventListener body "mouseenter"
                       #(swap! state assoc :show-config true))
    (.addEventListener body "mouseleave"
                       #(swap! state assoc :show-config false))
    (.addEventListener body "click"
                       #(when (identical? (.-target %) body)
                          (swap! state update :show-config not)))))
