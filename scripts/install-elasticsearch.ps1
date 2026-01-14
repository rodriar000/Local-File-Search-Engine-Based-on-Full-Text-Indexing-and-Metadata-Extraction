# PowerShell script to install Elasticsearch 8.11.0 on Windows

$ES_VERSION = "8.11.0"
$ES_URL = "https://artifacts.elastic.co/downloads/elasticsearch/elasticsearch-$ES_VERSION-windows-x86_64.zip"
$INSTALL_DIR = "C:\elasticsearch"
$ZIP_FILE = "$env:TEMP\elasticsearch.zip"

Write-Host "=== Elasticsearch Installer for TFG File Search ===" -ForegroundColor Cyan

# Check if Java is installed
try {
    java -version
    Write-Host "Java is installed." -ForegroundColor Green
} catch {
    Write-Host "Error: Java is not found. Please install Java 17+ first." -ForegroundColor Red
    exit 1
}

# Create installation directory
if (-not (Test-Path $INSTALL_DIR)) {
    New-Item -ItemType Directory -Force -Path $INSTALL_DIR | Out-Null
    Write-Host "Created directory: $INSTALL_DIR"
}

# Download Elasticsearch
if (-not (Test-Path $ZIP_FILE)) {
    Write-Host "Downloading Elasticsearch $ES_VERSION..."
    Invoke-WebRequest -Uri $ES_URL -OutFile $ZIP_FILE
} else {
    Write-Host "Using existing zip file."
}

# Extract
Write-Host "Extracting to $INSTALL_DIR..."
Expand-Archive -Path $ZIP_FILE -DestinationPath $INSTALL_DIR -Force

# Rename folder for simpler path
$EXTRACTED_FOLDER = Join-Path $INSTALL_DIR "elasticsearch-$ES_VERSION"
if (Test-Path $EXTRACTED_FOLDER) {
    # Move contents up one level if needed, or just keep it
    Write-Host "Installed at $EXTRACTED_FOLDER"
}

# Configure elasticsearch.yml for single-node development (disable security for local dev)
$CONFIG_FILE = Join-Path $EXTRACTED_FOLDER "config\elasticsearch.yml"
$CONFIG_CONTENT = @"
cluster.name: filesearch-cluster
node.name: node-1
path.data: $EXTRACTED_FOLDER\data
path.logs: $EXTRACTED_FOLDER\logs
network.host: 0.0.0.0
http.port: 9200
discovery.type: single-node
xpack.security.enabled: false
xpack.security.enrollment.enabled: false
xpack.security.http.ssl.enabled: false
xpack.security.transport.ssl.enabled: false
"@

Set-Content -Path $CONFIG_FILE -Value $CONFIG_CONTENT
Write-Host "Configured elasticsearch.yml for local development (security disabled)." -ForegroundColor Yellow

# Create start script
$START_SCRIPT = Join-Path $INSTALL_DIR "start-es.ps1"
Set-Content -Path $START_SCRIPT -Value "cd `"$EXTRACTED_FOLDER`"; .\bin\elasticsearch.bat"

Write-Host "=== Installation Complete ===" -ForegroundColor Green
Write-Host "To start Elasticsearch, run: $START_SCRIPT"
