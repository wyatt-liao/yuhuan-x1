package com.yuhuan.x1

import com.yuhuan.x1.core.*
import com.yuhuan.x1.sync.receiveSegment
import com.yuhuan.x1.transport.*
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.CRC32
import kotlin.math.log10

class ProtocolTest {
    private fun frame(req: Int,kind: Int,flags: Int,offset: Int,payload: ByteArray) = ByteBuffer.allocate(12+payload.size).order(ByteOrder.LITTLE_ENDIAN)
        .put(kind.toByte()).put(flags.toByte()).putShort(req.toShort()).putInt(offset).putInt(CRC32().apply { update(payload) }.value.toInt()).put(payload).array()
    private fun fixture()=javaClass.getResourceAsStream("/firmware-adpcm.wav")!!.readBytes()
    @Test fun requestIdsCrcAndOffsetsAreVerified() {
        val f=Frames(7,4096)
        assertFalse(f.append(frame(6,2,1,4096,byteArrayOf(9))))
        f.append(frame(7,2,0,4096,byteArrayOf(1,2)))
        f.append(frame(7,2,1,4098,byteArrayOf(3)))
        assertTrue(f.done);assertArrayEquals(byteArrayOf(1,2,3),f.bytes())
        assertThrows(IllegalArgumentException::class.java) { Frames(7).append(frame(7,2,1,0,byteArrayOf(1)).also { it[12]=9 }) }
        assertThrows(IllegalArgumentException::class.java) { Frames(7,100).append(frame(7,2,1,101,byteArrayOf(1))) }
    }
    @Test fun jsonErrorStartsAtZeroEvenDuringFileRead() {
        val f=Frames(8,1000);f.append(frame(8,1,3,0,"{\"ok\":false}".toByteArray()))
        assertTrue(f.error);assertEquals(1,f.kind);assertTrue(f.done)
    }
    @Test fun highUnsignedIdsAndPausedRangesDoNotLosePrecision() {
        assertEquals(ULong.MAX_VALUE,hex("ffffffffffffffff"))
        val s=Segment("fffffffffffffffe",100u,1000,0,572,true,false)
        val marks=listOf(Mark("0000000000000001",120u,200u,"0000000000000001",2),
            Mark("0000000000000002",180u,250u,"0000000000000001",2),Mark("0000000000000003",300u,400u,"0000000000000001",2))
        val cuts=cutsFor(listOf(s),marks)
        assertEquals(listOf(20L,200L),cuts.map { it.offset });assertEquals(listOf(130L,100L),cuts.map { it.count })
    }
    @Test fun firmwareAudioDecodesLikeIndependentFfmpegAndCropsPrecisely() {
        val dir=kotlin.io.path.createTempDirectory().toFile()
        try {
            val original=File(dir,"input.wav").apply { writeBytes(fixture()) }
            val h=ImaWav.validate(original,original.length())
            val s=Segment("0000000000000001",0u,h.samples,0,original.length(),false,false)
            val output=File(dir,"pcm.wav")
            ImaWav.render(listOf(Cut(s,0,h.samples)),{original},output)
            val pcm=ByteBuffer.wrap(output.readBytes().copyOfRange(44,output.length().toInt())).order(ByteOrder.LITTLE_ENDIAN)
            val reference=ByteBuffer.wrap(javaClass.getResourceAsStream("/ffmpeg-decoded.pcm")!!.readBytes()).order(ByteOrder.LITTLE_ENDIAN)
            var signal=0.0;var noise=0.0
            repeat(h.samples.toInt()) { val a=pcm.short.toInt();val b=reference.short.toInt();signal+=b.toDouble()*b;noise+=(a-b).toDouble()*(a-b) }
            val snr=10*log10(signal/maxOf(noise,0.000001));assertTrue("decoder vs FFmpeg SNR $snr",snr>35)
            val crop=File(dir,"crop.wav");ImaWav.render(listOf(Cut(s,123,321),Cut(s,1001,500)),{original},crop)
            assertEquals(44+821*2,crop.length().toInt())
            val full=output.readBytes();val expected=full.copyOfRange(44+123*2,44+(123+321)*2)+full.copyOfRange(44+1001*2,44+(1001+500)*2)
            assertArrayEquals(expected,crop.readBytes().copyOfRange(44,crop.length().toInt()))
        } finally { dir.deleteRecursively() }
    }
    @Test fun resumedTransferAcknowledgesOnlyAfterValidAtomicCommit()= runTest {
        val dir=kotlin.io.path.createTempDirectory().toFile()
        try {
            val data=fixture();val f=File(dir,"final.wav");val p=File(dir,"audio.part").apply { writeBytes(data.copyOfRange(0,503)) }
            val count=ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN).getInt(48).toLong()
            val s=Segment("0000000000000001",0u,count,0,data.size.toLong(),false,false)
            var ack=false
            val transport=object : Transport {
                override val label="test"
                override suspend fun command(body: JSONObject): JSONObject {
                    assertEquals("ack",body.getString("cmd"));assertTrue(f.exists());assertFalse(p.exists());assertArrayEquals(data,f.readBytes())
                    assertEquals(data.size.toLong(),body.getLong("bytes"));ack=true;return JSONObject().put("ok",true)
                }
                override suspend fun download(segment: Segment,partial: File,progress: (Long)->Unit) {
                    assertEquals(503,partial.length().toInt());partial.appendBytes(data.copyOfRange(503,data.size));progress(data.size.toLong())
                }
            }
            receiveSegment(transport,s,p,f) {};assertTrue(ack)
            f.delete();p.writeBytes(data.copyOfRange(0,100));ack=false
            val broken=object : Transport by transport {
                override suspend fun download(segment: Segment,partial: File,progress: (Long)->Unit) { error("link interrupted") }
            }
            try { receiveSegment(broken,s,p,f) {};fail("should fail") } catch(_: IllegalStateException) {}
            assertFalse(ack);assertFalse(f.exists());assertEquals(100,p.length().toInt())
        } finally { dir.deleteRecursively() }
    }
    @Test fun lanRejectsPublicHostsAndLoopback() {
        assertTrue(LanTransport.isPrivateIpv4("192.168.1.32"));assertTrue(LanTransport.isPrivateIpv4("172.20.1.4"));assertTrue(LanTransport.isPrivateIpv4("10.0.2.2"))
        for(s in listOf("127.0.0.1","8.8.8.8","169.254.1.2","device.example","192.168.1.999","192.168.1.1:8080"))assertFalse(s,LanTransport.isPrivateIpv4(s))
    }
}
