package net.realmofuz.commands.definitions;

import net.realmofuz.commanddata.Command;
import net.realmofuz.compile.parameters.ParameterBuilder;
import net.realmofuz.compile.parameters.ParameterSet;
import net.realmofuz.parser.tree.Ast;
import net.realmofuz.parser.tree.Type;

public class DefineCommand implements Command {
    @Override
    public String name() {
        return "define";
    }

    @Override
    public ParameterSet typeSet() {
        return new ParameterSet(ParameterBuilder.of().name(
            this.name(),
            ParameterBuilder.of()
                .literal("function")
                .argument("namespace", Type.RESOURCE_LOCATION)
                .argument("code", Type.BLOCK)
                .executes(ctx ->
                    ctx.compiler().compileBlockAsCode(
                        ctx.args().<Ast.Value.ResourceLocationValue>get("namespace").value(),
                        ctx.args().get("code")
                    )
                ),
            ParameterBuilder.of()
                .literal("event")
                .argument("event_name", Type.RESOURCE_LOCATION)
                .argument("code", Type.BLOCK)
                .executes(ctx -> {
                    var eventName = ctx.args()
                        .<Ast.Value.ResourceLocationValue>get("event_name")
                        .value()
                        .prependPath("event");
                    ctx.compiler().generateDataFile(
                        "advancement",
                        eventName,
                        """
                            {
                              "criteria": {
                                "Event Trigger": {
                                  "trigger": "${event}"
                                }
                              },
                              "rewards": {
                                "function": "${function}"
                              }
                            }
                            """.replace("${event}", eventName.names().getLast())
                            .replace("${function}", eventName.toString())
                    );

                    ctx.compiler().compileBlockAsCode(
                        eventName,
                        ctx.args().get("code"),
                        "",
                        "advancement revoke @s only " + eventName.toString()
                    );
                })
        ).build());
    }
}
