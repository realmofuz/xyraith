package net.realmofuz.compile;

import net.realmofuz.commanddata.Command;
import net.realmofuz.compile.contexts.CompileContext;
import net.realmofuz.compile.contexts.DatapackBuilder;
import net.realmofuz.parser.tree.Ast;
import net.realmofuz.util.ResourceLocation;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class Compiler {
    public static Path TARGET_PATH = Path.of("./target");
    public static Path OUTPUT_PATH = TARGET_PATH.resolve("./datapack");
    public static HashMap<ResourceLocation, Integer> nameSpecialCounts = new HashMap<>();

    Ast.File file;

    public Compiler(Ast.File file) {
        this.file = file;
    }

    void deleteDirectory(Path directoryToBeDeleted) throws IOException {
        if(Files.isDirectory(directoryToBeDeleted)) {
            var allContents = Files.list(directoryToBeDeleted);
            if (allContents != null) {
                allContents.forEach(file -> {
                    try {
                        deleteDirectory(file);
                    } catch (IOException _) {}
                });
            }
            Files.delete(directoryToBeDeleted);
        } else if(Files.isRegularFile(directoryToBeDeleted)) {
            Files.delete(directoryToBeDeleted);
        }

    }

    public void compile(boolean firstRun) throws IOException {
        if (!Files.exists(TARGET_PATH)) {
            Files.createDirectory(TARGET_PATH);
        }

        if(firstRun)
            deleteDirectory(OUTPUT_PATH);

        if (!Files.exists(OUTPUT_PATH)) {
            Files.createDirectory(OUTPUT_PATH);
        }


        for (var command : file.command()) {
            if(command.name().isEmpty())
                continue;

            var c = Command.tryCompile(command);

            var ctx = new CompileContext(
                new DatapackBuilder(),
                c.argumentSet(),
                new ResourceLocation("debug:definitions"),
                this
            );

            c.contextConsumer().accept(ctx);
        }
    }

    public ResourceLocation compileBlockAsUniqueCode(
        ResourceLocation currentName,
        Ast.Value.Block block
    ) throws IOException {
        return compileBlockAsUniqueCode(currentName, block, "", "");
    }

    /**
     * Compiles a block of code with a unique name based off of the current one.
     *
     * @param currentName The current name to compile under
     * @param block       The AST block to compile
     * @throws IOException If an IO error occurs.
     */
    public ResourceLocation compileBlockAsUniqueCode(
        ResourceLocation currentName,
        Ast.Value.Block block,
        String contentBeforeCode,
        String contentAfterCode
    ) throws IOException {
        nameSpecialCounts.put(
            currentName,
            nameSpecialCounts.getOrDefault(currentName, 0) + 1
        );
        var newName = currentName.withSuffix(String.valueOf(nameSpecialCounts.get(currentName)));

        compileBlockAsCode(
            newName,
            block,
            contentBeforeCode,
            contentAfterCode
        );

        return newName;
    }

    public void compileBlockAsCode(
        ResourceLocation name,
        Ast.Value.Block block
    ) {
        this.compileBlockAsCode(name, block, "", "");
    }

    public void compileBlockAsCode(
        ResourceLocation name,
        Ast.Value.Block block,
        String contentBeforeCode,
        String contentAfterCode
    ) {
        try {
            var packMeta = OUTPUT_PATH.resolve("./pack.mcmeta");
            var functionPath = OUTPUT_PATH.resolve(
                "./data/" + name.getNamespace() + "/function/" + name.getPath() + ".mcfunction");
            var functionFolder = OUTPUT_PATH.resolve(
                "./data/" + name.getNamespace() + "/function/");

            if (!Files.exists(functionPath)) {
                Files.createDirectories(functionFolder);
                Files.createDirectories(functionPath.getParent());
                Files.createFile(functionPath);
            }

            if(!Files.exists(packMeta))
                Files.createFile(packMeta);
            Files.writeString(packMeta, """
            {
                "pack": {
                    "pack_format": 48,
                    "description": "Xyraith generated pack"
                }
            }
            """);

            var sb = new DatapackBuilder();
            for (var command : block.commands()) {
                var cmd = Command.tryCompile(command);
                var ctx = new CompileContext(
                    sb,
                    cmd.argumentSet(),
                    name,
                    this
                );
                cmd.contextConsumer().accept(ctx);
            }

            Files.writeString(functionPath,
                contentBeforeCode + sb.toString() + contentAfterCode
            );
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
    }

    public ResourceLocation generateDataFile(
        String location,
        ResourceLocation name,
        String json
    ) {
        try {
            var dataPath = OUTPUT_PATH.resolve(
                "./data/" + name.getNamespace() + "/" + location + "/" + name.getPath() + ".json");
            var dataFolder = OUTPUT_PATH.resolve(
                "./data/" + name.getNamespace() + "/" + location + "/");

            if (!Files.exists(dataPath)) {
                Files.createDirectories(dataFolder);
                Files.createDirectories(dataPath.getParent());
                Files.createFile(dataPath);
            }

            Files.writeString(dataPath, json);

            return name;
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
    }
}
