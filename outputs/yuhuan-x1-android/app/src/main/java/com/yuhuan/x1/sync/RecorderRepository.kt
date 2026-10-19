package com.yuhuan.x1.sync

import android.content.Context
import com.yuhuan.x1.core.*
import com.yuhuan.x1.data.Library
import com.yuhuan.x1.data.Secrets
import com.yuhuan.x1.transport.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject

data class DeviceStatus(val recording: Boolean=false,val manual: String="idle",val batteryMv: Int=0,
                        val freeBytes: Long=0,val timeline: ULong=0u,val wifiIp: String="",val version: String="",
                        val storage: Boolean=false,val error: String="")
data class SyncState(val stage: String="等待连接",val running: Boolean=false,val completed: Int=0,val total: Int=0,
                     val bytes: Long=0,val totalBytes: Long=0,val transport: String="蓝牙",val file: String="",val error: String="")
class RecorderRepository(private val context: Context,val library: Library,val secrets: Secrets) {
    val scope=CoroutineScope(SupervisorJob()+Dispatchers.IO)
    val ble=BleTransport(context,scope)
    val status=MutableStateFlow(DeviceStatus())
    val sync=MutableStateFlow(SyncState())
    val auto=MutableStateFlow(secrets.auto)
    val notices=MutableSharedFlow<String>(extraBufferCapacity=8)
    val busy=MutableStateFlow(false)
    private val session=Mutex()
    private var syncJob: Job?=null
    private var loop: Job?=null
    init { scope.launch { ble.ready.collect { if(!it)library.disconnect() } } }
    private fun message(t: Throwable) = when(t) { is TimeoutCancellationException -> "连接超时，请重试"; else -> t.message?:"操作失败，请重试" }
    fun scan() { runCatching { ble.scan() }.onFailure { notices.tryEmit(message(it)) } }
    suspend fun connect(address: String,reportErrors: Boolean=true) {
        if(busy.value||ble.ready.value)return
        busy.value=true
        try {
            session.withLock {
                ble.connect(address);secrets.address=address
                ble.command(command("time","epoch_ms" to System.currentTimeMillis()))
                refreshLocked();catalogLocked(ble)
            }
            if(reportErrors)notices.emit("语环 X1 已连接")
            if(auto.value)startSync()
        } catch(t: Throwable) { if(t is CancellationException&&t !is TimeoutCancellationException)throw t;if(reportErrors)notices.emit(message(t)) }
        finally { busy.value=false }
    }
    private suspend fun refreshLocked() {
        val j=ble.command(command("status"))
        val token=j.optString("wifi_token")
        if(token.matches(Regex("[0-9a-f]{64}")))secrets.saveToken(ble.address.value,token)
        status.value=DeviceStatus(j.optBoolean("recording"),j.optString("manual","idle"),j.optInt("battery_mv"),
            j.optLong("free_bytes"),hex(j.optString("timeline_samples","0000000000000000")),j.optString("wifi_ip"),
            j.optString("version"),j.optBoolean("storage"),j.optString("store_error"))
    }
    private suspend fun catalogLocked(transport: Transport): Pair<String,List<Segment>> {
        var after="0000000000000000";val segments=mutableListOf<Segment>()
        repeat(2000) {
            val a=transport.command(command("list","after" to after,"limit" to 16)).getJSONArray("segments")
            if(a.length()==0) {
                val m=transport.command(command("marks")).getJSONArray("marks")
                val marks=(0 until m.length()).map { Mark.parse(m.getJSONObject(it)) }
                return library.merge(ble.address.value,segments,marks) to segments
            }
            for(i in 0 until a.length()) {
                val s=Segment.parse(a.getJSONObject(i));check(hex(s.id)>hex(after)) { "设备清单顺序错误" }
                segments+=s;after=s.id
            }
        }
        error("设备清单过大，请重新连接")
    }
    fun refresh()=scope.launch {
        if(!ble.ready.value)return@launch
        try { session.withLock { refreshLocked();catalogLocked(ble) } }
        catch(t: Throwable) { if(t !is CancellationException)notices.emit(message(t)) }
    }
    fun act(name: String, vararg values: Pair<String,Any>)=scope.launch {
        busy.value=true
        try {
            // BleTransport serializes individual requests, allowing controls between download chunks.
            ble.command(command(name,*values));refreshLocked();catalogLocked(ble)
            notices.emit(when(name){"protect"->"已保存在设备，等待同步到手机";"unprotect"->"已解除设备保存标记";"wifi"->"Wi-Fi 配置已发送";else->"设备操作已完成"})
        } catch(t: Throwable) { if(t !is CancellationException)notices.emit(message(t)) }
        finally { busy.value=false }
    }
    fun release(clip: Clip)=scope.launch {
        try {
            check(ble.ready.value&&clip.namespace==library.activeNamespace(ble.address.value)) { "请连接这段录音所属的设备" }
            for(mark in clip.marks)ble.command(command("unprotect","id" to mark.id))
            refreshLocked();catalogLocked(ble)
            notices.emit("已解除设备保存标记，手机副本保留")
        } catch(t: Throwable) { notices.emit(message(t)) }
    }
    fun startSync() {
        if(syncJob?.isActive==true)return
        syncJob=scope.launch {
            try {
                session.withLock {
                    check(ble.ready.value) { "请先连接语环 X1" }
                    sync.value=SyncState("准备同步",true)
                    ble.command(command("time","epoch_ms" to System.currentTimeMillis()));refreshLocked()
                    val (namespace,segments)=catalogLocked(ble)
                    val missing=segments.filter { library.source(namespace,it).length()!=it.bytes }
                    val totalBytes=missing.sumOf { it.bytes };var completed=0;var bytes=0L
                    var transport: Transport=ble
                    val ip=status.value.wifiIp;val token=secrets.token(ble.address.value)
                    if(ip.isNotBlank()&&ip!="0.0.0.0"&&token.isNotBlank()) {
                        val lan=runCatching { LanTransport(context,ip,token) }.getOrNull()
                        if(lan!=null && runCatching { lan.command(command("status")) }.isSuccess)transport=lan
                    }
                    sync.value=SyncState("正在同步",true,0,missing.size,0,totalBytes,transport.label)
                    for(s in segments) {
                        currentCoroutineContext().ensureActive()
                        val wasMissing=s in missing
                        if(wasMissing)check(context.filesDir.usableSpace>s.bytes+32L*1024*1024) { "手机空间不足，请清理后继续" }
                        try {
                            receiveSegment(transport,s,library.partial(namespace,s),library.source(namespace,s)) { current ->
                                if(wasMissing)sync.value=sync.value.copy(bytes=bytes+current,file=timeLabel(s.epochMs))
                            }
                        } catch(t: Throwable) {
                            if(t is CancellationException)throw t
                            if(transport!==ble) {
                                transport=ble;sync.value=sync.value.copy(transport="蓝牙",stage="Wi-Fi 中断，改用蓝牙续传")
                                receiveSegment(ble,s,library.partial(namespace,s),library.source(namespace,s)) { current ->
                                    if(wasMissing)sync.value=sync.value.copy(bytes=bytes+current,file=timeLabel(s.epochMs))
                                }
                            } else throw t
                        }
                        if(wasMissing) { completed++;bytes+=s.bytes;sync.value=sync.value.copy(completed=completed,bytes=bytes) }
                        library.committed()
                    }
                    catalogLocked(ble);refreshLocked()
                    sync.value=sync.value.copy(stage="同步完成",running=false,file="",error="")
                }
            } catch(t: Throwable) {
                val cancelled=t is CancellationException&&t !is TimeoutCancellationException
                sync.value=sync.value.copy(stage=if(cancelled)"同步已暂停" else "同步中断",running=false,error=if(cancelled)"" else message(t))
            }
        }
    }
    fun pauseSync() { syncJob?.cancel() }
    fun setAuto(enabled: Boolean) { auto.value=enabled;secrets.auto=enabled;if(enabled&&ble.ready.value)startSync() }
    fun startLoop() {
        if(loop?.isActive==true)return
        loop=scope.launch {
            while(isActive) {
                delay(30_000)
                if(!ble.ready.value&&!busy.value&&secrets.address.isNotBlank())connect(secrets.address,false)
                if(ble.ready.value && syncJob?.isActive!=true) {
                    runCatching { session.withLock { refreshLocked();catalogLocked(ble) } }
                    if(auto.value)startSync()
                }
            }
        }
    }
    fun stopLoop() { loop?.cancel();loop=null }
    fun disconnect() { pauseSync();stopLoop();ble.stopScan();ble.disconnect();library.disconnect() }
    fun forget() { disconnect();secrets.forget();auto.value=false;status.value=DeviceStatus() }
}
