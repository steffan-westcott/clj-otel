#!/usr/bin/env bash
cd ../../../..
clojure -T:build examples :projects '["solar-system-load-gen","solar-system-service","planet-service"]'
