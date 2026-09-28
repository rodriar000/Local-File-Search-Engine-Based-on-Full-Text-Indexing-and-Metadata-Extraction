# PowerShell script to run the File Search demo

$JAR_PATH = "..\tfg-filesearch\target\filesearch-1.0.0-jar-with-dependencies.jar"
$SAMPLE_DOCS = "..\dataset\sample_files"

if (-not (Test-Path $JAR_PATH)) {
    Write-Host "Error: JAR file not found. Please build the project first." -ForegroundColor Red
    Write-Host "Run: mvn clean package"
    exit 1
}

Write-Host "=== File Search Engine Demo ===" -ForegroundColor Cyan

# 1. Index Documents
Write-Host "`n1. Indexing Documents from $SAMPLE_DOCS..." -ForegroundColor Yellow
$Time = Measure-Command {
    java -jar $JAR_PATH update-index $SAMPLE_DOCS
}
Write-Host "Indexing took: $($Time.TotalSeconds) seconds" -ForegroundColor Green

# 2. Show Stats
Write-Host "`n2. Index Statistics:" -ForegroundColor Yellow
java -jar $JAR_PATH stats

# 3. Perform Searches
Write-Host "`n3. Performing Sample Searches..." -ForegroundColor Yellow

Write-Host "`nSearch: 'search'"
java -jar $JAR_PATH search "search" --size 5

Write-Host "`nSearch: 'java programming' (all words)"
java -jar $JAR_PATH search "java programming" --size 5

Write-Host "`nSearch: PDF files only"
java -jar $JAR_PATH search "" --ext pdf --size 5

Write-Host "`n=== Demo Complete ===" -ForegroundColor Cyan
