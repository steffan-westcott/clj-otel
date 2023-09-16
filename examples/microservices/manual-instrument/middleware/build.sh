#!/usr/bin/env bash
cd ../../../..
clojure -T:build examples :projects '["puzzle-load-gen","puzzle-service","random-word-service"]'
