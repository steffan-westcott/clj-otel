#!/usr/bin/env bash
docker build -t example.clj-otel/library-base ..
docker build -t example.clj-otel/microservices-base .
