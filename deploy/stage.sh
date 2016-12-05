#!/usr/bin/env bash

set -e

source "${BASH_SOURCE%/*}/applyEvolutions.sh"

HOME=${PWD}
SOURCE="${PWD}/${BASH_SOURCE%/*}"

echo "Running:"
echo "Dataplug: ${DATAPLUG:?Need to set DATAPLUG non-empty}"

sbt "project ${DATAPLUG}" "docker:stage"
docker build -t hubofallthings/${DATAPLUG} ${DATAPLUG}/target/docker/stage/
docker push hubofallthings/${DATAPLUG}

