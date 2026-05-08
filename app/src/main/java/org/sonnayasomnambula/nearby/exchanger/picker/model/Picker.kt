package org.sonnayasomnambula.nearby.exchanger.picker.model

interface Picker {
    fun accept()
    fun close()

    interface File {
        val name: String
        val path: String
        val isDirectory: Boolean
        fun length(): Long
        fun children(): List<File>
    }

    interface Volume {
        val name: String
        val path: String
        val isRemovable: Boolean
        fun file(): File
    }
}