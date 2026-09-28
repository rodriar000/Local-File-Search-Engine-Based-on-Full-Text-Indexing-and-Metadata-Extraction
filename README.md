# Design and Implementation of a Local Full-Text Search System for Heterogeneous Documents

**Author:** Rodrigo Allende Rial
**Degree:** Bachelor Degree in Computer Engineering
**Context:** Bachelor Thesis (Trabajo de Fin de Grado)
**Repository:** Source Code and Experimental Artifacts

---

## 1. Project Abstract

This repository contains the source code, documentation, and experimental scripts developed for the Bachelor Thesis titled "Design and Implementation of a Local Full-Text Search System for Heterogeneous Documents".

The primary objective of this project is to implement `filesearch`, a high-performance, privacy-preserving desktop search engine capable of indexing and retrieving content from heterogeneous file formats (PDF, DOCX, TXT, HTML, JSON) stored on the local filesystem. The system is architected as a modular application comprising a Java-based backend CLI for orchestration and indexing, and a React-based frontend for interactive graphical retrieval.

The core retrieval logic leverages an inverted index data structure implemented via Elasticsearch, with document parsing and metadata extraction handled by Apache Tika. This design ensures that all data processing occurs on-premise, guaranteeing data sovereignty and eliminating dependency on external cloud services.

## 2. System Architecture

The project is organized into three primary modules:

*   **`tfg-filesearch` (Backend/CLI)**: A Java application responsible for recursive directory crawling, content extraction (Apache Tika), and interaction with the Elasticsearch cluster. It exposes a Command Line Interface (picocli) for administrative tasks and automation.
*   **`tfg-filesearch-gui` (Frontend)**: A modern web-based graphical interface built with React, TypeScript, and Vite. It communicates directly with the search index to provide low-latency query results, highlighting, and relevance scoring.
*   **`scripts`**: A collection of Python automation scripts used to conduct the experimental evaluation (indexing throughput, search latency benchmarks) described in the thesis report.

## 3. Prerequisites

To build and execute the system, the following runtime environments are required:

*   **Java Development Kit (JDK)**: Version 17 or higher.
*   **Node.js**: Version 20.19 or higher.
*   **Maven**: Version 3.8 or higher (for backend build).
*   **Python**: Version 3.9 or higher (for benchmarking scripts).
*   **Elasticsearch**: Version 8.x, running on this computer (`localhost:9200`). The desktop app refuses any address that is not on this computer.

## 4. Installation and Build

### 4.1 Backend Compilation
Navigate to the backend directory and build the executable JAR artifact:

```bash
cd tfg-filesearch
mvn clean package
```

The resulting artifact will be located at `target/filesearch-1.0.0-jar-with-dependencies.jar`.

### 4.2 Frontend Setup
Navigate to the frontend directory and install dependencies:

```bash
cd tfg-filesearch-gui
npm ci
```

Quality checks for the frontend:

```bash
npm run typecheck   # TypeScript
npm test            # unit tests (Vitest)
npm run build:web   # production bundle without packaging
```

## 5. Execution Guide

### 5.1 Infrastructure Setup
Ensure the local Elasticsearch node is active and accessible:

```bash
curl -X GET "localhost:9200/"
```

### 5.2 CLI Operations (Indexing)
To create the index and process a target dataset (e.g., `~/Documents`):

```bash
java -jar tfg-filesearch/target/filesearch-1.0.0-jar-with-dependencies.jar update-index ~/Documents
```

To verify system health and connectivity:

```bash
java -jar tfg-filesearch/target/filesearch-1.0.0-jar-with-dependencies.jar doctor
```

### 5.3 GUI Execution (Search)
Launch the desktop app in development mode:

```bash
cd tfg-filesearch-gui
npm run dev
```

This opens the Electron window. From **Settings** you can choose a documents folder and index it (requires the backend JAR from step 4.1 and Java on the `PATH`). The browser preview at `http://localhost:5173` also works for searching during development; it reaches Elasticsearch through the Vite proxy (`VITE_DEV_ELASTIC_URL` in `.env`, see `.env.example`).

## 6. Experimental Evaluation

This repository includes the complete suite of tools required to reproduce the experimental results presented in Chapter 6 of the thesis.

### Reproducibility Workflow
1.  **Generate Test Data**: Execute `create-demo-data.sh` to create a controlled validation dataset.
2.  **Run Benchmarks**: Use the Python orchestrator to execute indexing and search performance tests.

```bash
python3 scripts/dataset_runner.py --config config/s1_validation.yaml
```

Outputs are generated in the `reports/` directory in JSON format.

## 7. License

This software is proprietary. See [LICENSE](LICENSE). All rights reserved.

*Copyright (c) 2026 Rodrigo Allende Rial*
