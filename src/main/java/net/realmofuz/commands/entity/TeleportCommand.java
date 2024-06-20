package net.realmofuz.commands.entity;

import net.realmofuz.commanddata.Command;
import net.realmofuz.compile.parameters.ParameterBuilder;
import net.realmofuz.compile.parameters.ParameterSet;
import net.realmofuz.parser.tree.Ast;
import net.realmofuz.parser.tree.Type;

public class TeleportCommand implements Command {
    @Override
    public String name() {
        return "teleport";
    }

    @Override
    public ParameterSet typeSet() {
        return new ParameterSet(ParameterBuilder.of().name(
            this.name(),
            ParameterBuilder.of()
                .argument("location", Type.LOCATION)
                .executes(ctx -> {
                    var loc = ctx.args().<Ast.Value.Component>get("location");
                    ctx.builder().append("teleport @s ")
                        .append(loc.<Ast.Value.Number>get("x"))
                        .append(" ")
                        .append(loc.<Ast.Value.Number>get("y"))
                        .append(" ")
                        .append(loc.<Ast.Value.Number>get("z"));
                    if(loc.has("pitch"))
                        ctx.builder().append(" ").append(loc.get("pitch"));
                    else
                        ctx.builder().append(" 0");
                    if(loc.has("yaw"))
                        ctx.builder().append(" ").append(loc.get("yaw"));
                    else
                        ctx.builder().append(" 0");

                    ctx.builder().append("\n");

                })
        ).build());
    }
}
