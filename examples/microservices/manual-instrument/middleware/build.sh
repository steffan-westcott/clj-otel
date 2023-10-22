#!/usr/bin/env bash
(cd ../../..            ; ./build-microservices-base.sh)
(cd puzzle-load-gen     ; docker build -t example.clj-otel/puzzle-load-gen .)
(cd puzzle-service      ; docker build -t example.clj-otel/puzzle-service .)
(cd random-word-service ; docker build -t example.clj-otel/random-word-service .)
