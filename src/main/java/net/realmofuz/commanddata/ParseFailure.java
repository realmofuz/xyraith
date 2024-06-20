package net.realmofuz.commanddata;

import net.realmofuz.compile.contexts.CompileError;

/**
 * Represents a failed attempt at parsing
 * @param error The error that occured
 * @param skippable Whether this can be skipped reasonably
 * @param depth How far it managed to parse
 */
public record ParseFailure(
    CompileError error,
    boolean skippable,
    int depth
) {
}
