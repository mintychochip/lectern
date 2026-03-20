# quill Language Support

A VS Code extension that provides syntax highlighting and linting for the quill programming language (`.quill` files).

## Features

- **Syntax Highlighting**: Full support for quill syntax including:
  - Keywords (`bool`, `int`, `float`, `double`, `string`, `let`, `const`, `fn`, etc.)
  - Control flow (`if`, `else`, `while`, `for`, `in`, `return`, `break`, `next`)
  - Operators (arithmetic, comparison, assignment)
  - Strings with `${...}` interpolation support
  - Comments (`//`)
  - Numbers (integers and floats)

- **Linting**: Real-time syntax validation using the Language Server Protocol
  - Detects invalid characters
  - Reports unterminated strings
  - Shows syntax errors as you type

- **Editor Features**:
  - Bracket matching and auto-closing
  - Comment toggle (Ctrl+/)
  - Auto-indentation

## Installation

1. Open VS Code
2. Press `Ctrl+Shift+X` to open Extensions
3. Search for "quill"
4. Click Install

### Manual Installation

1. Clone this repository
2. Run `npm install` in the extension directory
3. Run `npm run compile`
4. Press F5 in VS Code to launch Extension Development Host

## Usage

Create or open any file with the `.quill` extension to activate the language support.

### Example Code

```quill
// Variable declarations
let name = "quill";
const version = 1.0;

// Function definition
fn greet(person: string) -> string {
    return "Hello, ${person}!";
}

// Control flow
if (version > 0) {
    let message = greet(name);
    print(message);
}

// Loop
for (i in 0..10) {
    print(i);
}
```

## Configuration

This extension contributes the following settings:

- `quill.maxNumberOfProblems`: Maximum number of problems reported by the server (default: 1000)

## License

MIT
