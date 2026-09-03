#!/usr/bin/env sh
#
# Fails closed on Maven Central Portal publication until a byte-identical,
# post-attestation publish flow is deliberately configured and reviewed.
#
# Why this exists: `mvn deploy` re-runs the full Maven lifecycle from a fresh
# JVM (compile, test, package, sign) before it uploads anything. This
# reactor has no project.build.outputTimestamp / reproducible-build
# configuration, so a second `mvn` invocation after the build that was
# actually verified and attested (SBOM + provenance, see release.yml) is not
# guaranteed to produce byte-identical jars. Auto-publishing at that point
# could release artifacts to Central that differ from the ones GitHub
# attested. Rather than publish artifacts that might not match their own
# attestations, this gate hard-blocks by default.
#
# There is intentionally no environment-variable bypass. Re-enabling Central
# publication requires a reviewed source change that wires a byte-identical
# post-attestation upload/promote flow.
set -eu

echo "Maven Central publication is blocked (fail closed)." >&2
echo "No byte-identical, post-attestation publish/promote flow is configured." >&2
echo "A fresh 'mvn deploy' could rebuild artifacts that differ from those attested." >&2
echo "Re-enabling publication requires a reviewed workflow/source change; no environment variable can bypass this gate." >&2
exit 1
