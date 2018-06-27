package it.unibo.protelis.lora.client

import com.fazecast.jSerialComm.SerialPort
import com.google.common.cache.Cache
import com.google.common.cache.CacheBuilder
import com.google.common.collect.ImmutableMap
import it.unibo.protelis.lora.Codec
import it.unibo.protelis.lora.Compressor
import it.unibo.protelis.lora.JavaSerializer
import it.unibo.protelis.lora.NoCompression
import it.unibo.protelis.lora.Packer
import org.protelis.lang.datatype.DeviceUID
import org.protelis.vm.NetworkManager
import org.protelis.vm.util.CodePath
import java.util.concurrent.TimeUnit

class LoRaNetworkManager<P : Any> @JvmOverloads constructor(
    protected val network: LoRaClassANetworking,
    expireAfter: Long,
    protected val packer: Packer<P>,
    protected val codec: Codec<P> = JavaSerializer<P>(),
    protected val compressor: Compressor = NoCompression
) : NetworkManager {

    @JvmOverloads constructor(
        port: SerialPort,
        expireAfter: Long,
        packer: Packer<P>,
        codec: Codec<P> = JavaSerializer<P>(),
        compressor: Compressor = NoCompression
    ) : this (LoRaClassANetworking(RN2483(port)), expireAfter, packer, codec, compressor)

    private val cache: Cache<DeviceUID, MutableMap<CodePath, Any>> = CacheBuilder.newBuilder()
        .expireAfterWrite(expireAfter, TimeUnit.SECONDS)
        .build()

    override fun shareState(toSend: MutableMap<CodePath, Any>) {
        val response = network.send(compressor.compress(codec.encode(packer.pack(toSend))))
        cache.putAll(response.mapValues { packer.unpack(codec.decode(compressor.uncompress(it.value))) })
    }

    override fun getNeighborState(): MutableMap<DeviceUID, MutableMap<CodePath, Any>> = ImmutableMap.copyOf(cache.asMap())

}