package langimpl.lang.parser

import parser.Ast

sealed interface VisitorContext {
    data object None : VisitorContext
    data class Array(var index: Int, var isLast: Boolean) : VisitorContext
}

interface AstVisitor {
    fun visit(clazz: Ast.Class, context: VisitorContext)
    fun visit(block: Ast.Block, context: VisitorContext)
    fun visit(annotation: Ast.Annotation, context: VisitorContext)
    fun visit(function: Ast.Function, context: VisitorContext): Boolean
    fun visit(event: Ast.Event, context: VisitorContext): Boolean
    fun visit(field: Ast.DeclareField, context: VisitorContext): Boolean
    fun visit(value: Ast.Value, context: VisitorContext)
    fun visit(clazz: Ast.ConstructClass, context: VisitorContext)

    fun visit(access: Ast.Access, context: VisitorContext)
    fun visit(declareVariable: Ast.DeclareVariable, context: VisitorContext)
    fun visit(storeVariable: Ast.StoreVariable, context: VisitorContext)
    fun visit(ifStatement: Ast.IfStatement, context: VisitorContext)
    fun visit(forEachStatement: Ast.ForEachStatement, context: VisitorContext)
    fun visit(loopStatement: Ast.LoopStatement, context: VisitorContext)
    fun visitEnd(ifStatement: Ast.IfStatement, context: VisitorContext)
    fun visitEnd(forEachStatement: Ast.ForEachStatement, context: VisitorContext)
    fun visitEnd(loopStatement: Ast.LoopStatement, context: VisitorContext)
    fun visitEnd(header: Ast.Header, context: VisitorContext)
    fun visitEnd(clazz: Ast.Class, context: VisitorContext)
    fun visitEnd(block: Ast.Block, context: VisitorContext)
    fun visit(breakStatement: Ast.BreakStatement, context: VisitorContext)
}