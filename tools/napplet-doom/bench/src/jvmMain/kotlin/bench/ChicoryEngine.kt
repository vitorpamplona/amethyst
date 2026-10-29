package bench

import com.dylibso.chicory.compiler.MachineFactoryCompiler
import com.dylibso.chicory.runtime.ExportFunction
import com.dylibso.chicory.runtime.HostFunction
import com.dylibso.chicory.runtime.ImportValues
import com.dylibso.chicory.runtime.Instance
import com.dylibso.chicory.runtime.Memory
import com.dylibso.chicory.wasm.Parser
import com.dylibso.chicory.wasm.types.FunctionType
import com.dylibso.chicory.wasm.types.ValType

private class ChicoryMemory(private val memory: Memory) : GuestMemory {
    override fun readI32(address: Int) = memory.readInt(address)

    override fun writeI8(address: Int, value: Byte) = memory.writeByte(address, value)

    override fun writeI16(address: Int, value: Short) = memory.writeShort(address, value)

    override fun writeI32(address: Int, value: Int) = memory.writeI32(address, value)

    override fun writeI64(address: Int, value: Long) = memory.writeLong(address, value)

    override fun read(address: Int, into: ByteArray, offset: Int, length: Int) {
        memory.readBytes(address, length).copyInto(into, offset)
    }

    override fun write(address: Int, from: ByteArray, offset: Int, length: Int) {
        memory.write(address, from, offset, length)
    }
}

private fun WasmType.chicory(): ValType = if (this == WasmType.I32) ValType.I32 else ValType.I64

/**
 * doom.wasm on Chicory. [compiled] translates the module to JVM bytecode when it loads (the
 * `compiler` artifact); otherwise Chicory interprets it.
 */
class ChicoryEngine(wasm: ByteArray, host: DoomHost, compiled: Boolean) : DoomEngine {
    private val instance: Instance
    private val start: ExportFunction
    private val tick: ExportFunction

    init {
        val module = Parser.parse(wasm)
        val functions =
            host.imports.map { spec ->
                val type = FunctionType.of(spec.params.map { it.chicory() }, listOfNotNull(spec.result?.chicory()))
                HostFunction(spec.module, spec.name, type) { instance: Instance, args: LongArray ->
                    val value = spec.call(ChicoryMemory(instance.memory()), args)
                    if (value == null) null else longArrayOf(value)
                }
            }
        val builder =
            Instance
                .builder(module)
                .withImportValues(ImportValues.builder().withFunctions(functions).build())
        if (compiled) builder.withMachineFactory(MachineFactoryCompiler.compile(module))
        instance = builder.build()
        instance.export("_initialize").apply()
        start = instance.export("dg_start")
        tick = instance.export("dg_tick")
    }

    override fun setIwadName(name: String) {
        val pointer = instance.export("dg_iwad_name").apply()[0].toInt()
        instance.memory().write(pointer, (name + "\u0000").encodeToByteArray())
    }

    override fun start() {
        start.apply()
    }

    override fun tick() {
        tick.apply()
    }
}
