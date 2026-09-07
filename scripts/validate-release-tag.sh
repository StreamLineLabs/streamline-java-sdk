#!/usr/bin/env sh

set -eu

tag=${1:-}
if [ -z "$tag" ]; then
    echo "Usage: $0 <release-tag> [maven-project-version]" >&2
    exit 2
fi

case "$tag" in
    v*) ;;
    *)
        echo "Release tag must start with 'v': $tag" >&2
        exit 1
        ;;
esac

if [ "$#" -ge 2 ]; then
    project_version=$2
else
    project_version=$(mvn --batch-mode --quiet --non-recursive \
        org.apache.maven.plugins:maven-help-plugin:3.5.1:evaluate \
        -Dexpression=project.version \
        -DforceStdout)
fi

if [ -z "$project_version" ]; then
    echo "Unable to determine the Maven project version" >&2
    exit 1
fi

expected_tag="v${project_version}"
if [ "$tag" != "$expected_tag" ]; then
    echo "Release tag/version mismatch: tag '$tag' must equal '$expected_tag'" >&2
    exit 1
fi

echo "Release tag '$tag' matches Maven project version '$project_version'"
