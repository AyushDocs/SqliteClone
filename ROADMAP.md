# Project Roadmap: AeroSQL Engine

This roadmap outlines the evolution of the AeroSQL project into a high-performance, modern database engine leveraging cutting-edge JVM features and AI capabilities.

## 🚀 Phase 1: High-Performance Core (Project Panama)
*Transitioning from legacy I/O to zero-copy memory management.*

- [ ] **Task 1.1: Environment Setup**  
  Update `pom.xml` and `Makefile` to support Java 21+ features and enable native access flags (`--enable-native-access`).
- [ ] **Task 1.2: Memory-Mapped Storage**  
  Replace `FileInputStream` in `Database.java` with `java.lang.foreign.MemorySegment` and `FileChannel.map()`.
- [ ] **Task 1.3: Zero-Copy Reader Refactor**  
  Rewrite `SegmentReader` to use `MemorySegment` and `ValueLayout` for direct, hardware-level data access without heap copies.
- [ ] **Task 1.4: Performance Benchmarking**  
  Implement a basic benchmark suite to compare legacy I/O vs. Panama-based reads.

## 🧵 Phase 2: Massive Concurrency (Project Loom)
*Scaling to thousands of concurrent queries with minimal overhead.*

- [ ] **Task 2.1: Virtual Thread Scheduler**  
  Implement a query execution coordinator using `Executors.newVirtualThreadPerTaskExecutor()`.
- [ ] **Task 2.2: Thread-Safe Page Caching**  
  Implement a non-blocking Page Cache that facilitates sharing mapped memory segments across thousands of virtual threads.
- [ ] **Task 2.3: Structured Concurrency**  
  Utilize `StructuredTaskScope` to handle complex sub-tasks (like index scans and data fetches) in parallel.

## 🧠 Phase 3: AI-Ready Extensions (Vector Search)
*Adding modern search capabilities while maintaining SQLite compatibility.*

- [ ] **Task 3.1: Vector Data Type Support**  
  Define a protocol for storing float-array embeddings within SQLite's flexible type system.
- [ ] **Task 3.2: Stealth HNSW Indexing**  
  Implement a Hierarchical Navigable Small World (HNSW) algorithm that stores index pointers in the "Reserved Space" of standard SQLite pages.
- [ ] **Task 3.3: Semantic Query Parser**  
  Add support for a `vector_search()` function that performs nearest-neighbor lookups alongside standard SQL.

## 🛠 Phase 4: Compatibility & Tooling
*Ensuring the "Drop-in Replacement" promise.*

- [ ] **Task 4.1: Backward Compatibility Validation**  
  Automated testing to ensure standard `sqlite3` CLI can still read/write files modified by LiteModern.
- [ ] **Task 4.2: Migration Utility**  
  Build a tool to "vacuum" and optimize existing SQLite files specifically for Panama memory-mapping layouts.
- [ ] **Task 4.3: CLI Dashboard**  
  Create a modern, terminal-based dashboard (TUI) to monitor engine performance, cache hits, and virtual thread telemetry.

---
*This roadmap is designed to demonstrate deep systems-level Java expertise and awareness of modern AI infrastructure trends.*
