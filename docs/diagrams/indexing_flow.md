# Indexing Flow Diagram

```mermaid
sequenceDiagram
    participant User
    participant CLI
    participant DocExtractor
    participant ESService
    participant Elasticsearch

    User->>CLI: update-index /path
    CLI->>CLI: Scan directory
    
    loop For each file
        CLI->>DocExtractor: extractDocument(file)
        DocExtractor->>DocExtractor: Parse with Tika
        DocExtractor->>DocExtractor: Extract metadata
        DocExtractor->>DocExtractor: Calculate checksum
        DocExtractor-->>CLI: Document object
        
        CLI->>CLI: Add to batch
        
        alt Batch full (100 docs)
            CLI->>ESService: bulkIndexDocuments(batch)
            ESService->>Elasticsearch: Bulk API request
            Elasticsearch-->>ESService: Response
            ESService-->>CLI: Indexed count
            CLI->>CLI: Clear batch
        end
    end
    
    alt Remaining documents
        CLI->>ESService: bulkIndexDocuments(batch)
        ESService->>Elasticsearch: Bulk API request
        Elasticsearch-->>ESService: Response
        ESService-->>CLI: Indexed count
    end
    
    CLI-->>User: Indexing complete<br/>Total docs, time, speed
```

## Flow Description

1. **Directory Scanning**: CLI recursively scans the target directory
2. **File Filtering**: Checks extension whitelist and size limits
3. **Document Extraction**: Tika parses file and extracts content + metadata
4. **Batch Accumulation**: Documents accumulated until batch size (100) reached
5. **Bulk Indexing**: Batch sent to Elasticsearch via Bulk API
6. **Progress Reporting**: User receives real-time progress updates
7. **Final Statistics**: Total documents, duration, and throughput reported
