#!/usr/bin/env sh
set -eu
rm -rf out
mkdir -p out
javac -d out $(find src/main/java -name '*.java')
java -cp out learning.mail.TemplateMigrationExample
