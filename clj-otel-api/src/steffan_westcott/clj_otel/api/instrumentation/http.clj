(ns steffan-westcott.clj-otel.api.instrumentation.http
  "Support for instrumenting HTTP clients and servers.

   This namespace includes Ring middleware and Pedestal interceptors for
   working with HTTP servers. Support is provided for working either with
   or without the OpenTelemetry instrumentation agent, and for synchronous or
   asynchronous HTTP request handlers."
  (:require [steffan-westcott.clj-otel.context :as context])
  (:import (io.opentelemetry.instrumentation.api.semconv.http HttpServerRoute HttpServerRouteSource)
           (io.opentelemetry.semconv HttpAttributes)))

(defn add-route-data!
  "Adds data about the matched HTTP `route` to instrumentation data, for
   example `\"/users/:user-id\"`.  `route` is a  string that may contain path
   parameters in any format. See also [[wrap-route]] and [[route-interceptor]].

   May take an options map as follows:

   | key       | description |
   |-----------|-------------|
   |`:context` | Context containing server span (default: bound or current context).
   |`:app-root`| Web application root, a URL prefix for all HTTP routes served by this application e.g. `\"/webshop\"` (default: `nil`)."
  ([route]
   (add-route-data! route {}))
  ([route {:keys [context]
           :or   {context (context/dyn)}}]
   (when route
     (HttpServerRoute/update context HttpServerRouteSource/SERVER_FILTER route))))

(defn wrap-route
  "Ring middleware to add a matched route to the instrumentation data and Ring
   request map.  `route-fn` is a function which given a request returns the
   matched route as a string, or nil if no match."
  [handler route-fn]
  (fn
    ([request]
     (if-let [route (route-fn request)]
       (do
         (add-route-data! route)
         (handler (assoc-in request
                            [:io.opentelemetry/server-request-attrs HttpAttributes/HTTP_ROUTE]
                            route)))
       (handler request)))
    ([{:keys [io.opentelemetry/server-span-context]
       :as   request} respond raise]
     (if-let [route (route-fn request)]
       (do
         (add-route-data! route {:context server-span-context})
         (handler (assoc-in request
                            [:io.opentelemetry/server-request-attrs HttpAttributes/HTTP_ROUTE]
                            route)
                  respond
                  raise))
       (handler request respond raise)))))

(defn wrap-reitit-route
  "Ring middleware to add matched Reitit route to the instrumentation data
   and Ring request map.  This assumes `reitit.ring/ring-handler` is used with
   option `:inject-match?` set to true (which is the default)."
  [handler]
  (wrap-route handler
              (fn [request]
                (get-in request [:reitit.core/match :template]))))

(defn wrap-compojure-route
  "Ring middleware to add matched Compojure route to the instrumentation data
   and Ring request map.  Use `compojure.core/wrap-routes` to apply this
   middleware to all route handlers."
  [handler]
  (wrap-route handler
              (fn [{prefix   :compojure/route-context
                    [_ path] :compojure/route}]
                (str prefix path))))

(defn route-interceptor
  "Returns a Pedestal interceptor that adds a matched route to the
   instrumentation data and request map."
  []
  {:name  ::route
   :enter (fn [{:keys [io.opentelemetry/server-span-context route]
                :as   ctx}]
            (if-let [path (:path route)]
              (do
                (add-route-data! path {:context server-span-context})
                (assoc-in ctx
                          [:request :io.opentelemetry/server-request-attrs HttpAttributes/HTTP_ROUTE]
                          path))
              ctx))})
