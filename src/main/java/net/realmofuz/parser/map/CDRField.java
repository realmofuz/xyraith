package net.realmofuz.parser.map;

import net.realmofuz.parser.tree.Type;

public record CDRField(
    String name,
    Type expected,
    boolean optional
) {
}
