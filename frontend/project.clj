(defproject taxi "0.1.0-SNAPSHOT"
  :description "Taxi demo"
  :url "http://github.com/pushtechnology/"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}

  :dependencies [[org.clojure/clojure "1.7.0"]
                 [org.clojure/clojurescript "1.7.228"]
                 [figwheel "0.5.1"]
                 [org.clojure/core.async "0.2.374"]
                 [sablono "0.7.0"]
                 [secretary "1.2.3"]
                 [org.omcljs/om "1.0.0-alpha32"]
                 [racehub/om-bootstrap "0.6.1"]
                 [ring/ring-core "1.4.0"]
                 [compojure "1.5.0"]
                 [javax/javaee-api "7.0"]]

  :plugins [[lein-cljsbuild "1.1.3"]
            [lein-figwheel "0.5.1"]
            [lein-ring "0.9.7"]]

  :source-paths ["src/clj"]
  :clean-targets ^{:protect false} ["resources/public/js/compiled" "target"]
  :aot :all

  :cljsbuild {
    :builds [{:id "dev"
              :source-paths ["src/cljs" "dev_src/cljs"]
              :compiler {:output-to "resources/public/js/compiled/taxi.js"
                         :output-dir "resources/public/js/compiled/out"
                         :optimizations :none
                         :main taxi.dev
                         :asset-path "js/compiled/out"
                         :source-map true
                         :source-map-timestamp true
                         :cache-analysis true
                         :foreign-libs [{:file "lib/diffusion.js"
                                         :provides ["diffusion"]}]
                         }}
             {:id "min"
              :source-paths ["src/cljs"]
              :compiler {:output-to "resources/public/js/compiled/taxi.js"
                         :output-dir "resources/public/js/compiled/out2"
                         :main taxi.core
                         :optimizations :simple
                         :pretty-print false
                         :foreign-libs [{:file "lib/diffusion.js"
                                         :provides ["diffusion"]}]
                         }}]}

  :figwheel {:http-server-root "public" ;; default and assumes "resources"
             :server-port 3449 ;; default
             :css-dirs ["resources/public/css"] ;; watch and update CSS
             :ring-handler server.core/handler
             }

  :ring {:handler server.core/handler
         :war-exclusions [#"^\..+$", #"public/js/compiled/out.*$"]}

  :aliases {"release-build" ["do" "clean" ["cljsbuild" "once" "min"] ["ring" "uberwar"]]})