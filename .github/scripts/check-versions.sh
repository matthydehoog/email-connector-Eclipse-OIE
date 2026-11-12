#!/usr/bin/env bash
# The connector version is in four files; they must agree. On a tag build the tag must match too.
#
# Usage: check-versions.sh [tag, e.g. v3.1.2]. Run from the repository root.
set -euo pipefail

pom="$(sed -n '0,/<artifactId>email-reader-connector<\/artifactId>/d; s|.*<version>\(.*\)</version>.*|\1|p' connector/pom.xml | head -1)"
plugin="$(sed -n 's|.*<pluginVersion>\(.*\)</pluginVersion>.*|\1|p' connector/src/main/resources/source.xml)"
store="$(sed -n 's|.*"version": *"\([^"]*\)".*|\1|p' oie.json | head -1)"
web="$(sed -n 's|.*"version": *"\([^"]*\)".*|\1|p' connector/webadmin/plugin.json | head -1)"

echo "connector/pom.xml: ${pom}, source.xml: ${plugin}, oie.json: ${store}, webadmin/plugin.json: ${web}"
if [ -z "${pom}" ] || [ "${pom}" != "${plugin}" ] || [ "${pom}" != "${store}" ] || [ "${pom}" != "${web}" ]; then
  echo "The versions differ; set the same version in all four files." >&2
  exit 1
fi

tag="${1:-}"
if [ -n "${tag}" ] && [ "${tag}" != "v${pom}" ]; then
  echo "Tag ${tag} does not match version ${pom} (expected v${pom})." >&2
  exit 1
fi
echo "Version ${pom} OK"
