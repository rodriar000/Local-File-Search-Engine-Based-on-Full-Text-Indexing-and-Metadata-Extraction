# Search Flow Diagram

```mermaid
sequenceDiagram
    participant User
    participant CLI
    participant SearchExecutor
    participant Elasticsearch

    User->>CLI: search "query string" --size 10
    CLI->>SearchExecutor: search(queryString, size)
    
    SearchExecutor->>SearchExecutor: Build Query DSL<br/>query_string query
    SearchExecutor->>Elasticsearch: Search API request
    
    Elasticsearch->>Elasticsearch: Parse query
    Elasticsearch->>Elasticsearch: Score documents (BM25)
    Elasticsearch->>Elasticsearch: Rank results
    
    Elasticsearch-->>SearchExecutor: Search response<br/>(hits, scores, took)
    
    SearchExecutor->>SearchExecutor: Convert ES Map to Document
    SearchExecutor->>SearchExecutor: Build SearchResult object
    
    SearchExecutor-->>CLI: SearchResult
    
    alt Output format: text
        CLI->>CLI: Format as table
        CLI-->>User: Display results<br/>with scores, paths, previews
    else Output format: json
        CLI->>CLI: Serialize to JSON
        CLI-->>User: JSON array of results
    end
```

## Flow Description

1. **Query Input**: User provides search query using query_string syntax
2. **Query Building**: SearchExecutor constructs ES Query DSL
3. **Search Execution**: Query sent to Elasticsearch _search endpoint
4. **Scoring**: ES applies BM25 ranking algorithm
5. **Response Processing**: SearchExecutor converts raw response to typed objects
6. **Output Formatting**: CLI formats results based on user preference (text/JSON)
7. **Result Display**: User sees ranked results with relevance scores
