package bench

import io.github.charlietap.chasm.embedding.exports
import io.github.charlietap.chasm.embedding.function
import io.github.charlietap.chasm.embedding.instance
import io.github.charlietap.chasm.embedding.memory.writeBytes
import io.github.charlietap.chasm.embedding.module
import io.github.charlietap.chasm.embedding.prepareFunction
import io.github.charlietap.chasm.embedding.shapes.ChasmResult
import io.github.charlietap.chasm.embedding.shapes.Import
import io.github.charlietap.chasm.embedding.shapes.Instance
import io.github.charlietap.chasm.embedding.shapes.Memory
import io.github.charlietap.chasm.embedding.shapes.PreparedFunction
import io.github.charlietap.chasm.embedding.shapes.Store
import io.github.charlietap.chasm.embedding.shapes.expect
import io.github.charlietap.chasm.embedding.store
import io.github.charlietap.chasm.embedding.validate
import io.github.charlietap.chasm.host.HostFunction
import io.github.charlietap.chasm.host.HostMemory
import io.github.charlietap.chasm.host.readI64
import io.github.charlietap.chasm.host.withMemory
import io.github.charlietap.chasm.host.writeI32
import io.github.charlietap.chasm.host.writeI64
import io.github.charlietap.chasm.runtime.value.NumberValue
import io.github.charlietap.chasm.type.FunctionType
import io.github.charlietap.chasm.type.NumberType
import io.github.charlietap.chasm.type.ResultType
import io.github.charlietap.chasm.type.ValueType

private class ChasmMemory(private val memory: HostMemory) : GuestMemory {
    override fun readI32(address: Int) = memory.readI32(address)

    override fun writeI8(address: Int, value: Byte) = memory.writeI8(address, value)

    override fun writeI16(address: Int, value: Short) = memory.writeI16(address, value)

    override fun writeI32(address: Int, value: Int) = memory.writeI32(address, value)

    override fun writeI64(address: Int, value: Long) = memory.writeI64(address, value)

    override fun read(address: Int, into: ByteArray, offset: Int, length: Int) {
        memory.read(into, address, length, offset)
    }

    override fun write(address: Int, from: ByteArray, offset: Int, length: Int) {
        memory.write(address, from, offset, length)
    }
}

private fun WasmType.chasm(): ValueType = ValueType.Number(if (this == WasmType.I32) NumberType.I32 else NumberType.I64)

/** doom.wasm on Chasm, a Kotlin Multiplatform interpreter: the same code on JVM and native. */
class ChasmEngine(wasm: ByteArray, host: DoomHost) : DoomEngine {
    private val store: Store = store()
    private val instance: Instance
    private val initialize: PreparedFunction
    private val iwadName: PreparedFunction
    private val start: PreparedFunction
    private val tick: PreparedFunction

    init {
        val module = module(wasm).expect("decode doom.wasm")
        validate(module).expect("validate doom.wasm")
        val imports =
            host.imports.map { spec ->
                val type = FunctionType(ResultType(spec.params.map { it.chasm() }), ResultType(listOfNotNull(spec.result?.chasm())))
                val arity = spec.params.size
                val hostFunction =
                    HostFunction { parameters, results ->
                        val args = LongArray(arity) { parameters.readI64(it) }
                        val value = withMemory(0) { spec.call(ChasmMemory(this), args) }
                        when (spec.result) {
                            WasmType.I32 -> results.writeI32(0, value!!.toInt())
                            WasmType.I64 -> results.writeI64(0, value!!)
                            null -> {}
                        }
                    }
                Import(spec.module, spec.name, function(store, type, hostFunction))
            }
        instance = instance(store, module, imports).expect("instantiate doom.wasm")
        initialize = prepareFunction(store, instance, "_initialize").expect("_initialize")
        start = prepareFunction(store, instance, "dg_start").expect("dg_start")
        tick = prepareFunction(store, instance, "dg_tick").expect("dg_tick")
        iwadName = prepareFunction(store, instance, "dg_iwad_name").expect("dg_iwad_name")
        call(initialize)
    }

    private fun call(function: PreparedFunction) {
        when (val result = function()) {
            is ChasmResult.Success -> {}
            is ChasmResult.Error -> throw IllegalStateException(result.error.toString())
        }
    }

    override fun setIwadName(name: String) {
        val pointer = (iwadName().expect("dg_iwad_name").single() as NumberValue.I32).value
        val memory = exports(instance).first { it.value is Memory }.value as Memory
        writeBytes(store, memory, pointer, (name + "\u0000").encodeToByteArray()).expect("write iwad name")
    }

    override fun start() = call(start)

    override fun tick() = call(tick)
}
