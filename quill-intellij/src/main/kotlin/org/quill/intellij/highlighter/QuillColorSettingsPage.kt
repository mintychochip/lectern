package org.quill.intellij.highlighter

import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.fileTypes.SyntaxHighlighter
import com.intellij.openapi.options.colors.AttributesDescriptor
import com.intellij.openapi.options.colors.ColorDescriptor
import com.intellij.openapi.options.colors.ColorSettingsPage
import org.quill.intellij.file.QuillFileType
import javax.swing.Icon

/**
 * Color settings page for configuring Quill syntax highlighting colors.
 */
class QuillColorSettingsPage : ColorSettingsPage {

    companion object {
        private val DESCRIPTORS = arrayOf(
            AttributesDescriptor("Keyword", QuillSyntaxHighlighter.KEYWORD),
            AttributesDescriptor("String", QuillSyntaxHighlighter.STRING),
            AttributesDescriptor("Number", QuillSyntaxHighlighter.NUMBER),
            AttributesDescriptor("Boolean", QuillSyntaxHighlighter.BOOLEAN),
            AttributesDescriptor("Null", QuillSyntaxHighlighter.NULL),
            AttributesDescriptor("Identifier", QuillSyntaxHighlighter.IDENTIFIER),
            AttributesDescriptor("Operator", QuillSyntaxHighlighter.OPERATOR),
            AttributesDescriptor("Brackets", QuillSyntaxHighlighter.BRACKET),
            AttributesDescriptor("Parentheses", QuillSyntaxHighlighter.PAREN),
            AttributesDescriptor("Braces", QuillSyntaxHighlighter.BRACE),
            AttributesDescriptor("Comment", QuillSyntaxHighlighter.COMMENT),
            AttributesDescriptor("Bad character", QuillSyntaxHighlighter.BAD_CHARACTER),
        )

        private val DEMO_TEXT = """
// This is a line comment
/* This is a block comment */

// Variable declarations
let count = 0
const PI = 3.14159
let name = "Quill"

// Function declaration
fn factorial(n) {
    if (n <= 1) {
        return 1
    }
    return n * factorial(n - 1)
}

// Class declaration
class Person {
    fn greet() {
        print("Hello")
    }
}

// Control flow
if (count > 0) {
    print("Positive")
} else {
    print("Non-positive")
}

// Loops
for i in 0..10 {
    print(i)
}

while (count < 100) {
    count = count + 1
}

// Operators
let sum = 1 + 2 * 3
let power = 2 ** 10
let range = 0..100
let isEqual = a == b
let logical = (a and b) or (not c)

// Data structures
let numbers = [1, 2, 3, 4, 5]
        """.trimIndent()
    }

    override fun getIcon(): Icon = QuillFileType.icon

    override fun getHighlighter(): SyntaxHighlighter = QuillSyntaxHighlighter()

    override fun getDemoText(): String = DEMO_TEXT

    override fun getAdditionalHighlightingTagToDescriptorMap(): Map<String, TextAttributesKey> = emptyMap()

    override fun getAttributeDescriptors(): Array<AttributesDescriptor> = DESCRIPTORS

    override fun getColorDescriptors(): Array<ColorDescriptor> = ColorDescriptor.EMPTY_ARRAY

    override fun getDisplayName(): String = "Quill"
}
