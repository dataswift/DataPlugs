#!/usr/bin/env bash

set -e

source "${BASH_SOURCE%/*}/applyEvolutions.sh"

HOME=${PWD}
SOURCE="${PWD}/${BASH_SOURCE%/*}"

echo "Running:"
echo "Dataplug: ${DATAPLUG:?Need to set DATAPLUG non-empty}"
echo "Database: ${JDBCURL:?Need to set JDBCURL non-empty}"
echo "Database User: ${DBUSER:?Need to set DBUSER non-empty}"
echo "Database Password: ${DBPASS:?Need to set DBPASS non-empty}"

echo "Running generic Dataplug evolutions on $JDBCURL"
## declare an array of evolutions
declare -a evolutions=("1" "data")
cd  dataplug/conf/evolutions
runEvolutions -c structures,data
cd $HOME

echo "Running ${DATAPLUG} Dataplug evolutions on $JDBCURL"
## declare an array of evolutions
declare -a evolutions=("1" "data")
cd  "${DATAPLUG}/conf/evolutions"
runEvolutions -c structures,data
cd $HOME

echo "Compiling ${DATAPLUG}"
sbt compile

echo "Running ${DATAPLUG}"
sbt "project ${DATAPLUG}" "run"
