#!/usr/bin/env bash
# Regenerates patches/quickfixj-1114.patch from the QuickFIX/J 2.3.2 sources jar and run-patched.
set -euo pipefail
cd "$(dirname "$0")/.."
WORK="$(mktemp -d "${TMPDIR:-/tmp}/qfj-patch.XXXXXX")"
trap 'rm -rf "$WORK"' EXIT
JAR="$HOME/.m2/repository/org/quickfixj/quickfixj-core/2.3.2/quickfixj-core-2.3.2-sources.jar"
[[ -f "$JAR" ]] || mvn -B -q dependency:get -Dartifact=org.quickfixj:quickfixj-core:2.3.2:jar:sources
mkdir -p "$WORK/a/quickfixj-core/src/main/java" "$WORK/b/quickfixj-core/src/main/java/quickfix" \
         "$WORK/b/quickfixj-core/src/test/java/quickfix"
(cd "$WORK/a/quickfixj-core/src/main/java" && jar xf "$JAR" quickfix/Session.java)
cp run-patched/src/main/java/quickfix/Session.java "$WORK/b/quickfixj-core/src/main/java/quickfix/"
cp run-patched/src/test/java/quickfix/SessionResendRequestNotSentTest.java "$WORK/b/quickfixj-core/src/test/java/quickfix/"
(cd "$WORK" && diff -ruN a b || true) | sed -E 's#^(---|\+\+\+) ([ab]/[^\t]*)\t.*#\1 \2#' > patches/quickfixj-1114.patch
echo "wrote patches/quickfixj-1114.patch"
