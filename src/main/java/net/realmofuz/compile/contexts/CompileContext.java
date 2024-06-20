package net.realmofuz.compile.contexts;

import net.realmofuz.compile.parameters.ArgumentSet;
import net.realmofuz.compile.Compiler;
import net.realmofuz.util.ResourceLocation;

/**
 * Context to transform a Command into Datapack format.
 * @param builder The datapack builder the command should modify.
 * @param args Arguments passed to the command.
 * @param fileName The file name the command is working with.
 * @param compiler The compiler responsible for compiling.
 */
public record CompileContext(
    DatapackBuilder builder,
    ArgumentSet args,
    ResourceLocation fileName,
    Compiler compiler
) {
}
