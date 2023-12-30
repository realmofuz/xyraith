package langimpl.lang.parser

import langimpl.lang.jvm.FunctionMapper
import parser.Ast

class AstDebugger : AstVisitor {
    private lateinit var currentClass: Ast.Class
    private lateinit var currentHeader: Ast.Header

    override fun visit(clazz: Ast.Class, context: VisitorContext) {
        currentClass = clazz
        println("Visiting Class: ${clazz.name}")
    }

    override fun visit(block: Ast.Block, context: VisitorContext) {
        println("Visiting Block: ${block.eventName}")
    }

    override fun visit(annotation: Ast.Annotation, context: VisitorContext) {
        println("Annotation: ${annotation.name}")
    }

    override fun visit(function: Ast.Function, context: VisitorContext): Boolean {
        val mapped = FunctionMapper().map(function, currentClass)
        println("Visiting Function: ${mapped.signature.generateInternalSignature()}")
        return true
    }

    override fun visit(event: Ast.Event, context: VisitorContext): Boolean {
        val mapped = FunctionMapper().map(event, currentClass)
        println("Visiting Event: ${mapped.signature.generateInternalSignature()}")
        return true
    }

    override fun visit(field: Ast.DeclareField, context: VisitorContext): Boolean {
        val mapped = FunctionMapper().map(field, currentClass)
        println("Visiting Field: ${mapped.signature.generateInternalSignature()}")
        return true
    }

    override fun visit(value: Ast.Value, context: VisitorContext) {
        println("Load Value: $value")
    }

    override fun visit(clazz: Ast.ConstructClass, context: VisitorContext) {
        println("Init ${clazz.className}")
    }

    override fun visit(staticAccess: Ast.StaticAccess, context: VisitorContext) {
        println("Static Access: ${staticAccess.name.resolve()}::${staticAccess.name}")
    }

    override fun visit(memberAccess: Ast.MemberAccess, context: VisitorContext) {
        println("Member Access: ${memberAccess.variableName}->${memberAccess.name}")
    }

    override fun visit(declareVariable: Ast.DeclareVariable, context: VisitorContext) {
        println("Declare Variable: ${declareVariable.name}: ${declareVariable.type}")
    }

    override fun visit(storeVariable: Ast.StoreVariable, context: VisitorContext) {
        println("Store Variable: ${storeVariable.name}")
    }

    override fun visit(ifStatement: Ast.IfStatement, context: VisitorContext) {
        println("If:")
    }

    override fun visit(forEachStatement: Ast.ForEachStatement, context: VisitorContext) {
        println("For Each: ${forEachStatement.variable}")
    }

    override fun visit(loopStatement: Ast.LoopStatement, context: VisitorContext) {
        println("Loop:")
    }

    override fun visit(breakStatement: Ast.BreakStatement, context: VisitorContext) {
        println("Break")
    }

    override fun visitEnd(ifStatement: Ast.IfStatement, context: VisitorContext) {
        println("End If")
    }

    override fun visitEnd(forEachStatement: Ast.ForEachStatement, context: VisitorContext) {
        println("End For Each")
    }

    override fun visitEnd(loopStatement: Ast.LoopStatement, context: VisitorContext) {
        println("End Loop")
    }

    override fun visitEnd(header: Ast.Header, context: VisitorContext) {
        println("End Header")
    }

    override fun visitEnd(clazz: Ast.Class, context: VisitorContext) {
        println("End Class")
    }

    override fun visitEnd(block: Ast.Block, context: VisitorContext) {
        println("End Block")
    }
}