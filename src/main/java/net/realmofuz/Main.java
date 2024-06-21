package net.realmofuz;

import net.realmofuz.compile.Compiler;
import net.realmofuz.compile.contexts.CompileError;
import net.realmofuz.parser.Parser;
import net.realmofuz.util.FileCollector;
import net.realmofuz.util.Logger;

import java.io.IOException;

public class Main {
    public static FileCollector COLLECTOR = new FileCollector();
    public static void main(String[] args) throws IOException {
        COLLECTOR.collect();

        var firstRun = true;
        for (var fileName : COLLECTOR.files.keySet()) {
            try {
                var fileText = COLLECTOR.files.get(fileName);
                var parser = new Parser(fileText, fileName);
                var ast = parser.parseFile();
                var compiler = new Compiler(ast);
                compiler.compile(firstRun);
            } catch (Error e) {
                if (e instanceof CompileError ce) {
                    prettyPrintError(ce);
                    // e.printStackTrace();
                    firstRun = false;
                    continue;
                }
                throw e;
            }

            if (firstRun)
                firstRun = false;
        }
    }

    public static void prettyPrintError(CompileError ce) {
        var column = 1;
        var row = 0;

        var file = COLLECTOR.files.get(ce.span().fileName()) + "\n\n\n\n\n";

        var currentLine = new StringBuilder();

        currentLine = new StringBuilder();
        var ci = 0;
        char ch2;
        while((ch2 = file.charAt(ci)) != '\n') {
            currentLine.append(ch2);
            ci++;
        }

        for(int i = 0; i<file.length(); i++) {
            var ch = file.charAt(i);
            if(ch == '\n') {
                row = 0;
                column++;

                currentLine = new StringBuilder();
                ci = i+1;
                try {
                    while((ch2 = file.charAt(ci)) != '\n') {
                        currentLine.append(ch2);
                        ci++;
                    }
                } catch (StringIndexOutOfBoundsException _) {}
                System.out.println("cl: " + currentLine);
            }

            if(i > ce.span().point())
                break;
            row++;
        }

        /*
        Example error:
╔═ Failed to compile file: .\src\main.xr ═════════════════════════════════════╗
╠═══════╬═════════════════════════════════════════════════════════════════════╣
║ Error ║ The identifier __x is invalid.                                      ║
╠═══════╬═════════════════════════════════════════════════════════════════════╣
║       ║                                                                     ║
║ 3     ║ global __x += 15                                                    ║
║       ║                                                                     ║
╠═══════╬═════════════════════════════════════════════════════════════════════╣
║  Help ║ Remove the two leading underscores: x                               ║
╚═══════╩═════════════════════════════════════════════════════════════════════╝
         */

        var line = currentLine.toString().trim();
        System.out.println("╔═ Failed to compile file: " + ce.span().fileName() + " " + "═".repeat(78-28-ce.span().fileName().length()) + "╗");
        System.out.println("╠═══════╬═════════════════════════════════════════════════════════════════════╣");
        System.out.println("║ Error ║ " + ce.errorLog() + " ".repeat(78-10-ce.errorLog().length()) + "║");
        System.out.println("╠═══════╬═════════════════════════════════════════════════════════════════════╣");
        System.out.println("║       ║                                                                     ║");
        System.out.println("║ " + column + " ".repeat(5-(String.valueOf(column).length()))
            + " ║ " + line + " ".repeat(78-10-line.length()) + "║");
        System.out.println("║       ║                                                                     ║");
        System.out.println("╠═══════╬═════════════════════════════════════════════════════════════════════╣");
        System.out.println("║  Help ║ " + ce.helpMessage() + " ".repeat(78-10-ce.helpMessage().length()) + "║");
        System.out.println("╚═══════╩═════════════════════════════════════════════════════════════════════╝");
    }
}