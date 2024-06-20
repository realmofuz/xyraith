package net.realmofuz.parser.map;

import net.realmofuz.compile.contexts.CompileError;
import net.realmofuz.parser.tree.Ast;
import net.realmofuz.util.Result;

import java.util.List;

public record ComponentDataRestriction(
    List<CDRField> fields
) {
    public Result<Void, CompileError> validateMap(
        Ast.Value.Component component,
        boolean allowOtherFields
    ) {
        for(var f : fields()) {
            if(f.optional())
                continue;
            if(!component.component().containsKey(f.name()))
                return new Result.Err<>(new CompileError.MapMissingKey(
                    f.name(),
                    f.expected(),
                    component.span()
                ));
            if(!f.expected().ifOtherIsSubtype(
                component.component().get(f.name()).type()
            ))
                return new Result.Err<>(new CompileError.MapKeyBadType(
                    f.name(),
                    f.expected(),
                    component.component().get(f.name()).type(),
                    component.span()
                ));
        }
        if(!allowOtherFields) {
            var fieldNames = fields
                .stream()
                .map(CDRField::name)
                .toList();
            for(var k : component.component().keySet()) {
                if(!fieldNames.contains(k))
                    return new Result.Err<>(new CompileError.UnexpectedMapKey(
                        k,
                        component.span()
                    ));
            }
        }
        return new Result.Ok<>(null);
    }
}
