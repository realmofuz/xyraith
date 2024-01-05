package langimpl.lang.jvm

import langimpl.error.InvalidFunction
import langimpl.error.Unreachable
import langimpl.lang.lexer.SpanData
import langimpl.lang.parser.AstVisitor
import langimpl.lang.parser.VisitorContext
import parser.Ast
import parser.Type

/**
 * Represents the data surrounding a class.
 * @param superClass The class this class inherits from.
 * @param interfaces A list of interfaces this class inherits from.
 * @param properties A list of properties (fields & methods) this class contains.
 * @param isInterface Is this class an interface?
 */
data class ClassData(
    val superClass: Type.Object,
    val interfaces: MutableList<Type.Object>,
    val properties: MutableList<JvmMethodSignature>,
    val isInterface: Boolean,
)

/**
* Contains the result of AstGatherer#getProperty.
* @param exists Does this property exist?
* @param isInterface Is this property part of an interface?
* @param resultClass The containing class of the property.
 */
data class PropertyResult(
    val exists: Boolean,
    val isInterface: Boolean,
    val resultClass: String,
)
class AstGatherer : AstVisitor {
    /**
    * Represents class data.
    * Name is the name of the class, with package delimited by `.`.
    * E.g `java.lang.Object`
     */
    val data: MutableMap<String, ClassData> = mutableMapOf()
    val returnTypes: MutableMap<String, Type> = mutableMapOf()
    val functionTypes: MutableMap<String, FunctionType> = mutableMapOf()
    val annotations: MutableList<String> = mutableListOf()

    lateinit var currentClass: Ast.Class
    lateinit var currentClassName: String

    /**
     * Recursively searches classes & their properties for a property specified.
     * Return type is intentionally excluded to handle edge cases. Note that this may run into issues
     * when function overloading is involved.
     * @param clazz The root class to search in. For example, searching `java.lang.String` would search in that and `java.lang.Object`.
     * @param name The name of the property to search for.
     * @param parameters A list of types for the property to accept. Should be an empty list for fields.
     */
    fun getProperty(clazz: String, name: String, parameters: List<Type>): PropertyResult {
        println("searching for: $clazz::$name w/ format: ${data.keys}")
        if(!data.containsKey(clazz)) {
            return PropertyResult(
                false,
                false,
                ""
            )
        }
        data[clazz]!!.properties.forEach {
            if(it.parameters == parameters
                && it.name == name) {
                return PropertyResult(
                    true,
                    data[clazz]!!.isInterface,
                    clazz
                )
            }
        }
        if(clazz == "java.lang.Object") {
            return PropertyResult(
                false,
                false,
                ""
            )
        }
        return getProperty(data[clazz]!!.superClass.signature.resolve(), name, parameters)
    }

    /**
     * Computes the return type of a property.
     * @param clazz The root class to search in. For example, searching `java.lang.String` would search in that and `java.lang.Object`.
     * @param name The name of the property to search for.
     * @param parameters A list of types for the property to accept. Should be an empty list for fields.
     * @param span SpanData to throw InvalidFunction on if the property does not exist at all or is ambiguous.
     * @throws InvalidFunction Thrown when the property does not exist.
     */
    fun computeType(clazz: String, name: String, parameters: List<Type>, span: SpanData): Type {
        val property = getProperty(clazz, name, parameters)
        if(!property.exists) {
            throw InvalidFunction(span)
        }
        data[property.resultClass]!!.properties.forEach {
            println("${it.name} && ${it.parameters} vs. ${name} && ${parameters}")
            if(it.name == name
                && it.parameters == parameters) {
                return it.returns
            }
        }
        throw Unreachable()
    }

    override fun visit(clazz: Ast.Class, context: VisitorContext) {
        data[clazz.name.resolve()] = ClassData(
            clazz.inheritsFrom,
            mutableListOf(),
            mutableListOf(),
            clazz.isInterface,
        )
        currentClass = clazz
        currentClassName = clazz.name.resolve()
    }

    override fun visit(block: Ast.Block, context: VisitorContext) {

    }

    override fun visit(annotation: Ast.Annotation, context: VisitorContext) {
        annotations.add(annotation.name.resolve())
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

        if(currentClass.static || annotations.contains("static"))
            functionTypes[signature.generateInternalSignature()] = FunctionType.STATIC_METHOD
        else
            functionTypes[signature.generateInternalSignature()] = FunctionType.MEMBER_METHOD

        annotations.clear()
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

        if(currentClass.static || annotations.contains("static"))
            functionTypes[signature.generateInternalSignature()] = FunctionType.STATIC_FIELD
        else
            functionTypes[signature.generateInternalSignature()] = FunctionType.MEMBER_FIELD

        annotations.clear()
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