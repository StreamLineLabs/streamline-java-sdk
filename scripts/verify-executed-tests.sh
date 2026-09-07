#!/usr/bin/env sh
#
# Hard-blocks unless at least one test *actually executed* (i.e. was not
# skipped) across the given Failsafe/Surefire report directories.
#
# This exists because JUnit's @EnabledIfEnvironmentVariable-based opt-in
# (STREAMLINE_INTEGRATION=1) makes every *IT test report as "skipped" rather
# than "failed" when the opt-in is missing or the target server is
# unreachable. Left unchecked, a misconfigured CI job (e.g. a workflow that
# forgot to export STREAMLINE_INTEGRATION=1, or whose server never came up)
# would still exit 0 with zero conformance tests actually run, and a tagged
# release could publish on the strength of that false-green "live conformance"
# job. This script fails closed on exactly that case.
#
# Usage: verify-executed-tests.sh <report-dir> [<report-dir> ...]
#   Each <report-dir> is a directory containing Surefire/Failsafe
#   TEST-*.xml reports (e.g. target/failsafe-reports).
set -eu

if [ "$#" -eq 0 ]; then
    echo "Usage: $0 <report-dir> [<report-dir> ...]" >&2
    exit 2
fi

total_tests=0
total_skipped=0
report_files_found=0

extract_attr() {
    # extract_attr <attribute-name> <file>
    # Prints the first matching integer value of attribute="<digits>" found
    # anywhere in the file (Surefire/Failsafe put all <testsuite ...>
    # attributes on one line, but this tolerates wrapped XML too).
    attr=$1
    file=$2
    tr '\n' ' ' < "$file" \
        | sed -n "s/.*${attr}=\"\([0-9][0-9]*\)\".*/\1/p" \
        | head -n1
}

for dir in "$@"; do
    if [ ! -d "$dir" ]; then
        continue
    fi
    for report in "$dir"/TEST-*.xml; do
        [ -e "$report" ] || continue
        report_files_found=$((report_files_found + 1))

        tests=$(extract_attr tests "$report")
        skipped=$(extract_attr skipped "$report")
        tests=${tests:-0}
        skipped=${skipped:-0}

        total_tests=$((total_tests + tests))
        total_skipped=$((total_skipped + skipped))
    done
done

if [ "$report_files_found" -eq 0 ]; then
    echo "No test reports found under: $*" >&2
    echo "Zero test reports is treated as a hard failure for the live conformance gate." >&2
    exit 1
fi

executed=$((total_tests - total_skipped))

if [ "$executed" -le 0 ]; then
    echo "All $total_tests discovered test(s) were skipped (0 executed) across: $*" >&2
    echo "This usually means STREAMLINE_INTEGRATION was not exported, -Pintegration was" >&2
    echo "not active, or the server was unreachable. The live conformance gate refuses" >&2
    echo "to pass with zero executed tests." >&2
    exit 1
fi

echo "Executed $executed of $total_tests discovered test(s) (skipped $total_skipped) across: $*"
