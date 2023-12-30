package langimpl.lang.lexer

data class SpanData(val spanStart: Int, val spanEnd: Int, val file: String) {
    override fun toString(): String {
        return """{"start":$spanStart,"end":$spanEnd,"file":"$file"}"""
    }
}