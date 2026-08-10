#!/usr/bin/env bash
# Exhaustive disconnect-point exploration against stock, workaround and patched QuickFIX/J 2.3.2.
# Usage: scripts/explore.sh [--max-app-messages N] [--disconnects 1|2] [--stores MEMORY,FILE] [--check]
set -euo pipefail
cd "$(dirname "$0")/.."

MAX_APP=4
DISCONNECTS=2
STORES=MEMORY,FILE
CHECK=""
while [[ $# -gt 0 ]]; do
  case "$1" in
    --max-app-messages) MAX_APP="$2"; shift 2 ;;
    --disconnects) DISCONNECTS="$2"; shift 2 ;;
    --stores) STORES="$2"; shift 2 ;;
    --check) CHECK="--check"; shift ;;
    *) echo "unknown option: $1" >&2; exit 2 ;;
  esac
done

WORK="$(mktemp -d "${TMPDIR:-/tmp}/fix-explore.XXXXXX")"
trap 'rm -rf "$WORK"' EXIT

if [[ ! -f harness/target/classes/fixresend/ExplorerCli.class || ! -f run-patched/target/classes/quickfix/Session.class ]]; then
  mvn -B -q install -DskipTests
fi
mvn -B -q -pl harness dependency:build-classpath -Dmdep.outputFile="$WORK/cp.txt" >/dev/null
CP="harness/target/classes:$(cat "$WORK/cp.txt")"
PATCHED_CP="run-patched/target/classes:$CP"
JAVA_OPTS=(-Xmx512m "-Djava.io.tmpdir=$WORK")
ARGS=(--max-app-messages "$MAX_APP" --disconnects "$DISCONNECTS" --stores "$STORES")

java "${JAVA_OPTS[@]}" -cp "$CP" fixresend.ExplorerCli --build STOCK "${ARGS[@]}" --out "$WORK/stock.csv"
java "${JAVA_OPTS[@]}" -cp "$CP" fixresend.ExplorerCli --build WORKAROUND "${ARGS[@]}" --out "$WORK/workaround.csv"
java "${JAVA_OPTS[@]}" -cp "$PATCHED_CP" fixresend.ExplorerCli --build PATCHED "${ARGS[@]}" --out "$WORK/patched.csv"
java "${JAVA_OPTS[@]}" -cp "$CP" fixresend.SummaryCli --out-dir results $CHECK \
  "$WORK/stock.csv" "$WORK/workaround.csv" "$WORK/patched.csv"
