---
sidebar_position: 1
---

# Installation

Learn how to install and set up Lectern on your system.

## Prerequisites

- **Java Runtime Environment (JRE) 11+** - Lectern runs on the JVM
- **Gradle** (optional) - For building from source

## Building from Source

### 1. Clone the Repository

```bash
git clone https://github.com/your-github-username/lectern.git
cd lectern
```

### 2. Build with Gradle

```bash
./gradlew build
```

### 3. Run Lectern

```bash
./gradlew run --args="your-file.lec"
```

## Development Setup

For development, you can use:

- **IntelliJ IDEA** - Recommended for Kotlin development
- **VS Code** - With Kotlin extensions

## Verifying Installation

Create a simple test file:

```lec title="hello.lec"
print("Hello, Lectern!")
```

Run it:

```bash
./gradlew run --args="hello.lec"
```

You should see:

```
Hello, Lectern!
```

## Next Steps

Now that you have Lectern installed, let's write your [First Program](/docs/getting-started/first-program)!
