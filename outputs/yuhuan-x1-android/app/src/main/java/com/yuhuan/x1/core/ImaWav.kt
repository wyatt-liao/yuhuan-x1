package com.yuhuan.x1.core

import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs

object ImaWav {
    private val steps = intArrayOf(7,8,9,10,11,12,13,14,16,17,19,21,23,25,28,31,34,37,41,45,50,55,60,66,73,80,88,97,107,118,130,143,157,173,190,209,230,253,279,307,337,371,408,449,494,544,598,658,724,796,876,963,1060,1166,1282,1411,1552,1707,1878,2066,2272,2499,2749,3024,3327,3660,4026,4428,4871,5358,5894,6484,7132,7845,8630,9493,10442,11487,12635,13899,15289,16818,18500,20350,22385,24623,27086,29794,32767)
    private val adjust = intArrayOf(-1,-1,-1,-1,2,4,6,8,-1,-1,-1,-1,2,4,6,8)
    data class Header(val samples: Long, val dataBytes: Long)
    fun validate(file: File, expectedBytes: Long, expectedSamples: Long? = null): Header {
        require(file.length() == expectedBytes && expectedBytes >= 316) { "音频文件长度不符" }
        val h = ByteArray(60)
        RandomAccessFile(file,"r").use { it.readFully(h) }
        val b = ByteBuffer.wrap(h).order(ByteOrder.LITTLE_ENDIAN)
        fun text(offset: Int, value: String) = h.copyOfRange(offset,offset+value.length).toString(Charsets.US_ASCII) == value
        fun u32(offset: Int) = b.getInt(offset).toLong() and 0xffffffffL
        require(text(0,"RIFF") && text(8,"WAVEfmt ") && u32(4)+8 == expectedBytes && u32(16)==20L &&
            b.getShort(20).toInt()==17 && b.getShort(22).toInt()==1 && u32(24)==RATE &&
            b.getShort(32).toInt()==256 && b.getShort(34).toInt()==4 && b.getShort(38).toInt()==505 &&
            text(40,"fact") && u32(44)==4L && text(52,"data")) { "不支持或损坏的录音格式" }
        val data = u32(56); val samples = u32(48)
        require(data+60 == expectedBytes && data%256==0L && samples>0 && samples <= data/256*505 && samples > (data/256-1)*505) { "音频块或样本数不符" }
        if (expectedSamples != null) require(samples == expectedSamples) { "录音样本数与清单不符" }
        return Header(samples,data)
    }
    fun decodeBlock(block: ByteArray): ShortArray {
        require(block.size==256 && block[3].toInt()==0)
        var predictor = ByteBuffer.wrap(block).order(ByteOrder.LITTLE_ENDIAN).short.toInt()
        var index = block[2].toInt() and 255; require(index <= 88) { "ADPCM 步长索引错误" }
        val out = ShortArray(505); out[0] = predictor.toShort()
        for (i in 1..504) {
            val nibble = (block[4+(i-1)/2].toInt() ushr (((i-1)%2)*4)) and 15
            val step = steps[index]
            var delta = step shr 3
            if (nibble and 4 != 0) delta += step
            if (nibble and 2 != 0) delta += step shr 1
            if (nibble and 1 != 0) delta += step shr 2
            predictor = (predictor + if (nibble and 8 != 0) -delta else delta).coerceIn(-32768,32767)
            index = (index+adjust[nibble]).coerceIn(0,88); out[i] = predictor.toShort()
        }
        return out
    }
    fun render(cuts: List<Cut>, source: (Segment)->File, destination: File, cancelled: ()->Boolean = { false }) {
        val total = cuts.sumOf { it.count }; require(total*2 <= 0xffffffffL-36)
        destination.parentFile?.mkdirs()
        val partial = File(destination.path+".part")
        try {
            RandomAccessFile(partial,"rw").use { out ->
                out.setLength(0); out.write(pcmHeader(total))
                val block = ByteArray(256)
                for (cut in cuts) {
                    require(!cancelled()) { "已取消音频准备" }
                    val file = source(cut.segment); validate(file,cut.segment.bytes,cut.segment.samples)
                    RandomAccessFile(file,"r").use { input ->
                        var position = cut.offset; var remaining = cut.count
                        input.seek(60 + position/505*256)
                        while (remaining > 0) {
                            require(!cancelled()) { "已取消音频准备" }
                            input.readFully(block); val pcm = decodeBlock(block)
                            val start = (position%505).toInt(); val count = minOf(remaining,505L-start).toInt()
                            val data = ByteBuffer.allocate(count*2).order(ByteOrder.LITTLE_ENDIAN)
                            for (i in start until start+count) data.putShort(pcm[i])
                            out.write(data.array()); position += count; remaining -= count
                        }
                    }
                }
                out.fd.sync()
            }
            check(partial.renameTo(destination)) { "无法保存播放副本" }
        } catch (t: Throwable) { partial.delete(); throw t }
    }
    fun pcmHeader(samples: Long): ByteArray = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN).apply {
        put("RIFF".toByteArray());putInt((36+samples*2).toInt());put("WAVEfmt ".toByteArray());putInt(16)
        putShort(1);putShort(1);putInt(RATE.toInt());putInt((RATE*2).toInt());putShort(2);putShort(16)
        put("data".toByteArray());putInt((samples*2).toInt())
    }.array()
    fun peaks(pcmFile: File, bars: Int = 80): List<Float> {
        val samples = (pcmFile.length()-44)/2; if (samples <= 0) return emptyList()
        return RandomAccessFile(pcmFile,"r").use { f -> (0 until bars).map { bar ->
            val from = samples*bar/bars; val to = samples*(bar+1)/bars
            var peak = 0
            val stride = maxOf(1L,(to-from)/80)
            var i = from
            while (i < to) { f.seek(44+i*2); val lo = f.read();val hi=f.read();val v=((hi shl 8) or lo).toShort().toInt();peak=maxOf(peak,abs(v));i+=stride }
            (peak/32768f).coerceIn(0.015f,1f)
        } }
    }
}
