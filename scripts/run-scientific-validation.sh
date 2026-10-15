#!/usr/bin/env bash
set -euo pipefail

PROJECT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
DATA_DIR="$PROJECT_DIR/validation/data"
RESULTS_DIR="$PROJECT_DIR/validation/results"
CACHE_DIR="$PROJECT_DIR/validation/cache"

if [[ -n "${MAVEN_BIN:-}" ]]; then
  MVN="$MAVEN_BIN"
elif command -v mvn >/dev/null 2>&1; then
  MVN="$(command -v mvn)"
else
  echo "Maven 3.6.3 or later is required, or set MAVEN_BIN." >&2
  exit 1
fi

mkdir -p "$DATA_DIR" "$RESULTS_DIR" "$CACHE_DIR" "$PROJECT_DIR/tmp"
PREPARE_ARGS=(--cache "$CACHE_DIR" --output "$DATA_DIR" --manifest "$RESULTS_DIR/dataset-manifest.json")
if [[ -n "${PHP_DATASET_ZIP:-}" ]]; then PREPARE_ARGS+=(--php-archive "$PHP_DATASET_ZIP"); fi
if [[ -n "${JAVA_DATASET_ZIP:-}" ]]; then PREPARE_ARGS+=(--java-archive "$JAVA_DATASET_ZIP"); fi
python3 "$PROJECT_DIR/validation/prepare_datasets.py" "${PREPARE_ARGS[@]}"

"$MVN" -f "$PROJECT_DIR/pom.xml" -q verify
"$MVN" -f "$PROJECT_DIR/pom.xml" -q test-compile dependency:build-classpath \
  -Dmdep.includeScope=test -Dmdep.outputFile="$PROJECT_DIR/tmp/validation-classpath.txt"

CLASSPATH="$PROJECT_DIR/target/test-classes:$PROJECT_DIR/target/classes:$(cat "$PROJECT_DIR/tmp/validation-classpath.txt")"
INPUTS=("$DATA_DIR/php-code-smells.arff" "$DATA_DIR/java-code-test-smells.arff")
java -Dweka.packageManager.offline=true -cp "$CLASSPATH" \
  io.github.hchafai.weibull.ValidationRunner "$RESULTS_DIR/weka-results.csv" "${INPUTS[@]}"
python3 "$PROJECT_DIR/validation/reference_weibull.py" \
  "$RESULTS_DIR/python-reference.csv" "${INPUTS[@]}"
python3 "$PROJECT_DIR/validation/compare_results.py" \
  "$RESULTS_DIR/weka-results.csv" "$RESULTS_DIR/python-reference.csv" 1e-8
python3 "$PROJECT_DIR/validation/assess_fit.py" \
  "$RESULTS_DIR/weka-results.csv" "$RESULTS_DIR/fit-diagnostics.csv" "${INPUTS[@]}"

{
  echo 'component,version'
  echo 'WeibullSurvival,0.1.0'
  echo "Java,$(java -version 2>&1 | sed -n '1s/.*version "\([^"]*\)".*/\1/p')"
  echo "Python,$(python3 -c 'import platform; print(platform.python_version())')"
  echo 'Weka,3.8.7'
} >"$RESULTS_DIR/reference-versions.csv"

echo "Scientific validation completed: $RESULTS_DIR/weka-results.csv"
