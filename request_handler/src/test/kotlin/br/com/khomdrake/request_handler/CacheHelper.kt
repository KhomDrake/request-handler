package br.com.khomdrake.request_handler

abstract class CacheHelper {

    abstract fun <T> save(key: String, any: T): Boolean

    abstract fun <T> retrieve(key: String): T

    abstract fun remove(key: String): Boolean

}