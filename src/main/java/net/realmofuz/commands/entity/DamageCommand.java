package net.realmofuz.commands.entity;

import net.realmofuz.commanddata.Command;
import net.realmofuz.compile.parameters.ParameterBuilder;
import net.realmofuz.compile.parameters.ParameterSet;
import net.realmofuz.parser.tree.Ast;
import net.realmofuz.parser.tree.Type;

public class DamageCommand implements Command {
    @Override
    public String name() {
        return "damage";
    }

    @Override
    public ParameterSet typeSet() {
        return new ParameterSet(ParameterBuilder.of().name(
            this.name(),
            ParameterBuilder.of()
                .argument("amount", Type.NUMBER)
                .executes(ctx ->
                    ctx.builder().append("damage @s ")
                        .append(ctx.args().<Ast.Value.Number>get("amount").number())
                        .append('\n')
                )
        ).build());
    }
}
