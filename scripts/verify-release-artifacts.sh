#!/usr/bin/env sh

set -eu

if [ "$#" -ge 1 ]; then
    project_version=$1
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

missing=""
for artifact in \
    "pom.xml" \
    "streamline-client/pom.xml" \
    "streamline-client/target/streamline-client-${project_version}.jar" \
    "streamline-client/target/streamline-client-${project_version}-sources.jar" \
    "streamline-client/target/streamline-client-${project_version}-javadoc.jar" \
    "streamline-spring-boot-starter/pom.xml" \
    "streamline-spring-boot-starter/target/streamline-spring-boot-starter-${project_version}.jar" \
    "streamline-spring-boot-starter/target/streamline-spring-boot-starter-${project_version}-sources.jar" \
    "streamline-spring-boot-starter/target/streamline-spring-boot-starter-${project_version}-javadoc.jar" \
    "target/sbom.cdx.json"
do
    if [ ! -s "$artifact" ]; then
        missing="${missing} ${artifact}"
    fi
done

if [ -n "$missing" ]; then
    echo "Missing or empty release artifacts:${missing}" >&2
    exit 1
fi

echo "Release artifacts and CycloneDX SBOM are present for version ${project_version}"
