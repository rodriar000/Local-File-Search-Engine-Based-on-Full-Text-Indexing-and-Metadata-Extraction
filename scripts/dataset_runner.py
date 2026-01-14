import argparse
import yaml
import os
import subprocess
import sys

def run_command(cmd):
    print(f"\n> {' '.join(cmd)}")
    subprocess.check_call(cmd)

def main():
    parser = argparse.ArgumentParser(description="Dataset Evaluation Runner")
    parser.add_argument("--config", required=True, help="Path to dataset.yaml")
    args = parser.parse_args()
    
    with open(args.config, 'r') as f:
        config = yaml.safe_load(f)
        
    project_root = os.getcwd() # Assume running from root
    
    dataset_path = config.get('dataset_path')
    queries_file = config.get('queries_file')
    jar_path = config.get('jar_path', 'tfg-filesearch/target/filesearch-1.0.0-jar-with-dependencies.jar')
    
    print(f"=== Starting Evaluation: {config.get('name', 'Unnamed')} ===")
    
    # 1. Indexing
    if config.get('run_indexing', True):
        print("\n--- Phase 1: Indexing ---")
        run_command([
            sys.executable, "scripts/benchmark_indexing.py",
            "--jar", jar_path,
            "--dataset", dataset_path,
            "--out", "reports/indexing"
        ])
        
    # 2. Search
    if config.get('run_search', True):
        print("\n--- Phase 2: Search Benchmark ---")
        run_command([
            sys.executable, "scripts/benchmark_search.py",
            "--queries", queries_file,
            "--out", "reports/benchmarks"
        ])
        
    print("\n=== Evaluation Complete ===")

if __name__ == "__main__":
    main()
