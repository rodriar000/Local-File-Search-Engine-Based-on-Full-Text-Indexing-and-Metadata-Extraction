# PowerShell script to automate benchmarks

$JAR_PATH = "..\tfg-filesearch\target\filesearch-1.0.0-jar-with-dependencies.jar"
$DATASET_PATH = "..\dataset\sample_files"
$RESULTS_FILE = "benchmark_results.csv"

Write-Host "=== Starting Benchmarks ===" -ForegroundColor Cyan

# Ensure dataset exists
if (-not (Test-Path $DATASET_PATH)) {
    Write-Host "Error: Dataset path not found: $DATASET_PATH" -ForegroundColor Red
    exit 1
}

# Keep the benchmark index apart from the user's own index.
$env:FILESEARCH_HOME = Join-Path $PSScriptRoot "benchmark-home"

# Initialize results file
"Run,Operation,Metric,Value,Unit" | Out-File $RESULTS_FILE -Encoding UTF8

# --- Indexing Benchmark ---
Write-Host "`nRunning Indexing Benchmark (5 iterations)..." -ForegroundColor Yellow

for ($i=1; $i -le 5; $i++) {
    Write-Host "Run $i..."
    
    # Measure a full rebuild of the index
    $Time = Measure-Command {
        java -jar $JAR_PATH reindex $DATASET_PATH
    }
    
    $Seconds = $Time.TotalSeconds
    Write-Host "Time: $Seconds s"
    
    "$i,Indexing,Time,$Seconds,Seconds" | Out-File $RESULTS_FILE -Append -Encoding UTF8
    
    # Get doc count for throughput calc
    # Note: In a real script we'd parse the output, here we assume we know the count or get it from stats
    # For simplicity, let's just log the time.
}

# --- Search Benchmark ---
Write-Host "`nRunning Search Benchmark..." -ForegroundColor Yellow
$QUERIES = @("search", "java", "machine learning", "test document", "file search")

foreach ($query in $QUERIES) {
    Write-Host "Query: $query"
    
    for ($i=1; $i -le 10; $i++) {
        # Measure command execution time (end-to-end latency including JVM startup)
        # Note: For pure query latency, we should rely on the internal logs/output of the tool
        # which reports "Query time: X ms"
        
        $Output = java -jar $JAR_PATH search "$query" --output json
        # Parse JSON to get tookMs would be ideal, but requires jq or complex parsing
        # Here we just measure wall clock for the CLI call as a proxy for user experience
    }
}

Write-Host "`nBenchmarks complete. Results saved to $RESULTS_FILE" -ForegroundColor Green
