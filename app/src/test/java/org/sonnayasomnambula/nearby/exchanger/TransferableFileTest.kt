package org.sonnayasomnambula.nearby.exchanger

import org.junit.Assert.assertEquals
import org.junit.Test
import org.sonnayasomnambula.nearby.exchanger.nearby.NearbyExchanger

class TransferableFileTest {
    @Test
    fun `prefix trivial`() {
        val files = arrayOf(
            "/home/user/documents/1.txt",
            "/home/user/documents/2.txt",
            "/home/user/music/3.mp3"
        )
        val prefix = NearbyExchanger.TransferableFile.findCommonPrefix(files)
        assertEquals("unexpected prefix", "/home/user/", prefix)
    }
}