#!/usr/bin/env bash
# Prepares everything the installer ships next to the app, in
# tfg-filesearch-gui/build/runtime:
#   filesearch.jar  the search engine (build it first with "mvn package")
#   jre/            a minimal Java runtime made with jlink, so users need no Java
#   tesseract/      optional: Tesseract OCR with Spanish data (Windows installer)
#
# Usage: scripts/bundle-runtime.sh [tesseract-install-folder]
# Runs on Linux, macOS and Windows (Git Bash). Uses the jlink of $JAVA_HOME, or the one on the PATH.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
JAR="$ROOT/tfg-filesearch/target/filesearch-1.0.0-jar-with-dependencies.jar"
OUT="$ROOT/tfg-filesearch-gui/build/runtime"
TESSERACT_SOURCE="${1:-}"

# Spanish model from tesseract-ocr/tessdata_fast; the same file Debian and Ubuntu ship.
SPA_URL="https://raw.githubusercontent.com/tesseract-ocr/tessdata_fast/main/spa.traineddata"
SPA_SHA256="6f2e04d02774a18f01bed44b1111f2cd7f3ba7ac9dc4373cd3f898a40ea6b464"

# Modules the backend needs: jdeps on its dependencies, plus the ones loaded by
# reflection (charsets for old emails, Spanish locale data, Lucene's Unsafe and
# HotSpot probes, ZIP file system, elliptic-curve crypto for encrypted PDFs).
MODULES="java.base,java.xml,java.desktop,java.logging,java.sql,java.naming,java.scripting,\
java.management,jdk.management,jdk.xml.dom,jdk.httpserver,jdk.unsupported,jdk.charsets,\
jdk.localedata,jdk.crypto.ec,jdk.zipfs"

if [[ ! -f "$JAR" ]]; then
    echo "Backend JAR not found at $JAR. Run \"mvn package\" in tfg-filesearch first." >&2
    exit 1
fi

JLINK="jlink"
if [[ -n "${JAVA_HOME:-}" ]]; then
    JLINK="$JAVA_HOME/bin/jlink"
fi

rm -rf "$OUT"
mkdir -p "$OUT"
cp "$JAR" "$OUT/filesearch.jar"

# The leading space stops jlink from reading the value as one of its own options.
"$JLINK" --add-modules "$MODULES" \
    --include-locales=es,en \
    "--add-options= --enable-native-access=ALL-UNNAMED" \
    --strip-debug --no-header-files --no-man-pages --compress=zip-6 \
    --output "$OUT/jre"

if [[ -n "$TESSERACT_SOURCE" ]]; then
    if [[ ! -x "$TESSERACT_SOURCE/tesseract.exe" && ! -x "$TESSERACT_SOURCE/tesseract" ]]; then
        echo "No tesseract program in $TESSERACT_SOURCE" >&2
        exit 1
    fi
    mkdir -p "$OUT/tesseract"
    # Program and libraries only: the uninstaller, docs and other languages stay behind.
    find "$TESSERACT_SOURCE" -maxdepth 1 -type f \( -iname '*.exe' -o -iname '*.dll' -o -name 'tesseract' \) \
        ! -iname 'tesseract-uninstall.exe' ! -iname 'uninstall*.exe' -exec cp {} "$OUT/tesseract/" \;
    mkdir -p "$OUT/tesseract/tessdata"
    if [[ -d "$TESSERACT_SOURCE/tessdata/configs" ]]; then
        cp -r "$TESSERACT_SOURCE/tessdata/configs" "$OUT/tesseract/tessdata/"
    fi
    curl -fsSL --retry 3 -o "$OUT/tesseract/tessdata/spa.traineddata" "$SPA_URL"
    actual="$( (sha256sum "$OUT/tesseract/tessdata/spa.traineddata" 2>/dev/null \
        || shasum -a 256 "$OUT/tesseract/tessdata/spa.traineddata") | cut -d' ' -f1)"
    if [[ "$actual" != "$SPA_SHA256" ]]; then
        echo "spa.traineddata checksum mismatch: $actual" >&2
        exit 1
    fi
fi

du -sh "$OUT"/* || true
echo "Runtime ready in $OUT"
