package langimpl.lang.lexer

import stdlib.stdlibFiles
import java.io.File

data class SpanData(val spanStart: Int, val spanEnd: Int, val file: String) {
    override fun toString(): String {
        return """{"start":$spanStart,"end":$spanEnd,"file":"$file"}"""
    }

    fun calculateLineNumber(): Int {
        println("file: ${file.removePrefix("src/").removeSuffix(".xr")} ${stdlibFiles.keys}")
        val content = if(file.startsWith("src/std"))
                stdlibFiles[file.removePrefix("src/").removeSuffix(".xr")]!!
            else
                File(file).readText()
        var ptr = 0
        var lineCount = 1
        for(char in content) {
            ptr++
            if(char == '\n') {
                lineCount++
            }
            if(ptr in spanStart..spanEnd) {
                break
            }
        }
        return lineCount
    }
}