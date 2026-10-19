package com.yuhuan.x1.data

import android.content.Context
import android.util.AtomicFile
import com.yuhuan.x1.core.*
import kotlinx.coroutines.flow.MutableStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

data class Archive(val namespace: String, val address: String, var segments: List<Segment>,
                   var marks: List<Mark>, var remote: Set<String>, var meta: JSONObject)
class Library(context: Context) {
    private val root=File(context.filesDir,"library").apply { mkdirs() }
    private val index=AtomicFile(File(root,"index.json"))
    private val archives=mutableListOf<Archive>()
    private val active=mutableMapOf<String,String>()
    val clips=MutableStateFlow<List<Clip>>(emptyList())
    val error=MutableStateFlow("")
    init {
        try {
            if(index.baseFile.exists()) {
                val j=JSONObject(index.openRead().bufferedReader().use { it.readText() })
                val a=j.getJSONArray("archives")
                for(i in 0 until a.length()) {
                    val x=a.getJSONObject(i); val ns=x.getString("namespace")
                    require(ns.matches(Regex("[a-f0-9-]{36}")))
                    archives+=Archive(ns,x.getString("address"),array(x.getJSONArray("segments"),Segment::parse),
                        array(x.getJSONArray("marks"),Mark::parse),emptySet(),x.optJSONObject("meta")?:JSONObject())
                }
                val current=j.getJSONObject("active")
                current.keys().forEach { address -> active[address]=current.getString(address) }
            }
            publish()
        } catch(t: Throwable) {
            // Preserve the damaged index and all audio; never silently overwrite it.
            error.value="录音目录读取失败，原文件已保留。请先导出应用数据后排查。"
        }
    }
    private fun <T> array(a: JSONArray, parse: (JSONObject)->T) = (0 until a.length()).map { parse(a.getJSONObject(it)) }
    @Synchronized fun source(namespace: String, segment: Segment): File = File(File(root,namespace),segment.id+".wav")
    @Synchronized fun partial(namespace: String, segment: Segment): File = File(File(root,namespace).apply { mkdirs() },segment.id+".part")
    @Synchronized fun activeNamespace(address: String) = active[address]
    @Synchronized fun merge(address: String, incoming: List<Segment>, marks: List<Mark>): String {
        check(error.value.isEmpty()) { error.value }
        var archive=archives.find { it.namespace==active[address] }
        val old=archive?.segments?.associateBy { it.id }.orEmpty()
        val collision=incoming.any { s -> old[s.id]?.let { it.start!=s.start || it.samples!=s.samples || it.bytes!=s.bytes || it.epochMs!=s.epochMs } ?: false }
        if(archive==null || collision) {
            archive=Archive(UUID.randomUUID().toString(),address,emptyList(),emptyList(),emptySet(),JSONObject())
            archives+=archive;active[address]=archive.namespace
        }
        archive.segments=(archive.segments.associateBy { it.id }+incoming.associateBy { it.id }).values.sortedBy { it.start }
        archive.marks=marks;archive.remote=incoming.map { it.id }.toSet()
        save();publish();return archive.namespace
    }
    @Synchronized fun committed() { publish() }
    @Synchronized fun disconnect() { archives.forEach { it.remote=emptySet() };publish() }
    @Synchronized fun edit(key: String,title: String,note: String) {
        val archive=archives.firstOrNull { key.startsWith(it.namespace+":") } ?: return
        archive.meta.put(key,JSONObject().put("title",title.take(120)).put("note",note.take(4000)));save();publish()
    }
    @Synchronized fun deleteLocal(clip: Clip) {
        clip.cuts.map { source(clip.namespace,it.segment) }.distinct().forEach { check(!it.exists()||it.delete()) { "无法删除手机副本" } }
        publish()
    }
    @Synchronized fun localBytes() = root.walkTopDown().filter { it.isFile&&it.extension in listOf("wav","part") }.sumOf { it.length() }
    @Synchronized private fun save() {
        check(error.value.isEmpty()) { error.value }
        val j=JSONObject().put("version",1).put("active",JSONObject(active as Map<*,*>))
        val a=JSONArray()
        for(x in archives)a.put(JSONObject().put("namespace",x.namespace).put("address",x.address)
            .put("segments",JSONArray(x.segments.map { it.json() })).put("marks",JSONArray(x.marks.map { it.json() })).put("meta",x.meta))
        j.put("archives",a)
        val stream=index.startWrite()
        try { stream.write(j.toString().toByteArray());index.finishWrite(stream) }
        catch(t: Throwable) { index.failWrite(stream);throw t }
    }
    private fun publish() {
        clips.value=archives.flatMap { a ->
            val visible=a.segments.filter { source(a.namespace,it).exists()||it.id in a.remote }
            fun clip(key: String, kind: Int, cuts: List<Cut>, marks: List<Mark>): Clip {
                val id=a.namespace+":"+key; val meta=a.meta.optJSONObject(id)
                val epoch=cuts.firstOrNull()?.let { if(it.segment.epochMs>0)it.segment.epochMs+it.offset*1000/RATE else 0 }?:0
                val default=timeLabel(epoch)+(when(kind){1->" · 回溯保存";2->" · 手动录音";else->" · 循环录音"})
                return Clip(id,a.namespace,meta?.optString("title")?.takeIf { it.isNotBlank() }?:default,meta?.optString("note")?:"",kind,cuts,marks,
                    cuts.count { source(a.namespace,it.segment).length()==it.segment.bytes },
                    active[a.address]==a.namespace && cuts.all { it.segment.id in a.remote })
            }
            val saved=a.marks.groupBy { if(it.kind==2)"manual:${it.group}" else "mark:${it.id}" }.mapNotNull { (key,ms) ->
                val cuts=cutsFor(visible,ms);if(cuts.isEmpty())null else clip(key,ms.first().kind,cuts,ms)
            }
            val loops=visible.groupBy { if(it.epochMs>0)"clock:${it.epochMs/3_600_000}" else "samples:${it.start/57_600_000u}" }.map { (key,ss) ->
                clip("loop:$key",0,ss.map { Cut(it,0,it.samples) },emptyList())
            }
            saved+loops
        }.sortedWith(compareByDescending<Clip> { it.epochMs }.thenByDescending { it.cuts.firstOrNull()?.segment?.start })
    }
}
