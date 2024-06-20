package net.realmofuz.commanddata;

import net.realmofuz.compile.parameters.ArgumentSet;

/**
 * Represents the result parsing data associated with a command
 * @param command The regular command desired
 * @param argumentSet The associated argument set
 */
public record CommandParseData(
    Command command,
    ArgumentSet argumentSet
) {
}
