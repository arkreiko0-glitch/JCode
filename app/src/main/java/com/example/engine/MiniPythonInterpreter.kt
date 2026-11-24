package com.example.engine

import kotlin.math.pow

class MiniPythonInterpreter {

    private class ReturnSignal(val value: Any?) : Throwable()
    private class BreakSignal : Throwable()
    private class ContinueSignal : Throwable()

    private data class PyFunction(
        val name: String,
        val params: List<String>,
        val body: List<LineNode>
    )

    private data class LineNode(
        val lineNum: Int,
        val indent: Int,
        val code: String
    )

    private val output = StringBuilder()
    private val functions = mutableMapOf<String, PyFunction>()
    private var steps = 0
    private val maxSteps = 20_000

    fun execute(source: String): Result<String> {
        output.clear()
        functions.clear()
        steps = 0
        val nodes = parseLines(source)
        val globals = mutableMapOf<String, Any?>()
        return try {
            execBlock(nodes, globals)
            Result.success(output.toString().trimEnd('\n'))
        } catch (e: IllegalStateException) {
            Result.failure(e)
        } catch (e: Exception) {
            Result.failure(IllegalStateException("RuntimeError: ${e.message}"))
        }
    }

    private fun parseLines(source: String): List<LineNode> {
        val list = mutableListOf<LineNode>()
        source.lines().forEachIndexed { idx, raw ->
            val stripped = stripInlineComment(raw)
            if (stripped.trim().isNotEmpty()) {
                val indent = raw.takeWhile { it == ' ' || it == '\t' }.sumOf { if (it == '\t') 4 else 1 }
                list.add(LineNode(idx + 1, indent, stripped.trim()))
            }
        }
        return list
    }

    private fun stripInlineComment(line: String): String {
        var inSingle = false
        var inDouble = false
        var i = 0
        while (i < line.length) {
            val c = line[i]
            if (c == '\\') {
                i += 2
                continue
            }
            if (c == '\'' && !inDouble) inSingle = !inSingle
            else if (c == '"' && !inSingle) inDouble = !inDouble
            else if (c == '#' && !inSingle && !inDouble) {
                return line.substring(0, i)
            }
            i++
        }
        return line
    }

    private fun execBlock(nodes: List<LineNode>, env: MutableMap<String, Any?>) {
        var i = 0
        while (i < nodes.size) {
            if (++steps > maxSteps) throw IllegalStateException("Execution limit exceeded (possible infinite loop)")
            val node = nodes[i]
            val line = node.code

            when {
                line.startsWith("def ") -> {
                    val header = line.removePrefix("def ").trim()
                    if (!header.endsWith(":")) throw IllegalStateException("SyntaxError at line ${node.lineNum}: expected ':'")
                    val sig = header.dropLast(1).trim()
                    val openIdx = sig.indexOf('(')
                    val closeIdx = sig.lastIndexOf(')')
                    if (openIdx == -1 || closeIdx == -1) throw IllegalStateException("SyntaxError at line ${node.lineNum}: invalid def signature")
                    val fnName = sig.substring(0, openIdx).trim()
                    val paramsStr = sig.substring(openIdx + 1, closeIdx).trim()
                    val params = if (paramsStr.isEmpty()) emptyList() else paramsStr.split(",").map { it.trim() }
                    val (body, nextIdx) = collectIndentedBlock(nodes, i + 1, node.indent)
                    functions[fnName] = PyFunction(fnName, params, body)
                    i = nextIdx
                }

                line.startsWith("if ") -> {
                    val branches = mutableListOf<Pair<String?, List<LineNode>>>()
                    val cond = extractHeaderCondition(line, "if", node.lineNum)
                    val (ifBody, afterIf) = collectIndentedBlock(nodes, i + 1, node.indent)
                    branches.add(cond to ifBody)
                    var cursor = afterIf
                    while (cursor < nodes.size && nodes[cursor].indent == node.indent) {
                        val nextLine = nodes[cursor].code
                        if (nextLine.startsWith("elif ")) {
                            val elifCond = extractHeaderCondition(nextLine, "elif", nodes[cursor].lineNum)
                            val (elifBody, afterElif) = collectIndentedBlock(nodes, cursor + 1, node.indent)
                            branches.add(elifCond to elifBody)
                            cursor = afterElif
                        } else if (nextLine.startsWith("else:")) {
                            val (elseBody, afterElse) = collectIndentedBlock(nodes, cursor + 1, node.indent)
                            branches.add(null to elseBody)
                            cursor = afterElse
                            break
                        } else {
                            break
                        }
                    }
                    for ((bCond, bBody) in branches) {
                        if (bCond == null || isTruthy(evalExpr(bCond, env, node.lineNum))) {
                            execBlock(bBody, env)
                            break
                        }
                    }
                    i = cursor
                }

                line.startsWith("for ") -> {
                    if (!line.endsWith(":")) throw IllegalStateException("SyntaxError at line ${node.lineNum}: expected ':'")
                    val header = line.removePrefix("for ").dropLast(1).trim()
                    val inIdx = header.indexOf(" in ")
                    if (inIdx == -1) throw IllegalStateException("SyntaxError at line ${node.lineNum}: expected 'in'")
                    val varName = header.substring(0, inIdx).trim()
                    val iterExpr = header.substring(inIdx + 4).trim()
                    val (body, nextIdx) = collectIndentedBlock(nodes, i + 1, node.indent)
                    val iterable = toIterable(evalExpr(iterExpr, env, node.lineNum), node.lineNum)
                    for (item in iterable) {
                        if (++steps > maxSteps) throw IllegalStateException("Execution limit exceeded")
                        if (varName.contains(",") && item is List<*>) {
                            val vars = varName.split(",").map { it.trim() }
                            vars.forEachIndexed { idx, vn -> env[vn] = item.getOrNull(idx) }
                        } else {
                            env[varName] = item
                        }
                        try {
                            execBlock(body, env)
                        } catch (_: ContinueSignal) {
                            continue
                        } catch (_: BreakSignal) {
                            break
                        }
                    }
                    i = nextIdx
                }

                line.startsWith("while ") -> {
                    val cond = extractHeaderCondition(line, "while", node.lineNum)
                    val (body, nextIdx) = collectIndentedBlock(nodes, i + 1, node.indent)
                    while (isTruthy(evalExpr(cond, env, node.lineNum))) {
                        if (++steps > maxSteps) throw IllegalStateException("Execution limit exceeded in while loop")
                        try {
                            execBlock(body, env)
                        } catch (_: ContinueSignal) {
                            continue
                        } catch (_: BreakSignal) {
                            break
                        }
                    }
                    i = nextIdx
                }

                line == "break" -> throw BreakSignal()
                line == "continue" -> throw ContinueSignal()
                line == "pass" -> {
                    i++
                }
                line.startsWith("return") -> {
                    val expr = line.removePrefix("return").trim()
                    val value = if (expr.isEmpty()) null else evalExpr(expr, env, node.lineNum)
                    throw ReturnSignal(value)
                }

                else -> {
                    execSimpleStatement(line, env, node.lineNum)
                    i++
                }
            }
        }
    }

    private fun extractHeaderCondition(line: String, kw: String, lineNum: Int): String {
        if (!line.endsWith(":")) throw IllegalStateException("SyntaxError at line $lineNum: expected ':'")
        return line.removePrefix("$kw ").dropLast(1).trim()
    }

    private fun collectIndentedBlock(
        nodes: List<LineNode>,
        startIdx: Int,
        parentIndent: Int
    ): Pair<List<LineNode>, Int> {
        val body = mutableListOf<LineNode>()
        var idx = startIdx
        while (idx < nodes.size && nodes[idx].indent > parentIndent) {
            body.add(nodes[idx])
            idx++
        }
        return body to idx
    }

    private fun execSimpleStatement(line: String, env: MutableMap<String, Any?>, lineNum: Int) {
        // Augmented assignments +=, -=, *=, /=
        for (op in listOf("+=", "-=", "*=", "/=")) {
            val idx = findTopLevelToken(line, op)
            if (idx > 0) {
                val target = line.substring(0, idx).trim()
                val rhs = evalExpr(line.substring(idx + 2).trim(), env, lineNum)
                val current = env[target] ?: throw IllegalStateException("NameError at line $lineNum: '$target' is not defined")
                env[target] = when (op) {
                    "+=" -> applyAdd(current, rhs, lineNum)
                    "-=" -> applyNumOp(current, rhs, lineNum) { a, b -> a - b }
                    "*=" -> applyMul(current, rhs, lineNum)
                    "/=" -> toDouble(current, lineNum) / toDouble(rhs, lineNum)
                    else -> current
                }
                return
            }
        }

        // Regular assignment = (but not ==, !=, <=, >=)
        val assignIdx = findTopLevelAssignment(line)
        if (assignIdx > 0) {
            val target = line.substring(0, assignIdx).trim()
            val value = evalExpr(line.substring(assignIdx + 1).trim(), env, lineNum)
            if (target.contains("[") && target.endsWith("]")) {
                val bracket = target.indexOf('[')
                val containerName = target.substring(0, bracket).trim()
                val keyExpr = target.substring(bracket + 1, target.length - 1).trim()
                val container = env[containerName]
                val key = evalExpr(keyExpr, env, lineNum)
                when (container) {
                    is MutableList<*> -> {
                        @Suppress("UNCHECKED_CAST")
                        val list = container as MutableList<Any?>
                        val idx = toInt(key, lineNum)
                        val realIdx = if (idx < 0) list.size + idx else idx
                        list[realIdx] = value
                    }
                    is MutableMap<*, *> -> {
                        @Suppress("UNCHECKED_CAST")
                        (container as MutableMap<Any?, Any?>)[key] = value
                    }
                    else -> throw IllegalStateException("TypeError at line $lineNum: cannot assign item on $containerName")
                }
            } else {
                env[target] = value
            }
            return
        }

        evalExpr(line, env, lineNum)
    }

    private fun evalExpr(raw: String, env: MutableMap<String, Any?>, lineNum: Int): Any? {
        val expr = raw.trim()
        if (expr.isEmpty()) return null

        // Boolean 'or'
        findTopLevelKeyword(expr, "or").takeIf { it >= 0 }?.let { idx ->
            val left = evalExpr(expr.substring(0, idx), env, lineNum)
            return if (isTruthy(left)) left else evalExpr(expr.substring(idx + 4), env, lineNum)
        }
        // Boolean 'and'
        findTopLevelKeyword(expr, "and").takeIf { it >= 0 }?.let { idx ->
            val left = evalExpr(expr.substring(0, idx), env, lineNum)
            return if (!isTruthy(left)) left else evalExpr(expr.substring(idx + 5), env, lineNum)
        }
        if (expr.startsWith("not ")) {
            return !isTruthy(evalExpr(expr.removePrefix("not "), env, lineNum))
        }

        // Comparisons
        for (op in listOf("==", "!=", "<=", ">=", "<", ">", " in ")) {
            val idx = findTopLevelToken(expr, op)
            if (idx > 0) {
                val left = evalExpr(expr.substring(0, idx), env, lineNum)
                val right = evalExpr(expr.substring(idx + op.length), env, lineNum)
                return when (op.trim()) {
                    "==" -> left == right || (left is Number && right is Number && left.toDouble() == right.toDouble())
                    "!=" -> !(left == right || (left is Number && right is Number && left.toDouble() == right.toDouble()))
                    "<" -> toDouble(left, lineNum) < toDouble(right, lineNum)
                    "<=" -> toDouble(left, lineNum) <= toDouble(right, lineNum)
                    ">" -> toDouble(left, lineNum) > toDouble(right, lineNum)
                    ">=" -> toDouble(left, lineNum) >= toDouble(right, lineNum)
                    "in" -> when (right) {
                        is List<*> -> right.contains(left)
                        is Map<*, *> -> right.containsKey(left)
                        is String -> right.contains(left.toString())
                        else -> false
                    }
                    else -> false
                }
            }
        }

        // Addition / Subtraction (right-to-left scan for lowest precedence left-associative)
        val addSubIdx = findLastTopLevelPlusMinus(expr)
        if (addSubIdx > 0) {
            val op = expr[addSubIdx]
            val left = evalExpr(expr.substring(0, addSubIdx), env, lineNum)
            val right = evalExpr(expr.substring(addSubIdx + 1), env, lineNum)
            return if (op == '+') applyAdd(left, right, lineNum)
            else applyNumOp(left, right, lineNum) { a, b -> a - b }
        }

        // Multiplication / Division / Modulo
        val mulIdx = findLastTopLevelMulDiv(expr)
        if (mulIdx > 0) {
            val op2 = if (mulIdx > 0 && expr.substring(mulIdx - 1, mulIdx + 1) == "//") "//" else expr[mulIdx].toString()
            val leftEnd = if (op2 == "//") mulIdx - 1 else mulIdx
            val left = evalExpr(expr.substring(0, leftEnd), env, lineNum)
            val right = evalExpr(expr.substring(mulIdx + 1), env, lineNum)
            return when (op2) {
                "*" -> applyMul(left, right, lineNum)
                "/" -> {
                    val d = toDouble(right, lineNum)
                    if (d == 0.0) throw IllegalStateException("ZeroDivisionError at line $lineNum")
                    toDouble(left, lineNum) / d
                }
                "//" -> {
                    val d = toInt(right, lineNum)
                    if (d == 0) throw IllegalStateException("ZeroDivisionError at line $lineNum")
                    toInt(left, lineNum) / d
                }
                "%" -> toInt(left, lineNum) % toInt(right, lineNum)
                else -> null
            }
        }

        // Power **
        val powIdx = findTopLevelToken(expr, "**")
        if (powIdx > 0) {
            val left = evalExpr(expr.substring(0, powIdx), env, lineNum)
            val right = evalExpr(expr.substring(powIdx + 2), env, lineNum)
            return toDouble(left, lineNum).pow(toDouble(right, lineNum))
        }

        // Unary minus
        if (expr.startsWith("-")) {
            val v = evalExpr(expr.substring(1), env, lineNum)
            return if (v is Int) -v else -toDouble(v, lineNum)
        }

        // Parenthesized expression
        if (expr.startsWith("(") && expr.endsWith(")") && matchingClose(expr, 0) == expr.length - 1) {
            return evalExpr(expr.substring(1, expr.length - 1), env, lineNum)
        }

        // f-string
        if ((expr.startsWith("f\"") && expr.endsWith("\"")) || (expr.startsWith("f'") && expr.endsWith("'"))) {
            val inner = expr.substring(2, expr.length - 1)
            return interpolateFString(inner, env, lineNum)
        }

        // String literal
        if ((expr.startsWith("\"") && expr.endsWith("\"")) || (expr.startsWith("'") && expr.endsWith("'"))) {
            return expr.substring(1, expr.length - 1)
                .replace("\\n", "\n")
                .replace("\\t", "\t")
                .replace("\\\"", "\"")
                .replace("\\'", "'")
        }

        // List literal
        if (expr.startsWith("[") && expr.endsWith("]") && matchingClose(expr, 0) == expr.length - 1) {
            val inner = expr.substring(1, expr.length - 1).trim()
            if (inner.isEmpty()) return mutableListOf<Any?>()
            return splitTopLevelArgs(inner).map { evalExpr(it, env, lineNum) }.toMutableList()
        }

        // Dict literal
        if (expr.startsWith("{") && expr.endsWith("}") && matchingClose(expr, 0) == expr.length - 1) {
            val inner = expr.substring(1, expr.length - 1).trim()
            val map = linkedMapOf<Any?, Any?>()
            if (inner.isNotEmpty()) {
                for (entry in splitTopLevelArgs(inner)) {
                    val colon = findTopLevelToken(entry, ":")
                    if (colon == -1) throw IllegalStateException("SyntaxError at line $lineNum: invalid dict entry")
                    val k = evalExpr(entry.substring(0, colon), env, lineNum)
                    val v = evalExpr(entry.substring(colon + 1), env, lineNum)
                    map[k] = v
                }
            }
            return map
        }

        // Literals
        when (expr) {
            "True" -> return true
            "False" -> return false
            "None" -> return null
        }
        expr.toIntOrNull()?.let { return it }
        expr.toDoubleOrNull()?.let { return it }

        // Method call on object e.g. seq.append(x), s.upper(), etc.
        val callOpen = findTrailingCallOpen(expr)
        if (callOpen > 0 && expr.endsWith(")")) {
            val callee = expr.substring(0, callOpen).trim()
            val argsStr = expr.substring(callOpen + 1, expr.length - 1).trim()
            val args = if (argsStr.isEmpty()) emptyList() else splitTopLevelArgs(argsStr).map { evalExpr(it, env, lineNum) }
            val dotIdx = findLastTopLevelDot(callee)
            if (dotIdx > 0) {
                val target = evalExpr(callee.substring(0, dotIdx), env, lineNum)
                val method = callee.substring(dotIdx + 1).trim()
                return callMethod(target, method, args, lineNum)
            }
            return callFunction(callee, args, env, lineNum)
        }

        // Indexing e.g. arr[0]
        if (expr.endsWith("]")) {
            val openBracket = findMatchingOpenFromEnd(expr, '[', ']')
            if (openBracket > 0) {
                val target = evalExpr(expr.substring(0, openBracket), env, lineNum)
                val indexVal = evalExpr(expr.substring(openBracket + 1, expr.length - 1), env, lineNum)
                return when (target) {
                    is List<*> -> {
                        val idx = toInt(indexVal, lineNum)
                        target[if (idx < 0) target.size + idx else idx]
                    }
                    is Map<*, *> -> target[indexVal]
                    is String -> {
                        val idx = toInt(indexVal, lineNum)
                        target[if (idx < 0) target.length + idx else idx].toString()
                    }
                    else -> throw IllegalStateException("TypeError at line $lineNum: object is not subscriptable")
                }
            }
        }

        if (env.containsKey(expr)) return env[expr]
        throw IllegalStateException("NameError at line $lineNum: name '$expr' is not defined")
    }

    private fun callMethod(target: Any?, method: String, args: List<Any?>, lineNum: Int): Any? {
        return when (target) {
            is MutableList<*> -> {
                @Suppress("UNCHECKED_CAST")
                val list = target as MutableList<Any?>
                when (method) {
                    "append" -> {
                        list.add(args.firstOrNull())
                        null
                    }
                    "pop" -> if (list.isNotEmpty()) list.removeAt(list.lastIndex) else null
                    "clear" -> {
                        list.clear()
                        null
                    }
                    else -> throw IllegalStateException("AttributeError at line $lineNum: list has no method '$method'")
                }
            }
            is String -> when (method) {
                "upper" -> target.uppercase()
                "lower" -> target.lowercase()
                "strip" -> target.trim()
                "split" -> {
                    val sep = args.firstOrNull()?.toString() ?: " "
                    target.split(sep).toMutableList()
                }
                "join" -> {
                    val items = toIterable(args.firstOrNull(), lineNum)
                    items.joinToString(target) { formatPy(it) }
                }
                else -> throw IllegalStateException("AttributeError at line $lineNum: str has no method '$method'")
            }
            else -> throw IllegalStateException("AttributeError at line $lineNum: no method '$method'")
        }
    }

    private fun callFunction(name: String, args: List<Any?>, env: MutableMap<String, Any?>, lineNum: Int): Any? {
        when (name) {
            "print" -> {
                output.append(args.joinToString(" ") { formatPy(it) }).append('\n')
                return null
            }
            "len" -> return when (val v = args.firstOrNull()) {
                is Collection<*> -> v.size
                is Map<*, *> -> v.size
                is String -> v.length
                else -> throw IllegalStateException("TypeError at line $lineNum: object has no len()")
            }
            "range" -> {
                return when (args.size) {
                    1 -> (0 until toInt(args[0], lineNum)).toList()
                    2 -> (toInt(args[0], lineNum) until toInt(args[1], lineNum)).toList()
                    3 -> {
                        val start = toInt(args[0], lineNum)
                        val end = toInt(args[1], lineNum)
                        val step = toInt(args[2], lineNum)
                        if (step == 0) throw IllegalStateException("ValueError at line $lineNum: range() arg 3 must not be zero")
                        val res = mutableListOf<Int>()
                        var cur = start
                        if (step > 0) while (cur < end) { res.add(cur); cur += step }
                        else while (cur > end) { res.add(cur); cur += step }
                        res
                    }
                    else -> throw IllegalStateException("TypeError at line $lineNum: range() expects 1-3 arguments")
                }
            }
            "sum" -> {
                val items = toIterable(args.firstOrNull(), lineNum)
                var total = 0.0
                var allInt = true
                for (item in items) {
                    if (item is Double) allInt = false
                    total += toDouble(item, lineNum)
                }
                return if (allInt) total.toInt() else total
            }
            "min" -> {
                val items = if (args.size == 1 && args[0] is Iterable<*>) toIterable(args[0], lineNum) else args
                return items.minByOrNull { toDouble(it, lineNum) }
            }
            "max" -> {
                val items = if (args.size == 1 && args[0] is Iterable<*>) toIterable(args[0], lineNum) else args
                return items.maxByOrNull { toDouble(it, lineNum) }
            }
            "abs" -> {
                val v = args.firstOrNull()
                return if (v is Int) kotlin.math.abs(v) else kotlin.math.abs(toDouble(v, lineNum))
            }
            "int" -> return toInt(args.firstOrNull(), lineNum)
            "float" -> return toDouble(args.firstOrNull(), lineNum)
            "str" -> return formatPy(args.firstOrNull())
            "list" -> return toIterable(args.firstOrNull(), lineNum).toMutableList()
            "sorted" -> return toIterable(args.firstOrNull(), lineNum).sortedBy { toDouble(it, lineNum) }.toMutableList()
            "enumerate" -> return toIterable(args.firstOrNull(), lineNum).mapIndexed { idx, v -> listOf(idx, v) }.toMutableList()
        }

        val fn = functions[name] ?: throw IllegalStateException("NameError at line $lineNum: function '$name' is not defined")
        val localEnv = mutableMapOf<String, Any?>()
        // Include globals for read convenience, local params shadow
        localEnv.putAll(env)
        fn.params.forEachIndexed { idx, param ->
            localEnv[param] = args.getOrNull(idx)
        }
        return try {
            execBlock(fn.body, localEnv)
            null
        } catch (ret: ReturnSignal) {
            ret.value
        }
    }

    private fun interpolateFString(inner: String, env: MutableMap<String, Any?>, lineNum: Int): String {
        val sb = StringBuilder()
        var i = 0
        while (i < inner.length) {
            if (inner[i] == '{') {
                val close = inner.indexOf('}', i + 1)
                if (close != -1) {
                    val expr = inner.substring(i + 1, close)
                    sb.append(formatPy(evalExpr(expr, env, lineNum)))
                    i = close + 1
                    continue
                }
            }
            sb.append(inner[i])
            i++
        }
        return sb.toString()
    }

    private fun formatPy(v: Any?): String = when (v) {
        null -> "None"
        true -> "True"
        false -> "False"
        is List<*> -> "[${v.joinToString(", ") { formatPyRepr(it) }}]"
        is Map<*, *> -> "{${v.entries.joinToString(", ") { "${formatPyRepr(it.key)}: ${formatPyRepr(it.value)}" }}}"
        else -> v.toString()
    }

    private fun formatPyRepr(v: Any?): String = if (v is String) "'$v'" else formatPy(v)

    private fun isTruthy(v: Any?): Boolean = when (v) {
        null -> false
        is Boolean -> v
        is Int -> v != 0
        is Double -> v != 0.0
        is String -> v.isNotEmpty()
        is Collection<*> -> v.isNotEmpty()
        is Map<*, *> -> v.isNotEmpty()
        else -> true
    }

    private fun toIterable(v: Any?, lineNum: Int): List<Any?> = when (v) {
        is Iterable<*> -> v.toList()
        is String -> v.map { it.toString() }
        else -> throw IllegalStateException("TypeError at line $lineNum: '${v?.javaClass?.simpleName}' object is not iterable")
    }

    private fun toInt(v: Any?, lineNum: Int): Int = when (v) {
        is Int -> v
        is Double -> v.toInt()
        is String -> v.toIntOrNull() ?: throw IllegalStateException("ValueError at line $lineNum: invalid int '$v'")
        is Boolean -> if (v) 1 else 0
        else -> throw IllegalStateException("TypeError at line $lineNum: cannot convert $v to int")
    }

    private fun toDouble(v: Any?, lineNum: Int): Double = when (v) {
        is Int -> v.toDouble()
        is Double -> v
        is String -> v.toDoubleOrNull() ?: throw IllegalStateException("ValueError at line $lineNum: invalid float '$v'")
        is Boolean -> if (v) 1.0 else 0.0
        else -> throw IllegalStateException("TypeError at line $lineNum: cannot convert $v to float")
    }

    private fun applyAdd(a: Any?, b: Any?, lineNum: Int): Any? {
        if (a is String || b is String) return "${formatPy(a)}${formatPy(b)}"
        if (a is List<*> && b is List<*>) return (a + b).toMutableList()
        return if (a is Int && b is Int) a + b else toDouble(a, lineNum) + toDouble(b, lineNum)
    }

    private fun applyMul(a: Any?, b: Any?, lineNum: Int): Any? {
        if (a is String && b is Int) return a.repeat(b.coerceAtLeast(0))
        if (b is String && a is Int) return b.repeat(a.coerceAtLeast(0))
        return if (a is Int && b is Int) a * b else toDouble(a, lineNum) * toDouble(b, lineNum)
    }

    private inline fun applyNumOp(a: Any?, b: Any?, lineNum: Int, op: (Double, Double) -> Double): Any {
        val res = op(toDouble(a, lineNum), toDouble(b, lineNum))
        return if (a is Int && b is Int) res.toInt() else res
    }

    // Parsing helpers respecting quotes and nested brackets
    private fun findTopLevelAssignment(line: String): Int {
        var depth = 0
        var inSingle = false
        var inDouble = false
        var i = 0
        while (i < line.length) {
            val c = line[i]
            if (c == '\\') { i += 2; continue }
            if (c == '\'' && !inDouble) inSingle = !inSingle
            else if (c == '"' && !inSingle) inDouble = !inDouble
            else if (!inSingle && !inDouble) {
                when (c) {
                    '(', '[', '{' -> depth++
                    ')', ']', '}' -> depth--
                    '=' -> {
                        val prev = line.getOrNull(i - 1)
                        val next = line.getOrNull(i + 1)
                        if (depth == 0 && prev !in listOf('=', '!', '<', '>') && next != '=') {
                            return i
                        }
                    }
                }
            }
            i++
        }
        return -1
    }

    private fun findTopLevelToken(expr: String, token: String): Int {
        var depth = 0
        var inSingle = false
        var inDouble = false
        var i = 0
        while (i <= expr.length - token.length) {
            val c = expr[i]
            if (c == '\\') { i += 2; continue }
            if (c == '\'' && !inDouble) inSingle = !inSingle
            else if (c == '"' && !inSingle) inDouble = !inDouble
            else if (!inSingle && !inDouble) {
                when (c) {
                    '(', '[', '{' -> depth++
                    ')', ']', '}' -> depth--
                }
                if (depth == 0 && expr.startsWith(token, i)) return i
            }
            i++
        }
        return -1
    }

    private fun findTopLevelKeyword(expr: String, kw: String): Int =
        findTopLevelToken(expr, " $kw ")

    private fun findLastTopLevelPlusMinus(expr: String): Int {
        var depth = 0
        var inSingle = false
        var inDouble = false
        var last = -1
        var i = 0
        while (i < expr.length) {
            val c = expr[i]
            if (c == '\\') { i += 2; continue }
            if (c == '\'' && !inDouble) inSingle = !inSingle
            else if (c == '"' && !inSingle) inDouble = !inDouble
            else if (!inSingle && !inDouble) {
                when (c) {
                    '(', '[', '{' -> depth++
                    ')', ']', '}' -> depth--
                    '+', '-' -> if (depth == 0 && i > 0) {
                        val prev = expr.substring(0, i).trimEnd().lastOrNull()
                        if (prev != null && prev !in listOf('+', '-', '*', '/', '%', '(', '[', '{', ',', '=', '<', '>', '!')) {
                            last = i
                        }
                    }
                }
            }
            i++
        }
        return last
    }

    private fun findLastTopLevelMulDiv(expr: String): Int {
        var depth = 0
        var inSingle = false
        var inDouble = false
        var last = -1
        var i = 0
        while (i < expr.length) {
            val c = expr[i]
            if (c == '\\') { i += 2; continue }
            if (c == '\'' && !inDouble) inSingle = !inSingle
            else if (c == '"' && !inSingle) inDouble = !inDouble
            else if (!inSingle && !inDouble) {
                when (c) {
                    '(', '[', '{' -> depth++
                    ')', ']', '}' -> depth--
                    '*', '/', '%' -> if (depth == 0) {
                        if (c == '*' && (expr.getOrNull(i + 1) == '*' || expr.getOrNull(i - 1) == '*')) {
                            // skip **
                        } else {
                            last = i
                        }
                    }
                }
            }
            i++
        }
        return last
    }

    private fun matchingClose(expr: String, openIdx: Int): Int {
        val open = expr[openIdx]
        val close = when (open) { '(' -> ')'; '[' -> ']'; '{' -> '}'; else -> return -1 }
        var depth = 0
        var inSingle = false
        var inDouble = false
        var i = openIdx
        while (i < expr.length) {
            val c = expr[i]
            if (c == '\\') { i += 2; continue }
            if (c == '\'' && !inDouble) inSingle = !inSingle
            else if (c == '"' && !inSingle) inDouble = !inDouble
            else if (!inSingle && !inDouble) {
                if (c == open) depth++
                else if (c == close) {
                    depth--
                    if (depth == 0) return i
                }
            }
            i++
        }
        return -1
    }

    private fun findTrailingCallOpen(expr: String): Int {
        if (!expr.endsWith(")")) return -1
        return findMatchingOpenFromEnd(expr, '(', ')')
    }

    private fun findMatchingOpenFromEnd(expr: String, open: Char, close: Char): Int {
        var depth = 0
        var inSingle = false
        var inDouble = false
        var i = expr.length - 1
        while (i >= 0) {
            val c = expr[i]
            if (c == '\'' && !inDouble && expr.getOrNull(i - 1) != '\\') inSingle = !inSingle
            else if (c == '"' && !inSingle && expr.getOrNull(i - 1) != '\\') inDouble = !inDouble
            else if (!inSingle && !inDouble) {
                if (c == close) depth++
                else if (c == open) {
                    depth--
                    if (depth == 0) return i
                }
            }
            i--
        }
        return -1
    }

    private fun findLastTopLevelDot(expr: String): Int {
        var depth = 0
        var inSingle = false
        var inDouble = false
        var last = -1
        var i = 0
        while (i < expr.length) {
            val c = expr[i]
            if (c == '\\') { i += 2; continue }
            if (c == '\'' && !inDouble) inSingle = !inSingle
            else if (c == '"' && !inSingle) inDouble = !inDouble
            else if (!inSingle && !inDouble) {
                when (c) {
                    '(', '[', '{' -> depth++
                    ')', ']', '}' -> depth--
                    '.' -> if (depth == 0) last = i
                }
            }
            i++
        }
        return last
    }

    private fun splitTopLevelArgs(inner: String): List<String> {
        val result = mutableListOf<String>()
        var depth = 0
        var inSingle = false
        var inDouble = false
        var start = 0
        var i = 0
        while (i < inner.length) {
            val c = inner[i]
            if (c == '\\') { i += 2; continue }
            if (c == '\'' && !inDouble) inSingle = !inSingle
            else if (c == '"' && !inSingle) inDouble = !inDouble
            else if (!inSingle && !inDouble) {
                when (c) {
                    '(', '[', '{' -> depth++
                    ')', ']', '}' -> depth--
                    ',' -> if (depth == 0) {
                        result.add(inner.substring(start, i).trim())
                        start = i + 1
                    }
                }
            }
            i++
        }
        val tail = inner.substring(start).trim()
        if (tail.isNotEmpty()) result.add(tail)
        return result
    }
}
