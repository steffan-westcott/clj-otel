#!/usr/bin/env bash
(cd ../../..         ; ./build-microservices-base.sh)
(cd average-load-gen ; docker build -t example.clj-otel/average-load-gen .)
(cd average-service  ; docker build -t example.clj-otel/average-service .)
(cd sum-service      ; docker build -t example.clj-otel/sum-service .)
