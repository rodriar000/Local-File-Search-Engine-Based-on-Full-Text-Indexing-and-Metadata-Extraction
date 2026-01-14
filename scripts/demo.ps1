# PowerShell script to run the File Search demo

$JAR_PATH = "..\tfg-filesearch\target\filesearch-1.0.0-jar-with-dependencies.jar"
$SAMPLE_DOCS = "..\dataset\sample_files"

if (-not (Test-Path $JAR_PATH)) {
    Write-Host "Error: JAR file not found. Please build the project first." -ForegroundColor Red
    Write-Host "Run: mvn clean package"
    exit 1
}

Write-Host "=== File Search Engine Demo ===" -ForegroundColor Cyan

# 1. Create Index
Write-Host "`n1. Creating Index..." -ForegroundColor Yellow
java -jar $JAR_PATH create-index --name filesearch_demo

# 2. Index Documents
Write-Host "`n2. Indexing Documents from $SAMPLE_DOCS..." -ForegroundColor Yellow
$Time = Measure-Command {
    java -jar $JAR_PATH update-index $SAMPLE_DOCS
}
Write-Host "Indexing took: $($Time.TotalSeconds) seconds" -ForegroundColor Green

# 3. Show Stats
Write-Host "`n3. Index Statistics:" -ForegroundColor Yellow
java -jar $JAR_PATH stats

# 4. Perform Searches
Write-Host "`n4. Performing Sample Searches..." -ForegroundColor Yellow

Write-Host "`nSearch: 'elasticsearch'"
java -jar $JAR_PATH search "elasticsearch" --size 5

Write-Host "`nSearch: 'java AND programming'"
java -jar $JAR_PATH search "java AND programming" --size 5

Write-Host "`nSearch: 'extension:pdf'"
java -jar $JAR_PATH search "extension:pdf" --size 5

Write-Host "`n=== Demo Complete ===" -ForegroundColor Cyan
