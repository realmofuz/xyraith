package net.realmofuz.compile.contexts;

import net.realmofuz.compile.parameters.ArgumentSet;

import java.util.function.Consumer;

/**
 * Represents the result of a single command being compiled.
 * @param argumentSet The argument set given.
 * @param contextConsumer The consumer to invoke during datapack transformation.
 */
public record CompileSingleCommandResult(
    ArgumentSet argumentSet,
    Consumer<CompileContext> contextConsumer
) {
}
