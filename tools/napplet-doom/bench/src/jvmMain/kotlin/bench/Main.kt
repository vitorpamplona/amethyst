package bench

import java.io.File

private val RUNTIMES =
    mapOf(
        "chicory-aot" to WasmRuntime("Chicory, compiled to JVM bytecode") { wasm, host -> ChicoryEngine(wasm, host, compiled = true) },
        "chicory" to WasmRuntime("Chicory, interpreter") { wasm, host -> ChicoryEngine(wasm, host, compiled = false) },
        "chasm" to WasmRuntime("Chasm, JVM") { wasm, host -> ChasmEngine(wasm, host) },
    )

// usage: Main DOOM_WASM IWAD [TICS] [chicory-aot,chicory,chasm]
fun main(args: Array<String>) {
    val wasm = File(args[0]).readBytes()
    val wad = File(args[1]).readBytes()
    val tics = args.getOrNull(2)?.toInt() ?: 2100
    val selected = (args.getOrNull(3) ?: RUNTIMES.keys.joinToString(",")).split(',').map { RUNTIMES.getValue(it.trim()) }
    println("${System.getProperty("java.vm.name")} ${System.getProperty("java.version")}, ${Runtime.getRuntime().availableProcessors()} CPUs")

    val results =
        selected.map { runtime ->
            println("Running ${runtime.name} for $tics tics...")
            var result: BenchResult? = null
            val wadName = File(args[1]).name.lowercase()
            // Interpreters recurse deeply for a C program's call stack; give them room.
            val thread = Thread(null, { result = runBench(runtime, wasm, wad, wadName, tics) }, "bench", 1L shl 30)
            thread.start()
            thread.join()
            result!!.also { println(report(listOf(it))) }
        }
    println("==== Summary")
    println(report(results))
}
