#!/usr/bin/env bash
(cd ../../..                  ; ./build-microservices-base.sh)
(cd sentence-summary-load-gen ; docker build -t example.clj-otel/sentence-summary-load-gen .)
(cd sentence-summary-service  ; docker build -t example.clj-otel/sentence-summary-service .)
(cd word-length-service       ; docker build -t example.clj-otel/word-length-service .)
