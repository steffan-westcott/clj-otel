(ns steffan-westcott.clj-otel.api.trace.http
  "Support for creating and populating HTTP client and server spans.

   This namespace includes Ring middleware and Pedestal interceptors for
   working with HTTP server spans. Support is provided for working either with
   or without the OpenTelemetry instrumentation agent, and for synchronous or
   asynchronous HTTP request handlers."
  (:require [clojure.string :as str]
            [steffan-westcott.clj-otel.api.trace.span :as span]
            [steffan-westcott.clj-otel.context :as context])
  (:import (io.opentelemetry.semconv.trace.attributes SemanticAttributes)))

(defn- parse-long*
  [s]
  (try
    (Long/parseLong s)
    (catch Throwable _)))

(defn- merge-headers-attrs
  [init prefix captured-headers headers]
  (persistent! (reduce (fn [m header-name]
                         (if-let [v (get headers header-name)]
                           (assoc! m (str prefix (str/replace header-name \- \_)) [v])
                           m))
                       (transient init)
                       captured-headers)))

(defn- client-ip
  [forwarded x-forwarded-for remote-addr]
  (or (some->> forwarded
               (re-find #"(?i)for=([^,;]*)")
               second
               str/trim
               not-empty)
      (some->> x-forwarded-for
               (re-find #"[^,]*")
               str/trim
               not-empty)
      remote-addr))

(defn- server-request-attrs
  [request captured-request-headers]
  (let [{:keys [headers request-method scheme uri query-string protocol remote-addr]} request
        {:strs [user-agent content-length host forwarded x-forwarded-for]} headers
        [_ host-name host-port] (re-find #"^(.*?)(?::(\d*))?$" host)]
    (cond-> {SemanticAttributes/HTTP_METHOD    (str/upper-case (name request-method))
             SemanticAttributes/HTTP_SCHEME    (name scheme)
             SemanticAttributes/NET_HOST_NAME  host-name
             SemanticAttributes/HTTP_TARGET    (if query-string
                                                 (str uri "?" query-string)
                                                 uri)
             SemanticAttributes/HTTP_FLAVOR    (str/replace (str/upper-case protocol) #"^HTTP/" "")
             SemanticAttributes/HTTP_CLIENT_IP (client-ip forwarded x-forwarded-for remote-addr)}
      user-agent      (assoc SemanticAttributes/HTTP_USER_AGENT user-agent)
      content-length  (assoc SemanticAttributes/HTTP_REQUEST_CONTENT_LENGTH
                             (parse-long* content-length))
      (seq host-port) (assoc SemanticAttributes/NET_HOST_PORT (parse-long* host-port))
      captured-request-headers
      (merge-headers-attrs "http.request.header." captured-request-headers headers))))

(defn server-span-opts
  "Returns a span options map (a parameter for
   [[steffan-westcott.clj-otel.api.trace.span/new-span!]]) for a manually
   created HTTP server span, initiated by processing an HTTP request specified
   by Ring-style request map `request`. May take an options map as follows:

   | key                       | description |
   |---------------------------|-------------|
   |`:app-root`                | Web application root, a URL prefix for all HTTP routes served by this application e.g. `\"/webshop\"` (default: no app root)."
  ([request]
   (server-span-opts request {}))
  ([request {:keys [app-root]}]
   (let [{:keys [headers io.opentelemetry/server-request-attrs]} request]
     {:name       (let [method (get server-request-attrs SemanticAttributes/HTTP_METHOD)]
                    (if app-root
                      (str method " " app-root "/*")
                      method))
      :span-kind  :server
      ;; always merge extracted context with current context
      :parent     (context/headers->merged-context headers)
      :attributes server-request-attrs})))

(defn client-span-opts
  "Returns a span options map (a parameter for
   [[steffan-westcott.clj-otel.api.trace.span/new-span!]]) for a manually
   created HTTP client span, where an HTTP request specified by Ring-style
   request map `request` is issued in the span scope.  Only `:method` is used in
   `request` to populate the span. May take an options map as follows:

   | key      | description |
   |----------|-------------|
   |`:parent` | Context used to take parent span. If `nil` or no span is available in the context, the root context is used instead (default: use current context)."
  ([request]
   (client-span-opts request {}))
  ([request
    {:keys [parent]
     :or   {parent (context/current)}}]
   (let [{:keys [method]} request
         method' (str/upper-case (name method))]
     {:name       method'
      :span-kind  :client
      :parent     parent
      :attributes {SemanticAttributes/HTTP_METHOD method'}})))

(defn add-route-data!
  "Adds data about the matched HTTP `request-method` and `route` to a server
   span, for example `\"GET /users/:user-id\"`. `request-method` is a keyword
   corresponding to an HTTP request method; `route` is a string that may contain
   path parameters in any format. See also [[wrap-route]] and
   [[route-interceptor]].

   May take an options map as follows:

   | key       | description |
   |-----------|-------------|
   |`:context` | Context containing server span (default: current context).
   |`:app-root`| Web application root, a URL prefix for all HTTP routes served by this application e.g. `\"/webshop\"` (default: `nil`)."
  ([request-method route]
   (add-route-data! request-method route {}))
  ([request-method route
    {:keys [context app-root]
     :or   {context (context/current)}}]
   (when route
     (span/add-span-data!
      {:context    context
       :name       (str (str/upper-case (name request-method)) " " app-root route)
       :attributes {SemanticAttributes/HTTP_ROUTE route}}))))

(defn add-client-span-response-data!
  "Adds data about the HTTP `response` to a manually created client span. May
   take an options map as follows:

   | key       | description |
   |-----------|-------------|
   |`:context` | Context containing span to add response data to (default: current context)."
  ([response]
   (add-client-span-response-data! response {}))
  ([response
    {:keys [context]
     :or   {context (context/current)}}]
   (let [{:keys [status headers]} response
         {:strs [Content-Length]} headers
         Content-Length' (when Content-Length
                           (parse-long* Content-Length))
         attrs (cond-> {SemanticAttributes/HTTP_STATUS_CODE status}
                 Content-Length' (assoc SemanticAttributes/HTTP_RESPONSE_CONTENT_LENGTH
                                        Content-Length'))
         stat (when (<= 400 status)
                {:code :error})]
     (span/add-span-data! (cond-> {:context    context
                                   :attributes attrs}
                            stat (assoc :status stat))))))

(defn add-server-span-response-data!
  "Adds data about the HTTP `response` to a manually created server span. May
   take an options map as follows:

   | key       | description |
   |-----------|-------------|
   |`:context` | Context containing span to add response data to (default: current context)."
  ([response]
   (add-server-span-response-data! response {}))
  ([response
    {:keys [context]
     :or   {context (context/current)}}]
   (let [{:keys [status io.opentelemetry.api.trace.span.status/description]} response
         attrs {SemanticAttributes/HTTP_STATUS_CODE status}
         stat  (when (<= 500 status)
                 (cond-> {:code :error}
                   description (assoc :description description)))]
     (span/add-span-data! (cond-> {:context    context
                                   :attributes attrs}
                            stat (assoc :status stat))))))

;;; Ring middleware

(defn- wrap-new-server-span
  [handler create-span-opts]
  (fn
    ([request]
     (span/with-span! (server-span-opts request create-span-opts)
       (try
         (let [response (handler request)]
           (add-server-span-response-data! response)
           response)
         (catch Throwable e
           (add-server-span-response-data! {:status  500
                                            :headers {}})
           (throw e)))))
    ([request respond raise]
     (span/async-span (server-span-opts request create-span-opts)
                      (fn [context respond* raise*]
                        (handler (assoc request :io.opentelemetry/server-span-context context)
                                 (fn [response]
                                   (add-server-span-response-data! response {:context context})
                                   (respond* response))
                                 (fn [e]
                                   (add-server-span-response-data! {:status  500
                                                                    :headers {}}
                                                                   {:context context})
                                   (raise* e))))
                      respond
                      raise))))

(defn- wrap-server-request-attrs
  [handler {:keys [captured-request-headers]}]
  (fn
    ([request]
     (handler (assoc request
                     :io.opentelemetry/server-request-attrs
                     (server-request-attrs request captured-request-headers))))
    ([request respond raise]
     (handler (assoc request
                     :io.opentelemetry/server-request-attrs
                     (server-request-attrs request captured-request-headers))
              respond
              raise))))

(defn- wrap-existing-server-span
  [handler]
  (fn
    ([request]
     (handler request))
    ([request respond raise]
     (let [context (context/current)]
       (handler (assoc request :io.opentelemetry/server-span-context context)
                respond
                (fn [e]
                  (span/add-exception! e {:context context})
                  (raise e)))))))

(defn wrap-server-span
  "Ring middleware to add HTTP server span support. This middleware can be
   configured to either use existing server spans created by the OpenTelemetry
   instrumentation agent or manually create new server spans (when not using the
   agent). Both synchronous (1-arity) and asynchronous (3-arity) Ring handlers
   are supported.

   When `:create-span?` is false, for each request it is assumed the current
   context contains a server span created by the OpenTelemetry instrumentation
   agent.

   When `:create-span?` is true, for each request a new context containing a new
   server span is created, with parent context extracted from the HTTP request
   headers. For a synchronous handler, the current context is set to the new
   context during request processing and restored to its original value on
   completion. Finally, if the HTTP response status code is `5xx` then the span
   status error description is set to the value of
   `:io.opentelemetry.api.trace.span.status/description` in the response map.

   No matter how the server span is created, for an asynchronous handler the
   context containing the server span is set as the value of
   `:io.opentelemetry/server-span-context` in the request map.

   May take an option map as follows:

   | key                       | description |
   |---------------------------|-------------|
   |`:create-span?`            | When true, manually creates a new server span. Otherwise, assumes current context contains an existing server span created by OpenTelemetry instrumentation agent (default: false).
   |`:app-root`                | Web application root, a URL prefix for all HTTP routes served by this application e.g. `\"/webshop\"` (default: no app root).
   |`:captured-request-headers`| Collection of down-cased names of request headers that are captured as attributes of manually created server spans (default: no headers captured)."
  ([handler]
   (wrap-server-span handler {}))
  ([handler
    {:keys [create-span?]
     :as   create-span-opts}]
   (cond-> handler
     create-span?       (wrap-new-server-span create-span-opts)
     create-span?       (wrap-server-request-attrs create-span-opts)
     (not create-span?) (wrap-existing-server-span))))

(defn wrap-route
  "Ring middleware to add a matched route to the server span data and Ring
   request map. `route-fn` is a function which given a request returns the
   matched route as a string."
  [handler route-fn]
  (fn
    ([{:keys [request-method]
       :as   request}]
     (let [route (route-fn request)]
       (add-route-data! request-method route)
       (handler (assoc-in request
                 [:io.opentelemetry/server-request-attrs SemanticAttributes/HTTP_ROUTE]
                 route))))
    ([{:keys [request-method io.opentelemetry/server-span-context]
       :as   request} respond raise]
     (let [route (route-fn request)]
       (add-route-data! request-method route {:context server-span-context})
       (handler (assoc-in request
                 [:io.opentelemetry/server-request-attrs SemanticAttributes/HTTP_ROUTE]
                 route)
                respond
                raise)))))

;;; Pedestal interceptors

(defn- server-request-attrs-interceptor
  [{:keys [captured-request-headers]}]
  {:name  ::server-request-attrs
   :enter (fn [ctx]
            (let [attrs (server-request-attrs (:request ctx) captured-request-headers)]
              (update ctx :request assoc :io.opentelemetry/server-request-attrs attrs)))})

(defn- new-server-span-interceptor
  [create-span-opts]
  (span/span-interceptor :io.opentelemetry/server-span-context
                         (fn [ctx]
                           (server-span-opts (:request ctx) create-span-opts))))

(defn- response-data-interceptor
  []
  {:name  ::response-data
   :leave (fn [{:keys [io.opentelemetry/server-span-context response]
                :as   ctx}]
            (add-server-span-response-data! response {:context server-span-context})
            ctx)
   :error (fn [{:keys [io.opentelemetry/server-span-context]
                :as   ctx} e]
            (add-server-span-response-data! {:status  500
                                             :headers {}}
                                            {:context server-span-context})
            (assoc ctx :io.pedestal.interceptor.chain/error e))})

(defn- existing-server-span-interceptor
  []
  {:name  ::existing-server-span
   :enter (fn [ctx]
            (assoc ctx :io.opentelemetry/server-span-context (context/current)))
   :error (fn [{:keys [io.opentelemetry/server-span-context]
                :as   ctx} e]
            (span/add-interceptor-exception! e {:context server-span-context})
            (assoc ctx :io.pedestal.interceptor.chain/error e))})

(defn- execution-id-interceptor
  []
  {:name  ::execution-id
   :enter (fn [{:keys [io.opentelemetry/server-span-context
                       io.pedestal.interceptor.chain/execution-id]
                :as   ctx}]
            (span/add-span-data! {:context    server-span-context
                                  :attributes {:io.pedestal.interceptor.chain/execution-id
                                               execution-id}})
            ctx)})

(defn- copy-context-interceptor
  []
  {:name  ::copy-context
   :enter (fn [{:keys [io.opentelemetry/server-span-context]
                :as   ctx}]
            (update ctx :request assoc :io.opentelemetry/server-span-context server-span-context))})

(defn- current-context-interceptor
  []
  (context/current-context-interceptor :io.opentelemetry/server-span-context
                                       :io.opentelemetry/server-span-scope))

(defn server-span-interceptors
  "Returns a vector of Pedestal interceptor maps that add HTTP server span
   support to subsequent execution of the interceptor chain for an HTTP service.
   These interceptors can be configured to either use existing server spans
   created by the OpenTelemetry instrumentation agent or manually create new
   server spans (when not using the agent).

   The interceptors should prepend any others in the service map to ensure all
   HTTP requests are traced, including those without a matching route.

   When `:create-span?` is false, for each request it is assumed the current
   context contains a server span created by the OpenTelemetry instrumentation
   agent. `:set-current-context?` is ignored in this case.

   When `:create-span?` is true, for each request a new context containing a new
   server span is created, with parent context extracted from the HTTP request
   headers. In addition, if `:set-current-context?` is true the current context
   is set to the new context on interceptor entry and its original value is
   restored on exit; this is only appropriate if all requests are to be
   processed synchronously. Finally, if the HTTP response status code is `5xx`
   then the span status error description is set to the value of
   `:io.opentelemetry.api.trace.span.status/description` in the response map.

   No matter how the server span is created, the context containing the server
   span is set as the value of `:io.opentelemetry/server-span-context` in both
   the interceptor context and request maps.

   May take an option map as follows:

   | key                       | description |
   |---------------------------|-------------|
   |`:create-span?`            | When true, manually creates a new server span. Otherwise, assumes current context contains a server span created by OpenTelemetry instrumentation agent (default: false).
   |`:set-current-context?`    | When true and `:create-span?` is also true, sets the current context to the context containing the created server span. Should only be set to `true` if all requests handled by this interceptor will be processed synchronously (default: true).
   |`:app-root`                | Web application root, a URL prefix for all HTTP routes served by this application e.g. `\"/webshop\"` (default: no app root).
   |`:captured-request-headers`| Collection of down-cased names of request headers that are captured as attributes of manually created server spans (default: no headers captured)."
  ([]
   (server-span-interceptors {}))
  ([{:keys [create-span? set-current-context?]
     :or   {set-current-context? true}
     :as   create-span-opts}]
   (cond-> []
     create-span?       (conj (server-request-attrs-interceptor create-span-opts))
     create-span?       (conj (new-server-span-interceptor create-span-opts))
     create-span?       (conj (response-data-interceptor))
     (not create-span?) (conj (existing-server-span-interceptor))
     :always            (conj (execution-id-interceptor))
     :always            (conj (copy-context-interceptor))
     (and create-span? set-current-context?) (conj (current-context-interceptor)))))

(defn route-interceptor
  "Returns a Pedestal interceptor that adds a matched route to the server span
   data and request map."
  []
  {:name  ::route
   :enter (fn [{:keys [io.opentelemetry/server-span-context route request]
                :as   ctx}]
            (let [path (:path route)]
              (add-route-data! (:request-method request) path {:context server-span-context})
              (assoc-in ctx
               [:request :io.opentelemetry/server-request-attrs SemanticAttributes/HTTP_ROUTE]
               path)))})

