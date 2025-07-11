(ns main
  {:clj-kondo/config '{:lint-as {promesa.core/let clojure.core/let}}}
  (:require
    [reagent.core :as r]
    [reagent.dom :as rdom]
    [clojure.string :as str]
    [clojure.core :refer [read-string]]
    [promesa.core :as p]))

;*** data ***;

(def cdn-root "https://cdnjs.cloudflare.com/ajax/libs/codemirror/5.65.15")

(def themes
  ["3024-day" "3024-night" "abbott" "abcdef" "ambiance-mobile" "ambiance"
   "ayu-dark" "ayu-mirage" "base16-dark" "base16-light" "bespin" "blackboard"
   "cobalt" "colorforth" "darcula" "dracula" "duotone-dark" "duotone-light"
   "eclipse" "elegant" "erlang-dark" "gruvbox-dark" "hopscotch" "icecoder"
   "idea" "isotope" "juejin" "lesser-dark" "liquibyte" "lucario"
   "material-darker" "material-ocean" "material-palenight" "material" "mbo"
   "mdn-like" "midnight" "monokai" "moxer" "neat" "neo" "night" "nord"
   "oceanic-next" "panda-syntax" "paraiso-dark" "paraiso-light"
   "pastel-on-dark" "railscasts" "rubyblue" "seti" "shadowfox" "solarized"
   "ssms" "the-matrix" "tomorrow-night-bright" "tomorrow-night-eighties"
   "ttcn" "twilight" "vibrant-ink" "xq-dark" "xq-light" "yeti" "yonce"
   "zenburn"])

(defonce modes
  (->> js/CodeMirror.modeInfo
       (map #(.-mode %))
       (filter identity)
       (remove #(= "null" %))
       distinct
       sort
       vec))

(def initial-ui
  {:dots true
   :filename "hello.py"
   :mode "python"
   :theme "hopscotch"
   :line-numbers false})

(defonce state
  (r/atom
    {:code (str "def hello(who):\n  return \"Hello, \" + who + \"!\"\n\nhello(\"Billy\")")
     :show-config true
     :show-help false
     :readme-content nil
     :ui initial-ui}))

(defonce cm-instances
  (atom {:code nil}))

;*** functions ***;

(defn toggle-fullscreen []
  (if (.-fullscreenElement js/document)
    (.exitFullscreen js/document)
    (.requestFullscreen (.-documentElement js/document))))

(defn save-state-to-storage [*state]
  (try
    (let [to-save (select-keys *state [:code :ui]) ; Save :code and :ui
          edn-str (pr-str to-save)]
      (.setItem js/localStorage "code-editor-state" edn-str))
    (catch js/Error e
      (js/console.error "Failed to save state to localStorage:" e))))

(defn update-cm-options [cm ui]
  (when cm
    (.setOption cm "theme" (:theme ui))
    (.setOption cm "mode" (:mode ui))
    (.setOption cm "lineNumbers" (:line-numbers ui))
    (.refresh cm)))

(defn filter-readme-content [content]
  (when content
    (->> (str/split-lines content)
         (remove #(or
                    ; h1 headers
                    (re-find #"^# " %)
                    ; images
                    (re-find #"!\[.*\]\(.*\)" %)))
         (map (fn [line]
                (let [line-with-links (str/replace line #"\[(.*?)\]\((.*?)\)" "<a href=\"$2\">$1</a>")]
                  (cond
                    ; Convert h2 headers to hiccup
                    (re-find #"^## " line)
                    (str "<h2>" (str/replace line #"^## " "") "</h2>")

                    ; Convert list items (lines starting with dash)
                    (re-find #"^- " line)
                    (str line-with-links "</br>\n")

                    ; Lines with links but not starting with dash
                    (re-find #"\[.*?\]\(.*?\)" line)
                    line-with-links

                    :else
                    (str "<p>" line "</p>")))))
         (str/join "\n"))))

(defn load-readme []
  (p/let [response (js/fetch "README.md")
          text (when (.-ok response) (.text response))]
    (when text
      (swap! state assoc :readme-content (filter-readme-content text)))))

(defn add-script-tag [url]
  (if (.querySelector js/document (str "script[src=\"" url "\"]"))
    (p/resolved true)
    (js/Promise.
      (fn [res rej]
        (let [doc js/document
              head (or (.-head doc) (.-documentElement doc))
              script-el (.createElement doc "script")]
          (aset script-el "type" "text/javascript")
          (aset script-el "className" "dynamic")
          (aset script-el "onload" #(res true))
          (aset script-el "onerror"
                (fn [err]
                  (js/console.log "Failed to load script:" url err)
                  (rej err)))
          (aset script-el "src" url)
          (.appendChild head script-el))))))

(defn add-mode-script-tag [mode]
  (let [script-src (str cdn-root "/mode/" mode "/" mode ".min.js")]
    (add-script-tag script-src)))

(defn setup-cm-amd-loader! []
  (let [define-fn (fn [deps factory]
                    (let [base-url (.-src js/document.currentScript)
                          deps-clj (js/Array.from deps)
                          deps-to-load (remove #{"../../lib/codemirror"} deps-clj)
                          script-promises (map (fn [dep-path]
                                                 (let [dep-url (.toString (js/URL. (str dep-path ".min.js") base-url))]
                                                   (add-script-tag dep-url)))
                                               deps-to-load)]
                      (p/do!
                        (p/all script-promises)
                        (factory js/CodeMirror))))]
    (aset js/window "define" define-fn)
    (aset (.-define js/window) "amd" #js {:jQuery false})))

(defn remove-script-tag [el]
  (.removeChild (.-parentNode el) el))

;*** components ***;

(defn help-modal [state]
  (when (:show-help @state)
    [:div.modal-overlay
     {:on-click #(swap! state assoc :show-help false)}
     [:div.modal-content
      {:on-click (fn [e] (.stopPropagation e))}
      [:div.modal-header
       [:h2 "About CodeShow"]
       [:button.close-button
        {:on-click #(swap! state assoc :show-help false)}
        "×"]]
      [:div.modal-body
       (if-let [content (:readme-content @state)]
         [:div
          [:div {:dangerouslySetInnerHTML {:__html content}}]
          [:p
           [:a {:href "https://github.com/chr15m/codeshow" :target "_blank"}
            "Source code on GitHub"] "."]
          [:p
           "Made by "
           [:a {:href "https://mccormick.cx" :target "_blank"}
            "Chris McCormick"] "."]]
         [:p "Loading README..."])]]]))

(defn config-strip [state]
  (let [ui (:ui @state)]
    (when (:show-config @state)
      [:div.config-strip
       [:button {:on-click #(swap! state update-in [:ui :dots] not)}
        (if (:dots ui) "✅ Dots" "🚫 Dots")]
       [:input {:type "text"
                :placeholder "filename"
                :value (:filename ui)
                :on-change #(swap! state assoc-in [:ui :filename]
                                   (-> % .-target .-value))}]
       [:button {:on-click #(swap! state update-in [:ui :line-numbers] not)}
        (if (:line-numbers ui) "✅ Line #" "🚫 Line #")]
       [:select {:value (:mode ui)
                 :on-change #(swap! state assoc-in [:ui :mode]
                                    (-> % .-target .-value))}
        (for [mode-id modes]
          ^{:key mode-id} [:option {:value mode-id} mode-id])]
       [:select {:value (:theme ui)
                 :on-change #(swap! state assoc-in [:ui :theme]
                                    (-> % .-target .-value))}
        (for [theme (sort themes)]
          ^{:key theme} [:option {:value theme}
                         (str/replace theme #"\.css$" "")])]
       [:button {:on-click toggle-fullscreen}
        "Fullscreen"]
       [:button.help-button
        {:on-click #(swap! state assoc :show-help true)}
        "?"]])))

(defn codemirror-editor [state]
  (let [ui (:ui @state)
        has-filename (seq (:filename ui))
        top-padding (if (or (:dots ui) has-filename) "2em" "1em")
        filename-display (if (empty? (:filename ui)) "none" "block")]
    [:div.CodeMirror-themed-filename.cm-comment
     {:class (when (:dots ui) "threedots")
      :style {"--filename" (str "\"" (:filename ui) "\"")
              "--top-padding" top-padding
              "--filename-display" filename-display}}
     [:div.editor-container
      {;:on-mouse-enter #(swap! state assoc :show-config false)
       ;:on-mouse-leave #(swap! state assoc :show-config true)
       :ref (fn [el]
              (when el
                (let [cm-options #js {:value (:code @state)
                                      :mode (:mode ui)
                                      :theme (:theme ui)
                                      :lineNumbers (:line-numbers ui)
                                      :matchBrackets true
                                      :autoCloseBrackets true
                                      :lineWrapping true
                                      ; Render all lines for auto height
                                      :viewportMargin js/Infinity}]
                  (if-let [cm (:code @cm-instances)]
                    (update-cm-options cm ui)
                    (let [cm (js/CodeMirror el cm-options)]
                      (.on cm "change" (fn [_ _]
                                         (let [new-value (.getValue cm)]
                                           (swap! state assoc :code new-value)
                                           (save-state-to-storage @state))))
                      (.refresh cm)
                      (swap! cm-instances assoc :code cm))))))}]]))

(defn app [state]
  [:div.app-container
   [config-strip state]
   [:div.screenshot-wrapper
    [codemirror-editor state]]
   [help-modal state]])

;*** launch ***;

(setup-cm-amd-loader!)

(rdom/render [app state] (.getElementById js/document "app"))

(defn update-dynamic-settings! [*state]
  (let [{:keys [mode theme]} (:ui *state)
        style-el (js/document.getElementById "theme")]
    (aset style-el "href" (str cdn-root "/theme/" theme ".min.css"))
    (let [mode-els (js/document.querySelectorAll "script.dynamic")]
      (js/console.log "mode-els" (js/Array.from mode-els))
      (doseq [el (js/Array.from mode-els)]
        (remove-script-tag el)))
    (js/console.log "modeInfo" js/CodeMirror.modeInfo)
    (p/do!
      (add-mode-script-tag mode)
      (when-let [cm (:code @cm-instances)]
        (update-cm-options cm (:ui *state))))))

(add-watch state :ui-watcher
  (fn [_ _ old-state new-state]
    (when (not= (:ui old-state) (:ui new-state))
      (update-dynamic-settings! new-state)
      (save-state-to-storage new-state))))

(try
  (when-let [saved-state (.getItem js/localStorage "code-editor-state")]
    (swap! state merge (read-string saved-state))
    (some-> (:code @cm-instances) .getDoc (.setValue (:code @state))))
  (catch :default _e nil))

(defonce event-handlers
  (let [body (.-body js/document)]
    (.addEventListener body "mouseenter"
                       #(swap! state assoc :show-config true))
    (.addEventListener body "mouseleave"
                       #(when (not= (aget js/document "activeElement" "tagName")
                                    "SELECT")
                          (swap! state assoc :show-config false)))
    (.addEventListener body "click"
                       #(when (identical? (.-target %) body)
                          (swap! state update :show-config not)))))

(load-readme)
