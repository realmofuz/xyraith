package net.realmofuz.util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;

public class FileCollector {
    public HashMap<String, String> files = new HashMap<>();

    public void collect() throws IOException {
        try(var walk = Files.walk(Path.of("./"))) {
            System.out.println("got here");
            walk
                .filter(it -> it.getFileName().toString().endsWith(".xr"))
                .forEach(it -> {
                    System.out.println(it);
                    try {
                        files.put(it.toString(), Files.readString(it));
                    } catch (IOException _) {}
                });
        }
    }
}
