package langimpl.error

import langimpl.lang.lexer.SpanData
import langimpl.lang.lexer.Token
import parser.Type
import kotlin.math.exp

abstract class ParserError(open val span: SpanData) : Exception() {
    abstract fun emit(): Diagnostic
}

class UnexpectedToken(val expected: String, val found: Token, override val span: SpanData) : ParserError(span) {
    override fun emit(): Diagnostic {
        return Diagnostic(1, "expected ${expected}, found ${found}", span)
    }
}

class UnexpectedEOF(override val span: SpanData) : ParserError(span) {
    override fun emit(): Diagnostic {
        return Diagnostic(2, "unexpected end of file", span)
    }
}

class NotAValidImport(override val span: SpanData) : ParserError(span) {
    override fun emit(): Diagnostic {
        return Diagnostic(3, "this is not a valid import", span)
    }
}

class NotANumber(override val span: SpanData) : ParserError(span) {
    override fun emit(): Diagnostic {
        return Diagnostic(4, "not a valid number", span)
    }
}

class UnknownType(override val span: SpanData) : ParserError(span) {
    override fun emit(): Diagnostic {
        return Diagnostic(5, "failed to infer type", span)
    }
}

class InvalidFunction(override val span: SpanData) : ParserError(span) {
    override fun emit(): Diagnostic {
        return Diagnostic(6, "not a function in this scope", span, "try reordering methods or classes")
    }
}
class InvalidClass(override val span: SpanData) : ParserError(span) {
    override fun emit(): Diagnostic {
        return Diagnostic(7, "not a function in this scope", span, "try reordering classes or methods")
    }
}
class InvalidType(private val expected: Type, private val found: Type, override val span: SpanData) : ParserError(span) {
    override fun emit(): Diagnostic {
        return Diagnostic(8, "incorrect type", span, "expected $expected, found $found")
    }
}

class FailedToInferType(override val span: SpanData) : ParserError(span) {
    override fun emit(): Diagnostic {
        return Diagnostic(8, "failed to infer type", span, "specify it explicitly")
    }
}



class Unreachable : Exception()

// thanks chatgpt
fun calculateLevenshteinDistance(s1: String, s2: String): Int {
    val m = s1.length
    val n = s2.length

    val dp = Array(m + 1) { IntArray(n + 1) }

    for (i in 0..m) {
        for (j in 0..n) {
            if (i == 0) {
                dp[i][j] = j
            } else if (j == 0) {
                dp[i][j] = i
            } else if (s1[i - 1] == s2[j - 1]) {
                dp[i][j] = dp[i - 1][j - 1]
            } else {
                dp[i][j] = 1 + minOf(dp[i - 1][j], dp[i][j - 1], dp[i - 1][j - 1])
            }
        }
    }

    return dp[m][n]
}
