#!/usr/bin/env sh
set -eu

script_dir=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
guard="$script_dir/require-image-digest.sh"

good_digest="ghcr.io/streamlinelabs/streamline@sha256:$(printf '%064d' 1)"

# A well-formed digest-pinned reference is accepted.
"$guard" "$good_digest"

# An absent image (no argument, no env var) hard-blocks.
if "$guard"; then
    echo "Expected a missing image to fail" >&2
    exit 1
fi

# An explicit empty image hard-blocks the same way as a missing one.
if "$guard" ""; then
    echo "Expected an empty image to fail" >&2
    exit 1
fi

# ':latest' (or any bare tag) must never be accepted, even as a positional arg.
if "$guard" "ghcr.io/streamlinelabs/streamline:latest"; then
    echo "Expected ':latest' to fail" >&2
    exit 1
fi

# An untagged/default reference with no tag or digest must also fail.
if "$guard" "ghcr.io/streamlinelabs/streamline"; then
    echo "Expected an untagged reference to fail" >&2
    exit 1
fi

# A tag alongside a digest is not a substitute for pinning by digest alone,
# but the digest suffix itself must still be well-formed.
if "$guard" "ghcr.io/streamlinelabs/streamline@sha256:tooshort"; then
    echo "Expected a malformed (short) digest to fail" >&2
    exit 1
fi

non_hex_digest=$(head -c 64 /dev/zero | tr '\0' 'G')
if "$guard" "ghcr.io/streamlinelabs/streamline@sha256:${non_hex_digest}"; then
    echo "Expected a malformed (non-hex) digest to fail" >&2
    exit 1
fi

# The STREAMLINE_CONFORMANCE_IMAGE environment variable is honored when no
# positional argument is supplied.
if ! STREAMLINE_CONFORMANCE_IMAGE="$good_digest" "$guard"; then
    echo "Expected STREAMLINE_CONFORMANCE_IMAGE fallback to succeed" >&2
    exit 1
fi

if STREAMLINE_CONFORMANCE_IMAGE="ghcr.io/streamlinelabs/streamline:latest" "$guard"; then
    echo "Expected STREAMLINE_CONFORMANCE_IMAGE=':latest' to fail" >&2
    exit 1
fi

echo "Image digest guard checks passed"
