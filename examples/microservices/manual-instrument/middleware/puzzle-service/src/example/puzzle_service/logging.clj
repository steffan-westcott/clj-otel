(ns example.puzzle-service.logging
  "Management of application logging"
  (:import (io.opentelemetry.api OpenTelemetry)))


(defn install!
  "Installs application logging with the given OpenTelemetry instance."
  [^OpenTelemetry otel]
  (when (resolve `io.opentelemetry.instrumentation.log4j.appender.v2_17.OpenTelemetryAppender)
    (let [install
          (eval
           `#(io.opentelemetry.instrumentation.log4j.appender.v2_17.OpenTelemetryAppender/install
              %))]
      (install otel))))
