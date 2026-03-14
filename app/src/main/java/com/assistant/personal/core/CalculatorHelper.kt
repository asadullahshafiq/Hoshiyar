package com.assistant.personal.core

object CalculatorHelper {

    fun calculate(text: String): String? {
        val t = text.lowercase()
            .replace("plus", "+")
            .replace("minus", "-")
            .replace("times", "*")
            .replace("multiply", "*")
            .replace("divided by", "/")
            .replace("divide", "/")
            .replace("x", "*")
            .replace("percent of", "* 0.01 *")
            .replace("square of", "^2")
            .replace("what is", "")
            .replace("calculate", "")
            .replace("kitna", "")
            .replace("banta hai", "")
            .trim()

        return try {
            val result = evalExpression(t)
            if (result == result.toLong().toDouble()) {
                result.toLong().toString()
            } else {
                String.format("%.2f", result)
            }
        } catch (e: Exception) { null }
    }

    private fun evalExpression(expr: String): Double {
        val cleaned = expr.replace(" ", "")
        return object : Any() {
            var pos = -1
            var ch = 0

            fun nextChar() {
                ch = if (++pos < cleaned.length) cleaned[pos].code else -1
            }

            fun eat(charToEat: Int): Boolean {
                while (ch == ' '.code) nextChar()
                if (ch == charToEat) { nextChar(); return true }
                return false
            }

            fun parse(): Double {
                nextChar()
                val x = parseExpression()
                if (pos < cleaned.length) throw RuntimeException("Unexpected: ${cleaned[pos]}")
                return x
            }

            fun parseExpression(): Double {
                var x = parseTerm()
                while (true) {
                    x = when {
                        eat('+'.code) -> x + parseTerm()
                        eat('-'.code) -> x - parseTerm()
                        else -> return x
                    }
                }
            }

            fun parseTerm(): Double {
                var x = parseFactor()
                while (true) {
                    x = when {
                        eat('*'.code) -> x * parseFactor()
                        eat('/'.code) -> x / parseFactor()
                        else -> return x
                    }
                }
            }

            fun parseFactor(): Double {
                if (eat('+'.code)) return parseFactor()
                if (eat('-'.code)) return -parseFactor()
                var x: Double
                val startPos = pos
                if (eat('('.code)) {
                    x = parseExpression()
                    eat(')'.code)
                } else if (ch in '0'.code..'9'.code || ch == '.'.code) {
                    while (ch in '0'.code..'9'.code || ch == '.'.code) nextChar()
                    x = cleaned.substring(startPos + 1, pos).toDouble()
                } else {
                    throw RuntimeException("Unexpected: ${ch.toChar()}")
                }
                if (eat('^'.code)) x = Math.pow(x, parseFactor())
                return x
            }
        }.parse()
    }

    fun isDivisionByZero(expr: String): Boolean =
        expr.contains(Regex("/\\s*0(?![.\\d])"))

    fun isCalculation(text: String): Boolean {
        val t = text.lowercase()
        return t.contains(Regex("[+\\-*/]")) ||
            t.contains("plus") || t.contains("minus") ||
            t.contains("times") || t.contains("divided") ||
            t.contains("multiply") || t.contains("calculate") ||
            t.contains("kitna banta") || t.contains("percent")
    }
}
