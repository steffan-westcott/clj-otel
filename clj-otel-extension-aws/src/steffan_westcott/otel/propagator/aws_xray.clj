(ns steffan-westcott.otel.propagator.aws-xray
  "Access to implementation of the AWS X-Ray Trace Header propagation protocol."
  (:import (io.opentelemetry.extension.aws AwsXrayPropagator)))

(defn aws-xray-propagator
  []
  (AwsXrayPropagator/getInstance))
