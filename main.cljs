(ns main
  (:require
    [reagent.core :as r]
    [reagent.dom :as rdom]))

(def initial-code
  "(ns example)\n\n(print (+ 2 3))")

(defn codemirror-editor []
  (let [editor-instance (r/atom nil)] ; To hold the CodeMirror instance if needed later
    [:div {:ref (fn [el]
                  (when el ; el is the DOM node
                    (let [cm-options #js {:value initial-code
                                          :mode "clojure"
                                          :theme "seti"
                                          :lineNumbers false
                                          :matchBrackets true
                                          :autoCloseBrackets true
                                          :lineWrapping true ; Wrap long lines
                                          :viewportMargin js/Infinity ; Render all lines for auto height
                                          }]
                      ;; Check if CodeMirror is already initialized
                      (when-not @editor-instance
                        (let [cm (js/CodeMirror el cm-options)]
                          ;; Optionally refresh to ensure proper layout
                          (.refresh cm)
                          (reset! editor-instance cm))))))}]))


(defn app []
  [codemirror-editor])

(rdom/render [app] (.getElementById js/document "app"))
