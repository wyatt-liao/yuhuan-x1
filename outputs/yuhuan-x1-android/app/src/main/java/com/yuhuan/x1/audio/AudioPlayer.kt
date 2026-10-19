package com.yuhuan.x1.audio

import android.content.Context
import android.media.*
import com.yuhuan.x1.core.*
import com.yuhuan.x1.data.Library
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import java.io.RandomAccessFile
import kotlin.math.abs

data class PlayerState(val key: String="",val playing: Boolean=false,val position: Long=0,val duration: Long=0,
                       val speed: Float=1f,val peaks: List<Float> = emptyList(),val error: String="")
class AudioPlayer(context: Context,private val library: Library) {
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.IO)
    private val manager=context.getSystemService(AudioManager::class.java)
    val state=MutableStateFlow(PlayerState())
    private var clip: Clip?=null
    private var job: Job?=null
    @Volatile private var track: AudioTrack?=null
    @Volatile private var requested=false
    private val focus=AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN).setAudioAttributes(attributes())
        .setOnAudioFocusChangeListener { if(it!=AudioManager.AUDIOFOCUS_GAIN)pause() }.build()
    private fun attributes()=AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build()
    fun load(value: Clip) {
        if(clip?.key==value.key && clip?.complete==value.complete && clip?.samples==value.samples) { clip=value;return }
        stop();clip=value;state.value=PlayerState(key=value.key,duration=value.durationMs)
        if(value.complete)scope.launch {
            val peaks=runCatching { peaks(value) }.getOrDefault(emptyList())
            if(state.value.key==value.key)state.value=state.value.copy(peaks=peaks)
        }
    }
    fun toggle() { if(requested)pause() else play() }
    fun play() {
        val value=clip?:return
        if(!value.complete) { state.value=state.value.copy(error="请先将完整录音同步到手机");return }
        if(manager.requestAudioFocus(focus)!=AudioManager.AUDIOFOCUS_REQUEST_GRANTED) { state.value=state.value.copy(error="暂时无法获得音频播放权限");return }
        if(state.value.position>=value.durationMs)seek(0)
        requested=true;state.value=state.value.copy(playing=true,error="")
        if(job?.isActive==true) { track?.play();return }
        start(value,state.value.position)
    }
    fun pause() { requested=false;runCatching { track?.pause() };state.value=state.value.copy(playing=false);manager.abandonAudioFocusRequest(focus) }
    fun speed() {
        val speeds=listOf(1f,1.25f,1.5f,2f);val speed=speeds[(speeds.indexOf(state.value.speed)+1)%speeds.size]
        state.value=state.value.copy(speed=speed);runCatching { track?.playbackParams=PlaybackParams().setSpeed(speed) }
    }
    fun seek(ms: Long) {
        val value=clip?:return;val wasPlaying=requested
        job?.cancel();requested=false
        runCatching { track?.pause();track?.flush() }
        val position=ms.coerceIn(0,value.durationMs)
        state.value=state.value.copy(position=position,playing=false)
        if(wasPlaying) { requested=true;state.value=state.value.copy(playing=true);start(value,position) }
    }
    private fun start(value: Clip,startMs: Long) {
        val previous=job
        previous?.cancel()
        job=scope.launch {
            previous?.join()
            var audio: AudioTrack?=null
            try {
                val startSample=startMs*RATE/1000
                val size=maxOf(AudioTrack.getMinBufferSize(RATE.toInt(),AudioFormat.CHANNEL_OUT_MONO,AudioFormat.ENCODING_PCM_16BIT),4096)
                audio=AudioTrack.Builder().setAudioAttributes(attributes()).setAudioFormat(AudioFormat.Builder().setSampleRate(RATE.toInt())
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO).setEncoding(AudioFormat.ENCODING_PCM_16BIT).build()).setBufferSizeInBytes(size)
                    .setTransferMode(AudioTrack.MODE_STREAM).build()
                check(audio.state==AudioTrack.STATE_INITIALIZED) { "无法初始化播放器" }
                track=audio;audio.playbackParams=PlaybackParams().setSpeed(state.value.speed);audio.play()
                var skipped=startSample;var written=0L
                val block=ByteArray(256)
                for(cut in value.cuts) {
                    if(skipped>=cut.count) { skipped-=cut.count;continue }
                    var position=cut.offset+skipped;var remaining=cut.count-skipped;skipped=0
                    RandomAccessFile(library.source(value.namespace,cut.segment),"r").use { input ->
                        input.seek(60+position/505*256)
                        while(remaining>0) {
                            ensureActive()
                            while(!requested) { delay(40);ensureActive() }
                            input.readFully(block);val decoded=ImaWav.decodeBlock(block)
                            val offset=(position%505).toInt();val count=minOf(505L-offset,remaining).toInt()
                            var sent=0
                            while(sent<count) {
                                ensureActive()
                                if(!requested) { delay(40);continue }
                                val n=audio.write(decoded,offset+sent,count-sent,AudioTrack.WRITE_BLOCKING)
                                check(n>=0) { "音频输出中断" };sent+=n
                            }
                            written+=count;position+=count;remaining-=count
                            if(state.value.key==value.key)state.value=state.value.copy(position=minOf(value.durationMs,(startSample+(audio.playbackHeadPosition.toLong() and 0xffffffffL))*1000/RATE))
                        }
                    }
                }
                while((audio.playbackHeadPosition.toLong() and 0xffffffffL)<written) {
                    ensureActive();delay(30)
                    state.value=state.value.copy(position=minOf(value.durationMs,(startSample+(audio.playbackHeadPosition.toLong() and 0xffffffffL))*1000/RATE))
                }
                requested=false;state.value=state.value.copy(playing=false,position=value.durationMs)
            } catch(t: Throwable) {
                if(t !is CancellationException) { requested=false;state.value=state.value.copy(playing=false,error=t.message?:"播放失败") }
            } finally {
                runCatching { audio?.stop();audio?.release() }
                if(track===audio) { track=null;if(!requested)manager.abandonAudioFocusRequest(focus) }
            }
        }
    }
    private fun peaks(value: Clip): List<Float> {
        return (0 until 80).map { bar ->
            var target=value.samples*bar/80;var cut=value.cuts.last()
            for(c in value.cuts) { cut=c;if(target<c.count)break;target-=c.count }
            RandomAccessFile(library.source(value.namespace,cut.segment),"r").use { file ->
                val position=cut.offset+target.coerceAtMost(cut.count-1);file.seek(60+position/505*256)
                val block=ByteArray(256);file.readFully(block)
                (ImaWav.decodeBlock(block).maxOf { abs(it.toInt()) }/32768f).coerceAtLeast(0.015f)
            }
        }
    }
    fun stop() { requested=false;job?.cancel();runCatching { track?.pause() };manager.abandonAudioFocusRequest(focus);state.value=PlayerState() }
    fun close() { stop();scope.cancel() }
}
