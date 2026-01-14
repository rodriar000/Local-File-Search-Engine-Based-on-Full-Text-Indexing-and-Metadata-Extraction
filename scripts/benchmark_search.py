import argparse
import requests
import json
import time
import statistics
import os
from datetime import datetime

def run_benchmark(es_url, index_name, queries_file, output_dir):
    print(f"Starting Search Benchmark against {es_url}/{index_name}...")
    
    # Ensure output dir exists
    os.makedirs(output_dir, exist_ok=True)
    
    # Load queries
    with open(queries_file, 'r') as f:
        queries = [line.strip() for line in f if line.strip()]
        
    print(f"Loaded {len(queries)} queries.")
    
    results = []
    latencies = []
    
    start_time = time.time()
    
    for q in queries:
        payload = {
            "query": {
                "multi_match": {
                    "query": q,
                    "fields": ["filename^2", "title^1.5", "content", "author"],
                    "fuzziness": "AUTO"
                }
            }
        }
        
        req_start = time.time()
        try:
            resp = requests.post(f"{es_url}/{index_name}/_search", json=payload)
            req_end = time.time()
            
            latency_ms = (req_end - req_start) * 1000
            
            if resp.status_code == 200:
                data = resp.json()
                took = data['took'] # ES internal time
                hits = data['hits']['total']['value']
                
                latencies.append(latency_ms)
                results.append({
                    "query": q,
                    "latency_ms": latency_ms,
                    "es_took_ms": took,
                    "hits": hits,
                    "status": "ok"
                })
            else:
                print(f"Error querying '{q}': {resp.status_code}")
                results.append({"query": q, "status": "error", "code": resp.status_code})
                
        except Exception as e:
            print(f"Exception querying '{q}': {e}")
            results.append({"query": q, "status": "exception", "error": str(e)})

    total_time = time.time() - start_time
    throughput = len(queries) / total_time if total_time > 0 else 0
    
    # Calculate stats
    if latencies:
        p50 = statistics.median(latencies)
        p95 = statistics.quantiles(latencies, n=20)[18] if len(latencies) >= 20 else max(latencies)
        p99 = statistics.quantiles(latencies, n=100)[98] if len(latencies) >= 100 else max(latencies)
        avg = statistics.mean(latencies)
    else:
        p50 = p95 = p99 = avg = 0

    metrics = {
        "timestamp": datetime.now().isoformat(),
        "total_queries": len(queries),
        "total_time_sec": total_time,
        "throughput_qps": throughput,
        "latency_p50_ms": p50,
        "latency_p95_ms": p95,
        "latency_p99_ms": p99,
        "latency_avg_ms": avg
    }
    
    print("\nBenchmark Results:")
    print(json.dumps(metrics, indent=2))
    
    # Save Reports
    ts = datetime.now().strftime("%Y%m%d_%H%M%S")
    json_path = os.path.join(output_dir, f"benchmark_{ts}.json")
    csv_path = os.path.join(output_dir, f"benchmark_{ts}.csv")
    
    with open(json_path, 'w') as f:
        json.dump({"metrics": metrics, "details": results}, f, indent=2)
        
    print(f"\nReport saved to: {json_path}")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="Elasticsearch Search Benchmark")
    parser.add_argument("--url", default="http://localhost:9200", help="ES URL")
    parser.add_argument("--index", default="filesearch", help="Index Name")
    parser.add_argument("--queries", required=True, help="Path to queries file")
    parser.add_argument("--out", default="reports/benchmarks", help="Output directory")
    
    args = parser.parse_args()
    run_benchmark(args.url, args.index, args.queries, args.out)
