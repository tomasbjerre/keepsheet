#!/usr/bin/env bash
# Runs the instrumented tests on the connected emulator/device and re-runs only the
# tests that failed, up to MAX_ATTEMPTS runs in total. Emulator-driven UI tests on a
# shared CI runner fail for reasons unrelated to the change under test (a dropped
# tap, a stalled frame, a slow first launch), and re-running the whole suite for one
# such failure costs minutes, while a second whole-suite run is just as exposed to the
# next unrelated flake. Each retry starts from a wiped app (clearPackageData, see
# app/build.gradle.kts), so a re-run test never sees what its failed attempt left behind.
#
# A test that only passes on a retry is reported as a warning annotation rather than
# hidden. A test that fails every attempt still fails the build.
#
# Environment:
#   MAX_ATTEMPTS  total runs, including the first (default 3)
#   TEST_FILTER   "Class" or "Class#method" entries, comma separated, restricting the
#                 first run (default: every instrumented test)
#   ATTEMPTS_DIR  where per-attempt HTML reports and logcat go, wiped first
#                 (default app/build/instrumented-attempts)
set -uo pipefail

cd "$(dirname "$0")/.."

readonly MAX_ATTEMPTS="${MAX_ATTEMPTS:-3}"
readonly RESULTS_DIR="app/build/outputs/androidTest-results/connected"
readonly REPORT_DIR="app/build/reports/androidTests/connected"
readonly ATTEMPTS_DIR="${ATTEMPTS_DIR:-app/build/instrumented-attempts}"

# Prints "Class#method" for every failed or errored test in the AGP result XML, comma separated.
failed_tests() {
  python3 - "$RESULTS_DIR" <<'PY'
import glob
import sys
import xml.etree.ElementTree as ET

failed = []
for path in glob.glob(sys.argv[1] + "/**/*.xml", recursive=True):
    for case in ET.parse(path).getroot().iter("testcase"):
        if case.find("failure") is not None or case.find("error") is not None:
            failed.append(f'{case.get("classname")}#{case.get("name")}')
print(",".join(sorted(set(failed))))
PY
}

filter="${TEST_FILTER:-}"
retried=""
rm -rf "$ATTEMPTS_DIR"
mkdir -p "$ATTEMPTS_DIR"

for attempt in $(seq 1 "$MAX_ATTEMPTS"); do
  echo "=== Instrumented tests: attempt $attempt of $MAX_ATTEMPTS ${filter:+(only $filter)}"
  rm -rf "$RESULTS_DIR"
  adb logcat -c || true

  gradle_args=()
  if [ -n "$filter" ]; then
    gradle_args+=("-Pandroid.testInstrumentationRunnerArguments.class=$filter")
  fi
  ./gradlew connectedDebugAndroidTest "${gradle_args[@]}"
  status=$?

  attempt_dir="$ATTEMPTS_DIR/attempt-$attempt"
  mkdir -p "$attempt_dir"
  adb logcat -d > "$attempt_dir/logcat.txt" 2>&1 || true
  if [ -d "$REPORT_DIR" ]; then
    cp -r "$REPORT_DIR" "$attempt_dir/report" || true
  fi

  if [ "$status" -eq 0 ]; then
    if [ -n "$retried" ]; then
      echo "::warning title=Flaky instrumented tests::Passed only on a retry: $retried"
    fi
    exit 0
  fi

  failed="$(failed_tests)"
  if [ -n "$failed" ]; then
    echo "Failed in attempt $attempt: $failed"
    filter="$failed"
    retried="$failed"
  else
    # Gradle failed without a failing test (install error, emulator hiccup, ...) — run the
    # same selection again rather than give up.
    echo "Attempt $attempt failed without a failing test; running the same selection again"
  fi
done

echo "::error title=Instrumented tests failed::Still failing after $MAX_ATTEMPTS attempts: ${retried:-see log}"
exit 1
