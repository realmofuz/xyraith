package parser

import langimpl.lang.lexer.SpanData
import langimpl.lang.parser.AstVisitor
import langimpl.lang.parser.VisitorContext

data class PathName(val path: MutableList<String>) {
    companion object {
        fun parse(path: String): PathName {
            return PathName(path.split(".").toMutableList())
        }
    }
    fun resolve(): String {
        return path.joinToString(".")
    }
}

data class CommandArgument(
    val argument: Ast.Value,
    val span: SpanData,
) {
    override fun toString(): String {
        return """{"type":"argument","argument":$argument,"span":$span}"""
    }
}

sealed interface Type {
    data object String : Type {
        override fun toJvmSignature(): kotlin.String {
            return "Ljava/lang/String;"
        }
        fun toJvmReflectedType(): Class<*> {
            return Class.forName("java/lang/String")
        }
    }
    data object Number : Type {
        override fun toJvmSignature(): kotlin.String {
            return "D"
        }
    }
    data object Boolean : Type {
        override fun toJvmSignature(): kotlin.String {
            return "Z"
        }
    }
    data object Void : Type {
        override fun toJvmSignature(): kotlin.String {
            return "V"
        }
    }
    data class Array(val type: Type) : Type {
        override fun toJvmSignature(): kotlin.String {
            return "[${type.toJvmSignature()}"
        }
    }
    data class Object(val signature: PathName, val types: kotlin.collections.List<Type>, val isParameter: kotlin.Boolean = false) : Type {
        override fun toJvmSignature(): kotlin.String {
            if(isParameter)
                return signature.resolve().replace(".", "/")
            return "L${signature.resolve().replace(".", "/")}${if(types.isNotEmpty()) """<${types.joinToString("") { it.toJvmSignature() }}>""" else ""};"
        }

        fun toJvmReflectedType(): Class<*> {
            return Class.forName(signature.resolve().replace(".", "/"))
        }
    }

    fun toJvmSignature(): kotlin.String

    fun equalTo(other: Type): kotlin.Boolean {
        if(this is String && other is Object && other.signature.resolve() == "java.lang.String")
            return true
        if(other is Object && this is Object)
            return this.signature == other.signature
        return this == other
    }

}



sealed interface Ast {
    sealed interface Header : Ast
    sealed interface Value : Ast
    sealed interface Action : Ast

    val evalType: Type
    fun accept(visitor: AstVisitor, context: VisitorContext)

    class Program(val events: List<Class>) {
        override fun toString(): String {
            return events.toString()
        }
    }
    class Class(
        val name: PathName,
        val generics: List<Type>,
        val headers: List<Ast.Header>,
        val static: kotlin.Boolean,
        val inheritsFrom: Type.Object,
        val isNative: kotlin.Boolean,
    ) : Ast {
        override fun toString(): String {
            return """{"type":"class","name":"$name","headers":$headers,"static":$static}""".trimIndent()
        }
        fun generateSignature(): String {
            return """<${generics.joinToString("") { it.toJvmSignature() }}>Ljava/lang/Object;"""
        }

        override fun accept(visitor: AstVisitor, context: VisitorContext) {
            visitor.visit(this, context)
            headers.forEach {
                when(it) {
                    is DeclareField -> { it.accept(visitor, context) }
                    is Event -> { it.accept(visitor, context) }
                    is Function -> { it.accept(visitor, context) }
                    is Annotation -> { it.accept(visitor, context) }
                }
            }
            visitor.visitEnd(this, context)

        }
        override val evalType: Type
            get() = Type.Object(this.name, listOf())
    }
    class DeclareField(val name: String, val value: Value, val span: SpanData, val type: Type) : Ast, Header {
        override fun toString(): String {
            return """{"type":"declareField","name":"$name","value":$value}"""
        }
        override fun accept(visitor: AstVisitor, context: VisitorContext) {
            visitor.visit(this, context)
        }
        override val evalType: Type
            get() = Type.Void
    }
    class StringText(val value: String) : Ast, Value {
        override fun toString(): String {
            return """{"type":"string","value":"$value"}""".trimIndent()
        }
        override fun accept(visitor: AstVisitor, context: VisitorContext) {
            visitor.visit(this, context)
        }
        override val evalType: Type
            get() = Type.String
    }
    class Number(val value: Double) : Ast, Value {
        override fun toString(): String {
            return """{"type":"number","value":$value}""".trimIndent()
        }
        override fun accept(visitor: AstVisitor, context: VisitorContext) {
            visitor.visit(this, context)
        }
        override val evalType: Type
            get() = Type.Number
    }
    class Variable(val value: String) : Ast, Value {
        override fun toString(): String {
            return """{"type":"variable","value":"$value"}""".trimIndent()
        }
        override fun accept(visitor: AstVisitor, context: VisitorContext) {
            visitor.visit(this, context)
        }
        override val evalType: Type
            get() = Type.Void
    }
    class Boolean(val value: kotlin.Boolean) : Ast, Value {
        override fun toString(): String {
            return """{"type":"variable","value":$value}""".trimIndent()
        }
        override fun accept(visitor: AstVisitor, context: VisitorContext) {
            visitor.visit(this, context)
        }
        override val evalType: Type
            get() = Type.Boolean
    }

    data object Null : Ast, Value {
        override fun toString(): String {
            return """{"type":"null"}""".trimIndent()
        }
        override fun accept(visitor: AstVisitor, context: VisitorContext) {
            visitor.visit(this, context)
        }
        override val evalType: Type
            get() = Type.Void
    }

    class Event(val name: String, val code: Block, val eventNameSpan: SpanData) : Ast, Header {
        override fun toString(): String {
            return """{"type":"event","name": "$name","code":$code}""".trimIndent()
        }
        override fun accept(visitor: AstVisitor, context: VisitorContext) {
            if(visitor.visit(this, context))
                code.accept(visitor, context)
            visitor.visitEnd(this, context)
        }
        override val evalType: Type
            get() = Type.Void
    }
    class Function(
        val name: PathName, val code: Block, val eventNameSpan: SpanData,
        val parameters: MutableMap<String, Type>, val returns: Type) : Ast, Header {
        override fun toString(): String {
            return """{"type":"function","name": "$name","code":$code,"signature":"${generateSignature()}"}""".trimIndent()
        }
        override fun accept(visitor: AstVisitor, context: VisitorContext) {
            if(visitor.visit(this, context))
                code.accept(visitor, context)
            visitor.visitEnd(this, context)
        }

        fun generateSignature(): String {
            return """(${parameters.values.joinToString("") { it.toJvmSignature() }})${returns.toJvmSignature()}"""
        }

        override val evalType: Type
            get() = returns
    }
    class Block(val nodes: MutableList<Ast.Action>, val eventName: String, val span: SpanData) : Ast {
        override fun toString(): String {
            return """{"type":"block","name":"$eventName","nodes":$nodes}""".trimIndent()
        }
        override fun accept(visitor: AstVisitor, context: VisitorContext) {
            visitor.visit(this, context)
            for(node in nodes) {
                node.accept(visitor, context)
            }
            visitor.visitEnd(this, context)
        }
        override val evalType: Type
            get() = Type.Void
    }
    class Access(
        val path: PathName, val arguments: MutableList<CommandArgument>,
        val nameSpan: SpanData, var returns: Type) : Ast, Action, Value {
        override fun toString(): String {
            return """{"type":"access","path":"${path.resolve()}","arguments":$arguments}"""
        }
        override fun accept(visitor: AstVisitor, context: VisitorContext) {
            visitor.visit(this, context)
            arguments.forEach {
                it.argument.accept(visitor, context)
            }
            visitor.visitEnd(this, context)

        }
        override val evalType: Type
            get() = Type.Void
    }

    class Annotation(
        val name: PathName, private val arguments: MutableList<CommandArgument>,
        val nameSpan: SpanData) : Ast, Ast.Header {
        override fun toString(): String {
            return """{"type":"annotation","name":"${name.resolve()}","arguments":$arguments}"""
        }
        override fun accept(visitor: AstVisitor, context: VisitorContext) {
            arguments.forEach {
                it.argument.accept(visitor, context)
            }
            visitor.visit(this, context)
        }
        override val evalType: Type
            get() = Type.Void
    }
    class ArrayOf(val type: Type, val arguments: MutableList<CommandArgument>, val nameSpan: SpanData) : Ast, Value {
        override fun toString(): String {
            return """{"type":"arrayOf","arguments":$arguments}"""
        }
        override fun accept(visitor: AstVisitor, context: VisitorContext) {
            visitor.visit(this, context)
            var index = 0
            arguments.forEach {
                val ctx = VisitorContext.Array(index, false)
                index += 1
                if(index == arguments.size)
                    ctx.isLast = true
                it.argument.accept(visitor, ctx)
            }

        }
        override val evalType: Type
            get() = Type.Array(type)
    }
    class ConstructClass(val className: PathName, val arguments: MutableList<CommandArgument>, val classSpan: SpanData) : Ast, Action, Value {
        override fun toString(): String {
            return """{"type":"new","className":"$className","classSpan":$classSpan}"""
        }
        override fun accept(visitor: AstVisitor, context: VisitorContext) {
            visitor.visit(this, context)
        }
        override val evalType: Type
            get() = Type.Object(className, mutableListOf(), false)
    }
    class DeclareVariable(val name: String, val value: Value, val span: SpanData, var type: Type) : Ast, Action {
        override fun toString(): String {
            return """{"type":"declareVariable","name":"$name","value":$value}"""
        }
        override fun accept(visitor: AstVisitor, context: VisitorContext) {
            value.accept(visitor, context)
            visitor.visit(this, context)
        }
        override val evalType: Type
            get() = Type.Void
    }
    class StoreVariable(val name: String, val value: Value, val span: SpanData) : Ast, Action {
        override fun toString(): String {
            return """{"type":"storeVariable","name":"$name","value":$value}"""
        }
        override fun accept(visitor: AstVisitor, context: VisitorContext) {
            value.accept(visitor, context)
            visitor.visit(this, context)
        }
        override val evalType: Type
            get() = Type.Void
    }
    class IfStatement(val condition: Ast.Value, val ifTrue: Block) : Ast, Action {
        override fun toString(): String {
            return """{"type":"if","condition":$condition,"ifTrue":$ifTrue}"""
        }
        override fun accept(visitor: AstVisitor, context: VisitorContext) {
            condition.accept(visitor, context)
            visitor.visit(this, context)
            ifTrue.accept(visitor, context)
            visitor.visitEnd(this, context)
        }
        override val evalType: Type
            get() = Type.Void
    }
    class ForEachStatement(val variable: String, val list: Ast.Value, val ifTrue: Block) : Ast, Action {
        override fun toString(): String {
            return """{"type":"foreach","variable":"$variable","list":$list,"ifTrue":$ifTrue}"""
        }
        override fun accept(visitor: AstVisitor, context: VisitorContext) {
            list.accept(visitor, context)
            visitor.visit(this, context)
            ifTrue.accept(visitor, context)
            visitor.visitEnd(this, context)
        }
        override val evalType: Type
            get() = Type.Void
    }
    class LoopStatement(val block: Block) : Ast, Action {
        override fun toString(): String {
            return """{"type":"loop","code":$block}"""
        }
        override fun accept(visitor: AstVisitor, context: VisitorContext) {
            visitor.visit(this, context)
            block.accept(visitor, context)
            visitor.visitEnd(this, context)
        }
        override val evalType: Type
            get() = Type.Void
    }

    data object BreakStatement : Ast, Action {
        override fun toString(): String {
            return """{"type":"break"}"""
        }

        override fun accept(visitor: AstVisitor, context: VisitorContext) {
            visitor.visit(this, context)
        }

        override val evalType: Type
            get() = Type.Void
    }
}

