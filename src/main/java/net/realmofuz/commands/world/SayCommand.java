package net.realmofuz.commands.world;

import net.realmofuz.commanddata.Command;
import net.realmofuz.compile.parameters.ParameterBuilder;
import net.realmofuz.compile.parameters.ParameterSet;
import net.realmofuz.parser.tree.Ast;
import net.realmofuz.parser.tree.Type;

public class SayCommand implements Command {
    @Override
    public String name() {
        return "say";
    }

    @Override
    public ParameterSet typeSet() {
        return new ParameterSet(ParameterBuilder.of().name(
            this.name(),
            ParameterBuilder.of()
                .argument("message", Type.STRING)
                .executes(ctx ->
                    ctx.builder().append("say ")
                        .append(ctx.args().<Ast.Value.String>get("message").value())
                        .append('\n')
                )
        ).build());
    }
}
