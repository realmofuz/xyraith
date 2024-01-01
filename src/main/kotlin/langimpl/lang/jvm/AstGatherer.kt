package langimpl.lang.jvm

import langimpl.lang.parser.AstVisitor
import langimpl.lang.parser.VisitorContext
import parser.Ast
import parser.Type

data class ClassData(
    val superClass: Type.Object,
    val interfaces: MutableList<Type.Object>,
    val properties: MutableList<JvmMethodSignature>
)
class AstGatherer : AstVisitor {
    /*
    Represents inheritance.
     */
    val data: MutableMap<String, ClassData> = mutableMapOf()
    val returnTypes: MutableMap<String, Type> = mutableMapOf()

    lateinit var currentClass: Ast.Class
    lateinit var currentClassName: String

    fun hasProperty(clazz: String, name: String, parameters: List<Type>, returns: Type): Boolean {
        data[clazz]!!.properties.forEach {
            if(it.parameters == parameters
                && it.name == name
                && it.returns == returns) {
                return true
            }
        }
        return false
    }

    override fun visit(clazz: Ast.Class, context: VisitorContext) {
        data[clazz.name.resolve()] = ClassData(
            clazz.inheritsFrom,
            mutableListOf(),
            mutableListOf()
        )
        currentClass = clazz
        currentClassName = clazz.name.resolve()
    }

    override fun visit(block: Ast.Block, context: VisitorContext) {

    }

    override fun visit(annotation: Ast.Annotation, context: VisitorContext) {

    }

    override fun visit(function: Ast.Function, context: VisitorContext): Boolean {
        val signature = JvmMethodSignature(
            function.name.path.last(),
            currentClass.name,
            function.parameters.values.toList(),
            function.returns,
            HeaderType.METHOD
        )
        data[currentClassName]!!.properties.add(signature)
        returnTypes[signature.generateInternalSignature()] = function.returns
        return true
    }

    override fun visit(event: Ast.Event, context: VisitorContext): Boolean {
        return true
    }

    override fun visit(field: Ast.DeclareField, context: VisitorContext): Boolean {
        val signature = JvmMethodSignature(
            field.name,
            currentClass.name,
            listOf(),
            field.type,
            HeaderType.FIELD
        )
        data[currentClassName]!!.properties.add(signature)
        returnTypes[signature.generateInternalSignature()] = field.type
        return true
    }

    override fun visit(value: Ast.Value, context: VisitorContext) {

    }

    override fun visit(clazz: Ast.ConstructClass, context: VisitorContext) {

    }

    override fun visit(access: Ast.Access, context: VisitorContext) {

    }

    override fun visit(declareVariable: Ast.DeclareVariable, context: VisitorContext) {

    }

    override fun visit(storeVariable: Ast.StoreVariable, context: VisitorContext) {

    }

    override fun visit(ifStatement: Ast.IfStatement, context: VisitorContext) {

    }

    override fun visit(forEachStatement: Ast.ForEachStatement, context: VisitorContext) {

    }

    override fun visit(loopStatement: Ast.LoopStatement, context: VisitorContext) {

    }

    override fun visit(breakStatement: Ast.BreakStatement, context: VisitorContext) {

    }

    override fun visitEnd(access: Ast.Access, context: VisitorContext) {

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