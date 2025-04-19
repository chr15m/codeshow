(ns main
  (:require
    [reagent.core :as r]
    [reagent.dom :as rdom]))

(def initial-code
  "(ns example)\n\n(print (+ 2 3))")

(def initial-config
  "{:dots true\n :filename \"example.cljs\"\n :language \"clojure\"}")

(defn config-editor []
  (let [editor-instance (r/atom nil)]
    [:div.config-editor
     {:ref (fn [el]
             (when el ; el is the DOM node
               (let [cm-options #js {:value initial-config
                                     :mode "clojure"
                                     :theme "seti"
                                     :lineNumbers false
                                     :matchBrackets true
                                     :autoCloseBrackets true
                                     :lineWrapping true
                                     :viewportMargin js/Infinity}]
                 (when-not @editor-instance
                   (let [cm (js/CodeMirror el cm-options)]
                     (.refresh cm)
                     (reset! editor-instance cm))))))}]))

(defn codemirror-editor []
  (let [editor-instance (r/atom nil)]
    [:div.threedots
     {:ref (fn [el]
             (when el ; el is the DOM node
               (let [cm-options #js {:value initial-code
                                     :mode "clojure"
                                     :theme "seti"
                                     :lineNumbers false
                                     :matchBrackets true
                                     :autoCloseBrackets true
                                     :lineWrapping true ; Wrap long lines
                                     ; Render all lines for auto height
                                     :viewportMargin js/Infinity}]
                 (when-not @editor-instance
                   (let [cm (js/CodeMirror el cm-options)]
                     ;; Optionally refresh to ensure proper layout
                     (.refresh cm)
                     (reset! editor-instance cm))))))}]))


(defn app []
  (let [show-config (r/atom false)]
    (fn []
      [:div.app-container
       {:on-mouse-enter #(reset! show-config true)
        :on-mouse-leave #(reset! show-config false)}
       (when @show-config
         [config-editor])
       [codemirror-editor]])))

(rdom/render [app] (.getElementById js/document "app"))
