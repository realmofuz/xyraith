import org.jetbrains.kotlin.backend.wasm.ir2wasm.bind
import org.objectweb.asm.*
import java.util.UUID

val majorVersion = 0
val minorVersion = 3
val patchVersion = 0

plugins {
    kotlin("jvm") version "1.9.0"
    application
    id("com.github.johnrengelman.shadow") version "8.1.1"
    id("java")
}

group = "net.realmofuz"
version = "0.2.0"

repositories {
    mavenCentral()
    maven { url = uri("https://jitpack.io") }
}

dependencies {
    testImplementation(kotlin("test"))
    implementation("org.ow2.asm:asm:9.2")
    implementation("org.ow2.asm:asm-util:9.2")
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(17)
}

application {
    mainClass.set("MainKt")
}

/*
Generate the standard library from the source files
 */
fun generateStdlib(): String {
    var output = "package stdlib\n\n//Automatically generated in `build.gradle.kts`\n\n"
    output += "val stdlibFiles = mutableMapOf<String, String>(\n"
    File("${project.rootDir.path}/std/").walk().forEach {
        if(it.isFile && !it.isDirectory) {
            val path = it.canonicalPath
                .replace(project.rootDir.path + "\\", "")
                .replace("\\", "/")
                .removeSuffix(".xr")
            output += "\"${path}\" to \"\"\"${it.readText()}\"\"\"\n,"
        }
    }
    output += "\n)"
    return output
}

abstract class StdlibTask : DefaultTask() {
    @TaskAction
    fun generateStdlibPublic() {
        var output = "package stdlib\n\n//Automatically generated in `build.gradle.kts`\n\n"
        output += "val stdlibFiles = mutableMapOf<String, String>(\n"
        File("${project.rootDir.path}/std/").walk().forEach {
            if(it.isFile && !it.isDirectory) {
                val path = it.canonicalPath
                    .replace(project.rootDir.path + "\\", "")
                    .replace("\\", "/")
                    .removeSuffix(".xr")
                output += "\"${path}\" to \"\"\"\nclass Temp_${UUID.randomUUID().toString().replace("-", "_")} {}\n${it.readText()}\"\"\"\n,"
            }
        }
        output += "\n)"
        File("${project.rootDir.path}/src/main/kotlin/langimpl/stdlib/Stdlib.kt").writeText(output)
    }
}

abstract class BindingTask : DefaultTask() {
    fun forEachClassFile(run: (ClassReader) -> Unit) {
        File("${project.rootDir.path}/bindings/").walk().forEach {
            if(it.isFile && !it.isDirectory && it.name.endsWith(".class")) {
                val clazz = ClassReader(it.readBytes())
                run(clazz)
            }
        }
    }
    @TaskAction
    fun generateBindings(): String {
        val visitor = BindingVisitor()
        forEachClassFile {
            it.accept(
                visitor,
                ClassReader.SKIP_CODE)
            File("./std/papermc.xr").writeText(visitor.bindingString
                .replace("$", "__inacc__")
                .replace("package-info", "java__package_info"))
        }
        return """"""
    }
}

class BindingVisitor : ClassVisitor(Opcodes.ASM9) {
    var bindingString: String = ""
    override fun visit(
        version: Int,
        access: Int,
        name: String?,
        signature: String?,
        superName: String?,
        interfaces: Array<out String>?
    ) {

        val inters = if(access and 512 == 512)
            "@interface\n"
        else
            ""

        val implements = if(interfaces != null && interfaces.isNotEmpty())
            "implements " + interfaces.map { it.replace("/", ".") }.joinToString(",") + " "
        else
            ""

        val extends = if(superName == "java/lang/Object")
            ""
        else
            "extends ${superName!!.replace("/", ".")} "
        bindingString += "//access: ${access}\n@native\n${inters}class ${name!!.replace("/", ".")} ${extends}${implements}{\n"
    }

    override fun visitEnd() {
        bindingString += "}\n\n"
    }
    override fun visitMethod(
        access: Int,
        name: String?,
        descriptor: String?,
        signature: String?,
        exceptions: Array<out String>?
    ): MethodVisitor? {
        val accessing = if(access == 8)
            "\t@static\n"
        else
            ""

        if(name == "<init>") {
            bindingString += "  @native\n${accessing}  command construct ${stripDescriptor(descriptor!!)} {}\n"
        } else if(name == "<clinit>") {} else {
            bindingString += "  @native\n${accessing}  command $name ${stripDescriptor(descriptor!!)} {}\n"
        }
        return null
    }

    override fun visitField(
        access: Int,
        name: String?,
        descriptor: String?,
        signature: String?,
        value: Any?
    ): FieldVisitor? {
        val accessing = if(access == 8)
            "@static\n"
        else
            ""
        bindingString += "  @native\n${accessing}  let $name: ${stripDescriptor(descriptor!!)} = 0\n"
        return null
    }

    fun stripDescriptor(descriptor: String): String {
        if(!descriptor.startsWith("(")) {
            val type = descriptor
                .removeSuffix("(")
                .removePrefix(")")
            return when {
                type.startsWith("[") -> {
                    val newType = stripDescriptor(type.removePrefix("["))
                    return "array<${stripDescriptor(newType)}>"
                }
                type == "Ljava/lang/String;" -> "string"
                type.startsWith("L") && type.endsWith(";") -> {
                    return type
                        .removePrefix("L")
                        .removeSuffix(";")
                        .replace("/", ".")
                }
                type == "D" -> "number"
                type == "I" -> "jvm_integer"
                type == "Z" -> "boolean"
                type == "V" -> "void"
                else -> "Unknown<$type>"
            }
        } else {
            val split = descriptor.split(")")
            val arguments = split[0].removePrefix("(")
            val returns = split[1]
            val types = mutableListOf<String>()
            var charPointer = 0
            var isArray = false
            while(true) {
                if(charPointer >= (arguments.length-1))
                    break
                val prepend = if(isArray)
                    "["
                else
                    ""
                var char = arguments[charPointer]
                when(char) {
                    'D' -> types.add(prepend + char.toString())
                    'I' -> types.add(prepend + char.toString())
                    'Z' -> types.add(prepend + char.toString())
                    'V' -> types.add(prepend + char.toString())
                    '[' -> isArray = true
                    'L' -> {
                        var builder = ""
                        while(char != ';') {
                            builder += char
                            charPointer++
                            char = arguments[charPointer]
                        }
                        types.add("$prepend$builder;")
                    }
                }
                charPointer++
            }
            val argumentNames = listOf("a", "b", "c", "d", "e", "f", "g", "h", "i", "j", "k", "l", "m", "n", "o", "p",
                "q", "r", "s", "t", "u", "v", "w", "x", "y", "z")
            val mapped = types.mapIndexed { index, s -> "arg${argumentNames[index]}:${stripDescriptor(s)}" }
            return "${mapped.joinToString(" ")} -> ${stripDescriptor(returns)}"
        }
    }
}
tasks.register<BindingTask>("generateBindings")
tasks.register<StdlibTask>("generateStdlib")
