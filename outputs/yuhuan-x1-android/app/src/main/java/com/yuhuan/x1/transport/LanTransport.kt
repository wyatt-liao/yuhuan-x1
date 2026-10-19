package com.yuhuan.x1.transport

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.yuhuan.x1.core.Segment
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import org.json.JSONObject
import java.io.File
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.URL

class LanTransport(context: Context, ip: String, private val token: String) : Transport {
    override val label = "Wi-Fi"
    private val manager=context.getSystemService(ConnectivityManager::class.java)
    private val base: String
    init {
        require(isPrivateIpv4(ip)) { "设备地址必须是局域网 IPv4 地址" }
        require(token.matches(Regex("[0-9a-fA-F]{64}"))) { "请通过已配对蓝牙获取同步凭据" }
        base="http://$ip"
    }
    companion object {
        fun isPrivateIpv4(ip: String): Boolean {
            val p=ip.split('.');if(p.size!=4)return false
            val n=p.map { it.toIntOrNull() ?: return false }
            if(n.any { it !in 0..255 })return false
            return n[0]==10 || (n[0]==192&&n[1]==168) || (n[0]==172&&n[1] in 16..31)
        }
    }
    private fun open(path: String): HttpURLConnection {
        val network=manager.allNetworks.firstOrNull { manager.getNetworkCapabilities(it)?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)==true }
            ?: error("请将手机连接到与语环相同的 Wi-Fi")
        return (network.openConnection(URL(base+path)) as HttpURLConnection).apply {
            connectTimeout=8_000;readTimeout=15_000;instanceFollowRedirects=false
            setRequestProperty("Authorization","Bearer $token")
        }
    }
    override suspend fun command(body: JSONObject): JSONObject {
        val c=open("/v1/control")
        try {
            c.requestMethod="POST";c.doOutput=true;c.setRequestProperty("Content-Type","application/json")
            c.outputStream.use { it.write(body.toString().toByteArray()) }
            check(c.responseCode==200) { "Wi-Fi 操作失败（${c.responseCode}）" }
            return checked(JSONObject(c.inputStream.bufferedReader().use { it.readText() }))
        } finally { c.disconnect() }
    }
    override suspend fun download(segment: Segment, partial: File, progress: (Long)->Unit) {
        var offset=partial.length()
        if(offset>segment.bytes) { partial.delete();offset=0 }
        if(offset==segment.bytes)return
        val c=open("/v1/audio?id=${segment.id}")
        if(offset>0)c.setRequestProperty("Range","bytes=$offset-")
        try {
            check(c.responseCode==if(offset>0)206 else 200) { "Wi-Fi 下载失败（${c.responseCode}）" }
            if(offset>0)check(c.getHeaderField("Content-Range")=="bytes $offset-${segment.bytes-1}/${segment.bytes}") { "设备未正确响应续传" }
            val ctx=currentCoroutineContext()
            RandomAccessFile(partial,"rw").use { out ->
                out.setLength(offset);out.seek(offset)
                c.inputStream.use { input ->
                    val buffer=ByteArray(32768)
                    while(true) {
                        ctx.ensureActive();val count=input.read(buffer);if(count<0)break
                        check(offset+count<=segment.bytes) { "设备发送超出文件长度" }
                        out.write(buffer,0,count);offset+=count;progress(offset)
                    }
                }
                out.fd.sync()
            }
            check(offset==segment.bytes) { "下载中断，稍后可继续" }
        } finally { c.disconnect() }
    }
}
