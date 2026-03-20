package org.quill.intellij.formatter

import com.intellij.application.options.CodeStyleAbstractConfigurable
import com.intellij.application.options.CodeStyleAbstractPanel
import com.intellij.application.options.TabbedLanguageCodeStylePanel
import com.intellij.openapi.options.Configurable
import com.intellij.psi.codeStyle.CodeStyleSettings
import com.intellij.psi.codeStyle.CodeStyleSettingsProvider
import com.intellij.psi.codeStyle.CustomCodeStyleSettings
import org.quill.intellij.lang.QuillLanguage

/**
 * Code style settings for the Quill language.
 */
class QuillCodeStyleSettings(container: CodeStyleSettings) :
    CustomCodeStyleSettings("QuillCodeStyleSettings", container)

/**
 * Provider for Quill code style settings.
 */
class QuillCodeStyleSettingsProvider : CodeStyleSettingsProvider() {

    override fun createCustomSettings(settings: CodeStyleSettings): CustomCodeStyleSettings {
        return QuillCodeStyleSettings(settings)
    }

    override fun getConfigurableDisplayName(): String = QuillLanguage.INSTANCE.displayName

    override fun createSettingsPage(settings: CodeStyleSettings, originalSettings: CodeStyleSettings): Configurable {
        return object : CodeStyleAbstractConfigurable(settings, originalSettings, QuillLanguage.INSTANCE.displayName) {
            override fun createPanel(settings: CodeStyleSettings): CodeStyleAbstractPanel {
                return QuillCodeStyleMainPanel(currentSettings, settings)
            }
        }
    }
}

/**
 * Main panel for Quill code style settings.
 */
private class QuillCodeStyleMainPanel(
    currentSettings: CodeStyleSettings,
    settings: CodeStyleSettings
) : TabbedLanguageCodeStylePanel(QuillLanguage.INSTANCE, currentSettings, settings) {

    override fun initTabs(settings: CodeStyleSettings) {
        addIndentOptionsTab(settings)
        addWrappingAndBracesTab(settings)
        addBlankLinesTab(settings)
    }
}

/**
 * Provider for Quill language code style settings.
 */
class QuillLanguageCodeStyleSettingsProvider : com.intellij.psi.codeStyle.LanguageCodeStyleSettingsProvider() {

    override fun getLanguage() = QuillLanguage.INSTANCE

    override fun getCodeSample(settingsType: SettingsType): String? {
        return when (settingsType) {
            SettingsType.INDENT_SETTINGS -> INDENT_SAMPLE
            SettingsType.WRAPPING_AND_BRACES_SETTINGS -> WRAPPING_SAMPLE
            SettingsType.BLANK_LINES_SETTINGS -> BLANK_LINES_SAMPLE
            else -> null
        }
    }

    companion object {
        private const val INDENT_SAMPLE = """
fn factorial(n) {
    if (n <= 1) {
        return 1
    }
    return n * factorial(n - 1)
}

class Person {
    let name
    let age

    fn greet() {
        print("Hello, I'm " + this.name)
    }
}
"""

        private const val WRAPPING_SAMPLE = """
fn longFunction(param1, param2, param3, param4, param5) {
    let result = param1 + param2 + param3 + param4 + param5
    return result
}

class VeryLongClassName extends BaseClass {
    fn method() {
        if (condition1 and condition2 and condition3) {
            doSomething()
        }
    }
}
"""

        private const val BLANK_LINES_SAMPLE = """
// Import statements
import math
from "utils" import { helper }

// Class definition
class Calculator {
    fn add(a, b) {
        return a + b
    }

    fn subtract(a, b) {
        return a - b
    }
}

// Main function
fn main() {
    let calc = Calculator()
    print(calc.add(1, 2))
}
"""
    }
}
