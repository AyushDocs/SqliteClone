# AeroSQL Learning Log

This document tracks the advanced architectural and low-level binary concepts implemented during the development of the AeroSQL Database Engine.

## 1. Low-Level Binary Handling (Project Panama)

### Sign Extension & Masking (`& 0xFF`)
In Java, all primitives are signed. When a 8-bit `byte` (e.g., `0xFF` or -1) is converted to a 32-bit `int`, Java performs **sign extension**, resulting in `0xFFFFFFFF` (-1). 
*   **The Fix:** Using `& 0xFF` masks the upper bits, forcing Java to treat the byte as an unsigned value (`255`). This is essential for reading binary offsets and lengths accurately.

### Memory Alignment (`withByteAlignment(1)`)
CPUs prefer reading data on "natural boundaries" (e.g., a 4-byte `int` at a multiple of 4). However, binary formats like SQLite pack data tightly to save space, often starting fields at "unaligned" offsets.
*   **The Fix:** Using `MemoryLayout.withByteAlignment(1)` tells the JVM to allow unaligned access, preventing `IllegalArgumentException` while maintaining safe memory access via the Foreign Function & Memory API.

### Endianness (`_BE`)
Files written on one CPU (e.g., Big Endian) must be readable on another (e.g., Little Endian). SQLite uses **Big Endian** (Most Significant Byte first).
*   **The Fix:** Using `ValueLayout.JAVA_INT_BE` ensures that the engine correctly reconstructs integers regardless of the host machine's native architecture.

## 2. Memory Management & Performance

### Zero-Copy Memory Mapping (`MMap`)
Traditional Java I/O requires copying data from the disk/kernel into a `byte[]` on the JVM heap. For large databases, this causes high GC pressure and redundant memory movement.
*   **The Fix:** Using Panama's `FileChannel.map()` to create a `MemorySegment` directly from the OS page cache.
*   **The Result:** 
    *   **Zero-Copy:** No intermediate byte array allocations.
    *   **Performance:** Typically **40% - 200% faster** for large file scans compared to standard `read()` calls.
    *   **Scale:** Can handle multi-gigabyte files without increasing JVM heap consumption.

## 3. Architectural Principles

### Open-Closed Principle (OCP) via Pluggable Discovery
To avoid modifying the core `CommandRegistry` every time a new command is added, we implemented a **Pluggable Architecture** using the `java.util.ServiceLoader` API.
*   **The Result:** The registry is "Closed for Modification" (we never touch the code) but "Open for Extension" (new commands are discovered automatically via `META-INF/services`).

### Metadata-Driven Design
Hardcoding offsets for file headers makes code brittle. 
*   **The Fix:** We moved the SQLite header specification into an external file (`sqlite_header.spec`).
*   **The Result:** The `DynamicLayoutFactory` parses this file at runtime to build a `MemoryLayout`. This decouples the **file format** from the **parsing logic**, allowing the engine to adapt to format changes without recompilation.

### Static Factory & Single Initialization
Instead of using a complex constructor that performs I/O, we moved initialization to a `static Database open(path)` method.
*   **The Practical Win:** This ensured **Single Initialization**—the database is either fully opened and validated or not created at all. It prevents "half-initialized" objects and practically applies the **Factory Pattern** to keep construction clean and predictable.

### Resource Lifecycle Management (`AutoCloseable`)
Memory-mapped files are powerful but can lead to file locks and memory leaks if not unmapped correctly.
*   **The Fix:** We implemented the `AutoCloseable` interface and used **Project Panama's `Arena`** to manage memory boundaries.
*   **The Result:** Using `try-with-resources` guarantees that the database file is unmapped and the OS resources are released immediately after the command executes, even if an exception occurs.

### Lazy Loading & Scalability
A naive database engine reads the entire schema into memory at startup. This fails for databases with thousands of tables.
*   **The Fix:** We moved schema parsing into the `.tables` command itself, using an on-demand **B-Tree Traversal** engine.
*   **The Result:** 
    *   **Instant Startup:** The `Database.open()` call is now $O(1)$ regardless of database size.
    *   **Scalability:** The engine only "touches" the pages it needs to answer a specific query, making it highly efficient for massive datasets.

### Session Pattern (Connection-style APIs)
Instead of forcing the application to manage command contexts, we moved execution into the `Database` class itself via `db.execute(command)`.
*   **The Practical Win:** This mimics professional SQL drivers. The `Database` object acts as a **Session**, encapsulating all the state needed for a command to run, leading to cleaner and more intuitive usage in the main application loop.

## 4. Modern Java 21 Features
*   **Records:** Used for immutable data carriers like `CommandContext` and `CommandResult`.
*   **Sealed Interfaces:** Used in `HeaderValue` to create a strictly defined type hierarchy.
    *   **Constraint:** Limits which classes can implement an interface, creating a closed domain model.
    *   **Exhaustiveness:** Enables the compiler to verify that all possible types are handled in `switch` expressions, eliminating the need for brittle `default` cases.
*   **Pattern Matching for Switch:** Used in the `DatabaseHeaderParser` to handle heterogeneous layouts cleanly and without casting.

## 5. Lessons from the "Endianness Trap"
During the implementation of `DatabaseHeaderParser`, we encountered a critical bug where `pageSize` was read as **16** instead of **4096**.
*   **The Cause:** Relying on `ValueLayout.JAVA_SHORT` (which defaults to Little-Endian on x86) to read Big-Endian SQLite files.
*   **The Fix:** Explicitly using `.withOrder(ByteOrder.BIG_ENDIAN)` for all binary parsing.
*   **The Learning:** When working at the hardware level (Project Panama), the "defaults" of the host machine can be your biggest enemy. Always define explicit Byte Orders.

## 6. CLI Aesthetics & ANSI Magic
To make the engine feel like a professional tool, we implemented a custom `ConsoleTable` using **ANSI Escape Sequences**.
*   **What they are:** Non-printable char sequences (e.g., `\u001B[36m`) that control terminal behavior.
*   **How they work:**
    *   `\u001B[`: The "Escape" signal.
    *   `36m`: Instruction to change foreground color to Cyan.
    *   `0m`: The "Reset" code to return to standard formatting.
*   **Box Drawing:** We used Unicode characters (`┌`, `─`, `┼`) instead of simple dashes. This significantly improves readability and creates a "premium" feel.
*   **Regex Metadata:** We used `java.util.regex` to extract column names from the `CREATE TABLE` SQL strings stored in the schema, allowing for dynamic headers.
