@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.yuhuan.x1.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.automirrored.outlined.BluetoothSearching
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.yuhuan.x1.core.*
import com.yuhuan.x1.audio.PlayerState
import com.yuhuan.x1.sync.*
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Composable fun TodayScreen(status: DeviceStatus,connected: Boolean,clips: List<Clip>,sync: SyncState,
                            onConnect: ()->Unit,onProtect: (Int)->Unit,onChoose: ()->Unit,onManual: (String)->Unit,
                            onClip: (Clip)->Unit,onSync: ()->Unit) {
    val date=LocalDate.now().format(DateTimeFormatter.ofPattern("M月d日 EEEE",java.util.Locale.CHINESE))
    val important=clips.filter { it.kind!=0 }.take(3)
    Column(Modifier.fillMaxSize()) {
    LazyColumn(Modifier.weight(1f),contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
        item {
            Text(date,color=Slate,style=MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(4.dp))
            Text(if(connected&&status.recording)"语环正在安静记录" else "把刚才，留给自己",style=MaterialTheme.typography.titleLarge)
        }
        item { Panel {
            Row(verticalAlignment=Alignment.CenterVertically) {
                Box(Modifier.size(8.dp).background(if(connected)TealMid else Slate,CircleShape))
                Spacer(Modifier.width(8.dp))
                Text(if(connected) {if(status.recording)"已连接 · 持续记录中" else "已连接 · 录音已停止"} else "语环 X1 未连接",Modifier.weight(1f),style=MaterialTheme.typography.titleMedium,color=Teal)
            }
            if(connected) {
                FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                    Badge(if(status.batteryMv>0)"电池 ${"%.2f".format(status.batteryMv/1000f)} V" else "电池状态待更新",background=Quiet,icon=Icons.Outlined.BatteryStd)
                    Badge(if(status.storage)"设备本地存储" else "存储不可用",background=Quiet,icon=Icons.Outlined.SdCard)
                }
                if(status.error.isNotEmpty())Text(status.error,color=Red,style=MaterialTheme.typography.bodySmall)
                TextButton(onSync,Modifier.align(Alignment.End)) { Icon(Icons.Outlined.Sync,null,Modifier.size(16.dp));Spacer(Modifier.width(4.dp));Text(if(sync.running)"查看同步进度" else "同步到手机") }
            } else {
                Text("设备离线时仍按自身状态录音，手机里已有的内容随时可听。",style=MaterialTheme.typography.bodyMedium,color=Slate)
                OutlinedButton(onConnect) { Icon(Icons.Outlined.Bluetooth,null);Spacer(Modifier.width(4.dp));Text("连接设备") }
            }
        } }
        item { Panel {
            SectionTitle("24 小时音频时间轴")
            Text(if(clips.isEmpty())"连接语环后，查看仍保留的录音片段" else "按已获取的录音片段展示，不代表实时声响",style=MaterialTheme.typography.bodySmall,color=Slate)
            Timeline(clips)
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                Text("24 小时前",style=MaterialTheme.typography.labelSmall,color=Slate)
                Text("现在",style=MaterialTheme.typography.labelSmall,color=Slate)
            }
            HorizontalDivider(color=Line)
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                Badge("循环录音",background=Quiet)
                Badge("已保存",icon=Icons.Outlined.BookmarkBorder)
                Badge("手动录音",background=Quiet,icon=Icons.Outlined.MicNone)
            }
            InfoLine("未保存内容滚动保留 24 小时；保存标记用于防止循环覆盖。")
        } }
        item { SectionTitle("已保存的片段",if(important.isNotEmpty())"最近 ${important.size} 段" else "") }
        if(important.isEmpty())item { EmptyPanel("重要的内容，不用提前准备","按设备第二键，或在这里保存过去的录音。",Icons.Outlined.BookmarkBorder) }
        items(important,key={it.key}) { clip -> RecordingCard(clip,{onClip(clip)},{onClip(clip)}) }
        item { Spacer(Modifier.height(8.dp)) }
    }
    Column(Modifier.fillMaxWidth().background(Paper).padding(horizontal=16.dp,vertical=8.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
        HorizontalDivider(color=Line)
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            Button({onProtect(1)},Modifier.weight(1f).heightIn(min=52.dp),enabled=connected&&status.storage,shape=RoundedCornerShape(14.dp)) {
                Icon(Icons.Outlined.History,null,Modifier.size(20.dp));Spacer(Modifier.width(8.dp));Text("保存过去 1 小时")
            }
            FilledTonalButton(onChoose,Modifier.heightIn(min=52.dp),enabled=connected&&status.storage,shape=RoundedCornerShape(14.dp)) { Text("选择时长") }
        }
        if(status.manual=="idle") {
            OutlinedButton({onManual("start")},Modifier.fillMaxWidth(),enabled=connected&&status.recording) {
                Icon(Icons.Outlined.MicNone,null);Spacer(Modifier.width(8.dp));Text("开始手动录音")
            }
        } else {
            Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                Text(if(status.manual=="paused")"已暂停" else "手动录音中",Modifier.weight(1f),color=Red,style=MaterialTheme.typography.bodySmall)
                OutlinedButton({onManual(if(status.manual=="paused")"resume" else "pause")},enabled=connected) {
                    Icon(if(status.manual=="paused")Icons.Outlined.PlayArrow else Icons.Outlined.Pause,null,Modifier.size(18.dp))
                    Text(if(status.manual=="paused")"继续" else "暂停")
                }
                Button({onManual("stop")},enabled=connected,colors=ButtonDefaults.buttonColors(containerColor=Red)) { Icon(Icons.Outlined.Stop,null,Modifier.size(18.dp));Text("结束") }
            }
        }
        Text("手动暂停时，后台循环记录继续。",color=Slate,style=MaterialTheme.typography.labelSmall)
    }
    }
}

@Composable private fun Timeline(clips: List<Clip>) {
    val now=System.currentTimeMillis();val start=now-86_400_000L
    Canvas(Modifier.fillMaxWidth().height(112.dp).background(Quiet,RoundedCornerShape(10.dp))) {
        for(i in 0..6) {
            val x=size.width*i/6
            drawLine(Line,Offset(x,0f),Offset(x,size.height),1.dp.toPx())
        }
        val rows=clips.filter { it.epochMs>0 }
        for(clip in rows) {
            val left=((clip.epochMs-start)/86_400_000f).coerceIn(0f,1f)*size.width
            val right=((clip.epochMs+clip.durationMs-start)/86_400_000f).coerceIn(0f,1f)*size.width
            if(right>left)drawRoundRect(if(clip.kind==0)Color(0xFFB4CDCA) else if(clip.kind==1)TealMid else Teal,
                topLeft=Offset(left,if(clip.kind==0)size.height*0.6f else size.height*0.15f),
                size=Size(maxOf(3.dp.toPx(),right-left),if(clip.kind==0)size.height*0.25f else size.height*0.7f),cornerRadius=androidx.compose.ui.geometry.CornerRadius(3.dp.toPx()))
        }
    }
}

@Composable fun LibraryScreen(clips: List<Clip>,connected: Boolean,onConnect: ()->Unit,onClip: (Clip)->Unit,onDownload: ()->Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableIntStateOf(0) }
    val filtered=clips.filter { (query.isBlank()||it.title.contains(query,true)||it.note.contains(query,true)) && when(filter){1->it.kind!=0;2->it.kind==2;3->!it.complete;else->true} }
    LazyColumn(contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        if(!connected)item { Surface(color=Line,shape=RoundedCornerShape(12.dp)) {
            Row(Modifier.padding(12.dp),verticalAlignment=Alignment.CenterVertically) {
                Icon(Icons.Outlined.BluetoothDisabled,null,tint=Slate)
                Column(Modifier.weight(1f).padding(start=8.dp)) { Text("语环 X1 未连接",style=MaterialTheme.typography.bodyMedium);Text("手机已有录音可以离线播放",style=MaterialTheme.typography.bodySmall,color=Slate) }
                TextButton(onConnect) { Text("重连") }
            }
        } }
        item { OutlinedTextField(query,{query=it},Modifier.fillMaxWidth(),placeholder={Text("搜索录音名称、日期或备注")},leadingIcon={Icon(Icons.Outlined.Search,null)},singleLine=true,shape=RoundedCornerShape(14.dp)) }
        item { Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            listOf("全部","已保存","手动录音","待同步").forEachIndexed { i,s -> FilterChip(filter==i,{filter=i},label={Text(s)}) }
        } }
        if(filtered.isEmpty())item { EmptyPanel(if(clips.isEmpty())"你的录音，会在这里汇合" else "没有符合条件的录音",if(clips.isEmpty())"连接录音卡，把保留的音频同步到手机。音频仅保存在本机。" else "试试其他筛选条件或搜索词。",action=if(connected)"同步录音" else "连接设备",onClick=if(connected)onDownload else onConnect) }
        var previous=""
        for(clip in filtered) {
            val date=timeLabel(clip.epochMs,"M月d日")
            if(date!=previous) { item(key="date:${clip.key}") { SectionTitle(date) };previous=date }
            item(key=clip.key) { RecordingCard(clip,{onClip(clip)},{onClip(clip)}) }
        }
        item { InfoLine("同步成功不会删除设备音频。手机副本与设备保存标记分别管理。") }
    }
}

@Composable fun DetailScreen(clip: Clip,player: PlayerState,connected: Boolean,onPlay: ()->Unit,onSeek: (Long)->Unit,onSpeed: ()->Unit,
                            onSync: ()->Unit,onEdit: ()->Unit,onShare: ()->Unit,onDelete: ()->Unit,onRelease: ()->Unit,exporting: Boolean) {
    var tab by rememberSaveable(clip.key) { mutableIntStateOf(0) }
    var slider by remember(clip.key) { mutableStateOf<Float?>(null) }
    LazyColumn(contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
        item { Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            Badge(if(clip.kind!=0)"设备保存标记" else "循环录音",icon=if(clip.kind!=0)Icons.Outlined.BookmarkBorder else Icons.Outlined.History)
            Badge(if(clip.complete)"已在手机" else "待同步",background=Quiet)
        } }
        item { Row(verticalAlignment=Alignment.CenterVertically) {
            Text(clip.title,Modifier.weight(1f),style=MaterialTheme.typography.headlineMedium)
            IconButton(onEdit) { Icon(Icons.Outlined.Edit,"重命名或修改备注") }
        };Text("${timeLabel(clip.epochMs)} · ${formatDuration(clip.durationMs)}",color=Slate,style=MaterialTheme.typography.bodyMedium) }
        item { Panel {
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                Text("MONO · 单声道",style=MaterialTheme.typography.labelSmall,color=Teal)
                Text("${formatDuration(player.position)} / ${formatDuration(clip.durationMs)}",style=MaterialTheme.typography.labelSmall,color=Slate)
            }
            Waveform(player.peaks,if(clip.durationMs>0)player.position.toFloat()/clip.durationMs else 0f)
            if(!clip.complete) {
                Button(onSync,Modifier.fillMaxWidth(),enabled=connected) { Icon(Icons.Outlined.Download,null);Spacer(Modifier.width(8.dp));Text(if(connected)"同步后播放" else "连接设备后同步") }
            } else {
                Slider(slider?:player.position.toFloat(),{slider=it},valueRange=0f..maxOf(1f,clip.durationMs.toFloat()),onValueChangeFinished={slider?.let { onSeek(it.toLong()) };slider=null})
                Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceEvenly) {
                    TextButton(onSpeed) { Text("${player.speed}×") }
                    IconButton({onSeek(player.position-10_000)}) { Icon(Icons.Outlined.Replay10,"后退 10 秒") }
                    FilledIconButton(onPlay,Modifier.size(60.dp)) { Icon(if(player.playing)Icons.Outlined.Pause else Icons.Outlined.PlayArrow,if(player.playing)"暂停播放" else "播放录音",Modifier.size(32.dp)) }
                    IconButton({onSeek(player.position+30_000)}) { Icon(Icons.Outlined.Forward30,"快进 30 秒") }
                    IconButton(onShare,enabled=!exporting) { Icon(Icons.Outlined.Share,"导出并分享 WAV") }
                }
            }
            if(player.error.isNotEmpty())Text(player.error,color=Red,style=MaterialTheme.typography.bodySmall)
        } }
        item { Row(Modifier.fillMaxWidth().background(Line,RoundedCornerShape(14.dp)).padding(4.dp),horizontalArrangement=Arrangement.spacedBy(4.dp)) {
            listOf("音频","文字","摘要").forEachIndexed { i,s ->
                TextButton({tab=i},Modifier.weight(1f),colors=ButtonDefaults.textButtonColors(containerColor=if(tab==i)Color.White else Color.Transparent,contentColor=if(tab==i)Ink else Slate),shape=RoundedCornerShape(10.dp)) { Text(s) }
            }
        } }
        if(tab==0) {
            item { Panel {
                SectionTitle("音频档案",if(clip.complete)"手机副本" else "设备片段")
                Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) { Metric("编码","IMA ADPCM",Modifier.weight(1f));Metric("采样率","16,000 Hz",Modifier.weight(1f)) }
                Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) { Metric("声道","单声道",Modifier.weight(1f));Metric("组成", "${clip.cuts.size} 个片段",Modifier.weight(1f)) }
                InfoLine("播放与导出按保存区间拼接，手动暂停的时间不会混入本次录音。")
            } }
            item { Panel {
                SectionTitle("备注")
                Text(clip.note.ifBlank { "还没有备注，记下这段内容的线索。" },color=Slate,style=MaterialTheme.typography.bodyMedium)
                TextButton(onEdit) { Icon(Icons.Outlined.EditNote,null);Text("编辑备注") }
            } }
        } else item { EmptyPanel("尚未分析",if(tab==1)"本地转写引擎尚未接入。音频已同步不代表文字已生成。" else "摘要和待办将在接入手机本地 AI 后生成。",Icons.Outlined.AutoAwesome) }
        item { Panel {
            InfoLine("音频保存在这台手机。本版没有账号、服务器或云上传。",Icons.Outlined.Shield)
            SectionTitle("管理录音")
            OutlinedButton(onDelete,Modifier.fillMaxWidth(),enabled=clip.localCount>0) { Icon(Icons.Outlined.DeleteOutline,null);Spacer(Modifier.width(8.dp));Text("删除手机副本") }
            if(clip.marks.isNotEmpty())OutlinedButton(onRelease,Modifier.fillMaxWidth(),enabled=connected,colors=ButtonDefaults.outlinedButtonColors(contentColor=Red)) { Icon(Icons.Outlined.BookmarkRemove,null);Spacer(Modifier.width(8.dp));Text("解除设备保存标记") }
            Text("同步不删除设备音频。解除标记后，超出循环窗口的内容可能被覆盖。",style=MaterialTheme.typography.bodySmall,color=Slate)
        } }
    }
}

@Composable fun AssistantScreen(clips: List<Clip>,onLibrary: ()->Unit) {
    LazyColumn(contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
        item { Spacer(Modifier.height(16.dp));Text("从声音里，\n找回重要的事",style=MaterialTheme.typography.headlineLarge);Spacer(Modifier.height(8.dp));Text("你的个人记忆助手",color=Slate) }
        item { EmptyPanel("手机本地 AI · 即将接入","首版先把录音稳妥同步到手机。转写、摘要、信息提取和提醒将在本地 AI 接入后开放。",Icons.Outlined.AutoAwesome) }
        item { SectionTitle("待分析的录音", "${clips.count { it.complete&&it.kind!=0 }} 段已保存") }
        val pending=clips.filter { it.complete&&it.kind!=0 }.take(5)
        if(pending.isEmpty())item { InfoLine("保存并同步录音后，会在这里显示。") }
        items(pending,key={it.key}) { c -> Panel { Text(c.title,style=MaterialTheme.typography.titleMedium);Badge("待分析",background=Quiet) } }
        item { OutlinedButton(onLibrary,Modifier.fillMaxWidth()) { Text("查看录音库") } }
        item { InfoLine("本版尚不执行转写、创建提醒或发送提醒通知。") }
    }
}

@Composable fun DeviceScreen(status: DeviceStatus,connected: Boolean,state: String,auto: Boolean,localBytes: Long,
                            onConnect: ()->Unit,onAuto: (Boolean)->Unit,onWifi: ()->Unit,onSync: ()->Unit,onDisconnect: ()->Unit) {
    LazyColumn(contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
        item { Panel {
            SectionTitle("语环 X1",state)
            DeviceDrawing()
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                Metric("电池电压",if(connected&&status.batteryMv>0)"${"%.2f".format(status.batteryMv/1000f)} V" else "待连接",Modifier.weight(1f))
                Metric("设备剩余空间",if(connected&&status.storage)sizeLabel(status.freeBytes) else "待连接",Modifier.weight(1f))
            }
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                Metric("音频格式","16kHz 单声道",Modifier.weight(1f));Metric("手机音频",sizeLabel(localBytes),Modifier.weight(1f))
            }
            if(!connected)Button(onConnect,Modifier.fillMaxWidth()) { Icon(Icons.AutoMirrored.Outlined.BluetoothSearching,null);Spacer(Modifier.width(8.dp));Text("连接语环 X1") }
            else OutlinedButton(onSync,Modifier.fillMaxWidth()) { Icon(Icons.Outlined.Sync,null);Spacer(Modifier.width(8.dp));Text("同步中心") }
        } }
        item { Panel {
            SectionTitle("实体双按键", "脱离手机也可操作")
            Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                Badge("键 1",background=Quiet)
                Column { Text("手动录音",style=MaterialTheme.typography.titleMedium);Text("单击开始，再次单击结束。暂停时也可直接结束。",style=MaterialTheme.typography.bodyMedium,color=Slate) }
            }
            HorizontalDivider(color=Line)
            Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                Badge("键 2",background=Quiet)
                Column(verticalArrangement=Arrangement.spacedBy(6.dp)) {
                    Text("暂停与回溯",style=MaterialTheme.typography.titleMedium)
                    Text("手动录音时：单击暂停 / 继续。\n没有手动录音时：连按 N 次保存前 N 小时，最多 24 小时。",style=MaterialTheme.typography.bodyMedium,color=Slate)
                    Text("连按间隔小于 500ms，停止点击后执行。",style=MaterialTheme.typography.bodySmall,color=Slate)
                }
            }
        } }
        item { Panel {
            Row(verticalAlignment=Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) { Text("连接后自动同步",style=MaterialTheme.typography.titleMedium);Text("连接服务运行期间检查待同步录音",style=MaterialTheme.typography.bodySmall,color=Slate) }
                Switch(auto,onAuto)
            }
            HorizontalDivider(color=Line)
            TextButton(onWifi,Modifier.fillMaxWidth(),enabled=connected) { Icon(Icons.Outlined.Wifi,null);Spacer(Modifier.width(8.dp));Text("配置 2.4GHz Wi-Fi");Spacer(Modifier.weight(1f));Icon(Icons.Outlined.ChevronRight,null) }
            if(connected&&status.wifiIp.isNotEmpty()&&status.wifiIp!="0.0.0.0")Text("Wi-Fi 已连接：${status.wifiIp}",style=MaterialTheme.typography.bodySmall,color=Slate)
            InfoLine("Wi-Fi 用于局域网快速传输。连接中断后，已下载部分可继续。")
        } }
        item { Panel {
            SectionTitle("本地保存，随时回听")
            Text("无需注册账号。音频保存在手机应用内；卸载 App 会清除这些副本，请先分享导出重要录音。",style=MaterialTheme.typography.bodyMedium,color=Slate)
            if(status.version.isNotEmpty())Text("设备固件 ${status.version} · App 0.1.0",style=MaterialTheme.typography.bodySmall,color=Slate)
            InfoLine("低功耗录音由设备固件控制，本版 App 不提供麦克风模式或降噪强度调节。")
        } }
        if(connected)item { OutlinedButton(onDisconnect,Modifier.fillMaxWidth(),colors=ButtonDefaults.outlinedButtonColors(contentColor=Red)) { Icon(Icons.Outlined.LinkOff,null);Spacer(Modifier.width(8.dp));Text("断开并忘记此设备") } }
    }
}

@Composable fun SyncScreen(sync: SyncState,connected: Boolean,onSync: ()->Unit,onPause: ()->Unit,onWifi: ()->Unit) {
    LazyColumn(contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
        item { Text("让记忆，回到手机",style=MaterialTheme.typography.headlineMedium);Text("完整落盘后才向设备确认接收",color=Slate,style=MaterialTheme.typography.bodyMedium) }
        item { Panel {
            Icon(Icons.Outlined.Sync,null,Modifier.size(32.dp),tint=TealMid)
            SectionTitle(sync.stage,sync.transport)
            if(sync.running&&sync.totalBytes>0)LinearProgressIndicator(progress={ (sync.bytes.toFloat()/sync.totalBytes).coerceIn(0f,1f) },modifier=Modifier.fillMaxWidth(),color=TealMid,trackColor=Mint)
            else if(sync.running)LinearProgressIndicator(Modifier.fillMaxWidth())
            Text("${sync.completed} / ${sync.total} 个待同步片段",style=MaterialTheme.typography.titleMedium)
            Text("${sizeLabel(sync.bytes)} / ${sizeLabel(sync.totalBytes)}",style=MaterialTheme.typography.bodySmall,color=Slate)
            if(sync.file.isNotEmpty())Text(sync.file,style=MaterialTheme.typography.bodyMedium,color=Slate)
            if(sync.error.isNotEmpty())Text(sync.error,color=Red)
            if(sync.running)OutlinedButton(onPause,Modifier.fillMaxWidth()) { Text("暂停同步") }
            else Button(onSync,Modifier.fillMaxWidth(),enabled=connected) { Text(if(sync.stage=="同步完成")"检查新录音" else "开始 / 继续同步") }
        } }
        item { Panel {
            SectionTitle("大量录音，优先用 Wi-Fi")
            Text("手机与录音卡连接同一个 2.4GHz Wi-Fi，App 会优先使用局域网传输。蓝牙可作为备用。",style=MaterialTheme.typography.bodyMedium,color=Slate)
            OutlinedButton(onWifi,Modifier.fillMaxWidth(),enabled=connected) { Text("配置设备 Wi-Fi") }
        } }
        item { InfoLine("活动录音按一分钟左右分段，尚未封口的片段会在稍后出现。") }
        item { InfoLine("同步只表示音频已在手机，并不表示 AI 分析完成。同步不会删除设备内容。",Icons.Outlined.Shield) }
        item { InfoLine("连接服务有持续通知；系统省电限制或强制停止 App 可能中断同步。") }
    }
}
