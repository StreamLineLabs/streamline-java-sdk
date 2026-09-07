#!/usr/bin/env sh
set -eu

script_dir=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
repo_root=$(CDPATH= cd -- "$script_dir/.." && pwd)
verifier="$script_dir/verify-executed-tests.sh"

scratch_root="$repo_root/target/scripts-test-fixtures"
fixtures="$scratch_root/verify-executed-tests"
rm -rf "$scratch_root"
mkdir -p "$fixtures/executed" "$fixtures/all-skipped" "$fixtures/empty-dir"
# Remove the whole scratch root (not just this test's subdirectory) so no
# empty directories are left behind under target/ once the suite finishes.
trap 'rm -rf "$scratch_root"' EXIT

# A report with executed (non-skipped) tests passes.
cat > "$fixtures/executed/TEST-dev.streamline.conformance.ConformanceIT.xml" <<'EOF'
<?xml version="1.0" encoding="UTF-8"?>
<testsuite name="dev.streamline.conformance.ConformanceIT" tests="46" skipped="0" failures="0" errors="0" time="12.3">
</testsuite>
EOF

if ! "$verifier" "$fixtures/executed"; then
    echo "Expected a report with executed tests to pass" >&2
    exit 1
fi

# A report where every discovered test was skipped (the STREAMLINE_INTEGRATION
# opt-in was never exported, or the server was unreachable) must hard-fail.
cat > "$fixtures/all-skipped/TEST-dev.streamline.conformance.ConformanceIT.xml" <<'EOF'
<?xml version="1.0" encoding="UTF-8"?>
<testsuite name="dev.streamline.conformance.ConformanceIT" tests="46" skipped="46" failures="0" errors="0" time="0.01">
</testsuite>
EOF

if "$verifier" "$fixtures/all-skipped"; then
    echo "Expected an all-skipped report to fail" >&2
    exit 1
fi

# A mix of skipped and executed tests still passes as long as at least one
# test actually executed.
mkdir -p "$fixtures/mixed"
cat > "$fixtures/mixed/TEST-dev.streamline.conformance.ConformanceIT.xml" <<'EOF'
<?xml version="1.0" encoding="UTF-8"?>
<testsuite name="dev.streamline.conformance.ConformanceIT" tests="46" skipped="3" failures="0" errors="0" time="9.1">
</testsuite>
EOF

if ! "$verifier" "$fixtures/mixed"; then
    echo "Expected a partially-skipped report with executed tests to pass" >&2
    exit 1
fi

# An empty/missing report directory (e.g. Failsafe never ran) must hard-fail
# rather than silently pass with zero tests.
if "$verifier" "$fixtures/empty-dir"; then
    echo "Expected an empty report directory to fail" >&2
    exit 1
fi

if "$verifier" "$fixtures/does-not-exist"; then
    echo "Expected a nonexistent report directory to fail" >&2
    exit 1
fi

# No arguments at all is a usage error, not a silent pass.
if "$verifier"; then
    echo "Expected a missing report-dir argument to fail" >&2
    exit 1
fi

# Multiple directories are aggregated: one all-skipped module plus one with
# executed tests must still pass overall.
if ! "$verifier" "$fixtures/all-skipped" "$fixtures/executed"; then
    echo "Expected aggregation across directories to pass when any has executed tests" >&2
    exit 1
fi

echo "Executed-test-count guard checks passed"
