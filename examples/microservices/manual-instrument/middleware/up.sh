#!/usr/bin/env bash
docker volume create --label example.clj-otel=m2 example.clj-otel.m2
docker volume create --label example.clj-otel=gitlibs example.clj-otel.gitlibs
docker compose up -d
