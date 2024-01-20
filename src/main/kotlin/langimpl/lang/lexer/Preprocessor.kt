package langimpl.lang.lexer

import langimpl.error.NotAValidImport
import langimpl.error.UnexpectedEOF
import langimpl.error.UnexpectedToken
import stdlib.stdlibFiles
import java.io.File
import java.io.FileNotFoundException

fun preprocessMain(mainTokens: MutableList<Token>, directory: String): MutableList<Token> {
    val outputTokens = mutableListOf<Token>()

    var hitCode = false

    for ((index, token) in mainTokens.withIndex()) {
        if (!hitCode) {
            if (token is Token.IncludeKeyword) {
                if (mainTokens.size < index) {
                    throw UnexpectedEOF(token.span)
                }
                val next = mainTokens[index + 1]
                if (next !is Token.StringText) {
                    throw UnexpectedToken("string", next, next.span)
                }
                if (stdlibFiles.containsKey(next.value)) {
                    val lexer = Lexer(
                        stdlibFiles[next.value]!!,
                        directory + next.value + ".xr"
                    )
                    val o = lexer.transform()
                    o.removeLast()
                    outputTokens.addAll(
                        preprocessMain(o, directory)
                    )
                } else {
                    try {
                        val lexer = Lexer(
                            File(directory + next.value + ".xr").readText(),
                            directory + next.value + ".xr"
                        )
                        val o = lexer.transform()
                        o.removeLast()
                        outputTokens.addAll(
                            preprocessMain(o, directory)
                        )
                    } catch (e: FileNotFoundException) {
                        throw NotAValidImport(next.span)
                    }
                }
            } else if (token is Token.ClassKeyword || token is Token.NamespaceKeyword) {
                hitCode = true
                outputTokens.add(token)
            }
        } else {
            outputTokens.add(token)
        }
    }
    return outputTokens
}