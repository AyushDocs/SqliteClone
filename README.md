# SqliteClone

A lightweight Java implementation of a SQLite-compatible database engine, built with a focus on understanding low-level database internals like binary file parsing, B-Trees, and SQL execution.

![Java](https://img.shields.io/badge/Java-17-orange)
![Maven](https://img.shields.io/badge/Maven-3.x-blue)
![License](https://img.shields.io/badge/License-MIT-green)

## 🚀 Features

- **Binary Data Parsing**: Handles SQLite's binary format, including page headers and cell structures.
- **Variable Length Integers**: Robust handling of SQLite's `varint` encoding.
- **Metadata Management**: Reads table schemas and system information from SQLite's internal master table.
- **Flexible Configuration**: Uses XML-based configuration for header fields to avoid hardcoding.

## 🛠️ Prerequisites

- **Java 17** or higher
- **Maven 3.x**
- Standard Unix environment (Linux/macOS)

## 📦 Installation & Setup

1. Clone the repository:

   ```bash
   git clone https://github.com/AyushDocs/SqliteClone.git
   cd SqliteClone
   ```

2. Build the project:

   ```bash
   mvn clean package
   ```

## 🖥️ Usage

You can run the application using several methods:

### 1. Using Make (Recommended)
The project includes a `Makefile` for a cleaner CLI experience.

```bash
# Build and run
make run DB=sample.db CMD=".dbinfo"
```

### 2. Using Maven Exec Plugin
Run directly from the source code without manually handling JARS:

```bash
mvn exec:java -Dexec.args="sample.db .dbinfo"
```

### 3. Using the legacy script
```bash
./sqlite.sh [path_to_db] [command]
```

### Examples

**Get database information:**

```bash

./sqlite.sh sample.db ".dbinfo"
```

**List tables:**

```bash

./sqlite.sh sample.db ".tables"
```

## 🏗️ Project Structure

- `src/main/java`: Core logic for database parsing and SQL handling.
- `src/main/resources`: Configuration files and resources.
- `sqlite.sh`: Entry point script for running the application.
- `pom.xml`: Dependency management and build configuration.

## 🤝 Contributing

Contributions are what make the open-source community such an amazing place to learn, inspire, and create. Any contributions you make are **greatly appreciated**.

Please see [CONTRIBUTING.md](CONTRIBUTING.md) for more details.

## 🛡️ Security

If you find any security vulnerabilities, please refer to our [Security Policy](SECURITY.md).

## 📄 License

Distributed under the MIT License. See [LICENSE](LICENSE) for more information.

---
*Built as part of a deep dive into database internals.*
