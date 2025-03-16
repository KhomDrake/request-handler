package br.com.khomdrake.request_handler.log

import timber.log.Timber

enum class LogLevel(
    val value: String
) {
    ZERO(""),
    ONE("\t"),
    TWO("\t\t"),
    THREE("\t\t\t"),
    FOUR("\t\t\t\t"),
}

object LogHandler {
    
    private var tag = "Request-Handler"
    
    fun setDefaultTag(newDefault: String) {
        tag = newDefault
    }

    fun d(tag: String = this.tag, message: String) {
        Timber.tag(tag).d(message.trim())
    }

    fun d(message: String) {
        Timber.tag(tag).d(message.trim())
    }

    fun d(message: Any) {
        Timber.tag(tag).d(message.toString().trim())
    }

    fun d(topic: String, message: Any?) {
        Timber.tag(tag).d("[$topic] ${message?.toString()?.trim()}")
    }

    fun d(topic: String, message: Any?, level: LogLevel = LogLevel.ZERO) {
        Timber.tag(tag).d("${level.value} [$topic] ${message?.toString()?.trim()}")
    }

    fun d(topic: String, message: Any?, vararg args: Any?) {
        Timber.tag(tag).d("[$topic] ${message?.toString()?.trim()}", args)
    }

    fun e(message: Any) {
        Timber.tag(tag).e(message.toString().trim())
    }

    fun e(topic: String, message: Any?) {
        Timber.tag(tag).e("[$topic] ${message?.toString()?.trim()}")
    }

    fun e(topic: String, message: Any?, vararg args: Any?) {
        Timber.tag(tag).e("[$topic] ${message?.toString()?.trim()}", args)
    }

    fun e(topic: String, message: Any?, level: LogLevel = LogLevel.ZERO) {
        Timber.tag(tag).e("${level.value} [$topic] ${message?.toString()?.trim()}")
    }

}