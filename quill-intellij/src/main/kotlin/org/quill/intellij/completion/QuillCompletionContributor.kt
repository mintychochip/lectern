package org.quill.intellij.completion

import com.intellij.codeInsight.completion.*
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.patterns.PlatformPatterns
import com.intellij.util.ProcessingContext
import org.quill.intellij.lang.QuillLanguage
import org.quill.intellij.lexer.QuillTokens

/**
 * Code completion contributor for the Quill language.
 */
class QuillCompletionContributor : CompletionContributor() {

    init {
        // Keyword completion
        extend(
            CompletionType.BASIC,
            PlatformPatterns.psiElement().withLanguage(QuillLanguage.INSTANCE),
            KeywordCompletionProvider
        )

        // Built-in functions
        extend(
            CompletionType.BASIC,
            PlatformPatterns.psiElement().withLanguage(QuillLanguage.INSTANCE),
            BuiltinCompletionProvider
        )
    }

    /**
     * Provider for keyword completion.
     */
    private object KeywordCompletionProvider : CompletionProvider<CompletionParameters>() {
        private val KEYWORDS = listOf(
            // Type keywords
            "bool", "int", "float", "double", "string",
            // Value keywords
            "true", "false", "null",
            // Declaration keywords
            "let", "const", "fn", "class", "enum", "extends",
            // Control flow keywords
            "if", "else", "while", "for", "in",
            // Jump keywords
            "return", "break", "next",
            // Logical keywords
            "and", "or", "not", "is",
            // Import keywords
            "import", "from",
            // Other
            "this"
        )

        override fun addCompletions(
            parameters: CompletionParameters,
            context: ProcessingContext,
            result: CompletionResultSet
        ) {
            KEYWORDS.forEach { keyword ->
                result.addElement(
                    LookupElementBuilder.create(keyword)
                        .withBoldness(true)
                        .withTypeText("Keyword")
                )
            }
        }
    }

    /**
     * Provider for built-in functions completion.
     */
    private object BuiltinCompletionProvider : CompletionProvider<CompletionParameters>() {
        private val BUILTINS = listOf(
            // I/O
            LookupElementBuilder.create("print")
                .withTailText("(value)")
                .withTypeText("Built-in"),
            LookupElementBuilder.create("println")
                .withTailText("(value)")
                .withTypeText("Built-in"),
            LookupElementBuilder.create("input")
                .withTailText("(prompt)")
                .withTypeText("Built-in"),

            // Type conversion
            LookupElementBuilder.create("toInt")
                .withTailText("(value)")
                .withTypeText("Built-in"),
            LookupElementBuilder.create("toFloat")
                .withTailText("(value)")
                .withTypeText("Built-in"),
            LookupElementBuilder.create("toDouble")
                .withTailText("(value)")
                .withTypeText("Built-in"),
            LookupElementBuilder.create("toString")
                .withTailText("(value)")
                .withTypeText("Built-in"),
            LookupElementBuilder.create("toBool")
                .withTailText("(value)")
                .withTypeText("Built-in"),

            // String functions
            LookupElementBuilder.create("length")
                .withTailText("(string)")
                .withTypeText("Built-in"),
            LookupElementBuilder.create("substring")
                .withTailText("(string, start, end)")
                .withTypeText("Built-in"),
            LookupElementBuilder.create("concat")
                .withTailText("(str1, str2)")
                .withTypeText("Built-in"),
            LookupElementBuilder.create("split")
                .withTailText("(string, delimiter)")
                .withTypeText("Built-in"),
            LookupElementBuilder.create("join")
                .withTailText("(array, delimiter)")
                .withTypeText("Built-in"),
            LookupElementBuilder.create("trim")
                .withTailText("(string)")
                .withTypeText("Built-in"),
            LookupElementBuilder.create("upper")
                .withTailText("(string)")
                .withTypeText("Built-in"),
            LookupElementBuilder.create("lower")
                .withTailText("(string)")
                .withTypeText("Built-in"),

            // Array functions
            LookupElementBuilder.create("push")
                .withTailText("(array, value)")
                .withTypeText("Built-in"),
            LookupElementBuilder.create("pop")
                .withTailText("(array)")
                .withTypeText("Built-in"),
            LookupElementBuilder.create("size")
                .withTailText("(array)")
                .withTypeText("Built-in"),
            LookupElementBuilder.create("contains")
                .withTailText("(array, value)")
                .withTypeText("Built-in"),
            LookupElementBuilder.create("indexOf")
                .withTailText("(array, value)")
                .withTypeText("Built-in"),
            LookupElementBuilder.create("remove")
                .withTailText("(array, index)")
                .withTypeText("Built-in"),
            LookupElementBuilder.create("sort")
                .withTailText("(array)")
                .withTypeText("Built-in"),
            LookupElementBuilder.create("reverse")
                .withTailText("(array)")
                .withTypeText("Built-in"),
            LookupElementBuilder.create("map")
                .withTailText("(array, fn)")
                .withTypeText("Built-in"),
            LookupElementBuilder.create("filter")
                .withTailText("(array, fn)")
                .withTypeText("Built-in"),
            LookupElementBuilder.create("reduce")
                .withTailText("(array, fn, initial)")
                .withTypeText("Built-in"),

            // Math functions
            LookupElementBuilder.create("abs")
                .withTailText("(number)")
                .withTypeText("Built-in"),
            LookupElementBuilder.create("floor")
                .withTailText("(number)")
                .withTypeText("Built-in"),
            LookupElementBuilder.create("ceil")
                .withTailText("(number)")
                .withTypeText("Built-in"),
            LookupElementBuilder.create("round")
                .withTailText("(number)")
                .withTypeText("Built-in"),
            LookupElementBuilder.create("sqrt")
                .withTailText("(number)")
                .withTypeText("Built-in"),
            LookupElementBuilder.create("pow")
                .withTailText("(base, exponent)")
                .withTypeText("Built-in"),
            LookupElementBuilder.create("min")
                .withTailText("(a, b)")
                .withTypeText("Built-in"),
            LookupElementBuilder.create("max")
                .withTailText("(a, b)")
                .withTypeText("Built-in"),
            LookupElementBuilder.create("random")
                .withTailText("()")
                .withTypeText("Built-in"),

            // Time functions
            LookupElementBuilder.create("time")
                .withTailText("()")
                .withTypeText("Built-in"),
            LookupElementBuilder.create("sleep")
                .withTailText("(milliseconds)")
                .withTypeText("Built-in")
        )

        override fun addCompletions(
            parameters: CompletionParameters,
            context: ProcessingContext,
            result: CompletionResultSet
        ) {
            BUILTINS.forEach { element ->
                result.addElement(element)
            }
        }
    }
}
