#!/usr/bin/env bash
# 순수 로직 검증 — 안드로이드 없이 JVM 에서 실행
# 사용: bash tools/logic-check/run.sh   (프로젝트 루트에서)
set -euo pipefail

JAVA_HOME=${JAVA_HOME:-/workspace/buildenv/jdk-17.0.21.1+2}
export JAVA_HOME
export PATH="$JAVA_HOME/bin:$PATH"
GRADLE_USER_HOME=${GRADLE_USER_HOME:-/workspace/buildenv/gradle-home}
export GRADLE_USER_HOME

KJAR=$(find "$GRADLE_USER_HOME/caches/modules-2" -name 'kotlin-compiler-embeddable-2.0.21.jar' | head -1)
STDLIB=$(find "$GRADLE_USER_HOME/caches/modules-2" -name 'kotlin-stdlib-2.0.21.jar' | head -1)
COROUT=$(find "$GRADLE_USER_HOME/caches/modules-2" -name 'kotlinx-coroutines-core-jvm-*.jar' | head -1)
TROVE=$(find "$GRADLE_USER_HOME/caches/modules-2" -name 'trove4j-1.0.20200330.jar' | head -1)
ANNOT=$(find "$GRADLE_USER_HOME/caches/modules-2" -name 'annotations-23.0.0.jar' | head -1)

SRC=app/src/main/java/com/youngsu/scanpdf
OUT=tools/logic-check/out
rm -rf "$OUT"
mkdir -p "$OUT"

java -cp "$KJAR:$STDLIB:$COROUT:$TROVE:$ANNOT" org.jetbrains.kotlin.cli.jvm.K2JVMCompiler \
  -no-stdlib -cp "$STDLIB" -d "$OUT" \
  "$SRC/pdf/PdfWriter.kt" "$SRC/pdf/Filters.kt" "$SRC/pdf/ScanGeometry.kt" \
  "$SRC/pdf/PdfSpecs.kt" "$SRC/util/Names.kt" tools/logic-check/Check.kt

java -cp "$OUT:$STDLIB" CheckKt
