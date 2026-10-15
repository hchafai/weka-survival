#!/usr/bin/env bash
set -euo pipefail

PROJECT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ARCHIVE="$PROJECT_DIR/output/WeibullSurvival-0.1.0.zip"
DATA="$PROJECT_DIR/examples/weibull-demo.arff"

if [[ -n "${MAVEN_BIN:-}" ]]; then
  MVN="$MAVEN_BIN"
elif command -v mvn >/dev/null 2>&1; then
  MVN="$(command -v mvn)"
else
  echo "Maven 3.6.3 or later is required, or set MAVEN_BIN." >&2
  exit 1
fi

"$PROJECT_DIR/scripts/build-package.sh" >/dev/null
mkdir -p "$PROJECT_DIR/tmp"
"$MVN" -f "$PROJECT_DIR/pom.xml" -q dependency:build-classpath \
  -Dmdep.includeScope=test -Dmdep.outputFile="$PROJECT_DIR/tmp/classpath.txt"
WEKA_CLASSPATH="$(awk -v RS=: '$0 ~ /\.jar$/ {if (result) result=result ":" $0; else result=$0} END {print result}' \
  "$PROJECT_DIR/tmp/classpath.txt")"

ISOLATED_WEKA_HOME="$(mktemp -d "${TMPDIR:-/tmp}/weka-survival-home.XXXXXX")"
REPORT="$(mktemp "${TMPDIR:-/tmp}/weka-survival-report.XXXXXX.txt")"
trap 'rm -rf "$ISOLATED_WEKA_HOME"; rm -f "$REPORT"' EXIT
export WEKA_HOME="$ISOLATED_WEKA_HOME"

java -Dweka.packageManager.offline=true -cp "$WEKA_CLASSPATH" \
  weka.core.WekaPackageManager -install-package "$ARCHIVE" >/dev/null

java -Dweka.packageManager.offline=true -cp "$WEKA_CLASSPATH" \
  weka.Run weka.classifiers.functions.WeibullSurvival \
  -t "$DATA" -c last -event event -event-value yes -no-cv >"$REPORT"
grep -q 'Weibull survival model' "$REPORT"
grep -q 'Shape beta:' "$REPORT"
grep -q 'Converged: true' "$REPORT"

echo "WeibullSurvival package installation and execution checks passed."
