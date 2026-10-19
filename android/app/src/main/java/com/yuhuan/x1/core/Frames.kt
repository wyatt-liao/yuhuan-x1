package com.yuhuan.x1.core

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.CRC32

/** One response, with a fresh request id for every retry. No Android dependency. */
class Frames(private val request: Int, private val fileOffset: Long = 0) {
    private val out = ByteArrayOutputStream()
    var kind: Int = 0; private set
    var error: Boolean = false; private set
    var done: Boolean = false; private set
    fun append(packet: ByteArray): Boolean {
        require(packet.size >= 12) { "蓝牙数据帧过短" }
        val h = ByteBuffer.wrap(packet).order(ByteOrder.LITTLE_ENDIAN)
        val incomingKind = h.get().toInt() and 255
        val flags = h.get().toInt() and 255
        val req = h.short.toInt() and 65535
        if (req != request) return false
        require(!done && incomingKind in 1..2 && flags and 252 == 0) { "蓝牙数据状态无效" }
        val offset = h.int.toLong() and 0xffffffffL
        val crc = h.int.toLong() and 0xffffffffL
        if (kind == 0) kind = incomingKind
        require(kind == incomingKind) { "蓝牙响应类型改变" }
        val base = if (kind == 1) 0 else fileOffset
        require(offset == base + out.size()) { "蓝牙数据偏移不连续" }
        val payload = packet.copyOfRange(12, packet.size)
        require(CRC32().apply { update(payload) }.value == crc) { "蓝牙数据校验失败" }
        require(out.size() + payload.size <= 2 * 1024 * 1024) { "响应过大" }
        out.write(payload)
        error = error || flags and 2 != 0
        done = flags and 1 != 0
        return true
    }
    fun bytes(): ByteArray { check(done); return out.toByteArray() }
}
