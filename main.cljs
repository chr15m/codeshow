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

(def modes
  ["apl" "asciiarmor" "asn.1" "asterisk" "brainfuck" "ceylon" "clike" "clojure"
   "cmake" "cobol" "coffeescript" "commonlisp" "crystal" "css" "cypher" "d"
   "dart" "diff" "django" "dockerfile" "dtd" "dylan" "ebnf" "ecl" "eiffel"
   "elixir" "elm" "erlang" "factor" "fcl" "forth" "fortran" "gas" "gherkin" "go"
   "groovy" "haml" "handlebars" "haskell" "haskell-literate" "haxe"
   "htmlembedded" "htmlmixed" "http" "idl" "javascript" "jinja2" "jsx" "julia"
   "livescript" "lua" "markdown" "mathematica" "mbox" "mirc" "mllike" "modelica"
   "mscgen" "mumps" "nginx" "nsis" "ntriples" "octave" "oz" "pascal" "pegjs"
   "perl" "php" "pig" "powershell" "properties" "protobuf" "pug" "puppet"
   "python" "q" "r" "rpm" "rst" "ruby" "rust" "sas" "sass" "scheme" "shell"
   "sieve" "slim" "smalltalk" "smarty" "solr" "soy" "sparql" "spreadsheet" "sql"
   "stex" "stylus" "swift" "tcl" "textile" "tiddlywiki" "tiki" "toml" "tornado"
   "troff" "ttcn" "ttcn-cfg" "turtle" "twig" "vb" "vbscript" "velocity"
   "verilog" "vhdl" "vue" "wast" "webidl" "xml" "xquery" "yacas" "yaml"
   "yaml-frontmatter" "z80"])

(def initial-ui
  {:dots true
   :filename "example.cljs"
   :mode "clojure"
   :theme "seti"})

(defonce state
  (r/atom
    {:code "(ns example)\n\n(print (+ 2 3))"
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
    (.refresh cm)))

(defn filter-readme-content [content]
  (when content
    (->> (str/split-lines content)
         (remove #(or (re-find #"^# " %)             ;; Remove h1 headers
                      (re-find #"!\[.*\]\(.*\)" %)   ;; Remove images
                      (re-find #"\[.*\]\(.*\)" %)))  ;; Remove links
         (str/join "\n"))))

(defn load-readme []
  (p/let [response (js/fetch "README.md")
          text (when (.-ok response) (.text response))]
    (when text
      (swap! state assoc :readme-content (filter-readme-content text)))))

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
          [:pre content]
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
                                      :lineNumbers false
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
   [codemirror-editor state]
   [help-modal state]])

;*** launch ***;

(rdom/render [app state] (.getElementById js/document "app"))

(defn update-dynamic-settings! [*state]
  (let [{:keys [mode theme]} (:ui *state)
        style-el (js/document.getElementById "theme")
        lang-el (js/document.getElementById "mode")]
    (aset style-el "href" (str cdn-root "/theme/" theme ".min.css"))
    (let [new-lang-el (.createElement js/document "script")
          parent-node (.-parentNode lang-el)
          new-src (str cdn-root "/mode/" mode "/" mode ".min.js")]
      (aset new-lang-el "id" "mode")
      (aset new-lang-el "src" new-src)
      (aset new-lang-el "onload"
            #(when-let [cm (:code @cm-instances)]
               (update-cm-options cm (:ui *state))))
      (.replaceChild parent-node new-lang-el lang-el))))

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
