# File Search Engine - User Manual

**Version**: 1.0  
**Author**: Rodrigo Allende Rial  
**Date**: January 2026

---

## Table of Contents

1. [Getting Started](#getting-started)
2. [Installation](#installation)
3. [Basic Usage](#basic-usage)
4. [Command Reference](#command-reference)
5. [Configuration](#configuration)
6. [Tips and Best Practices](#tips-and-best-practices)
7. [Troubleshooting](#troubleshooting)

---

## 1. Getting Started

### What is File Search Engine?

File Search Engine is a command-line tool that indexes and searches documents on your local computer. It supports multiple file formats (TXT, PDF, DOCX, HTML) and provides fast, accurate search results.

### Key Features

- ✅ **Fast indexing**: Process thousands of documents in minutes
- ✅ **Powerful search**: Boolean operators, phrase matching, field-specific queries
- ✅ **Auto-sync**: Watch mode automatically updates index when files change
- ✅ **Multiple formats**: Support for text files, PDFs, Word documents, HTML
- ✅ **Flexible output**: Results in text or JSON format

---

## 2. Installation

### Prerequisites

Before installing, ensure you have:

1. **Java 17 or later** - [Download from Adoptium](https://adoptium.net/)
2. **Elasticsearch 8.x** - [Download from elastic.co](https://www.elastic.co/downloads/elasticsearch)

### Step 1: Install Java

**macOS** (using Homebrew):
```bash
brew install openjdk@17
```

**Windows**:
1. Download installer from [Adoptium](https://adoptium.net/)
2. Run installer and follow prompts
3. Verify: `java -version`

### Step 2: Install and Start Elasticsearch

**macOS**:
```bash
# Download and extract
cd ~/Downloads
wget https://artifacts.elastic.co/downloads/elasticsearch/elasticsearch-8.11.0-darwin-x86_64.tar.gz
tar -xzf elasticsearch-8.11.0-darwin-x86_64.tar.gz
cd elasticsearch-8.11.0

# Start (single-node mode)
./bin/elasticsearch
```

**Windows** (PowerShell):
```powershell
# Download from https://www.elastic.co/downloads/elasticsearch
# Extract to C:\elasticsearch

# Start
cd C:\elasticsearch\bin
.\elasticsearch.bat
```

Verify Elasticsearch is running:
```bash
curl http://localhost:9200/
```

### Step 3: Download File Search Engine

**Option A: Download pre-built JAR**
```bash
# Download from releases page
wget https://github.com/rodriar000/tfg-filesearch/releases/download/v1.0.0/filesearch.jar
```

**Option B: Build from source**
```bash
git clone https://github.com/rodriar000/tfg-filesearch.git
cd tfg-filesearch/tfg-filesearch
mvn clean package

# JAR located at: target/filesearch-1.0.0-jar-with-dependencies.jar
```

---

## 3. Basic Usage

### Quick Start Example

```bash
# 1. Create an index
java -jar filesearch.jar create-index

# 2. Index your documents
java -jar filesearch.jar update-index ~/Documents

# 3. Search
java -jar filesearch.jar search "project report"

# 4. View statistics
java -jar filesearch.jar stats
```

### Workflow

```
┌─────────────────┐
│ Create Index    │  One-time setup
└────────┬────────┘
         │
┌────────▼────────┐
│ Index Documents │  Add files to index
└────────┬────────┘
         │
┌────────▼────────┐
│ Search          │  Find documents
└────────┬────────┘
         │
┌────────▼────────┐
│ Update (Auto)   │  Watch mode keeps index fresh
└─────────────────┘
```

---

## 4. Command Reference

### 4.1 create-index

**Purpose**: Initialize a new Elasticsearch index with proper mappings.

**Syntax**:
```bash
java -jar filesearch.jar create-index [--name NAME]
```

**Options**:
| Option | Description | Default |
|--------|-------------|---------|
| `--name, -n` | Index name | `filesearch` (from config) |

**Examples**:
```bash
# Use default index name
java -jar filesearch.jar create-index

# Custom index name
java -jar filesearch.jar create-index --name my_documents
```

**Notes**:
- Only needs to be run once
- If index exists, command will skip creation
- Defines field types for optimal search performance

---

### 4.2 update-index

**Purpose**: Index documents from a directory into Elasticsearch.

**Syntax**:
```bash
java -jar filesearch.jar update-index PATH [OPTIONS]
```

**Arguments**:
| Argument | Description |
|----------|-------------|
| `PATH` | Directory to index (absolute or relative) |

**Options**:
| Option | Description | Default |
|--------|-------------|---------|
| `--recursive, -r` | Index subdirectories | `true` |
| `--create-if-missing` | Create index if doesn't exist | `true` |

**Examples**:
```bash
# Index current directory
java -jar filesearch.jar update-index .

# Index specific directory
java -jar filesearch.jar update-index ~/Documents/Projects

# Index without recursion
java -jar filesearch.jar update-index ~/Documents --recursive false
```

**Output**:
```
Starting document indexing...
Path: /Users/johndoe/Documents
Indexed 100 documents...
Indexed 200 documents...
...
=== Indexing Complete ===
Total documents indexed: 2000
Errors: 0
Time: 235000 ms (235.0 seconds)
Speed: 8.51 docs/sec
```

**Supported File Types**:
- `.txt` - Plain text files
- `.pdf` - PDF documents
- `.docx` - Microsoft Word documents
- `.html` - HTML files

**Notes**:
- Large files (> 100 MB) are skipped by default
- Hidden files and system directories are excluded
- Duplicate files (same path) are updated, not duplicated

---

### 4.3 search

**Purpose**: Search for documents in the index.

**Syntax**:
```bash
java -jar filesearch.jar search QUERY [OPTIONS]
```

**Arguments**:
| Argument | Description |
|----------|-------------|
| `QUERY` | Search query (use query_string syntax) |

**Options**:
| Option | Description | Default |
|--------|-------------|---------|
| `--size, -n` | Number of results to return | `10` |
| `--output, -o` | Output format (`text` or `json`) | `text` |

**Query Syntax**:

| Pattern | Example | Description |
|---------|---------|-------------|
| Keywords | `elasticsearch java` | Match any word |
| AND operator | `machine AND learning` | Match both words |
| OR operator | `python OR java` | Match either word |
| NOT operator | `java NOT javascript` | Exclude word |
| Phrase | `"neural networks"` | Exact phrase |
| Wildcard | `elect*` | Prefix matching |
| Field-specific | `author:john` | Search specific field |
| Combined | `title:"report" AND extension:pdf` | Complex query |

**Examples**:
```bash
# Simple search
java -jar filesearch.jar search "elasticsearch"

# Boolean query
java -jar filesearch.jar search "machine AND learning"

# Phrase search
java -jar filesearch.jar search '"information retrieval"'

# Field-specific
java -jar filesearch.jar search "author:smith AND extension:pdf"

# Return more results
java -jar filesearch.jar search "python" --size 20

# JSON output
java -jar filesearch.jar search "data science" --output json
```

**Text Output**:
```
=== Search Results ===
Query time: 45 ms
Total hits: 127
Showing top 10 results:

[1] Score: 12.3456
    Path: /Users/johndoe/Documents/elasticsearch-guide.pdf
    Title: Introduction to Elasticsearch
    Author: John Doe
    Size: 2.5 MB
    Preview: Elasticsearch is a distributed search and analytics engine...

[2] Score: 10.2341
    Path: /Users/johndoe/Documents/search-tutorial.txt
    ...
```

**JSON Output**:
```json
{
  "totalHits": 127,
  "tookMs": 45,
  "maxScore": 12.3456,
  "hits": [
    {
      "document": {
        "path": "/Users/johndoe/Documents/elasticsearch-guide.pdf",
        "filename": "elasticsearch-guide.pdf",
        "title": "Introduction to Elasticsearch",
        "author": "John Doe",
        "size": 2621440,
        "content": "Elasticsearch is a distributed..."
      },
      "score": 12.3456
    }
  ]
}
```

---

### 4.4 stats

**Purpose**: Display index statistics.

**Syntax**:
```bash
java -jar filesearch.jar stats
```

**Output**:
```
=== Index Statistics ===
Index name: filesearch
Documents: 2000
Index size: 42.50 MB
Health: green
Shards: 1
Replicas: 0
```

**Metrics**:
- **Documents**: Total number of indexed files
- **Index size**: Disk space used by index
- **Health**: Elasticsearch cluster health (green/yellow/red)
- **Shards**: Number of index shards
- **Replicas**: Number of replica copies

---

### 4.5 reindex

**Purpose**: Delete existing index and reindex all documents from scratch.

**Syntax**:
```bash
java -jar filesearch.jar reindex PATH
```

**⚠️ Warning**: This command deletes the existing index!

**When to use**:
- Index is corrupted
- Mapping changes required
- Starting fresh after major file reorganization

**Example**:
```bash
java -jar filesearch.jar reindex ~/Documents
```

**Process**:
1. Deletes existing index
2. Creates new index with mappings
3. Indexes all documents from PATH

---

### 4.6 watch

**Purpose**: Monitor a directory for file changes and automatically update the index.

**Syntax**:
```bash
java -jar filesearch.jar watch PATH
```

**Example**:
```bash
java -jar filesearch.jar watch ~/Documents
# Press Ctrl+C to stop watching
```

**Output**:
```
Starting file system watcher...
Watching: /Users/johndoe/Documents
Press Ctrl+C to stop

[14:23:15] Indexed: report.pdf
[14:25:30] Indexed: notes.txt
[14:27:12] Deleted from index: old_file.docx
```

**How it works**:
1. Monitors directory for CREATE, MODIFY, DELETE events
2. Debounces changes (waits 1 second to batch rapid edits)
3. Automatically reindexes modified files
4. Removes deleted files from index

**Best practice**: Run in background terminal while working

---

## 5. Configuration

### Configuration File

Location: `src/main/resources/config.yaml`

**Full configuration**:
```yaml
elasticsearch:
  host: localhost
  port: 9200
  scheme: http
  index_name: filesearch
  bulk_size: 100
  refresh_interval: 5s

indexing:
  extensions:
    - txt
    - pdf
    - docx
    - html
  max_file_size_mb: 100
  exclude_patterns:
    - "**/.git/**"
    - "**/node_modules/**"
    - "**/__pycache__/**"
    - "**/.DS_Store"
  threads: 4

search:
  default_size: 10
  max_size: 100
  highlight: true

watch:
  enabled: true
  debounce_ms: 1000
  rescan_interval_minutes: 60

logging:
  level: INFO
  file: logs/filesearch.log
```

### Key Settings

**Elasticsearch**:
- `host`: ES server hostname
- `port`: ES server port
- `index_name`: Name of the index to use
- `bulk_size`: Batch size for indexing (100-500 recommended)

**Indexing**:
- `extensions`: File types to index
- `max_file_size_mb`: Skip files larger than this
- `exclude_patterns`: Glob patterns to exclude (e.g., `.git`, `node_modules`)

**Search**:
- `default_size`: Default number of results when not specified
- `max_size`: Maximum allowed result size

**Watch**:
- `debounce_ms`: Wait time before processing file changes
- `rescan_interval_minutes`: Periodic full rescan interval

---

## 6. Tips and Best Practices

### Performance Tips

**1. Use SSD storage**
- Index and search are I/O intensive
- SSDs provide 10-20x performance improvement

**2. Optimize bulk size**
- Larger batches (200-500) for fast networks
- Smaller batches (50-100) for constrained memory

**3. Increase JVM heap for large corpora**
```bash
java -Xmx4g -jar filesearch.jar update-index /large/directory
```

**4. Exclude unnecessary directories**
- Add `.git`, `node_modules`, `__pycache__` to `exclude_patterns`
- Reduces indexing time and index size

### Search Tips

**1. Use field-specific queries**
```bash
# Search only in titles
filesearch search "title:report"

# Search by author
filesearch search "author:john"
```

**2. Combine operators**
```bash
filesearch search "title:(report OR summary) AND extension:pdf"
```

**3. Use wildcard for partial matches**
```bash
# Find "elastic", "elasticsearch", "elasticity"
filesearch search "elast*"
```

### Maintenance

**Weekly**:
- Check index size: `filesearch stats`
- Review logs: `tail -f logs/filesearch.log`

**Monthly**:
- Consider reindexing if files heavily modified
- Clean up orphaned entries

---

## 7. Troubleshooting

### Common Issues

#### Issue: "Connection refused" error

**Symptom**:
```
✗ Failed to create index: Connection refused
```

**Cause**: Elasticsearch not running

**Solution**:
1. Start Elasticsearch: `./bin/elasticsearch`
2. Verify: `curl http://localhost:9200/`
3. Check logs: `tail -f elasticsearch/logs/*.log`

---

#### Issue: "Out of memory" error

**Symptom**:
```
java.lang.OutOfMemoryError: Java heap space
```

**Cause**: Insufficient heap memory for large files

**Solution**:
```bash
# Increase heap to 2GB
java -Xmx2g -jar filesearch.jar update-index /path

# Or skip large files
# Edit config.yaml: max_file_size_mb: 50
```

---

#### Issue: Slow indexing

**Symptom**: < 5 docs/sec indexing speed

**Possible causes**:
1. **HDD instead of SSD**: Upgrade to SSD
2. **Network latency**: Use local ES instance
3. **Small bulk size**: Increase to 200-500
4. **CPU-intensive PDF parsing**: Normal for complex PDFs

**Solution**:
```yaml
# config.yaml
elasticsearch:
  bulk_size: 200  # Increase from 100
```

---

#### Issue: Search results not updated

**Symptom**: Modified file content doesn't appear in search

**Cause**: ES refresh interval delay

**Solution**:
1. **Wait 5 seconds** (default refresh interval)
2. **Force refresh** (not recommended for production):
   ```yaml
   elasticsearch:
     refresh_interval: 1s  # Faster but more resource-intensive
   ```
3. **Use watch mode** for automatic updates

---

#### Issue: No results found

**Symptom**: Search returns 0 hits for known documents

**Debugging**:
```bash
# 1. Check index exists
filesearch stats

# 2. Check document count
curl http://localhost:9200/filesearch/_count

# 3. Try broad query
filesearch search "*"

# 4. Check Elasticsearch logs
tail -f elasticsearch/logs/*.log
```

---

**For additional help, see**:
- [GitHub Issues](https://github.com/rodriar000/tfg-filesearch/issues)
- [Elasticsearch Documentation](https://www.elastic.co/guide/)
- [Apache Tika Documentation](https://tika.apache.org/)

---

**End of User Manual**
