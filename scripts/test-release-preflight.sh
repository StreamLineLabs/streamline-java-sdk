#!/usr/bin/env sh

set -eu

script_dir=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
preflight="$script_dir/release-preflight.sh"

CENTRAL_USERNAME=test \
CENTRAL_PASSWORD=test \
MAVEN_GPG_PRIVATE_KEY=test \
MAVEN_GPG_PASSPHRASE=test \
    "$preflight"

if CENTRAL_USERNAME= \
    CENTRAL_PASSWORD=test \
    MAVEN_GPG_PRIVATE_KEY=test \
    MAVEN_GPG_PASSPHRASE=test \
    "$preflight"; then
    echo "Expected a missing Central username to fail" >&2
    exit 1
fi

if CENTRAL_USERNAME=test \
    CENTRAL_PASSWORD= \
    MAVEN_GPG_PRIVATE_KEY=test \
    MAVEN_GPG_PASSPHRASE=test \
    "$preflight"; then
    echo "Expected a missing Central password to fail" >&2
    exit 1
fi

if CENTRAL_USERNAME=test \
    CENTRAL_PASSWORD=test \
    MAVEN_GPG_PRIVATE_KEY= \
    MAVEN_GPG_PASSPHRASE=test \
    "$preflight"; then
    echo "Expected a missing GPG private key to fail" >&2
    exit 1
fi

if CENTRAL_USERNAME=test \
    CENTRAL_PASSWORD=test \
    MAVEN_GPG_PRIVATE_KEY=test \
    MAVEN_GPG_PASSPHRASE= \
    "$preflight"; then
    echo "Expected a missing GPG passphrase to fail" >&2
    exit 1
fi

echo "Release secret preflight checks passed"
