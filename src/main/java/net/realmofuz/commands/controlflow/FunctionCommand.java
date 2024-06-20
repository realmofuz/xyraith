package net.realmofuz.commands.controlflow;

import net.realmofuz.commanddata.Command;
import net.realmofuz.compile.parameters.ParameterBuilder;
import net.realmofuz.compile.parameters.ParameterSet;
import net.realmofuz.parser.tree.Ast;
import net.realmofuz.parser.tree.Type;

public class FunctionCommand implements Command {
    @Override
    public String name() {
        return "function";
    }

    @Override
    public ParameterSet typeSet() {
        return new ParameterSet(ParameterBuilder.of().name(
            this.name(),
            ParameterBuilder.of()
                .argument("function_to_go_to", Type.LITERAL)
                .executes(ctx ->
                    ctx.builder().append("function ")
                        .append(ctx.args().<Ast.Value.Literal>get("function_to_go_to").value())
                        .append('\n')
                )
        ).build());
    }
}
