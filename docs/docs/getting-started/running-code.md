---
sidebar_position: 3
---

# Running Lectern Code

Learn different ways to run your Lectern programs.

## Using Gradle

The standard way to run Lectern programs:

```bash
./gradlew run --args="filename.lectern"
```

## Running Multiple Files

You can pass multiple files:

```bash
./gradlew run --args="mlectern.lectern utils.lectern"
```

## Common Run Options

### Debug Mode

For debugging output:

```bash
./gradlew run --args="--debug filename.lectern"
```

### Help

View available options:

```bash
./gradlew run --args="--help"
```

## REPL (Coming Soon)

Lectern will soon support an interactive REPL:

```bash
./gradlew run --args="--repl"
```

## Next Steps

Now that you can run code, learn about [Variables](/docs/basics/variables) and [Data Types](/docs/basics/data-types).
