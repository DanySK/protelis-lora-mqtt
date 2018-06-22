package it.unibo.protelis.lora.test

import com.google.gson.Gson
import it.unibo.protelis.lora.Inbound
import org.junit.Test
import kotlin.test.assertNotNull

class TestJSON {
    @Test
    fun testUnmarshal() {
        val gson = Gson()
        val res = gson.fromJson("""{
        |"applicationID":"1",
        |"applicationName":"test",
        |"deviceName":"abp01",
        |"devEUI":"003fb1fe08f0f264",
        |"rxInfo":[{
        |   "mac":"ad4bad4bad4bad48",
        |   "rssi":-42,
        |   "loRaSNR":9.8,
        |   "name":"test2",
        |   "latitude":44.1514508,
        |   "longitude":12.2411046,
        |   "altitude":0
        |}],
        |"txInfo":{
        |   "frequency":868100000,
        |   "dataRate":{"
        |       modulation":"LORA",
        |       "bandwidth":125,
        |       "spreadFactor":7
        |   },
        |   "adr":true,
        |   "codeRate":"4/5"
        |},
        |"fCnt":4930,
        |"fPort":10,
        |"data":"////////",
        |"object":{
        |   "transaction":"129",
        |   "framecount": 130,
        |   "frame": 130,
        |   "payload": [ 2, 3, 234, 255 ]
        |}
    |}""".trimMargin(), Inbound::class.java)
        assertNotNull(res)
        println(res)
    }
}
