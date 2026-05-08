package org.sonnayasomnambula.nearby.exchanger.main.model

import androidx.lifecycle.SavedStateHandle
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

interface Keeper {
    fun <T> get(key: String): T?
    fun <T> set(key: String, value: T?)
    fun <T> remove(key: String)

    class Tag<T>(
        private val key: String, private val keeper: Keeper
    ) : ReadWriteProperty<Any?, T?> {

        fun get(): T? = keeper.get(key)

        fun set(value: T?) {
            if (value != null) keeper.set(key, value)
            else keeper.remove<T>(key)
        }

        operator fun invoke(): T? = get()

        operator fun invoke(value: T) = set(value)

        override fun getValue(thisRef: Any?, property: KProperty<*>): T? {
            return get()
        }

        override fun setValue(
            thisRef: Any?,
            property: KProperty<*>,
            value: T?
        ) {
            set(value)
        }
    }
}

class SavedStateKeeper(private val savedStateHandle: SavedStateHandle) : Keeper {
    override fun <T> get(key: String): T? = savedStateHandle.get(key)
    override fun <T> set(key: String, value: T?) { savedStateHandle.set(key, value) }
    override fun <T> remove(key: String) { savedStateHandle.remove<T>(key) }
}