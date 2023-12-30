package langimpl.lang.jvm

import langimpl.error.FailedToInferType
import langimpl.error.InvalidClass
import langimpl.error.InvalidFunction
import langimpl.error.InvalidType
import langimpl.lang.parser.AstVisitor
import langimpl.lang.parser.VisitorContext
import parser.Ast
import parser.PathName
import parser.Type
import kotlin.io.path.Path

enum class FunctionType {
    STATIC_FIELD,
    STATIC_METHOD,
    MEMBER_FIELD,
    MEMBER_METHOD;
}
class AstValidator : AstVisitor {
    val classes: MutableList<PathName> = mutableListOf()
    val functions: MutableMap<String, JvmMethodSignature> = mutableMapOf()
    val functionTypes: MutableMap<String, FunctionType> = mutableMapOf()
    val localVariables: MutableMap<String, Type> = mutableMapOf()
    lateinit var currentClass: Ast.Class
    val annotations: MutableList<PathName> = mutableListOf()

    private fun evaluateType(value: Ast.Value): Type {
        return when(value) {
            is Ast.ArrayOf -> Type.Array(value.type)
            is Ast.Boolean -> Type.Boolean
            is Ast.ConstructClass -> Type.Object(
                value.className,
                listOf(),
                false
            )
            is Ast.Access -> {
                val funcSig = JvmMethodSignature(
                    value.name.resolve().replace(".", "__"),
                    currentClass.name,
                    value.arguments.map { evaluateType(it.argument) }.toList(),
                    value.returns,
                    HeaderType.METHOD
                )
                val isig = funcSig.generateInternalSignature()
                if(!functions.containsKey(isig)) {
                    throw InvalidFunction(value.nameSpan)
                }
                return functions[isig]!!.returns
            }
            Ast.Null -> TODO()
            is Ast.Number -> return Type.Number
            is Ast.StringText -> return Type.String
            is Ast.Variable -> {
                if(localVariables.containsKey(value.value))
                    return localVariables[value.value]!!
                return Type.Void
            }
        }
    }

    override fun visit(clazz: Ast.Class, context: VisitorContext) {
        currentClass = clazz
        localVariables.clear()
        classes.add(clazz.name)
    }

    override fun visit(block: Ast.Block, context: VisitorContext) {

    }

    override fun visit(annotation: Ast.Annotation, context: VisitorContext) {
        annotations.add(annotation.name)
    }

    override fun visit(function: Ast.Function, context: VisitorContext): Boolean {
        val sig = JvmMethodSignature(
            function.name.resolve().replace(".", "__"),
            currentClass.name,
            function.parameters.values.toList(),
            function.returns,
            HeaderType.METHOD
        )
        functions[sig.generateInternalSignature()] = sig
        functionTypes[sig.generateInternalSignature()] =
            if(annotations.contains(PathName.parse("static")) || currentClass.static)
                FunctionType.STATIC_METHOD
            else
                FunctionType.MEMBER_METHOD
        val b = !annotations.contains(PathName.parse("native"))
        annotations.clear()
        return b
    }

    override fun visit(event: Ast.Event, context: VisitorContext): Boolean {
        when(event.name) {
            "join", "startup" -> {}
            else -> throw InvalidFunction(event.eventNameSpan)
        }
        val b = !annotations.contains(PathName.parse("native"))
        annotations.clear()
        return b
    }

    override fun visit(field: Ast.DeclareField, context: VisitorContext): Boolean {

        val sig = JvmMethodSignature(
            field.name,
            currentClass.name,
            listOf(),
            field.type,
            HeaderType.FIELD
        )
        functions[sig.generateInternalSignature()] = sig
        if(!evaluateType(field.value).equalTo(field.type)
            && !annotations.contains(PathName.parse("native"))) {
            throw InvalidType(field.type, evaluateType(field.value), field.span)
        }
        val b = !annotations.contains(PathName.parse("native"))
        annotations.clear()
        return b
    }

    override fun visit(value: Ast.Value, context: VisitorContext) {

    }

    override fun visit(clazz: Ast.ConstructClass, context: VisitorContext) {
        if(!classes.contains(clazz.className)
            && !clazz.className.resolve().startsWith("java.")) {
            throw InvalidClass(clazz.classSpan)
        }
    }

    override fun visit(access: Ast.Access, context: VisitorContext) {
        val reserved = listOf(
            "add", "sub", "mul", "div", "return", "jvmarraylen", "jvmarrayindex"
        )
        if(reserved.contains(access.name.resolve()))
            return
        val path = access.name.resolve().split(".").toMutableList()
        val fn = path.removeLast()
        val signature = JvmMethodSignature(
            fn,
            PathName(path),
            access.arguments.map { evaluateType(it.argument) },
            access.returns,
            HeaderType.METHOD,
        )
        val sig = signature.generateInternalSignature()
        println("functions:\n${functions.keys}\ncomparing: ${signature.generateInternalSignature()}")

        if(!functions.containsKey(signature.generateInternalSignature())) {
            throw InvalidFunction(access.nameSpan)
        }
    }

    override fun visit(declareVariable: Ast.DeclareVariable, context: VisitorContext) {
        localVariables[declareVariable.name] = if(declareVariable.type is Type.Void)
            evaluateType(declareVariable.value)
        else
            declareVariable.type
        println("type of value ${declareVariable.value} is ${evaluateType(declareVariable.value)}")

        if(!evaluateType(declareVariable.value).equalTo(localVariables[declareVariable.name]!!)
            && declareVariable.type !is Type.Void) {
            throw InvalidType(
                localVariables[declareVariable.name]!!,
                evaluateType(declareVariable.value),
                declareVariable.span)
        }
    }

    override fun visit(storeVariable: Ast.StoreVariable, context: VisitorContext) {
        if(!evaluateType(storeVariable.value).equalTo(localVariables[storeVariable.name]!!)) {
            throw InvalidType(
                localVariables[storeVariable.name]!!,
                evaluateType(storeVariable.value),
                storeVariable.span)
        }
    }

    override fun visit(ifStatement: Ast.IfStatement, context: VisitorContext) {

    }

    override fun visit(forEachStatement: Ast.ForEachStatement, context: VisitorContext) {

    }

    override fun visit(loopStatement: Ast.LoopStatement, context: VisitorContext) {

    }

    override fun visit(breakStatement: Ast.BreakStatement, context: VisitorContext) {

    }

    override fun visitEnd(ifStatement: Ast.IfStatement, context: VisitorContext) {

    }

    override fun visitEnd(forEachStatement: Ast.ForEachStatement, context: VisitorContext) {

    }

    override fun visitEnd(loopStatement: Ast.LoopStatement, context: VisitorContext) {

    }

    override fun visitEnd(header: Ast.Header, context: VisitorContext) {

    }

    override fun visitEnd(clazz: Ast.Class, context: VisitorContext) {

    }

    override fun visitEnd(block: Ast.Block, context: VisitorContext) {

    }
}