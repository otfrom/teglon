(ns ^{:doc "This namespace provides Teglon's RESTful API built on the Aleph web
 server for managing, browsing, and querying Maven repositories."
      :author "David Edgar Liebke"}
  teglon.web
  (:require [teglon.repo :as repo]
	    [teglon.web.json :as json]
	    [teglon.web.html :as html]
	    [teglon.web.upload :as upload]
	    [teglon.web.openid :as openid]
	    [compojure.route :as route]
	    [clojure.contrib.logging :as log])
  (:use compojure.core
	[ring.adapter.jetty :only (run-jetty)]
	[ring.middleware.file :only (wrap-file)]
	[ring.middleware.file-info :only (wrap-file-info)]
	[ring.middleware.multipart-params :only (wrap-multipart-params)]
	;; [ring.middleware.session :only (wrap-session)]
	;; [ring.middleware.session.cookie :only (cookie-store)]
	[ring.middleware.cookies :only (wrap-cookies)]
	[sandbar.stateful-session :only (wrap-stateful-session)]))

;; (defn openid-handler [request]
;;   (let [counter (or (-> request :session :counter) 1)]
;;     {:status 200
;;      :headers {"Content-Type" "text/html"}
;;      :body (prn-str request)
;;      :session {:counter (+ 1 counter)}}))

(defn teglon-app [repo-dir]
  (routes
   (GET (str json/*model-show-url* "/*") [& artifact-id]
	(json/model-show (artifact-id "*")))
   (GET (str json/*base-url* "/versions/show/*") [& group-name]
	(json/versions-show (group-name "*")))
   (GET (str json/*base-url* "/group/show/*") [& group]
	(json/group-show (group "*")))
   (GET (str json/*base-url* "/models/show") []
	(json/models-show))
   (GET (str json/*base-url* "/models/search") [q]
	(json/models-search q))
   (GET (str json/*base-url* "/children/model/show/*") [& artifact-id]
	(json/models-children (artifact-id "*")))
   (GET (str json/*base-url* "/children/versions/show/*") [& group-name]
	(json/versions-children (group-name "*")))
   (GET (str json/*base-url* "/children/group/show/*") [& group]
	(json/group-children (group "*")))
   (GET (str html/*base-url* "/model/show/*") [& artifact-id]
	(html/model-show (artifact-id "*")))
   (GET (str html/*base-url* "/models/search") [q]
	(html/models-search q))
   (GET (str html/*base-url* "/group/show/*") [& group]
	(html/group-show (group "*")))
   (GET (str html/*base-url* "/versions/show/*") [& group-name]
	(html/versions-show (group-name "*")))
   (GET (str html/*base-url* "/models/show") []
	(html/groups-show))
   (GET "/repo" request {:status 302 :headers {"Location" "/repo/"}})
   (GET "/repo/" []
	(html/repo-directory-listing))
   (GET "/repo/*/" [& relative-path]
	(html/repo-directory-listing (str (relative-path "*") "/")))
   (GET "/" request (html/index-page))
   (POST "/upload" request (upload/upload-file-to-repo request))
   (GET "/upload" [] (html/upload-page))
   (route/files "/repo" {:root repo-dir})
   (route/files "/static" {:root "public"})
   (GET "/openid" request (openid/openid-handler request))
   (GET "/echo" request {:status 200
			 :headers {"Content-Type" "text/html"}
			 :body (prn-str request)})
   (ANY "*" request {:status 404 :body (html/missing-file request)})))


(defn wrap-logging [app]
  (fn [request]
    (let [{:keys [remote-addr uri query-string]} request]
      (log/info (str remote-addr " | " uri " | " query-string))
      (app request))))

(def *server* (ref nil))

(defn start-server
  ([] (start-server (repo/default-repo-dir)))
  ([repo-dir & [port]]
     (let [port (or port 8080)
	   web-app (-> (teglon-app repo-dir)
		       wrap-multipart-params
		       wrap-logging
		       wrap-cookies
		       ;; (wrap-session {:store (cookie-store)})
		       wrap-stateful-session)]
       (println "Initializing Teglon server...")
       (repo/init-repo repo-dir)
       (println (str "Starting webserver on port " port "..."))
       (dosync (ref-set *server* (run-jetty web-app
					    {:port port
					     :join? false})))
       (println "Web server started."))))

(defn stop-server []
  (println "Stopping Teglon server...")
  (.stop @*server*)
  (println "Server stopped."))


