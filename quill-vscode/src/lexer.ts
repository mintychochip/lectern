/**
 * quill Lexer - TypeScript port of the Kotlin lexer
 * Provides tokenization for syntax validation and diagnostics
 */

export enum TokenType {
    // Keywords - Types
    KW_BOOL,
    KW_INT,
    KW_FLOAT,
    KW_DOUBLE,
    KW_STRING,

    // Keywords - Literals
    KW_TRUE,
    KW_FALSE,
    KW_NULL,

    // Keywords - Declarations
    KW_LET,
    KW_CONST,
    KW_FN,

    // Keywords - Control Flow
    KW_IF,
    KW_ELSE,
    KW_WHILE,
    KW_FOR,
    KW_IN,
    KW_RETURN,
    KW_BREAK,
    KW_NEXT,

    // Keywords - Logical
    KW_AND,
    KW_OR,
    KW_NOT,

    // Keywords - Types/Structure
    KW_ENUM,
    KW_CLASS,
    KW_EXTENDS,
    KW_IMPORT,
    KW_FROM,
    KW_IS,

    // Literals and Identifiers
    IDENTIFIER,

    // Operators - Arithmetic
    PLUS,
    MINUS,
    STAR,
    SLASH,
    PERCENT,
    INCREMENT,
    DECREMENT,
    POW,

    // Operators - Comparison
    EQ_EQ,
    BANG_EQ,
    LT,
    GT,
    LTE,
    GTE,

    // Operators - Assignment
    ASSIGN,
    ARROW,
    ADD_EQUALS,
    SUB_EQUALS,
    MUL_EQUALS,
    DIV_EQUALS,
    MOD_EQUALS,

    // Punctuation
    L_BRACE,
    R_BRACE,
    L_PAREN,
    R_PAREN,
    L_SQUARE,
    R_SQUARE,
    BANG,
    COMMA,
    DOT,
    DOT_DOT,
    COLON,
    SEMICOLON,

    // String Interpolation
    INTERPOLATION_START,  // ${
    INTERPOLATION_END,    // } (closing interpolation)
    DOLLAR,               // $ (for error reporting)

    // Special
    EOF
}

export interface Token {
    type: TokenType;
    lexeme: string;
    line: number;
    column: number;
}

export interface LexerError {
    message: string;
    line: number;
    column: number;
    length: number;
}

// Tokens that can end a statement (for ASI)
const STATEMENT_ENDERS = new Set([
    TokenType.IDENTIFIER,
    TokenType.KW_INT, TokenType.KW_FLOAT, TokenType.KW_DOUBLE,
    TokenType.KW_STRING, TokenType.KW_TRUE, TokenType.KW_FALSE, TokenType.KW_NULL,
    TokenType.R_PAREN, TokenType.R_SQUARE,
    TokenType.KW_BREAK, TokenType.KW_NEXT
]);

const KEYWORDS = new Map<string, TokenType>([
    ["bool", TokenType.KW_BOOL],
    ["int", TokenType.KW_INT],
    ["float", TokenType.KW_FLOAT],
    ["double", TokenType.KW_DOUBLE],
    ["string", TokenType.KW_STRING],
    ["true", TokenType.KW_TRUE],
    ["false", TokenType.KW_FALSE],
    ["let", TokenType.KW_LET],
    ["const", TokenType.KW_CONST],
    ["if", TokenType.KW_IF],
    ["else", TokenType.KW_ELSE],
    ["while", TokenType.KW_WHILE],
    ["for", TokenType.KW_FOR],
    ["in", TokenType.KW_IN],
    ["fn", TokenType.KW_FN],
    ["return", TokenType.KW_RETURN],
    ["and", TokenType.KW_AND],
    ["or", TokenType.KW_OR],
    ["not", TokenType.KW_NOT],
    ["null", TokenType.KW_NULL],
    ["break", TokenType.KW_BREAK],
    ["next", TokenType.KW_NEXT],
    ["enum", TokenType.KW_ENUM],
    ["class", TokenType.KW_CLASS],
    ["extends", TokenType.KW_EXTENDS],
    ["import", TokenType.KW_IMPORT],
    ["from", TokenType.KW_FROM],
    ["is", TokenType.KW_IS]
]);

export class Lexer {
    private source: string;
    private tokens: Token[] = [];
    private errors: LexerError[] = [];
    private start = 0;
    private cursor = 0;
    private line = 1;
    private column = 0;
    private interpolationDepth = 0;

    constructor(source: string) {
        this.source = source;
    }

    tokenize(): { tokens: Token[], errors: LexerError[] } {
        if (this.tokens.length > 0) {
            return { tokens: this.tokens, errors: this.errors };
        }

        while (!this.isAtEnd()) {
            this.start = this.cursor;
            const c = this.advance();

            switch (c) {
                // Grouping & Punctuation
                case '(':
                    this.addToken(TokenType.L_PAREN);
                    break;
                case ')':
                    this.addToken(TokenType.R_PAREN);
                    break;
                case '{':
                    this.addToken(TokenType.L_BRACE);
                    break;
                case '}':
                    if (this.interpolationDepth > 0) {
                        this.handleInterpolationEnd();
                    } else {
                        this.addToken(TokenType.R_BRACE);
                    }
                    break;
                case '[':
                    this.addToken(TokenType.L_SQUARE);
                    break;
                case ']':
                    this.addToken(TokenType.R_SQUARE);
                    break;
                case ',':
                    this.addToken(TokenType.COMMA);
                    break;
                case '.':
                    if (this.match('.')) {
                        this.addToken(TokenType.DOT_DOT);
                    } else {
                        this.addToken(TokenType.DOT);
                    }
                    break;
                case ';':
                    this.addToken(TokenType.SEMICOLON);
                    break;
                case ':':
                    this.addToken(TokenType.COLON);
                    break;

                // Math & Operators
                case '+':
                    if (this.match('+')) {
                        this.addToken(TokenType.INCREMENT);
                    } else if (this.match('=')) {
                        this.addToken(TokenType.ADD_EQUALS);
                    } else {
                        this.addToken(TokenType.PLUS);
                    }
                    break;

                case '-':
                    if (this.match('-')) {
                        this.addToken(TokenType.DECREMENT);
                    } else if (this.match('=')) {
                        this.addToken(TokenType.SUB_EQUALS);
                    } else if (this.match('>')) {
                        this.addToken(TokenType.ARROW);
                    } else {
                        this.addToken(TokenType.MINUS);
                    }
                    break;

                case '*':
                    if (this.match('*')) {
                        this.addToken(TokenType.POW);
                    } else if (this.match('=')) {
                        this.addToken(TokenType.MUL_EQUALS);
                    } else {
                        this.addToken(TokenType.STAR);
                    }
                    break;

                case '/':
                    if (this.match('=')) {
                        this.addToken(TokenType.DIV_EQUALS);
                    } else if (this.match('/')) {
                        // Line comment - skip until newline
                        while (this.peek() !== '\n' && !this.isAtEnd()) {
                            this.advance();
                        }
                    } else {
                        this.addToken(TokenType.SLASH);
                    }
                    break;

                case '%':
                    if (this.match('=')) {
                        this.addToken(TokenType.MOD_EQUALS);
                    } else {
                        this.addToken(TokenType.PERCENT);
                    }
                    break;

                case '!':
                    if (this.match('=')) {
                        this.addToken(TokenType.BANG_EQ);
                    } else {
                        this.addToken(TokenType.BANG);
                    }
                    break;

                case '=':
                    if (this.match('=')) {
                        this.addToken(TokenType.EQ_EQ);
                    } else {
                        this.addToken(TokenType.ASSIGN);
                    }
                    break;

                case '<':
                    if (this.match('=')) {
                        this.addToken(TokenType.LTE);
                    } else {
                        this.addToken(TokenType.LT);
                    }
                    break;

                case '>':
                    if (this.match('=')) {
                        this.addToken(TokenType.GTE);
                    } else {
                        this.addToken(TokenType.GT);
                    }
                    break;

                // Whitespace
                case ' ':
                case '\r':
                case '\t':
                    // Ignore whitespace
                    break;

                case '\n':
                    // Automatic Semicolon Insertion (ASI)
                    if (this.tokens.length > 0 && STATEMENT_ENDERS.has(this.tokens[this.tokens.length - 1].type)) {
                        this.addToken(TokenType.SEMICOLON);
                    }
                    this.line++;
                    this.column = 0;
                    break;

                // String
                case '"':
                    this.string();
                    break;

                default:
                    if (this.isDigit(c)) {
                        this.number();
                    } else if (this.isAlpha(c) || c === '_') {
                        this.identifier();
                    } else {
                        this.errors.push({
                            message: `Unexpected character: '${c}'`,
                            line: this.line,
                            column: this.column - 1,
                            length: 1
                        });
                    }
                    break;
            }
        }

        this.addToken(TokenType.EOF);
        return { tokens: this.tokens, errors: this.errors };
    }

    private string(): void {
        while (this.peek() !== '"' && !this.isAtEnd()) {
            // Check for interpolation start ${
            if (this.peek() === '$' && this.peekNext() === '{') {
                // Emit the string part we've accumulated so far
                if (this.cursor > this.start + 1) {
                    const value = this.source.substring(this.start + 1, this.cursor);
                    this.tokens.push({
                        type: TokenType.KW_STRING,
                        lexeme: `"${value}"`,
                        line: this.line,
                        column: this.column - value.length
                    });
                }

                // Emit INTERPOLATION_START
                this.advance(); // consume $
                this.advance(); // consume {
                this.tokens.push({
                    type: TokenType.INTERPOLATION_START,
                    lexeme: '${',
                    line: this.line,
                    column: this.column - 1
                });
                this.interpolationDepth++;
                return;
            }

            if (this.peek() === '\n') {
                this.line++;
                this.column = 0;
            }

            // Handle escape sequences
            if (this.peek() === '\\') {
                this.advance(); // consume backslash
                if (!this.isAtEnd()) {
                    this.advance(); // consume escaped char
                }
            } else {
                this.advance();
            }
        }

        if (this.isAtEnd()) {
            this.errors.push({
                message: "Unterminated string",
                line: this.line,
                column: this.column,
                length: 1
            });
            return;
        }

        // Closing quote
        this.advance();

        // Trim the surrounding quotes
        const value = this.source.substring(this.start + 1, this.cursor - 1);
        this.addToken(TokenType.KW_STRING);
    }

    private handleInterpolationEnd(): void {
        this.tokens.push({
            type: TokenType.INTERPOLATION_END,
            lexeme: '}',
            line: this.line,
            column: this.column - 1
        });
        this.interpolationDepth--;

        if (this.peek() === '"') {
            this.advance(); // consume closing quote
        } else {
            this.scanStringTail();
        }
    }

    private scanStringTail(): void {
        this.start = this.cursor;

        while (this.peek() !== '"' && !this.isAtEnd()) {
            if (this.peek() === '$' && this.peekNext() === '{') {
                if (this.cursor > this.start) {
                    const value = this.source.substring(this.start, this.cursor);
                    this.tokens.push({
                        type: TokenType.KW_STRING,
                        lexeme: `"${value}"`,
                        line: this.line,
                        column: this.column - value.length
                    });
                }

                this.advance(); // consume $
                this.advance(); // consume {
                this.tokens.push({
                    type: TokenType.INTERPOLATION_START,
                    lexeme: '${',
                    line: this.line,
                    column: this.column - 1
                });
                this.interpolationDepth++;
                return;
            }

            if (this.peek() === '\n') {
                this.line++;
                this.column = 0;
            }

            if (this.peek() === '\\') {
                this.advance();
                if (!this.isAtEnd()) {
                    this.advance();
                }
            } else {
                this.advance();
            }
        }

        if (this.isAtEnd()) {
            this.errors.push({
                message: "Unterminated string",
                line: this.line,
                column: this.column,
                length: 1
            });
            return;
        }

        this.advance(); // consume closing quote

        if (this.cursor > this.start + 1) {
            const value = this.source.substring(this.start, this.cursor - 1);
            this.tokens.push({
                type: TokenType.KW_STRING,
                lexeme: `"${value}"`,
                line: this.line,
                column: this.column - value.length
            });
        }
    }

    private identifier(): void {
        while (this.isAlphaNumeric(this.peek()) || this.peek() === '_') {
            this.advance();
        }

        const text = this.source.substring(this.start, this.cursor);
        const tokenType = KEYWORDS.get(text) ?? TokenType.IDENTIFIER;
        this.addToken(tokenType);
    }

    private number(): void {
        while (this.isDigit(this.peek())) {
            this.advance();
        }

        if (this.peek() === '.' && this.isDigit(this.peekNext())) {
            this.advance();
            while (this.isDigit(this.peek())) {
                this.advance();
            }
            this.addToken(TokenType.KW_DOUBLE);
        } else {
            this.addToken(TokenType.KW_INT);
        }
    }

    private advance(): string {
        const c = this.source[this.cursor++];
        this.column++;
        return c;
    }

    private match(expected: string): boolean {
        if (this.isAtEnd() || this.source[this.cursor] !== expected) {
            return false;
        }
        this.cursor++;
        this.column++;
        return true;
    }

    private peek(): string {
        return this.isAtEnd() ? '\0' : this.source[this.cursor];
    }

    private peekNext(): string {
        return this.cursor + 1 >= this.source.length ? '\0' : this.source[this.cursor + 1];
    }

    private isAtEnd(): boolean {
        return this.cursor >= this.source.length;
    }

    private addToken(type: TokenType): void {
        const text = this.source.substring(this.start, this.cursor);
        this.tokens.push({
            type,
            lexeme: text,
            line: this.line,
            column: this.column - text.length
        });
    }

    private isDigit(c: string): boolean {
        return c >= '0' && c <= '9';
    }

    private isAlpha(c: string): boolean {
        return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z');
    }

    private isAlphaNumeric(c: string): boolean {
        return this.isAlpha(c) || this.isDigit(c);
    }
}

export function tokenize(source: string): { tokens: Token[], errors: LexerError[] } {
    const lexer = new Lexer(source);
    return lexer.tokenize();
}
