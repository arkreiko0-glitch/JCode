package com.example

import com.example.data.DiagnosticSeverity
import com.example.engine.CodeLinter
import com.example.engine.MiniPythonInterpreter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ZenncodeEnginesTest {

    @Test
    fun pythonInterpreter_runsFibonacciAndPrintsOutput() {
        val interpreter = MiniPythonInterpreter()
        val code = """
            def fib(n):
                if n <= 1:
                    return n
                return fib(n - 1) + fib(n - 2)

            items = []
            for i in range(6):
                items.append(fib(i))
            print("Fib:", items)
            print("Sum:", sum(items))
        """.trimIndent()

        val result = interpreter.execute(code)
        assertTrue(result.isSuccess)
        val out = result.getOrThrow()
        assertTrue(out.contains("Fib: [0, 1, 1, 2, 3, 5]"))
        assertTrue(out.contains("Sum: 12"))
    }

    @Test
    fun pythonInterpreter_reportsExactLineOnError() {
        val interpreter = MiniPythonInterpreter()
        val code = """
            x = 10
            y = 0
            print(x / y)
        """.trimIndent()

        val result = interpreter.execute(code)
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("line 3") == true)
    }

    @Test
    fun codeLinter_detectsExactLineErrorsForJsAndPythonAndJson() {
        val jsDiags = CodeLinter.analyze("app.js", "function test() {\n  const x = [1, 2\n  return x;\n}", "javascript")
        assertTrue(jsDiags.any { it.severity == DiagnosticSeverity.ERROR && it.line == 2 })

        val pyDiags = CodeLinter.analyze("main.py", "def hello()\n    print('hi')", "python")
        assertTrue(pyDiags.any { it.severity == DiagnosticSeverity.ERROR && it.line == 1 })

        val jsonDiags = CodeLinter.analyze("config.json", "{\n  \"a\": 1,\n  \"b\": \"oops\n}", "json")
        assertTrue(jsonDiags.any { it.severity == DiagnosticSeverity.ERROR && it.line == 3 })
    }
}
