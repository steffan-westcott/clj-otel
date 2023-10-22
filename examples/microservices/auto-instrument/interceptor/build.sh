#!/usr/bin/env bash
(cd ../../..              ; ./build-microservices-base.sh)
(cd solar-system-load-gen ; docker build -t example.clj-otel/solar-system-load-gen .)
(cd solar-system-service  ; docker build -t example.clj-otel/solar-system-service .)
(cd planet-service        ; docker build -t example.clj-otel/planet-service .)
