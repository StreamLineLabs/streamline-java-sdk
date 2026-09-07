#!/usr/bin/env sh

set -eu

missing=""

require_secret() {
    name=$1
    value=$2
    if [ -z "$value" ]; then
        missing="${missing} ${name}"
    fi
}

require_secret "CENTRAL_USERNAME" "${CENTRAL_USERNAME:-}"
require_secret "CENTRAL_PASSWORD" "${CENTRAL_PASSWORD:-}"
require_secret "MAVEN_GPG_PRIVATE_KEY" "${MAVEN_GPG_PRIVATE_KEY:-}"
require_secret "MAVEN_GPG_PASSPHRASE" "${MAVEN_GPG_PASSPHRASE:-}"

if [ -n "$missing" ]; then
    echo "Missing required release secrets:${missing}" >&2
    exit 1
fi

echo "Central Portal and GPG release secrets are present"
