import java.io.File
import kotlin.reflect.KCallable

fun main() {
    val functions = Parser.parseFile(File("src/Example.kt").readText())

    println("Compiled")
    script()

    println("Reflection")
    val env = Env(
        mapOf(
            "html" to ::html,
            "body" to Unit::body,
            "div" to Unit::div,
            "a" to Unit::a,
            "+" to String::unaryPlus
        )
    )
    evalFunction(env, functions[0])
}


data class Env(val functions: Map<String, KCallable<Unit>>)

fun evalFunction(env: Env, func: Function) {
    require(!func.header.external) { "Unable to evaluate external function: $func" }

    func.body.forEach { node ->
        evalNode(env, node)
    }
}

fun evalNode(env: Env, node: DslNode) {
    when (node) {
        is DslNode.FunctionCall -> {
            val args: MutableList<Any?> = node.parameters.map { it.value.toString() }.toMutableList()
            val lambda: Unit.() -> Unit = { node.children.forEach { evalNode(env, it) } }
            args += lambda

            val genericFun: KCallable<Unit> = env.functions[node.name] ?: return

            genericFun.call(*args.toTypedArray())
        }
    }
}