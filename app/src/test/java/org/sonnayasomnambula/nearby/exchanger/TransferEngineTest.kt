package org.sonnayasomnambula.nearby.exchanger

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

import org.sonnayasomnambula.nearby.exchanger.nearby.JsonSerializer
import org.sonnayasomnambula.nearby.exchanger.nearby.TransferEngine

class TestStorage(val name: String) {
    class File(
        override val path: String = "",
        override val size: Long = 0,
    ) : TransferEngine.FileImpl() {
        override fun save(destination: TransferEngine.File) {}
        override fun createChild(entry: JsonSerializer.FileEntry): TransferEngine.File =
            File(path + "/" + entry.path, entry.size)
    }

    var files: MutableList<File> = mutableListOf()
    val engine = TransferEngine()

    init {
        engine.saveDir = File("Downloads", 0)
    }

    fun applyNetwork(action: TransferEngine.Action.Network) : List<TransferEngine.Action> {
        when (action) {
            is TransferEngine.Action.Network.SendMessage -> {
                println("$name received ${action.message}")
                return engine.readMessage(action.message)
            }

            is TransferEngine.Action.Network.SendFile -> {
                println("$name received file ${action.file.path}")
                return engine.readFile(action.file)
            }
        }
    }

    fun applyLocal(action: TransferEngine.Action) :  List<TransferEngine.Action> {
        return when (action) {
            is TransferEngine.Action.Local.Save -> {
                println("$name saves file ${action.destination.path}")
                files.add(action.destination as File)
                return emptyList()
            }
            else -> emptyList()
        }
    }
}

class TransferEngineTest {

    private lateinit var sender: TestStorage
    private lateinit var receiver: TestStorage

    private fun exchange(
        a: TestStorage,
        b: TestStorage,
        initial: List<TransferEngine.Action>
    ) {
        var queueA = initial
        var queueB = emptyList<TransferEngine.Action>()

        while (queueA.isNotEmpty() || queueB.isNotEmpty()) {

            // A -> B
            val networkA = queueA.filterIsInstance<TransferEngine.Action.Network>()
            val localA = queueA.filterIsInstance<TransferEngine.Action.Local>()

            localA.forEach { a.applyLocal(it) }
            queueB = networkA.flatMap { b.applyNetwork(it) }

            queueA = emptyList()

            // B -> A
            val networkB = queueB.filterIsInstance<TransferEngine.Action.Network>()
            val localB = queueB.filterIsInstance<TransferEngine.Action.Local>()

            localB.forEach { b.applyLocal(it) }
            queueA = networkB.flatMap { a.applyNetwork(it) }

            queueB = emptyList()
        }
    }

    @Before
    fun setUp() {
        sender = TestStorage("sender")
        receiver = TestStorage("receiver")
    }

    @Test
    fun `transfer two files`() {
        val files = listOf(
            TestStorage.File("dir/1", 32),
            TestStorage.File("dir/2", 64)
        )

        println("## test started")

        val initial = sender.engine.send(files)
        exchange(sender, receiver, initial)
        assertEquals(2, receiver.files.size)

        files.zip(receiver.files).forEachIndexed { index, (sent, received) ->
            val expectPath = sender.engine.saveDir?.path + "/" + sent.path
            assertEquals("File $index: path mismatch", expectPath, received.path)
            assertEquals("File $index: size mismatch", sent.size, received.size)
        }

        println("## test finished")
    }
}