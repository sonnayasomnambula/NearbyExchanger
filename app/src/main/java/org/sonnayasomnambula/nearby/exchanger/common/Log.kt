package org.sonnayasomnambula.nearby.exchanger.common

val LOG_TRACE = "org.sonnayasomnambula.trace"

fun __func__(): String {
    val element = Thread.currentThread().stackTrace[3]
    val className = element.className.substringAfterLast('.')
    val methodName = element.methodName
    val lineNumber = element.lineNumber
    return className + "." + methodName + " : " + lineNumber.toString()
}