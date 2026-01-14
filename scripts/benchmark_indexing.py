import argparse
import subprocess
import time
import json
import os
import requests
from datetime import datetime

def get_index_stats(es_url, index_name):
    try:
        resp = requests.get(f"{es_url}/{index_name}/_stats")
        if resp.status_code == 200:
            data = resp.json()
            primaries = data['_all']['primaries']
            return {
                "docs_count": primaries['docs']['count'],
                "store_size_bytes": primaries['store']['size_in_bytes']
            }
    except:
        pass
    return {"docs_count": 0, "store_size_bytes": 0}

def run_indexing_benchmark(jar_path, dataset_path, es_url, index_name, output_dir):
    print(f"Starting Indexing Benchmark of {dataset_path}...")
    
    os.makedirs(output_dir, exist_ok=True)
    
    # Initial stats
    initial_stats = get_index_stats(es_url, index_name)
    print(f"Initial Index Stats: {initial_stats}")
    
    start_time = time.time()
    
    # Run Java CLI
    # Expected command: java -jar filesearch.jar update-index <path>
    cmd = ["java", "-jar", jar_path, "update-index", dataset_path]
    
    try:
        process = subprocess.run(cmd, capture_output=True, text=True)
        rc = process.returncode
        stdout = process.stdout
        stderr = process.stderr
    except Exception as e:
        print(f"Failed to execute Java CLI: {e}")
        return
        
    end_time = time.time()
    duration = end_time - start_time
    
    # Final stats
    final_stats = get_index_stats(es_url, index_name)
    print(f"Final Index Stats: {final_stats}")
    
    docs_added = final_stats['docs_count'] - initial_stats['docs_count']
    size_added = final_stats['store_size_bytes'] - initial_stats['store_size_bytes']
    
    docs_per_sec = docs_added / duration if duration > 0 else 0
    
    metrics = {
        "timestamp": datetime.now().isoformat(),
        "dataset": dataset_path,
        "duration_sec": duration,
        "docs_added": docs_added,
        "size_added_bytes": size_added,
        "throughput_docs_per_sec": docs_per_sec,
        "cli_exit_code": rc
    }
    
    print("\nIndexing Metrics:")
    print(json.dumps(metrics, indent=2))
    
    if rc != 0:
        print("\nCLI Error Output:")
        print(stderr)
        
    # Save Report
    ts = datetime.now().strftime("%Y%m%d_%H%M%S")
    report_path = os.path.join(output_dir, f"indexing_{ts}.json")
    
    with open(report_path, 'w') as f:
        json.dump({
            "metrics": metrics,
            "stdout": stdout,
            "stderr": stderr
        }, f, indent=2)
        
    print(f"\nReport saved to: {report_path}")

if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="Indexing Benchmark Wrapper")
    parser.add_argument("--jar", required=True, help="Path to filesearch.jar")
    parser.add_argument("--dataset", required=True, help="Path to dataset directory")
    parser.add_argument("--url", default="http://localhost:9200", help="ES URL")
    parser.add_argument("--index", default="filesearch", help="Index Name")
    parser.add_argument("--out", default="reports/indexing", help="Output directory")
    
    args = parser.parse_args()
    run_indexing_benchmark(args.jar, args.dataset, args.url, args.index, args.out)
