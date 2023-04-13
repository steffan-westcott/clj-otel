(ns steffan-westcott.clj-otel.propagator.aws-xray
  "Access to implementation of the [AWS X-Ray Trace Header propagation
   protocol](https://docs.aws.amazon.com/xray/latest/devguide/xray-concepts.html#xray-concepts-tracingheader)."
  (:import (io.opentelemetry.contrib.awsxray.propagator AwsXrayPropagator)))

(defn aws-xray-propagator
  "Returns an implementation of the [AWS X-Ray Trace header propagation
   protocol](https://docs.aws.amazon.com/xray/latest/devguide/xray-concepts.html#xray-concepts-tracingheader)."
  []
  (AwsXrayPropagator/getInstance))
