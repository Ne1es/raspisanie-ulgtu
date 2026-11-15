#!/usr/bin/env bash
set -euo pipefail
project_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
cd "$project_dir"
: "${JSON_JAR:?Set JSON_JAR to org.json:json jar for JVM tests}"
test_classes="$project_dir/build/jvm-tests"
mkdir -p "$test_classes"
classpath="$JSON_JAR:$project_dir/app/libs/jsoup-1.18.3.jar"
sources=("$project_dir/app/src/main/java/ru/ne1es/timetable/"{Schedule,ScheduleStore,ScheduleLogic,RussianText,LocationQuality,SourceSession}.java "$project_dir/app/src/test/java/ru/ne1es/timetable/"*.java)
if command -v javac >/dev/null; then
 javac -encoding UTF-8 -source 8 -target 8 -cp "$classpath" -d "$test_classes" "${sources[@]}"
else
 : "${ECJ_JAR:?Set ECJ_JAR when javac is unavailable}"
 java -jar "$ECJ_JAR" -8 -encoding UTF-8 -warn:none -classpath "$classpath" -d "$test_classes" "${sources[@]}"
fi
for test_name in ParserTest RefreshRegressionTest FeatureTest LocationQualityTest SessionTest; do
 java -cp "$test_classes:$classpath" "ru.ne1es.timetable.$test_name"
done
node "$project_dir/tests/map-regression.cjs"
