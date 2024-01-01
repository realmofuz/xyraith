import langimpl.lang.lexer.Lexer
import langimpl.error.ParserError
import langimpl.lang.jvm.AstGatherer
import langimpl.lang.jvm.AstValidator
import langimpl.lang.jvm.Emitter
import langimpl.lang.parser.AstDebugger
import langimpl.lang.parser.Parser
import langimpl.lang.parser.VisitorContext
import langimpl.runtime.startServer
import java.io.File

const val debug = 1
val loader = XyraithLoader()
val classes = mutableMapOf<String, Class<*>>()
val bytecodeClasses = mutableMapOf<String, ByteArray>()
fun main(args: Array<String>) {
    when(args.getOrNull(0)) {
        "run" -> {
                        runServer(true)
        }
        "serverless" -> {
                        runServer(false)
        }
        "init" -> {
                        initProject()
        }
        "help" -> {
            helpCommand()
        }
        else -> {
                        helpCommand()
        }
    }
}

fun helpCommand() {
    println("""
Xyraith's official compiler & tooling
Version v0.3 (??/??/24)

Usage: java -jar Xyraith.jar [subcommand]

Subcommands:
init - Initialize a Xyraith project in the current directory.
run - Run the server. Currently grabs code from file at: ./src/main/xyraith/main.xr
docs - Generate documentation. This will open your web browser.
serverless - Run the Xyraith directory without a server.

Advanced Subcommands:
dumpcommandinfo - Dump a JSON of command info to the file at `docs/commanddump.json`.

    """.trimIndent())
}

fun runServer(withServer: Boolean) {
    val text = File("./src/main.xr").readText()
    val lexer = Lexer(text, "./src/main.xr")


    try {
            val tokens = lexer.transform()
            val parser = Parser(tokens)
            val ast = parser.parseAll()

//            val debugger = AstDebugger()
//            ast.events.map { it.accept(debugger, VisitorContext.None) }

            val gatherer = AstGatherer()
            ast.events.map { it.accept(gatherer, VisitorContext.None) }

            val tc = AstValidator(gatherer)
            ast.events.map { it.accept(tc, VisitorContext.None) }

            val emitter = Emitter(gatherer)
            ast.events.map { it.accept(emitter, VisitorContext.None) }

            for(clazz in emitter.emittedClasses) {
                if(emitter.nativeClasses.contains(clazz.key))
                    continue
                bytecodeClasses[clazz.key] = clazz.value
            }

            println("Starting up...")

            startServer()



    } catch(e: ParserError) {
        println(e.emit())
        e.printStackTrace()
    } catch(e: Exception) {
        println(e.stackTraceToString())
        return
    }
}

fun initProject() {
    File("./src").mkdirs()
    File("./src/main.xr").createNewFile()
    File("./src/main.xr").writeText("""
;; This is a simple server that sends "Hello world!" when you join the server.
;; Use the `run` subcommand to run it.
event join {
    player.sendMessage "Hello world!"    
}
    """)
    File("./xyraith.toml").createNewFile()
    File("./xyraith.toml").writeText("""
[server]
# 0.0.0.0 for local machine, your IP address for public access
host = "0.0.0.0"
# Port to host the server on.
# 25565 recommended & default.
port = 25565
# MOTD to display in serverlist.
# Supports MiniMessage format.
motd = ""
    """.trimIndent())
}

class XyraithLoader : ClassLoader() {
    fun defineClass(name: String, bytes: ByteArray): Class<*> {
        return super.defineClass(name, bytes, 0, bytes.size)
    }
}