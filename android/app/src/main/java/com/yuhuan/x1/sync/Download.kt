package com.yuhuan.x1.sync

import com.yuhuan.x1.core.ImaWav
import com.yuhuan.x1.core.Segment
import com.yuhuan.x1.transport.Transport
import com.yuhuan.x1.transport.command
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/** The ACK is deliberately the last step, after validation, fsync and the atomic rename. */
suspend fun receiveSegment(transport: Transport, segment: Segment, partial: File, final: File, progress: (Long)->Unit) {
    final.parentFile?.mkdirs()
    val alreadyValid=final.exists() && runCatching { ImaWav.validate(final,segment.bytes,segment.samples) }.isSuccess
    if(final.exists() && runCatching { ImaWav.validate(final,segment.bytes,segment.samples) }.isFailure) {
        check(final.renameTo(File(final.path+".invalid-${System.currentTimeMillis()}"))) { "损坏副本无法隔离" }
    }
    if(!final.exists()) {
        if(partial.exists() && partial.length()>segment.bytes)check(partial.delete())
        transport.download(segment,partial,progress)
        try { ImaWav.validate(partial,segment.bytes,segment.samples) }
        catch(t: Throwable) { partial.delete();throw t }
        java.io.RandomAccessFile(partial,"rw").use { it.fd.sync() }
        Files.move(partial.toPath(),final.toPath(),StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING)
    }
    progress(segment.bytes)
    if(!alreadyValid || !segment.synced)transport.command(command("ack","id" to segment.id,"bytes" to segment.bytes))
}
