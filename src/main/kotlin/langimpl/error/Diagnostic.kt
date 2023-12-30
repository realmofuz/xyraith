package langimpl.error

import langimpl.lang.lexer.SpanData
import stdlib.stdlibFiles
import java.io.File

class Diagnostic(val errorCode: Int, val problem: String, val span: SpanData, val help: String? = null) {
    override fun toString(): String {
        val content = if(span.file.startsWith("src/std/")) {
            stdlibFiles[span.file.removePrefix("src/").removeSuffix(".xr")]!!
        } else {
            File(span.file).readText()
        }
        var line = ""
        var ptr = 0
        var lineCount = 1
        var lineStart = 0
        var inRange = false
        for(char in content.toString()) {
            line += char
            ptr++
            if(char == '\n') {
                lineCount++
                if(inRange) {
                    line = line.replace("\n", "")
                    break
                }
                lineStart = ptr
                line = ""
            }
            if(ptr in span.spanStart..span.spanEnd) {
                inRange = true
            }
        }
        val (spanStart, spanEnd, file) = span
        var len = lineCount.toString().length+1
        if(len < 0) len = 1
        val escape = "\u001B"
        return """
$escape[31m[E$errorCode] $problem
${" ".repeat(len)}$escape[32m| $escape[0mIn file `$file`
${" ".repeat(len)}$escape[32m|
$escape[1;36m$lineCount $escape[0m$escape[32m| $escape[0m$line
${" ".repeat(len)}$escape[32m| $escape[31m${" ".repeat(span.spanStart-lineStart)} ${"^".repeat(span.spanEnd-span.spanStart)}
${" ".repeat(len)}$escape[32m|
${if(help == null) "" else "$escape[1;39m= help: $escape[0m$help"}
        """.trimIndent()
    }
}