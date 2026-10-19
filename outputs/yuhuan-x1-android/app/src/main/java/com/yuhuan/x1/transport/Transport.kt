package com.yuhuan.x1.transport

import com.yuhuan.x1.core.Segment
import org.json.JSONObject
import java.io.File

interface Transport {
    val label: String
    suspend fun command(body: JSONObject): JSONObject
    suspend fun download(segment: Segment, partial: File, progress: (Long)->Unit)
}
fun command(name: String, vararg values: Pair<String,Any>): JSONObject = JSONObject().put("cmd",name).also { j -> values.forEach { j.put(it.first,it.second) } }
fun checked(json: JSONObject): JSONObject {
    check(json.optBoolean("ok")) { json.optString("error","设备未完成操作") }
    return json
}
