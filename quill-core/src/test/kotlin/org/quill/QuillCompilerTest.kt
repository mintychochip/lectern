package org.quill

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class QuillCompilerTest {
    private val compiler = QuillCompiler()

    @Test
    fun testCompileReturnsNonNull() {
        val result = compiler.compile("print(42)")
        assertNotNull(result)
    }

    @Test
    fun testCompileAndExecuteWithFakeContext() {
        val ctx = FakeQuillContext()
        val result = compiler.compile("print(42)")
        result.execute(ctx)
        assertEquals(listOf("42"), ctx.prints)
    }

    @Test
    fun testLogFunction() {
        val ctx = FakeQuillContext()
        val result = compiler.compile("log(\"hello\")")
        result.execute(ctx)
        assertEquals(listOf("hello"), ctx.logs)
    }

    @Test
    fun testCompilationErrorThrows() {
        assertThrows(Exception::class.java) {
            compiler.compile("let x =")
        }
    }

    @Test
    fun testInfiniteLoopTimesOut() {
        val compiler = QuillCompiler()
        val ctx = FakeQuillContext()
        // Infinite loop that would run forever
        val result = compiler.compile("while (true) {}")
        // Default timeout is 10M instructions; this should throw
        assertThrows(Exception::class.java) {
            result.execute(ctx)
        }
    }
}

class FakeQuillContext : QuillContext {
    val logs = mutableListOf<String>()
    val prints = mutableListOf<String>()
    override fun log(message: String) { logs.add(message) }
    override fun print(message: String) { prints.add(message) }
}
