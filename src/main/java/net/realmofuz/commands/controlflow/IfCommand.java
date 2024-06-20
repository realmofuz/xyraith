package net.realmofuz.commands.controlflow;

import net.realmofuz.commanddata.Command;
import net.realmofuz.compile.parameters.ParameterBuilder;
import net.realmofuz.compile.parameters.ParameterSet;
import net.realmofuz.parser.tree.Ast;
import net.realmofuz.parser.tree.Type;

import java.io.IOException;

public class IfCommand implements Command {
    @Override
    public String name() {
        return "if";
    }

    @Override
    public ParameterSet typeSet() {
        return new ParameterSet(ParameterBuilder.of().name(
            this.name(),
            ParameterBuilder.of()
                .literal("entity")
                .argument("selection", Type.SELECTOR)
                .argument("code", Type.BLOCK)
                .executes(ctx -> {
                    try {
                        var n = ctx.compiler().compileBlockAsUniqueCode(
                            ctx.fileName(),
                            ctx.args().<Ast.Value.Block>get("code")
                        );

                        ctx.builder().append("execute if entity @")
                            .append(ctx.args().<Ast.Value.Selection>get("selection").value())
                            .append(" run function ")
                            .append(n)
                            .append('\n');
                    } catch (IOException _) {
                    }
                })
        ).build());
    }
}
