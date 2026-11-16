#!/usr/bin/env bash
# Builds target/RelicPowers-1.0.0.jar  (needs Java 21 + Maven 3.8+, and internet for the first build)
set -e
cd "$(dirname "$0")"
mvn -q -B clean package
echo
echo "Done -> $(ls target/RelicPowers-*.jar)"
