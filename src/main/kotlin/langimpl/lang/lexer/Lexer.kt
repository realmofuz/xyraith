package langimpl.lang.lexer

import langimpl.error.NotANumber
import java.lang.NumberFormatException

class Lexer(val source: String, val file: String) {
    fun transform(): MutableList<Token> {
        val output: MutableList<Token> = mutableListOf()
        val source = source.trim()
        var position = 0
        while(position < source.length) {
            when {
                source[position] == '\\' -> {
                    position++
                    while(source[position].isWhitespace())
                        position++

                }
                source[position] == '/' -> {
                    position++
                    while(source[position] != '\n')
                        position++
                }
                source[position] == '\n' -> {
                    output.add(Token.NewLine(SpanData(position, position++, file)))
                }
                source[position].isWhitespace() -> {
                    position++
                }
                source[position] == '(' -> {
                    output.add(Token.OpenParen(SpanData(position, ++position, file)))
                }
                source[position] == ')' -> {
                    output.add(Token.CloseParen(SpanData(position, ++position, file)))
                }
                source[position] == '>' -> {
                    output.add(Token.GreaterThan(SpanData(position, ++position, file)))
                }
                source[position] == '<' -> {
                    output.add(Token.LessThan(SpanData(position, ++position, file)))
                }
                source[position] == '{' -> {
                    output.add(Token.OpenBrace(SpanData(position, ++position, file)))
                }
                source[position] == '}' -> {
                    output.add(Token.CloseBrace(SpanData(position, ++position, file)))
                }
                source[position] == '[' -> {
                    output.add(Token.OpenBracket(SpanData(position, ++position, file)))
                }
                source[position] == ']' -> {
                    output.add(Token.CloseBracket(SpanData(position, ++position, file)))
                }
                source[position] == '@' -> {
                    output.add(Token.At(SpanData(position, ++position, file)))
                }
                source[position] == '.' -> {
                    output.add(Token.Dot(SpanData(position, ++position, file)))
                }
                source[position] == ':' -> {
                    output.add(Token.Colon(SpanData(position, ++position, file)))
                }
                source[position] == '=' -> {
                    output.add(Token.Equals(SpanData(position, ++position, file)))
                }
                source[position] == '!' -> {
                    output.add(Token.Bang(SpanData(position, ++position, file)))
                }
                source[position] == ',' -> {
                    output.add(Token.Comma(SpanData(position, ++position, file)))
                }
                source[position].isDigit() || source[position] == '-' || source[position] == '~' -> {
                    val spanStart = position
                    var number = ""
                    while(position < source.length &&
                        (source[position].isDigit()
                                || source[position] == '-'
                                || source[position] == '~'
                                || source[position] == '.'
                                // hacky fix to make -> writable
                                || source[position] == '>')) {
                        number = "$number${source[position]}"
                        position++
                    }
                    try {
                        output.add(Token.Number(number.toDouble(), SpanData(spanStart, position, file)))
                    } catch(e: NumberFormatException) {
                        if(number == "->") {
                            output.add(Token.Arrow(SpanData(spanStart, position, file), false))
                        } else if(number == "~>") {
                            output.add(Token.Arrow(SpanData(spanStart, position, file), true))
                        } else {
                            throw NotANumber(SpanData(spanStart, position, file))
                        }

                    }
                }
                source[position] == '"' -> {
                    val spanStart = position
                    var string = ""
                    position++
                    while(position < source.length && source[position] != '"') {
                        string = "$string${source[position]}"
                        position++
                    }
                    output.add(Token.StringText(string, SpanData(spanStart, position, file)))
                    position++
                }
                source[position] == ';' -> {
                    position++
                    while(position < source.length && source[position] != '\n') {
                        position++
                    }
                    position++
                }
                else -> {
                    val spanStart = position
                    var symbol = ""
                    var iters = 0
                    while (position < source.length &&
                        !source[position].isWhitespace() &&
                        "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ1234567890_".contains(source[position])) {
                        iters++
                        symbol = "$symbol${source[position]}"
                        position++
                    }
                    if(iters == 0)
                        position++
                    val span = SpanData(spanStart, position, file)
                    when(symbol) {
                        "arrayOf" -> output.add(Token.ArrayOfKeyword(span))
                        "let" -> output.add(Token.LetKeyword(span))
                        "if" -> output.add(Token.IfKeyword(span))
                        "foreach" -> output.add(Token.ForEachKeyword(span))
                        "on" -> output.add(Token.OnKeyword(span))
                        "command" -> output.add(Token.CommandKeyword(span))
                        "class" -> output.add(Token.ClassKeyword(span))
                        "namespace" -> output.add(Token.NamespaceKeyword(span))
                        "new" -> output.add(Token.NewKeyword(span))
                        "builtin" -> output.add(Token.BuiltinKeyword(span))
                        "loop" -> output.add(Token.LoopKeyword(span))
                        "break" -> output.add(Token.BreakKeyword(span))
                        "include" -> output.add(Token.IncludeKeyword(span))
                        "extends" -> output.add(Token.ExtendsKeyword(span))
                        "implements" -> output.add(Token.ImplementsKeyword(span))
                        else -> output.add(Token.Identifier(symbol, span))
                    }
                }
            }
        }
        output.add(Token.EOF(SpanData(position, position + 1, file)))
        return preprocessMain(output, "src/")
    }
}