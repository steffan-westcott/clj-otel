(ns example.common.async-style.utils
  (:require [com.xadecimal.async-style :as style]
            [ring.util.response :as response]
            [steffan-westcott.clj-otel.api.trace.span :as span]
            [steffan-westcott.clj-otel.context :as context]
            [steffan-westcott.clj-otel.util :as util]))


#_{:clj-kondo/ignore [:missing-docstring]}
(defmacro ^:no-doc style-span-binding'
  [[context span-opts] & body]
  `(style/async
     (let [~context (span/new-span!' ~span-opts)]
       (style/finally
         (do
           ~@body)
         (fn [res#]
           (when (style/error? res#)
             (span/add-exception! res# {:context ~context}))
           (span/end-span! {:context ~context}))))))


(defmacro style-span-binding
  "Asynchronously starts a new span, binds `context` to the new context
   containing the span and evaluates `body` which is expected give a channel
   value. The span is ended when the channel is settled (a result or exception
   is put on the channel)."
  [[context span-opts] & body]
  `(let [span-opts# (span/span-opts* ~span-opts ~(:line (meta &form)) ~*file* (util/fn-name))]
     (style-span-binding' [~context span-opts#]
       ~@body)))



(defmacro async-bound-style-span
  "Asynchronously starts a new span, sets the bound context to the new context
   containing the span and evaluates `body` which is expected give a channel
   value. The bound context is restored to its original value and the span is
   ended when the channel is settled (a result or exception is put on the
   channel)."
  [span-opts & body]
  `(let [span-opts# (span/span-opts* ~span-opts ~(:line (meta &form)) ~*file* (util/fn-name))]
     (style-span-binding' [context# span-opts#]
       (context/bind-context! context#
         ~@body))))



(defn ch->respond-raise
  "Asynchronously takes a single value from channel `ch` and invokes callback
   function `respond` or `raise`."
  [<ch respond raise]
  (style/async
    (let [x (style/await* <ch)]
      (if (style/ok? x)
        (respond x)
        (raise x)))))



(defn exception-response
  "Converts exception to a response, with status set to `:http.response/status`
   value if exception is an `IExceptionInfo` instance, 500 Server Error
   otherwise."
  [e]
  (let [resp   (response/response (ex-message e))
        status (:http.response/status (ex-data e) 500)]
    (response/status resp status)))



(defn <assoc-response
  "Takes a channel containing an HTTP response and returns a channel of ctx
   with the response attached. If the channel contains an exception, it is
   first transformed to a response."
  [<ch ctx]
  (-> <ch
      (style/catch exception-response)
      (style/then #(assoc ctx :response %))))
