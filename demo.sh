#!/usr/bin/env bash
#
# End-to-end Apache UIMA demo: Java pipeline  ->  Python client  ->  Java re-read.
# Everything is exchanged through the shared/ folder as a serialized UIMA CAS.
#
set -euo pipefail
ROOT="$(cd "$(dirname "$0")" && pwd)"
export PATH="$HOME/.local/bin:$PATH"
# Match slf4j-simple to slf4j-api 1.7 and keep UIMA's own logging quiet.
export MAVEN_OPTS="-Dorg.slf4j.simpleLogger.defaultLogLevel=warn --add-opens java.base/java.lang=ALL-UNNAMED"

hr() { printf '\n\033[1;36m========== %s ==========\033[0m\n' "$1"; }

hr "STEP 1/3  Java: run the Apache UIMA pipeline, serialize the CAS"
cd "$ROOT/java-uima"
mvn -q compile
mvn -q exec:java -Dexec.mainClass=com.example.uima.RunPipeline

hr "STEP 2/3  Python (dkpro-cassis): read the CAS, enrich it, write it back"
cd "$ROOT/python-client"
if [ ! -d .venv ]; then
  python3 -m venv .venv
  ./.venv/bin/python -m pip install -q -r requirements.txt
fi
./.venv/bin/python analyze.py

hr "STEP 3/3  Java: reload the CAS Python enriched"
cd "$ROOT/java-uima"
mvn -q exec:java -Dexec.mainClass=com.example.uima.ReadEnriched

hr "Done.  Interchange files:"
ls -1 "$ROOT/shared"
