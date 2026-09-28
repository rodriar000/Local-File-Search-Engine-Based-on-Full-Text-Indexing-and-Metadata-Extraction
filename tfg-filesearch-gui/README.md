# File Search GUI & CLI

A modern desktop frontend and robust CLI for the File Search Engine, built with Electron, React, TypeScript, and Java.

## New Features (Update)
- **Observability**: Real-time metrics for search latency and indexing throughput.
- **Data Export**: Export search results to JSON or CSV directly from the UI.
- **Benchmark CLI**: New `benchmark` command to test search performance.
- **System Doctor**: New `doctor` command to diagnose configuration issues.

## GUI Features
- **Live Search**: Real-time search as you type.
- **Filters**: Filter by extension, size, and date.
- **File Actions**: Open files, show in explorer, copy path.
- **Export**: Download results for external analysis.
- **Settings**: Choose the documents folder, index it with a progress bar and see what could not be read.

## CLI Usage

The CLI now supports advanced operations:

### Benchmark
Run a performance benchmark using a file of queries:
```bash
java -jar filesearch.jar benchmark --queries queries.txt --runs 10
```
This generates a CSV report in `./out/`.

### Doctor
Check text extraction, the index and permissions:
```bash
java -jar filesearch.jar doctor
```

## Prerequisites
- Node.js (v18 or later)
- Java 17+ (for CLI/Backend)

## Installation & Build

1. **Backend (CLI)**:
   ```bash
   cd tfg-filesearch
   mvn clean package
   ```

2. **Frontend (GUI)**:
   ```bash
   cd tfg-filesearch-gui
   npm install
   npm run build
   ```
