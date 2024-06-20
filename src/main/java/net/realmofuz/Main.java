package net.realmofuz;

import net.realmofuz.compile.Compiler;
import net.realmofuz.compile.contexts.CompileError;
import net.realmofuz.parser.Parser;
import net.realmofuz.util.FileCollector;

import java.io.IOException;

public class Main {
    public static void main(String[] args) throws IOException {
        var collector = new FileCollector();
        collector.collect();
        System.out.println(collector.files);

        var firstRun = true;
        for(var fileName : collector.files.keySet()) {
            var fileText = collector.files.get(fileName);
            var parser = new Parser(fileText, fileName);
            var ast = parser.parseFile();
            System.out.println(ast);
            var compiler = new Compiler(ast);

            try {
                compiler.compile(firstRun);
            } catch (Error e) {
                if(e instanceof CompileError ce) {
                    System.out.println(ce.errorLog());
                    System.out.println(ce.span());
                    e.printStackTrace();
                    firstRun = false;
                    continue;
                }
                throw e;
            }

            if(firstRun)
                firstRun = false;
        }

    }
}