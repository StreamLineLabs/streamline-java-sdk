#!/usr/bin/env sh
set -eu

script_dir=$(CDPATH='' cd -- "$(dirname -- "$0")" && pwd)
gate="$script_dir/central-publish-gate.sh"

# The gate must hard-block for every environment value. Publication can only
# be re-enabled by a reviewed source/workflow change.
if CENTRAL_PUBLISH_READY='' "$gate"; then
    echo "Expected the gate to block when CENTRAL_PUBLISH_READY is unset" >&2
    exit 1
fi

if CENTRAL_PUBLISH_READY=true "$gate"; then
    echo "Expected the gate to remain blocked even when CENTRAL_PUBLISH_READY=true" >&2
    exit 1
fi

echo "Central Portal fail-closed gate checks passed"
