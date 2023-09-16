#!/usr/bin/env bash
cd ../../../..
clojure -T:build examples :projects '["sentence-summary-load-gen","sentence-summary-service","word-length-service"]'
