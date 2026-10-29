package bench

/** The guest's linear memory, as each runtime exposes it. */
interface GuestMemory {
    fun readI32(address: Int): Int

    fun writeI8(address: Int, value: Byte)

    fun writeI16(address: Int, value: Short)

    fun writeI32(address: Int, value: Int)

    fun writeI64(address: Int, value: Long)

    fun read(address: Int, into: ByteArray, offset: Int, length: Int)

    fun write(address: Int, from: ByteArray, offset: Int, length: Int)
}

enum class WasmType { I32, I64 }

/**
 * One function doom.wasm imports. [call] gets the arguments widened to Long (an i32 in its low
 * 32 bits) and returns the result, or null for a function without one.
 */
class ImportSpec(
    val module: String,
    val name: String,
    val params: List<WasmType>,
    val result: WasmType?,
    val call: (GuestMemory, LongArray) -> Long?,
)

class GuestExit(val code: Int) : RuntimeException("proc_exit($code)")

/**
 * Everything doom.wasm imports, in Kotlin: the 14 WASI calls against an in-memory file system
 * holding the WAD, and the page's 10 functions (frame, clock, sound, music).
 *
 * The clock is virtual. While the engine starts up it advances a millisecond per read, so the
 * engine's start-up waits end. After that, [setTic] pins it to an exact game tic before each
 * dg_tick(), so every call runs exactly one 35 Hz tic and every runtime does identical work.
 */
class DoomHost(wad: ByteArray, wadName: String) {
    private class FileEntry(var data: ByteArray, var size: Int)

    private sealed interface Fd

    private class DirFd(val path: String) : Fd

    private class FileFd(val path: String, val entry: FileEntry, var position: Int, val append: Boolean) : Fd

    private object TtyFd : Fd

    private val files = HashMap<String, FileEntry>()
    private val dirs = HashSet<String>().apply { add("/") }
    private val fds = HashMap<Int, Fd>()
    private var nextFd = 4

    val log = ArrayDeque<String>()
    private val pendingLine = StringBuilder()

    var frames = 0
        private set
    var frame = ByteArray(0)
        private set

    private var clockMs = 1L
    private var freeRunning = true
    private var baseMs = -1L

    init {
        files["/$wadName"] = FileEntry(wad, wad.size)
        fds[0] = TtyFd
        fds[1] = TtyFd
        fds[2] = TtyFd
        fds[3] = DirFd("/")
    }

    /** The game tic the engine's clock reads right now. */
    fun currentTic(): Long = if (baseMs < 0) 0 else (clockMs - baseMs) * 35 / 1000

    fun setTic(tic: Long) {
        freeRunning = false
        // The smallest clock reading that I_GetTime() turns into exactly this tic.
        clockMs = baseMs + (tic * 1000 + 34) / 35
    }

    private fun ticksMs(): Long {
        if (baseMs < 0) baseMs = clockMs
        val now = clockMs
        if (freeRunning) clockMs++
        return now
    }

    private fun drawFrame(memory: GuestMemory, pointer: Int, width: Int, height: Int) {
        val bytes = width * height * 4
        if (frame.size != bytes) frame = ByteArray(bytes)
        // The copy a Compose card would make into its bitmap each frame, so it is part of the work.
        memory.read(pointer, frame, 0, bytes)
        frames++
    }

    fun frameHash(): String {
        var hash = -0x340d631b7bdddcdbL
        for (b in frame) {
            hash = hash xor (b.toLong() and 0xff)
            hash *= 0x100000001b3L
        }
        return hash.toULong().toString(16).padStart(16, '0')
    }

    // ---- WASI ----------------------------------------------------------------------------------

    private fun text(memory: GuestMemory, pointer: Int, length: Int): String {
        val bytes = ByteArray(length)
        memory.read(pointer, bytes, 0, length)
        return bytes.decodeToString()
    }

    private fun normalize(path: String): String {
        val parts = ArrayList<String>()
        for (part in path.split('/')) {
            when (part) {
                "", "." -> {}
                ".." -> if (parts.isNotEmpty()) parts.removeAt(parts.size - 1)
                else -> parts.add(part)
            }
        }
        return "/" + parts.joinToString("/")
    }

    private fun parentOf(path: String) = path.substringBeforeLast('/').ifEmpty { "/" }

    private fun resolve(memory: GuestMemory, dirFd: Int, pointer: Int, length: Int): String? {
        val dir = fds[dirFd] as? DirFd ?: return null
        return normalize(dir.path + "/" + text(memory, pointer, length))
    }

    private fun open(fd: Fd, memory: GuestMemory, out: Int): Int {
        val number = nextFd++
        fds[number] = fd
        memory.writeI32(out, number)
        return SUCCESS
    }

    private fun pathOpen(memory: GuestMemory, a: LongArray): Int {
        val path = resolve(memory, a[0].toInt(), a[2].toInt(), a[3].toInt()) ?: return BADF
        val oflags = a[4].toInt()
        val fdflags = a[7].toInt()
        val out = a[8].toInt()
        if (path in dirs) {
            if (oflags and O_CREAT != 0 && oflags and O_EXCL != 0) return EXIST
            return open(DirFd(path), memory, out)
        }
        if (oflags and O_DIRECTORY != 0) return if (path in files) NOTDIR else NOENT
        var entry = files[path]
        if (entry == null) {
            if (oflags and O_CREAT == 0 || parentOf(path) !in dirs) return NOENT
            entry = FileEntry(ByteArray(0), 0)
            files[path] = entry
        } else if (oflags and O_CREAT != 0 && oflags and O_EXCL != 0) {
            return EXIST
        }
        if (oflags and O_TRUNC != 0) entry.size = 0
        return open(FileFd(path, entry, 0, fdflags and 1 != 0), memory, out)
    }

    private fun fdRead(memory: GuestMemory, fd: Int, iovs: Int, count: Int, out: Int): Int {
        val file = fds[fd] ?: return BADF
        if (file !is FileFd) {
            memory.writeI32(out, 0)
            return if (file is DirFd) ISDIR else SUCCESS
        }
        var total = 0
        for (i in 0 until count) {
            val pointer = memory.readI32(iovs + i * 8)
            val length = memory.readI32(iovs + i * 8 + 4)
            val n = minOf(length, file.entry.size - file.position).coerceAtLeast(0)
            memory.write(pointer, file.entry.data, file.position, n)
            file.position += n
            total += n
            if (n < length) break
        }
        memory.writeI32(out, total)
        return SUCCESS
    }

    private fun fdWrite(memory: GuestMemory, fd: Int, iovs: Int, count: Int, out: Int): Int {
        val file = fds[fd] ?: return BADF
        var total = 0
        for (i in 0 until count) {
            val pointer = memory.readI32(iovs + i * 8)
            val length = memory.readI32(iovs + i * 8 + 4)
            when (file) {
                is TtyFd -> emit(text(memory, pointer, length))
                is FileFd -> {
                    val entry = file.entry
                    if (file.append) file.position = entry.size
                    val end = file.position + length
                    if (end > entry.data.size) entry.data = entry.data.copyOf(maxOf(end, entry.data.size * 2, 4096))
                    memory.read(pointer, entry.data, file.position, length)
                    file.position = end
                    entry.size = maxOf(entry.size, end)
                }
                is DirFd -> return BADF
            }
            total += length
        }
        memory.writeI32(out, total)
        return SUCCESS
    }

    private fun emit(chunk: String) {
        pendingLine.append(chunk)
        while (true) {
            val newline = pendingLine.indexOf("\n")
            if (newline < 0) break
            log.addLast(pendingLine.substring(0, newline))
            if (log.size > 40) log.removeFirst()
            pendingLine.deleteRange(0, newline + 1)
        }
    }

    private fun fdSeek(memory: GuestMemory, fd: Int, offset: Long, whence: Int, out: Int): Int {
        val file = fds[fd] as? FileFd ?: return BADF
        val base = when (whence) {
            0 -> 0
            1 -> file.position
            else -> file.entry.size
        }
        val position = base + offset
        if (position < 0) return INVAL
        file.position = position.toInt()
        memory.writeI64(out, position)
        return SUCCESS
    }

    private fun fdstatGet(memory: GuestMemory, fd: Int, out: Int): Int {
        val file = fds[fd] ?: return BADF
        memory.writeI8(out, if (file is DirFd) 3 else if (file is TtyFd) 2 else 4)
        memory.writeI16(out + 2, 0)
        memory.writeI64(out + 8, -1L)
        memory.writeI64(out + 16, -1L)
        return SUCCESS
    }

    val imports: List<ImportSpec> = run {
        val i32 = WasmType.I32
        val i64 = WasmType.I64
        fun wasi(name: String, vararg params: WasmType, call: (GuestMemory, LongArray) -> Int) =
            ImportSpec("wasi_snapshot_preview1", name, params.toList(), i32) { m, a -> call(m, a).toLong() }
        fun doom(name: String, vararg params: WasmType, result: WasmType? = null, call: (GuestMemory, LongArray) -> Long?) =
            ImportSpec("doom", name, params.toList(), result, call)

        listOf(
            wasi("fd_prestat_get", i32, i32) { m, a ->
                if (a[0].toInt() != 3) {
                    BADF
                } else {
                    m.writeI8(a[1].toInt(), 0)
                    m.writeI32(a[1].toInt() + 4, 1)
                    SUCCESS
                }
            },
            wasi("fd_prestat_dir_name", i32, i32, i32) { m, a ->
                if (a[0].toInt() != 3) BADF else SUCCESS.also { m.writeI8(a[1].toInt(), '/'.code.toByte()) }
            },
            wasi("fd_fdstat_get", i32, i32) { m, a -> fdstatGet(m, a[0].toInt(), a[1].toInt()) },
            wasi("fd_fdstat_set_flags", i32, i32) { _, _ -> SUCCESS },
            wasi("path_open", i32, i32, i32, i32, i32, i64, i64, i32, i32) { m, a -> pathOpen(m, a) },
            wasi("fd_read", i32, i32, i32, i32) { m, a -> fdRead(m, a[0].toInt(), a[1].toInt(), a[2].toInt(), a[3].toInt()) },
            wasi("fd_write", i32, i32, i32, i32) { m, a -> fdWrite(m, a[0].toInt(), a[1].toInt(), a[2].toInt(), a[3].toInt()) },
            wasi("fd_seek", i32, i64, i32, i32) { m, a -> fdSeek(m, a[0].toInt(), a[1], a[2].toInt(), a[3].toInt()) },
            wasi("fd_close", i32) { _, a ->
                val fd = a[0].toInt()
                if (fd < 3) SUCCESS else if (fds.remove(fd) != null) SUCCESS else BADF
            },
            wasi("path_create_directory", i32, i32, i32) { m, a ->
                val path = resolve(m, a[0].toInt(), a[1].toInt(), a[2].toInt())
                when {
                    path == null -> BADF
                    path in dirs || path in files -> EXIST
                    parentOf(path) !in dirs -> NOENT
                    else -> SUCCESS.also { dirs.add(path) }
                }
            },
            wasi("path_remove_directory", i32, i32, i32) { m, a ->
                val path = resolve(m, a[0].toInt(), a[1].toInt(), a[2].toInt())
                if (path == null) BADF else if (dirs.remove(path)) SUCCESS else NOENT
            },
            wasi("path_unlink_file", i32, i32, i32) { m, a ->
                val path = resolve(m, a[0].toInt(), a[1].toInt(), a[2].toInt())
                if (path == null) BADF else if (files.remove(path) != null) SUCCESS else NOENT
            },
            wasi("path_rename", i32, i32, i32, i32, i32, i32) { m, a ->
                val from = resolve(m, a[0].toInt(), a[1].toInt(), a[2].toInt())
                val to = resolve(m, a[3].toInt(), a[4].toInt(), a[5].toInt())
                val entry = from?.let { files.remove(it) }
                when {
                    from == null || to == null -> BADF
                    entry == null -> NOENT
                    else -> SUCCESS.also { files[to] = entry }
                }
            },
            ImportSpec("wasi_snapshot_preview1", "proc_exit", listOf(i32), null) { _, a -> throw GuestExit(a[0].toInt()) },
            doom("draw_frame", i32, i32, i32) { m, a ->
                drawFrame(m, a[0].toInt(), a[1].toInt(), a[2].toInt())
                null
            },
            doom("ticks_ms", result = i32) { _, _ -> ticksMs() },
            doom("sfx_start", i32, i32, i32, i32, i32, i32) { _, _ -> null },
            doom("sfx_update", i32, i32, i32) { _, _ -> null },
            doom("sfx_stop", i32) { _, _ -> null },
            doom("sfx_playing", i32, result = i32) { _, _ -> 0 },
            doom("music_play", i32, i32, i32) { _, _ -> null },
            doom("music_stop") { _, _ -> null },
            doom("music_volume", i32) { _, _ -> null },
            doom("music_pause", i32) { _, _ -> null },
        )
    }

    private companion object {
        const val SUCCESS = 0
        const val BADF = 8
        const val EXIST = 20
        const val INVAL = 28
        const val ISDIR = 31
        const val NOENT = 44
        const val NOTDIR = 54
        const val O_CREAT = 1
        const val O_DIRECTORY = 2
        const val O_EXCL = 4
        const val O_TRUNC = 8
    }
}
