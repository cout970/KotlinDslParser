//---
fun html(func: Unit.() -> Unit): Unit {
    println("<html>")
    Unit.func()
    println("</html>")
}

fun Unit.body(func: Unit.() -> Unit): Unit {
    println("<body>")
    Unit.func()
    println("</body>")
}

fun Unit.div(func: Unit.() -> Unit): Unit {
    println("<div>")
    Unit.func()
    println("</div>")
}

fun Unit.a(link: String, func: Unit.() -> Unit): Unit {
    println("<a href=\"$link\">")
    Unit.func()
    println("</a>")
}

operator fun String.unaryPlus(): Unit {
    println(this)
}

//var Unit.target: ATarget
//    external get
//    external set
//
//enum class ATarget {
//    blank
//}