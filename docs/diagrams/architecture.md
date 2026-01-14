# System Architecture

```mermaid
graph TB
    subgraph "User Interface"
        CLI[CLI - Picocli]
    end
    
    subgraph "Processing Layer"
        DOC[DocumentExtractor<br/>Apache Tika]
        WATCH[FileSystemWatcher<br/>Java NIO]
    end
    
    subgraph "Storage Layer"
        ES_SVC[ElasticsearchService]
        IDX_MGR[IndexManager]
        SEARCH[SearchExecutor]
    end
    
    subgraph "Data Store"
        ES[(Elasticsearch<br/>8.x)]
    end
    
    CLI -->|extract| DOC
    CLI -->|watch| WATCH
    CLI -->|index| ES_SVC
    CLI -->|search| ES_SVC
    
    DOC -->|documents| ES_SVC
    WATCH -->|changes| ES_SVC
    
    ES_SVC --> IDX_MGR
    ES_SVC --> SEARCH
    
    IDX_MGR <-->|HTTP/JSON| ES
    SEARCH <--> ES
    
    style CLI fill:#e1f5ff
    style DOC fill:#fff4e1
    style WATCH fill:#fff4e1
    style ES_SVC fill:#e8f5e9
    style ES fill:#f3e5f5
```

## Component Description

### User Interface Layer
- **CLI (Picocli)**: Command-line interface providing six main commands:
  - `create-index`: Initialize Elasticsearch index
  - `update-index`: Bulk index documents
  - `search`: Query indexed documents
  - `stats`: Display index statistics
  - `reindex`: Recreate index from scratch
  - `watch`: Monitor filesystem changes

### Processing Layer
- **DocumentExtractor**: Extracts text content and metadata using Apache Tika
- **FileSystemWatcher**: Monitors directories for CREATE/MODIFY/DELETE events

### Storage Layer
- **ElasticsearchService**: Main service coordinating all ES operations
- **IndexManager**: Handles index creation, deletion, mappings
- **SearchExecutor**: Executes queries and retrieves statistics

### Data Store
- **Elasticsearch**: Distributed search engine storing inverted index
