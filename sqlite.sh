#!/bin/sh

set -e
(
  cd "$(dirname "$0")"
  mvn -q -B package -Ddir=/tmp/codecrafters-build-sqlite-java
)

exec java -jar /tmp/codecrafters-build-sqlite-java/codecrafters-sqlite.jar "$@"
