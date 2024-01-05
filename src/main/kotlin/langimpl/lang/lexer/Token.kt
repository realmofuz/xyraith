package langimpl.lang.lexer

sealed class Token {

    abstract val span: SpanData

    class OpenParen(override val span: SpanData) : Token()
    class CloseParen(override val span: SpanData) : Token()
    class OpenBrace(override val span: SpanData) : Token()
    class CloseBrace(override val span: SpanData) : Token()
    class OpenBracket(override val span: SpanData) : Token()
    class CloseBracket(override val span: SpanData) : Token()
    class GreaterThan(override val span: SpanData) : Token()
    class LessThan(override val span: SpanData) : Token()

    class Identifier(val value: String, override val span: SpanData) : Token()
    class StringText(val value: String, override val span: SpanData) : Token()
    class Number(val value: Double, override val span: SpanData) : Token()

    class EOF(override val span: SpanData) : Token()
    class NewLine(override val span: SpanData) : Token()

    class Colon(override val span: SpanData) : Token()
    class At(override val span: SpanData) : Token()
    class Equals(override val span: SpanData) : Token()
    class Bang(override val span: SpanData) : Token()
    class Dot(override val span: SpanData) : Token()
    class Comma(override val span: SpanData) : Token()
    class Arrow(override val span: SpanData, val isSquiggly: Boolean) : Token()
    class ForEachKeyword(override val span: SpanData) : Token()
    class IfKeyword(override val span: SpanData) : Token()
    class GlobalKeyword(override val span: SpanData) : Token()
    class OnKeyword(override val span: SpanData) : Token()
    class CommandKeyword(override val span: SpanData) : Token()
    class ArrayOfKeyword(override val span: SpanData) : Token()
    class LoopKeyword(override val span: SpanData) : Token()
    class BreakKeyword(override val span: SpanData) : Token()
    class ClassKeyword(override val span: SpanData) : Token()
    class NamespaceKeyword(override val span: SpanData) : Token()
    class LetKeyword(override val span: SpanData) : Token()
    class NewKeyword(override val span: SpanData) : Token()
    class BuiltinKeyword(override val span: SpanData) : Token()
    class IncludeKeyword(override val span: SpanData) : Token()
    class ExtendsKeyword(override val span: SpanData) : Token()
    class ImplementsKeyword(override val span: SpanData) : Token()


    override fun toString(): String {
        return when(this) {
            is OpenParen -> """{"type":"leftParen","span":$span}"""
            is CloseParen -> """{"type":"rightParen","span":$span}"""
            is OpenBrace -> """{"type":"leftBrace","span":$span}"""
            is CloseBrace -> """{"type":"rightBrace","span":$span}"""
            is OpenBracket -> """{"type":"leftBrack","span":$span}"""
            is CloseBracket -> """{"type":"rightBrack","span":$span}"""
            is GreaterThan -> """{"type":"ge","span":$span}"""
            is LessThan -> """{"type":"le","span":$span}"""
            is Identifier -> """{"type":"identifier","value":"${this.value}","span":$span}"""
            is StringText -> """{"type":"string","value":"${this.value}","span":$span}"""
            is Number -> """{"type":"number","value":${this.value},"span":$span}"""

            is EOF -> """{"type":"eof","span":$span}"""
            is NewLine -> """{"type":"newLine","span":$span}"""

            is ForEachKeyword -> """{"type":"foreach","span":$span}"""
            is IfKeyword -> """{"type":"if","span":$span}"""
            is LetKeyword -> """{"type":"let","span":$span}"""

            is Colon -> """{"type":"colon","span":$span}"""
            is Equals -> """{"type":"equals","span":$span}"""
            is Bang -> """{"type":"bang","span":$span}"""
            is Dot -> """{"type":"dot","span":$span}"""
            is Comma -> """{"type":"comma","span":$span}"""
            is At -> """{"type":"at","span":$span}"""
            is Arrow -> """{"type":"arrow","span":$span}"""

            is OnKeyword -> """{"type":"event","span":$span}"""
            is BuiltinKeyword -> """{"type":"native","span":$span}"""
            is CommandKeyword -> """{"type":"function","span":$span}"""
            is GlobalKeyword -> """{"type":"global","span":$span}"""
            is ClassKeyword -> """{"type":"class","span":$span}"""
            is NamespaceKeyword -> """{"type":"namespace","span":$span}"""
            is IncludeKeyword -> """{"type":"import","span":$span}"""
            is NewKeyword -> """{"type":"new","span":$span}"""
            is ArrayOfKeyword -> """{"type":"arrayOf","span":$span}"""
            is LoopKeyword -> """{"type":"loop","span":$span}"""
            is BreakKeyword -> """{"type":"break","span":$span}"""
            is ExtendsKeyword -> """{"type":"extends","span":$span}"""
            is ImplementsKeyword -> """{"type":"implements","span":$span}"""
        }
    }
}