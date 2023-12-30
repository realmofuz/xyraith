package langimpl.runtime

import bytecodeClasses
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

fun generateJar() {
    File("./xyraithbuild").deleteRecursively()
    File("./xyraithbuild").mkdirs()
    for(pair in bytecodeClasses) {
        val split = pair.key.split("/").toMutableList()
        split.removeLast()
        var cf = "./xyraithbuild"
        println("splitting on $split")
        for(folder in split) {
            val f = File("$cf/$folder/")
            if(!f.exists()) {
                println("making dir for ${f.absolutePath}")
                f.mkdirs()
            }
            cf += "/$folder"
        }
        println("making class file for ./xyraithbuild/${pair.key}.class")
        File("./xyraithbuild/${pair.key}.class").createNewFile()
        File("./xyraithbuild/${pair.key}.class").writeBytes(pair.value)
    }

    File("./xyraithbuild/META-INF/").mkdirs()
    File("./xyraithbuild/META-INF/MANIFEST.MF").createNewFile()
    File("./xyraithbuild/META-INF/MANIFEST.MF").writeText("""
Manifest-Version: 1.0
Created-By: 1.7.0 (XyraithSDK)
Main-Class: Main

    """.trimIndent())

    zipDirectory("./xyraithbuild/", "xyrplugin.jar")
}
fun zipDirectory(directoryPath: String, zipFilePath: String) {
    val sourceFile = File(directoryPath)
    val zipFile = File(zipFilePath)

    val zipOut = ZipOutputStream(FileOutputStream(zipFile))

    zipFiles(sourceFile, sourceFile.name, zipOut)

    zipOut.close()
}

private fun zipFiles(directory: File, parentPathOld: String, zipOut: ZipOutputStream) {
    val files = directory.listFiles() ?: return

    var parentPath = parentPathOld.removePrefix("xyraithbuild")
    parentPath = parentPath.removePrefix("/")
    parentPath = parentPath.trim()
    for (file in files) {
        if (file.isDirectory) {
            if(parentPath.isEmpty())
                zipFiles(file, file.name, zipOut)
            else
                zipFiles(file, "$parentPath/${file.name}", zipOut)
        } else {
            val entry = if(parentPath.isEmpty())
                ZipEntry(file.name)
            else
                ZipEntry("$parentPath/${file.name}")
            zipOut.putNextEntry(entry)

            val fis = FileInputStream(file)
            val buffer = ByteArray(1024)
            var len: Int

            while (fis.read(buffer).also { len = it } > 0) {
                zipOut.write(buffer, 0, len)
            }

            fis.close()
            zipOut.closeEntry()
        }
    }
}
