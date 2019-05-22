data class Function(
    val header: FunctionHeader,
    val body: List<DslNode>
)

data class FunctionHeader(
    val external: Boolean,
    val receiver: String?,
    val name: String,
    val arguments: List<FunctionArgument>,
    val returnType: String?
)

data class FunctionArgument(
    val name: String,
    val type: String
)

sealed class DslNode {
    data class FunctionCall(
        val receiver: String?,
        val name: String,
        val parameters: List<Parameter>,
        val children: List<DslNode>
    ) : DslNode()

    data class Assignment(val name: String, val value: Value) : DslNode()
    data class UnaryOperator(val operator: String, val value: Value) : DslNode()
}

sealed class Parameter(open val value: Value) {
    data class Named(val name: String, override val value: Value) : Parameter(value)
    data class Single(override val value: Value) : Parameter(value)
}

sealed class Value {
    data class StringValue(val content: String) : Value()
    data class NumberValue(val number: Number) : Value()
    data class FunctionValue(val call: DslNode.FunctionCall) : Value()
    data class EnumValue(val type: String, val name: String) : Value()
}
