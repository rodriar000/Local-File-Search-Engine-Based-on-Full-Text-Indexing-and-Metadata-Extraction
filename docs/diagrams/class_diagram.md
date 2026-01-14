# Class Diagram

```mermaid
classDiagram
    class FileSearchCLI {
        +main(String[] args)
        +run()
    }
    
    class CreateIndexCommand {
        -String indexName
        +call() Integer
    }
    
    class UpdateIndexCommand {
        -String path
        -boolean recursive
        +call() Integer
    }
    
    class SearchCommand {
        -String query
        -int size
        -String outputFormat
        +call() Integer
    }
    
    class Document {
        -String path
        -String filename
        -String extension
        -long size
        -Instant createdAt
        -String content
        -String author
        -String title
        +getters/setters()
    }
    
    class DocumentExtractor {
        -Tika tika
        -Parser parser
        +extractDocument(Path) Document
        +isSupported(String) boolean
    }
    
    class ElasticsearchService {
        -ElasticsearchClient client
        -Config config
        -IndexManager indexManager
        -SearchExecutor searchExecutor
        +indexDocument(Document)
        +bulkIndexDocuments(List~Document~) int
        +deleteDocument(String)
    }
    
    class IndexManager {
        -ElasticsearchClient client
        +createIndex(String)
        +deleteIndex(String)
        +indexExists(String) boolean
    }
    
    class SearchExecutor {
        -ElasticsearchClient client
        +search(String, int) SearchResult
        +getIndexStats() IndexStats
    }
    
    class FileSystemWatcher {
        -WatchService watchService
        -ElasticsearchService esService
        -DocumentExtractor extractor
        +start()
        -handleFileEvent(Kind, Path)
    }
    
    class SearchResult {
        -long totalHits
        -long tookMs
        -List~DocumentHit~ hits
    }
    
    class Config {
        -ElasticsearchConfig elasticsearch
        -IndexingConfig indexing
        -SearchConfig search
        -WatchConfig watch
    }
    
    FileSearchCLI --> CreateIndexCommand
    FileSearchCLI --> UpdateIndexCommand
    FileSearchCLI --> SearchCommand
    
    UpdateIndexCommand --> DocumentExtractor
    UpdateIndexCommand --> ElasticsearchService
    
    SearchCommand --> ElasticsearchService
    
    ElasticsearchService --> IndexManager
    ElasticsearchService --> SearchExecutor
    ElasticsearchService --> Document
    
    DocumentExtractor --> Document
    
    SearchExecutor --> SearchResult
    SearchExecutor --> Document
    
    FileSystemWatcher --> DocumentExtractor
    FileSystemWatcher --> ElasticsearchService
    
    ElasticsearchService --> Config
    UpdateIndexCommand --> Config
```

## Class Responsibilities

### CLI Layer
- **FileSearchCLI**: Entry point, routes to subcommands
- **CreateIndexCommand**, **UpdateIndexCommand**, **SearchCommand**: Command implementations

### Model Layer
- **Document**: POJO representing indexed file with metadata
- **SearchResult**: Search query results with hits and scores
- **Config**: Configuration loaded from YAML

### Processing Layer
- **DocumentExtractor**: Tika integration for content extraction
- **FileSystemWatcher**: Monitors filesystem changes with Java NIO

### Storage Layer
- **ElasticsearchService**: Main service facade
- **IndexManager**: Index lifecycle management
- **SearchExecutor**: Query execution and statistics
