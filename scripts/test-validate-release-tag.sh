#!/usr/bin/env sh

set -eu

script_dir=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
validator="$script_dir/validate-release-tag.sh"

"$validator" v1.2.3 1.2.3

if "$validator" v1.2.4 1.2.3; then
    echo "Expected a mismatched release tag to fail" >&2
    exit 1
fi

if "$validator" 1.2.3 1.2.3; then
    echo "Expected a tag without the v prefix to fail" >&2
    exit 1
fi

if "$validator"; then
    echo "Expected a missing release tag to fail" >&2
    exit 1
fi

echo "Release tag validation checks passed"
