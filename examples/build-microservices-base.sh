#!/usr/bin/env bash
docker build -t example.clj-otel/library-base-deps -f ../deps.Dockerfile ..
docker build -t example.clj-otel/library-base -f ../build.Dockerfile ..
docker build -t example.clj-otel/microservices-base .
