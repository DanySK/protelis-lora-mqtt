package it.unibo.protelis.lora.test

import com.esotericsoftware.kryo.Kryo
import com.esotericsoftware.kryo.io.Input
import com.esotericsoftware.kryo.io.Output
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import gnu.trove.list.array.TByteArrayList
import org.apache.commons.compress.compressors.lzma.LZMACompressorInputStream
import org.apache.commons.compress.compressors.lzma.LZMACompressorOutputStream
import org.apache.commons.io.IOUtils
import org.apache.commons.lang3.SerializationUtils
import org.eclipse.core.internal.preferences.Base64
import org.mapdb.elsa.ElsaMaker
import org.mapdb.elsa.ElsaSerializer
import org.objenesis.strategy.StdInstantiatorStrategy
import org.protelis.lang.ProtelisLoader
import org.protelis.lang.datatype.DeviceUID
import org.protelis.lang.datatype.Field
import org.protelis.lang.datatype.impl.IntegerUID
import org.protelis.vm.NetworkManager
import org.protelis.vm.ProtelisVM
import org.protelis.vm.SpatiallyEmbeddedDevice
import org.protelis.vm.impl.AbstractExecutionContext
import org.protelis.vm.impl.SimpleExecutionEnvironment
import org.protelis.vm.util.CodePath
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.Serializable

val programs = listOf(
    "1",
    "nbr(1)",
    "gradient",
    """
    import protelis:coord:spreading
    import protelis:coord:accumulation
    let source = true;
    broadcast(source, countDevices(distanceTo(source)))
    """.trimIndent()
)

interface Encoder {
    val name: String
    fun encode(map: Any): ByteArray
    fun decode(enc: ByteArray): Any
}

interface Prepare<T> {
    val name: String
    fun pack(map: MutableMap<CodePath, Any>): T
    fun unpack(packed: Any): MutableMap<CodePath, Any>
}

val encoders = listOf<Encoder>(
    object : Encoder {
        override val name = "java"
        override fun encode(map: Any): ByteArray = SerializationUtils.serialize(map as Serializable)
        override fun decode(enc: ByteArray): Any = SerializationUtils.deserialize(enc)
    },
    object : Encoder {
        override val name = "kryo"
        private val kryo = Kryo().also { it.instantiatorStrategy = Kryo.DefaultInstantiatorStrategy(StdInstantiatorStrategy()) }
        override fun encode(map: Any): ByteArray {
            val out = ByteArrayOutputStream()
            val output = Output(out)
            kryo.writeClassAndObject(output, map)
            output.flush()
            return out.toByteArray()
        }
        override fun decode(enc: ByteArray): Any {
            val input = ByteArrayInputStream(enc)
            return kryo.readClassAndObject(Input(input))
        }
    },
    object : Encoder {
        override val name = "elsa"
        private val elsa: ElsaSerializer = ElsaMaker().make()
        override fun encode(map: Any): ByteArray {
            val out = ByteArrayOutputStream()
            val data = DataOutputStream(out)
            elsa.serialize(data, map)
            return out.toByteArray()
        }
        override fun decode(enc: ByteArray): Any = elsa.deserialize(DataInputStream(ByteArrayInputStream(enc)))
    }
)

val preparators = listOf<Prepare<*>>(
    object : Prepare<MutableMap<CodePath, Any>> {
        override val name = "identity"
        override fun pack(map: MutableMap<CodePath, Any>) = map
        override fun unpack(packed: Any) = packed as MutableMap<CodePath, Any>
    },
    object : Prepare<Array<Any>> {
        override val name = "flatten"
        override fun pack(map: MutableMap<CodePath, Any>) = map.flatMap { listOf(it.key.path, it.value) }.toTypedArray()
        override fun unpack(packed: Any): MutableMap<CodePath, Any> {
            val arr = packed as Array<Any>
            val result = LinkedHashMap<CodePath, Any>()
            for (i in 0 until arr.size step 2) {
                result.put(CodePath(TByteArrayList(arr[i] as ByteArray)), arr[i + 1])
            }
            return result
        }
    },
    object : Prepare<String> {
        override val name = "json"
        val gson: Gson = GsonBuilder().serializeSpecialFloatingPointValues().create()
        override fun pack(map: MutableMap<CodePath, Any>) = gson.toJson(map.mapKeys { String(Base64.encode(it.key.path), Charsets.US_ASCII) })
        override fun unpack(enc: Any) = gson.fromJson<MutableMap<String, Any>>(enc as String, object: TypeToken<MutableMap<String, Any>>() { }.type)
                    .mapKeys { CodePath(TByteArrayList(Base64.decode(it.key.toByteArray(Charsets.US_ASCII)))) }
                    .toMutableMap()
    }
)

fun main(a: Array<String>) {
    for (program in programs) {
        println("Program ${program.take(10)}")
        for (encoder in encoders) {
            for (prepare in preparators) {
                val vm = ProtelisVM(ProtelisLoader.parse(program),
                    object: AbstractExecutionContext(SimpleExecutionEnvironment(),
                            object: NetworkManager {
                                var toSend: MutableMap<CodePath, Any> = mutableMapOf()
                                override fun getNeighborState(): MutableMap<DeviceUID, MutableMap<CodePath, Any>> = mutableMapOf(IntegerUID(1234355646) to toSend)
                                override fun shareState(toSend: MutableMap<CodePath, Any>) {
                                    this.toSend = toSend
                                    val packed: Any = prepare.pack(toSend)!!
                                    val serialized = encoder.encode(packed)
                                    assert(prepare.unpack(encoder.decode(serialized)).equals(toSend))
                                    val out = ByteArrayOutputStream()
                                    val lzma = LZMACompressorOutputStream(out)
                                    IOUtils.copy(ByteArrayInputStream(serialized), lzma)
                                    lzma.finish()
                                    val compressed = out.toByteArray()
                                    val buffer = ByteArrayOutputStream()
                                    LZMACompressorInputStream(ByteArrayInputStream(compressed)).copyTo(buffer)
                                    buffer.flush()
                                    val decompressed = buffer.toByteArray()
                                    assert(prepare.unpack(encoder.decode(decompressed)).equals(toSend))
                                    println("Sent payload with encoder ${encoder.name}-${prepare.name}, size: ${serialized.size} bytes (${compressed.size} bytes with LZMA compression)")
                                }
                            }), SpatiallyEmbeddedDevice {
                        override fun nbrRange(): Field = buildField({it}, Math.random())
                        override fun getDeviceUID(): DeviceUID = IntegerUID(1234355646)
                        override fun getCurrentTime() = System.currentTimeMillis().toDouble() / 1000
                        override fun instance(): AbstractExecutionContext = TODO("not implemented")
                        override fun nextRandomDouble() = TODO("not implemented")

                    })
                vm.runCycle()
            }
        }
    }
}
