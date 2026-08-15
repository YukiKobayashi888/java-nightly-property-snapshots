#!/bin/sh
set -eu
classes="${TMPDIR:-/tmp}/property-snapshot-classes"
mkdir -p "$classes"
javac -d "$classes" $(find src/main/java -name '*.java')
java -cp "$classes" example.property.NightlyPropertyBackup "$@"
