package bench

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.posix.SEEK_END
import platform.posix.SEEK_SET
import platform.posix.fclose
import platform.posix.fopen
import platform.posix.fread
import platform.posix.fseek
import platform.posix.ftell

@OptIn(ExperimentalForeignApi::class)
private fun readFile(path: String): ByteArray {
    val file = fopen(path, "rb") ?: error("cannot open $path")
    try {
        fseek(file, 0, SEEK_END)
        val size = ftell(file).toInt()
        fseek(file, 0, SEEK_SET)
        val bytes = ByteArray(size)
        bytes.usePinned { fread(it.addressOf(0), 1u, size.toULong(), file) }
        return bytes
    } finally {
        fclose(file)
    }
}

// usage: bench.kexe DOOM_WASM IWAD [TICS]
fun main(args: Array<String>) {
    val wasm = readFile(args[0])
    val wad = readFile(args[1])
    val tics = args.getOrNull(2)?.toInt() ?: 2100
    val runtime = WasmRuntime("Chasm, Kotlin/Native") { w, host -> ChasmEngine(w, host) }
    println("Running ${runtime.name} for $tics tics...")
    println(report(listOf(runBench(runtime, wasm, wad, args[1].substringAfterLast('/').lowercase(), tics))))
}
