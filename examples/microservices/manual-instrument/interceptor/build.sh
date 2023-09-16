#!/usr/bin/env bash
cd ../../../..
clojure -T:build examples :projects '["average-load-gen","average-service","sum-service"]'
