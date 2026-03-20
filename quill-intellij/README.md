# quill IntelliJ Plugin

An IntelliJ IDEA plugin providing language support for the [quill](https://quill.org) programming language.

## Features

- **Syntax Highlighting**: Full syntax highlighting for quill source files
- **File Type Recognition**: Automatic recognition of `.quill` files
- **Code Completion**: Keyword and built-in function completion
- **Structure View**: Navigate through classes, functions, and variables
- **Brace Matching**: Automatic matching of parentheses, brackets, and braces
- **Comment Handling**: Line (`//`) and block (`/* */`) comment support
- **Code Formatting**: Basic code formatting and indentation
- **File Templates**: Templates for creating new quill files

## Installation

### From JetBrains Marketplace
1. Open IntelliJ IDEA
2. Go to `Settings` → `Plugins` → `Marketplace`
3. Search for "quill"
4. Click `Install`

### From Source
1. Clone this repository
2. Open the project in IntelliJ IDEA
3. Run the `runIde` Gradle task to test the plugin
4. Run the `buildPlugin` task to create a distribution

## Building

```bash
./gradlew buildPlugin
```

The built plugin will be available in `build/distributions/`.

## Development

### Prerequisites
- JDK 17 or later
- IntelliJ IDEA 2023.3 or later

### Running in Development Mode
```bash
./gradlew runIde
```

### Running Tests
```bash
./gradlew test
```

## Language Overview

quill is a dynamically-typed programming language with the following features:

### Variables
```lec
let count = 0
const PI = 3.14159
let name = "quill"
```

### Functions
```lec
fn greet(name) {
    print("Hello, " + name + "!")
}

fn add(a, b = 10) {
    return a + b
}
```

### Classes
```lec
class Person {
    let name
    let age

    fn greet() {
        print("Hello, I'm " + this.name)
    }
}
```

### Control Flow
```lec
if (condition) {
    // ...
} else {
    // ...
}

for i in 0..10 {
    print(i)
}

while (count < 100) {
    count = count + 1
}
```

### String Interpolation
```lec
let name = "World"
let greeting = "Hello, ${name}!"
```

### Data Structures
```lec
let numbers = [1, 2, 3, 4, 5]
let person = { "name": "John", "age": 30 }
```

## License

This project is licensed under the MIT License.

## Contributing

Contributions are welcome! Please feel free to submit a Pull Request.
