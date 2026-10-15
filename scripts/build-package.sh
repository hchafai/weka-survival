#!/usr/bin/env bash
set -euo pipefail

PROJECT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
VERSION="0.1.0"
OUTPUT_DIR="$PROJECT_DIR/output"
JAR="$PROJECT_DIR/target/WeibullSurvival.jar"

if [[ -n "${MAVEN_BIN:-}" ]]; then
  MVN="$MAVEN_BIN"
elif command -v mvn >/dev/null 2>&1; then
  MVN="$(command -v mvn)"
else
  echo "Maven 3.6.3 or later is required, or set MAVEN_BIN." >&2
  exit 1
fi

"$MVN" -f "$PROJECT_DIR/pom.xml" -q verify

STAGE_DIR="$(mktemp -d "${TMPDIR:-/tmp}/weka-survival-package.XXXXXX")"
trap 'rm -rf "$STAGE_DIR"' EXIT

cp "$JAR" "$STAGE_DIR/WeibullSurvival.jar"
for item in Description.props LICENSE README.md CITATION.cff CHANGELOG.md pom.xml; do
  cp "$PROJECT_DIR/$item" "$STAGE_DIR/$item"
done
cp -R "$PROJECT_DIR/src" "$STAGE_DIR/src"
cp -R "$PROJECT_DIR/examples" "$STAGE_DIR/examples"
cp -R "$PROJECT_DIR/validation" "$STAGE_DIR/validation"
rm -rf "$STAGE_DIR/validation/cache"
rm -f "$STAGE_DIR/validation/data/php-code-smells.arff" \
      "$STAGE_DIR/validation/data/java-code-test-smells.arff"
cp -R "$PROJECT_DIR/target/reports/apidocs" "$STAGE_DIR/doc"

mkdir -p "$OUTPUT_DIR"
STAGED_ARCHIVE="$STAGE_DIR/WeibullSurvival-$VERSION.zip"
(cd "$STAGE_DIR" && /usr/bin/zip -q -r "$STAGED_ARCHIVE" \
  WeibullSurvival.jar Description.props LICENSE README.md CITATION.cff CHANGELOG.md \
  pom.xml src examples validation doc)
cp "$STAGED_ARCHIVE" "$OUTPUT_DIR/WeibullSurvival-$VERSION.zip"

if /usr/bin/zipinfo -1 "$OUTPUT_DIR/WeibullSurvival-$VERSION.zip" \
  | grep -Eq '(^|/)(\.DS_Store|__MACOSX|\._)'; then
  echo "macOS metadata was found in the package archive" >&2
  exit 1
fi
(cd "$OUTPUT_DIR" && shasum -a 256 "WeibullSurvival-$VERSION.zip" > SHA256SUMS.txt)
cat "$OUTPUT_DIR/SHA256SUMS.txt"
