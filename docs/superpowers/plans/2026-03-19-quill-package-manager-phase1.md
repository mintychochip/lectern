# Quill Package Manager — Phase 1: Rename + Scaffolding

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Verify the project rename from `lectern` → `quill` is complete, then scaffold the `lt/` subproject and self-hosting `quill.toml`.

**Architecture:** The project has already been partially renamed. This plan verifies what's done and completes the remaining scaffolding.

**Tech Stack:** Kotlin 2.2.21, JVM 21, Gradle, kotlin.test

---

## Pre-Flight: Verify Current State

- [ ] **Step 1: Check Kotlin source package declarations**

Run: `grep -r "^package org\." src/main/kotlin/org/ | head -3`
Expected: `package org.quill` (not `org.lectern`)

- [ ] **Step 2: Check Gradle settings**

Run: `cat settings.gradle.kts`
Expected: `rootProject.name = "quill"`, includes `quill-intellij` and `quill-vscode`

- [ ] **Step 3: Check directory names**

Run: `ls -d quill-* 2>/dev/null`
Expected: `quill-intellij/` and `quill-vscode/` (not `lectern-*`)

- [ ] **Step 4: Check for remaining .lec/.ain files**

Run: `find . -name "*.lec" -not -path "./.idea/*" -not -path "./build/*" -not -path "./.worktrees/*" | wc -l`
Expected: 0 (all should be `.quill`)

- [ ] **Step 5: Check for remaining lectern references in source**

Run: `grep -r "org\.lectern" src/`
Expected: no output

**Result:** If all checks pass, the rename is complete. Proceed to remaining scaffolding.

---

## Chunk 1: Create Self-Hosting `quill.toml`

**Files:**
- Create: `quill.toml` (at project root)

- [ ] **Step 1: Create quill.toml for the quill project itself**

Write to `quill.toml`:
```toml
[package]
name = "quill"
version = "0.1.0"
main = "main"
description = "The Quill language compiler and VM"

[dependencies]
```

- [ ] **Step 2: Verify file contents**

Run: `cat quill.toml`
Expected: shows the manifest above

---

## Chunk 2: Scaffold `lt/` Gradle Subproject

**Files:**
- Create: `lt/build.gradle.kts`
- Create: `lt/settings.gradle.kts`
- Create: `lt/src/main/kotlin/org/quill/lt/Main.kt`
- Modify: `settings.gradle.kts`

- [ ] **Step 1: Create `lt/` directory structure**

Run: `mkdir -p lt/src/main/kotlin/org/quill/lt`

- [ ] **Step 2: Create `lt/build.gradle.kts`**

Write to `lt/build.gradle.kts`:
```kotlin
plugins {
    kotlin("jvm") version "2.2.21"
    application
}

group = "org.quill"
version = "0.1.0"

kotlin {
    jvmToolchain(21)
}

application {
    mainClass = "org.quill.lt.MainKt"
}

repositories {
    mavenCentral()
}

dependencies {
    // Quills's standard library
    implementation(project(":"))
}
```

- [ ] **Step 3: Create `lt/settings.gradle.kts`**

Write to `lt/settings.gradle.kts`:
```kotlin
pluginManagement {
    repositories {
        mavenCentral()
    }
}

rootProject.name = "lt"
```

- [ ] **Step 4: Create `lt/src/main/kotlin/org/quill/lt/Main.kt`**

Write to `lt/src/main/kotlin/org/quill/lt/Main.kt`:
```kotlin
package org.quill.lt

fun main(args: Array<String>) {
    if (args.isEmpty()) {
        println("lt - Quill package manager v0.1.0")
        println("Usage: lt <command> [options]")
        println("")
        println("Commands:")
        println("  lt new <name>     Create a new package")
        println("  lt add <pkg>      Install a package")
        println("  lt install        Install all dependencies")
        println("  lt build          Build the package")
        println("  lt run [file]     Run the package")
        println("  lt clean          Remove build artifacts")
        return
    }

    val command = args[0]
    when (command) {
        "new" -> println("lt new: not yet implemented")
        "add" -> println("lt add: not yet implemented")
        "install" -> println("lt install: not yet implemented")
        "build" -> println("lt build: not yet implemented")
        "run" -> println("lt run: not yet implemented")
        "clean" -> println("lt clean: not yet implemented")
        else -> println("Unknown command: $command")
    }
}
```

- [ ] **Step 5: Add `lt/` to root `settings.gradle.kts`**

Read `settings.gradle.kts`, then add `include("lt")`:
```kotlin
rootProject.name = "quill"
include("quill-intellij")
include("quill-vscode")
include("lt")
```

- [ ] **Step 6: Verify `lt/` compiles**

Run: `./gradlew :lt:compileKotlin 2>&1 | tail -10`
Expected: BUILD SUCCESSFUL

- [ ] **Step 7: Test `lt/` runs and shows help**

Run: `./gradlew :lt:run --args="" 2>&1 | tail -10`
Expected: shows "lt - Quill package manager v0.1.0" and command list

---

## Chunk 3: Final Verification

- [ ] **Step 1: Run full test suite**

Run: `./gradlew test 2>&1 | tail -15`
Expected: BUILD SUCCESSFUL, all tests pass

- [ ] **Step 2: Verify project structure**

Run: `ls -la quill.toml lt/`
Expected: both exist

- [ ] **Step 3: Commit Phase 1**

```bash
git add quill.toml lt/
git status
git commit -m "$(cat <<'EOF'
feat: scaffold lt/ package manager and self-hosting quill.toml

- Add quill.toml (self-hosting manifest for the quill project itself)
- Add lt/ Gradle subproject for the package manager CLI tool
- lt/ currently stubs all commands (new, add, install, build, run, clean)

Co-Authored-By: Claude Opus 4.6 <noreply@anthropic.com>
EOF
)"
```

---

## Summary

After Phase 1:
- `lt/` subproject exists and runs (`./gradlew :lt:run`)
- `quill.toml` exists at project root (self-hosting)
- Rename from `lectern` → `quill` was already complete (verified)
- Next: Phase 2 implements actual `lt` commands
