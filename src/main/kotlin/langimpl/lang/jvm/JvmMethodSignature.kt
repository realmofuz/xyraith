package langimpl.lang.jvm

import parser.PathName
import parser.Type

/**
 * Represents a method signature in Xyraith or the JVM.
 * @param name Name of the method
 * @param owner The class that owns the method
 * @param parameters A list of types this method accepts as parameters
 * @param headerType The HeaderType of this function.
 * @see HeaderType
 */
data class JvmMethodSignature(
    val name: String,
    val owner: PathName,
    val parameters: List<Type>,
    val returns: Type,
    val headerType: HeaderType,
) {
    fun methodSignature(): String = when(headerType) {
        HeaderType.METHOD -> "(${parameters.joinToString("") { it.toJvmSignature() }})${returns.toJvmSignature()}"
        HeaderType.FIELD -> returns.toJvmSignature()
    }

    fun ownerSignature(): String {
        return owner.resolve().replace(".", "/")
    }

    fun generateInternalSignature(): String {
        return "${owner.resolve()}::$name(${parameters.joinToString("") { it.toJvmSignature() }})"
    }
}

enum class HeaderType {
    METHOD,
    FIELD
}