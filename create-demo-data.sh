#!/bin/bash

# TFG File Search - Demo Data Population Script
# Creates sample documents for demonstration purposes

echo "TFG File Search - Demo Data Populator"
echo "========================================"
echo ""

# Create demo directory
DEMO_DIR="$HOME/TFG_Demo_Data"
mkdir -p "$DEMO_DIR"

echo "Creating demo documents in: $DEMO_DIR"
echo ""

# Create various test files
echo "Creating text files..."
cat > "$DEMO_DIR/README.txt" << 'EOF'
File Search Engine - TFG Project
=================================

This is a demonstration file for the TFG file search engine.
The system uses Elasticsearch for indexing and Apache Tika for content extraction.

Key Features:
- Full-text search across multiple file formats
- Real-time indexing and search
- Modern Electron-based GUI
- Command-line interface for automation

Technologies:
- Backend: Java 17, Maven
- Search: Elasticsearch 7.17
- Content Extraction: Apache Tika
- Frontend: React, TypeScript, Electron
EOF

cat > "$DEMO_DIR/elasticsearch_notes.txt" << 'EOF'
Elasticsearch Quick Reference
==============================

Elasticsearch is a distributed search and analytics engine built on Apache Lucene.

Key Concepts:
- Index: A collection of documents
- Document: A JSON object containing data
- Mapping: Schema definition for documents
- Query DSL: Powerful query language

Common Operations:
1. Index a document: POST /index/_doc
2. Search: GET /index/_search
3. Update: POST /index/_update/id
4. Delete: DELETE /index/_doc/id

Best Practices:
- Use bulk API for indexing multiple documents
- Design mappings carefully
- Monitor cluster health
- Use filters for better performance
EOF

cat > "$DEMO_DIR/project_notes.txt" << 'EOF'
TFG Project Development Notes
==============================

Progress Report - Week 12
--------------------------
✓ Completed Elasticsearch integration
✓ Implemented Tika document extraction
✓ Built CLI with PicoCLI framework
✓ Created React frontend with Electron
✓ Added dark mode support
✓ Implemented real-time search

Next Steps:
- Performance benchmarking
- Memory optimization
- Documentation completion
- Final presentation preparation

Meeting with Supervisor:
Discussed architecture improvements and UX enhancement strategies.
Focus on making the system more user-friendly and robust.
EOF

cat > "$DEMO_DIR/research.txt" << 'EOF'
Information Retrieval Research
===============================

Literature Review on Search Engines:

1. Classic IR Models:
   - Boolean Model
   - Vector Space Model
   - Probabilistic Model

2. Modern Approaches:
   - BM25 Ranking Function
   - Neural Information Retrieval
   - Dense Passage Retrieval

3. Elasticsearch Specifics:
   - Inverted Index structure
   - TF-IDF scoring
   - Query-time boosting
   - Fuzzy matching

References:
- Manning et al., "Introduction to Information Retrieval"
- Elasticsearch: The Definitive Guide
- TREC Conference Proceedings
EOF

cat > "$DEMO_DIR/install_guide.txt" << 'EOF'
Installation and Setup Guide
=============================

Prerequisites:
- Java 17 or higher
- Node.js 18+ and npm
- Elasticsearch 7.17+
- Git

Installation Steps:

1. Clone the repository:
   git clone https://github.com/user/tfg-filesearch.git
   cd tfg-filesearch

2. Start Elasticsearch:
   brew services start elasticsearch-full

3. Build the backend:
   cd tfg-filesearch
   mvn clean package

4. Run the CLI:
   java -jar target/filesearch-1.0.0-jar-with-dependencies.jar --help

5. Launch the GUI:
   cd ../tfg-filesearch-gui
   npm install
   npm run dev

Quick Start:
- Index documents: java -jar filesearch.jar update-index ~/Documents
- Search: java -jar filesearch.jar search "your query"
- View stats: java -jar filesearch.jar stats
EOF

# Create JSON files
echo "Creating JSON files..."
cat > "$DEMO_DIR/config.json" << 'EOF'
{
  "system": "File Search Engine",
  "version": "1.0.0",
  "elasticsearch": {
    "host": "localhost",
    "port": 9200,
    "indexName": "filesearch"
  },
  "features": [
    "full-text search",
    "multi-format support",
    "real-time indexing",
    "modern UI"
  ],
  "author": "Rodrigo Allende"
}
EOF

cat > "$DEMO_DIR/sample_data.json" << 'EOF'
{
  "documents": [
    {
      "id": 1,
      "title": "Introduction to Elasticsearch",
      "category": "Tutorial",
      "tags": ["elasticsearch", "search", "database"]
    },
    {
      "id": 2,
      "title": "React Best Practices",
      "category": "Development",
      "tags": ["react", "javascript", "frontend"]
    },
    {
      "id": 3,
      "title": "Java 17 New Features",
      "category": "Programming",
      "tags": ["java", "programming", "backend"]
    }
  ]
}
EOF

# Create Markdown files
echo "Creating Markdown files..."
cat > "$DEMO_DIR/project_overview.md" << 'EOF'
# TFG File Search Engine

## Project Overview
A modern file search application using Elasticsearch for indexing and Apache Tika for content extraction.

## Architecture
- **Backend**: Java 17 with Maven
- **Search Engine**: Elasticsearch 7.17
- **Content Parser**: Apache Tika 2.9
- **Frontend**: React + TypeScript + Electron
- **Styling**: TailwindCSS

## Features
- 🔍 Full-text search across multiple formats
- 📁 Support for TXT, PDF, DOCX, HTML, JSON
- 🎨 Modern UI with dark mode
- ⚡ Real-time search results
- 📊 Index statistics and health monitoring

## Getting Started
See `install_guide.txt` for setup instructions.

## License
MIT License - See LICENSE file for details
EOF

cat > "$DEMO_DIR/api_documentation.md" << 'EOF'
# File Search API Documentation

## CLI Commands

### create-index
Create a new Elasticsearch index.
```
java -jar filesearch.jar create-index [--name NAME]
```

### update-index
Index documents from a directory.
```
java -jar filesearch.jar update-index PATH [--recursive]
```

### search
Search for documents.
```
java -jar filesearch.jar search QUERY [--size N] [--ext EXTENSION]
```

### stats
Display index statistics.
```
java -jar filesearch.jar stats
```

### reindex
Delete and recreate index with all documents.
```
java -jar filesearch.jar reindex PATH
```

## REST API (via Elasticsearch)
All standard Elasticsearch APIs are available at `http://localhost:9200`
EOF

echo ""
echo "Demo data creation completed."
echo ""
echo "Summary:"
echo "   - Location: $DEMO_DIR"
echo "   - Files created: 9"
echo "   - Types: TXT (5), JSON (2), MD (2)"
echo ""
echo "Instructions:"
echo "   1. Index the demo data:"
echo "      java -jar tfg-filesearch/target/filesearch-1.0.0-jar-with-dependencies.jar update-index $DEMO_DIR"
echo ""
echo "   2. Search the demo data:"
echo "      java -jar tfg-filesearch/target/filesearch-1.0.0-jar-with-dependencies.jar search \"elasticsearch\""
echo ""
echo "   3. Launch the GUI and search:"
echo "      cd tfg-filesearch-gui && npm run dev"
echo ""
