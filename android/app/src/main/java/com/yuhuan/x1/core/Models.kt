package com.yuhuan.x1.core

import org.json.JSONObject
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

const val RATE = 16000L
const val OPEN_END = "ffffffffffffffff"
fun hex(value: String): ULong {
    require(value.matches(Regex("[0-9a-f]{16}"))) { "设备标识格式错误" }
    return value.toULong(16)
}
data class Segment(val id: String, val start: ULong, val samples: Long, val epochMs: Long,
                   val bytes: Long, val protected: Boolean, val synced: Boolean) {
    val end get() = start + samples.toULong()
    fun json() = JSONObject().put("id", id).put("start", start.toString(16).padStart(16, '0'))
        .put("samples", samples).put("epoch_ms", epochMs).put("bytes", bytes)
        .put("protected", protected).put("synced", synced)
    companion object {
        fun parse(j: JSONObject): Segment {
            val id = j.getString("id"); hex(id)
            val count = j.getLong("samples"); val bytes = j.getLong("bytes")
            require(count in 1..(RATE * 120) && bytes in 316..2_000_000) { "音频清单范围错误" }
            val start = hex(j.getString("start")); require(start <= ULong.MAX_VALUE - count.toULong())
            return Segment(id, start, count, j.optLong("epoch_ms"), bytes,
                j.optBoolean("protected"), j.optBoolean("synced"))
        }
    }
}
data class Mark(val id: String, val start: ULong, val end: ULong, val group: String, val kind: Int) {
    fun json() = JSONObject().put("id", id).put("start", start.toString(16).padStart(16, '0'))
        .put("end", end.toString(16).padStart(16, '0')).put("group", group).put("kind", kind)
    companion object {
        fun parse(j: JSONObject): Mark {
            val id = j.getString("id"); val group = j.getString("group"); hex(id); hex(group)
            val start = hex(j.getString("start")); val end = hex(j.getString("end"))
            require(end >= start && j.getInt("kind") in 1..2)
            return Mark(id, start, end, group, j.getInt("kind"))
        }
    }
}
data class Cut(val segment: Segment, val offset: Long, val count: Long)
data class Clip(val key: String, val namespace: String, val title: String, val note: String,
                val kind: Int, val cuts: List<Cut>, val marks: List<Mark>, val localCount: Int,
                val available: Boolean) {
    val samples get() = cuts.sumOf { it.count }
    val durationMs get() = samples * 1000 / RATE
    val epochMs get() = cuts.firstOrNull()?.let { if (it.segment.epochMs > 0) it.segment.epochMs + it.offset * 1000 / RATE else 0 } ?: 0
    val complete get() = cuts.isNotEmpty() && localCount == cuts.size
}
/** Union protection ranges before cutting; pauses are excluded, overlapping marks never duplicate audio. */
fun cutsFor(segments: List<Segment>, marks: List<Mark>): List<Cut> {
    val merged = mutableListOf<Pair<ULong, ULong>>()
    for (m in marks.sortedBy { it.start }) {
        val last = merged.lastOrNull()
        if (last != null && m.start <= last.second) merged[merged.lastIndex] = last.first to maxOf(last.second, m.end)
        else merged += m.start to m.end
    }
    return segments.sortedBy { it.start }.flatMap { s -> merged.mapNotNull { (a,b) ->
        val lo = maxOf(a,s.start); val hi = minOf(b,s.end)
        if (hi <= lo) null else Cut(s,(lo-s.start).toLong(),(hi-lo).toLong())
    } }
}
fun formatDuration(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return if (s >= 3600) "%d:%02d:%02d".format(s/3600,s/60%60,s%60) else "%02d:%02d".format(s/60,s%60)
}
fun timeLabel(epoch: Long, pattern: String = "MM月dd日 HH:mm"): String =
    if (epoch <= 0) "未校时录音" else DateTimeFormatter.ofPattern(pattern).format(Instant.ofEpochMilli(epoch).atZone(ZoneId.systemDefault()))
fun sizeLabel(bytes: Long): String = if (bytes >= 1_000_000_000) "%.1f GB".format(bytes/1e9) else "%.1f MB".format(bytes/1e6)
