package langimpl.lang.jvm

import parser.PathName
import parser.Type

data class JvmMethodSignature(
    val name: String,
    val owner: PathName,
    val parameters: List<Type>,
    val returns: Type,
    val headerType: HeaderType
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