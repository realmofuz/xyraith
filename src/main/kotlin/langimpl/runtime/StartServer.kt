package langimpl.runtime

import classes
import langimpl.error.Unreachable
import java.io.BufferedOutputStream
import java.io.BufferedReader
import java.io.File
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.net.URL

fun startServer() {
    val serverFolder = File("./xyraithserver/")
    if(!serverFolder.exists())
        serverFolder.mkdir()

    val pluginsFolder = File("./xyraithserver/plugins/")
    if(!pluginsFolder.exists())
        pluginsFolder.mkdir()

    val paperJar = File("./xyraithserver/paper.jar")
    if(!paperJar.exists()) {
        println("Installing Paper jar file for you...")
        Thread.sleep(1000)

        // link: https://api.papermc.io/v2/projects/paper/versions/1.20.2/builds/318/downloads/paper-1.20.2-318.jar
        val url = "https://api.papermc.io/v2/projects/paper/versions/1.20.2/builds/318/downloads/paper-1.20.2-318.jar"
        val connection = URL(url).openConnection()
        val inputStream = connection.getInputStream()
        val outputStream = BufferedOutputStream(FileOutputStream("./xyraithserver/paper.jar"))

        val buffer = ByteArray(1024)
        var bytesRead: Int
        var progressCounter = 0
        var pct = 1

        while (inputStream.read(buffer).also { bytesRead = it } != -1) {
            progressCounter++

            if(progressCounter == 398) {
                progressCounter = 0
                val back = when {
                    (1..9).contains(pct) -> 1
                    (10..99).contains(pct) -> 2
                    (100..105).contains(pct) -> 3
                    else -> throw Unreachable()
                }
                pct++
                print("\b".repeat(back+1))
                print("$pct%")
                System.out.flush()
            }
            outputStream.write(buffer, 0, bytesRead)
        }
        print(" DONE!\n")

        outputStream.close()
        inputStream.close()
    }

    val eulaTxt = File("./xyraithserver/eula.txt")
    if(!eulaTxt.exists()) {
        eulaTxt.createNewFile()
        eulaTxt.writeText("#By changing the setting below to TRUE you are indicating your agreement to our EULA (https://aka.ms/MinecraftEULA).\n" +
                "#Timestamp\n" +
                "eula=true\n")
        println("By using Xyraith you are indicating your agreement to Minecraft's EULA. (https://aka.ms/MinecraftEULA)")
    }

    generateJar()

    println("Xyraith server files initiated and Xyraith plugin created, passing it over to PaperMC.")
    println("Show them support, they do amazing work! https://papermc.io/")
    println("You can view the PaperMC source code here: https://github.com/PaperMC/Paper")

    val jarPath = "./paper.jar"
    val processBuilder = ProcessBuilder("java", "-jar", jarPath)
    processBuilder.directory(File("${System.getProperty("user.dir").removeSuffix("/")}/xyraithserver"))
    val process = processBuilder.start()
    val exitCode = process.waitFor()

    println("PaperMC exited with exit code $exitCode")
}