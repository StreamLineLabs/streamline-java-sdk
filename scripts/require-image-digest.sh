#!/usr/bin/env sh
#
# Hard-blocks unless an explicit, immutable, digest-pinned Streamline image
# reference is supplied. Live conformance (and, transitively, tagged
# publication) must never run against an absent image or a mutable tag
# (":latest", a bare/untagged reference, or any other floating tag) — those
# can silently change or vanish out from under a release.
#
# Usage: require-image-digest.sh [<image-reference>]
#   <image-reference> may also be supplied via STREAMLINE_CONFORMANCE_IMAGE.
#   A positional argument takes precedence when both are set.
#
# Accepts only references of the form:
#   <repository>@sha256:<64 lowercase hex characters>
set -eu

image=${1:-${STREAMLINE_CONFORMANCE_IMAGE:-}}

if [ -z "$image" ]; then
    echo "No Streamline conformance image was supplied." >&2
    echo "Live conformance is hard-blocked: set the STREAMLINE_CONFORMANCE_IMAGE_DIGEST" >&2
    echo "repository variable (or the workflow_dispatch 'image' input) to an immutable," >&2
    echo "digest-pinned reference, e.g.:" >&2
    echo "  ghcr.io/streamlinelabs/streamline@sha256:<64 lowercase hex characters>" >&2
    exit 1
fi

case "$image" in
    *@sha256:*)
        digest=${image#*@sha256:}
        ;;
    *)
        echo "Rejected non-digest-pinned image reference: $image" >&2
        echo "Mutable tags (including ':latest' or an untagged default) are never accepted" >&2
        echo "for live conformance; pin an immutable digest, e.g.:" >&2
        echo "  repository@sha256:<64 lowercase hex characters>" >&2
        exit 1
        ;;
esac

# Reject anything after the digest (e.g. a stray tag suffix or trailing text)
# and enforce exactly 64 lowercase hex characters.
case "$digest" in
    ????????????????????????????????????????????????????????????????) ;;
    *)
        echo "Rejected malformed sha256 digest (expected exactly 64 hex characters): $digest" >&2
        exit 1
        ;;
esac

case "$digest" in
    *[!0-9a-f]*)
        echo "Rejected malformed sha256 digest (expected lowercase hex characters only): $digest" >&2
        exit 1
        ;;
esac

echo "Accepted immutable conformance image: $image"
