# PowerShell script to configure index settings

$JAR_PATH = "..\tfg-filesearch\target\filesearch-1.0.0-jar-with-dependencies.jar"

Write-Host "=== Configure Index ===" -ForegroundColor Cyan

# Recreate index with specific name
java -jar $JAR_PATH create-index --name filesearch_production

Write-Host "Index 'filesearch_production' created."
