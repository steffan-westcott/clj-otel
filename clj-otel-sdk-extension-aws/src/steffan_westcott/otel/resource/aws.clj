(ns steffan-westcott.otel.resource.aws
  "Provide `Resource` objects describing the current execution environment in
  the Amazon Web Services (AWS) platform."
  (:import (io.opentelemetry.sdk.extension.aws.resource BeanstalkResource Ec2Resource EcsResource EksResource LambdaResource)))

(defn beanstalk-resource
  "Returns a `Resource` which provides information about the current EC2
  instance if running on AWS Elastic Beanstalk."
  []
  (BeanstalkResource/get))

(defn ec2-resource
  "Returns a `Resource` which provides information about the current EC2
  instance if running on AWS EC2."
  []
  (Ec2Resource/get))

(defn ecs-resource
  "Returns a `Resource` which provides information about the current ECS
  container if running on AWS ECS."
  []
  (EcsResource/get))

(defn eks-resource
  "Returns a `Resource` which provides information about the current ECS
  container if running on AWS EKS."
  []
  (EksResource/get))

(defn lambda-resource
  "Returns a `Resource` which provides information about the AWS Lambda
  function."
  []
  (LambdaResource/get))