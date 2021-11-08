(ns steffan-westcott.otel.propagator.aws-xray
  "Access to implementation of the AWS X-Ray Trace Header propagation protocol."
  (:import (io.opentelemetry.extension.aws AwsXrayPropagator)))

(defn aws-xray-propagator
  "Returns an implementation of the AWS X-Ray Trace header propagation
  protocol."
  []
  (AwsXrayPropagator/getInstance))
