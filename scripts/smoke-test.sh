#!/usr/bin/env bash
# Checks the packaged backend JAR end to end: Word, OpenDocument, RTF, email and ZIP files
# are extracted, indexed and found. Catches packaging mistakes (such as missing
# Tika parser registrations) that unit tests running from classes cannot see.
set -euo pipefail

JAR="${1:-tfg-filesearch/target/filesearch-1.0.0-jar-with-dependencies.jar}"
# Set JAVA to test with another runtime, e.g. the one bundled with the installer.
JAVA="${JAVA:-java}"
PYTHON="${PYTHON:-python3}"
WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT
export FILESEARCH_HOME="$WORK/home"
mkdir -p "$WORK/docs"

"$PYTHON" - "$WORK/docs" <<'PY'
import sys, zipfile
docs = sys.argv[1]

with zipfile.ZipFile(f"{docs}/demanda.docx", "w") as z:
    z.writestr("[Content_Types].xml", '<?xml version="1.0" encoding="UTF-8"?><Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/></Types>')
    z.writestr("_rels/.rels", '<?xml version="1.0" encoding="UTF-8"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/></Relationships>')
    z.writestr("word/document.xml", '<?xml version="1.0" encoding="UTF-8"?><w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"><w:body><w:p><w:r><w:t>Demanda de desahucio contra el arrendatario</w:t></w:r></w:p></w:body></w:document>')

with zipfile.ZipFile(f"{docs}/recurso.odt", "w") as z:
    z.writestr(zipfile.ZipInfo("mimetype"), "application/vnd.oasis.opendocument.text")
    z.writestr("META-INF/manifest.xml", '<?xml version="1.0" encoding="UTF-8"?><manifest:manifest xmlns:manifest="urn:oasis:names:tc:opendocument:xmlns:manifest:1.0"><manifest:file-entry manifest:full-path="/" manifest:media-type="application/vnd.oasis.opendocument.text"/><manifest:file-entry manifest:full-path="content.xml" manifest:media-type="text/xml"/></manifest:manifest>')
    z.writestr("content.xml", '<?xml version="1.0" encoding="UTF-8"?><office:document-content xmlns:office="urn:oasis:names:tc:opendocument:xmlns:office:1.0" xmlns:text="urn:oasis:names:tc:opendocument:xmlns:text:1.0" office:version="1.2"><office:body><office:text><text:p>Recurso de apelación sobre la cláusula suelo</text:p></office:text></office:body></office:document-content>')

with open(f"{docs}/requerimiento.eml", "w") as f:
    f.write("From: Ana <ana@despacho.es>\r\nTo: luis@cliente.com\r\nSubject: Requerimiento de pago\r\n"
            "MIME-Version: 1.0\r\nContent-Type: text/plain; charset=UTF-8\r\n\r\n"
            "Le remitimos el burofax por las rentas impagadas.\r\n")

with zipfile.ZipFile(f"{docs}/expediente.zip", "w") as z:
    z.writestr("escritos/contestacion.txt", "Escrito de contestación a la demanda de reconvención")

with open(f"{docs}/poder.rtf", "w") as f:
    f.write(r"{\rtf1\ansi Poder notarial otorgado en Sevilla\par}")
PY

"$JAVA" -jar "$JAR" update-index "$WORK/docs"

expect_hit() {
    local query="$1" file="$2"
    if ! "$JAVA" -jar "$JAR" search "$query" --output json | grep -q "$file"; then
        echo "FAIL: searching \"$query\" did not find $file" >&2
        exit 1
    fi
    echo "ok: \"$query\" -> $file"
}

expect_hit "desahucios" "demanda.docx"
expect_hit "clausula" "recurso.odt"
expect_hit "notarial" "poder.rtf"
expect_hit "burofax" "requerimiento.eml"
expect_hit "reconvencion" "expediente.zip"
echo "Smoke test passed"
