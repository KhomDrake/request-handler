package br.com.khomdrake.request_handler.log

import timber.log.Timber

object LogHandler {

    fun logInfo(tag: String, text: String) {
        Timber.tag(tag).d(text.trim())
    }

}