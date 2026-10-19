@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.yuhuan.x1.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yuhuan.x1.audio.AudioPlayer
import com.yuhuan.x1.core.*
import com.yuhuan.x1.sync.*
import kotlinx.coroutines.*
import java.io.File

@Composable fun X1App(repo: RecorderRepository,player: AudioPlayer) {
    val context=LocalContext.current
    val scope=rememberCoroutineScope()
    val snack=remember { SnackbarHostState() }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var page by rememberSaveable { mutableStateOf("") }
    var selectedKey by rememberSaveable { mutableStateOf("") }
    val clips by repo.library.clips.collectAsStateWithLifecycle()
    val connected by repo.ble.ready.collectAsStateWithLifecycle()
    val connection by repo.ble.state.collectAsStateWithLifecycle()
    val status by repo.status.collectAsStateWithLifecycle()
    val sync by repo.sync.collectAsStateWithLifecycle()
    val auto by repo.auto.collectAsStateWithLifecycle()
    val found by repo.ble.found.collectAsStateWithLifecycle()
    val scanning by repo.ble.scanning.collectAsStateWithLifecycle()
    val busy by repo.busy.collectAsStateWithLifecycle()
    val playerState by player.state.collectAsStateWithLifecycle()
    val libraryError by repo.library.error.collectAsStateWithLifecycle()
    val clip=clips.find { it.key==selectedKey }
    var sheet by rememberSaveable { mutableStateOf("") }
    var dialog by remember { mutableStateOf("") }
    var exporting by remember { mutableStateOf(false) }
    fun notice(text: String) { scope.launch { snack.showSnackbar(text) } }
    fun stopService() { context.stopService(Intent(context,ConnectionService::class.java)) }
    fun startConnection(address: String) {
        try { ContextCompat.startForegroundService(context,Intent(context,ConnectionService::class.java).putExtra("address",address)) }
        catch(t: Throwable) { notice(t.message?:"无法启动设备连接服务") }
    }
    val permissions=rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        val required=if(Build.VERSION.SDK_INT>=31)listOf(Manifest.permission.BLUETOOTH_SCAN,Manifest.permission.BLUETOOTH_CONNECT) else listOf(Manifest.permission.ACCESS_FINE_LOCATION)
        if(required.all { permission -> ContextCompat.checkSelfPermission(context,permission)==PackageManager.PERMISSION_GRANTED })repo.scan()
        else notice("连接录音卡需要附近设备权限，可在系统设置中允许")
    }
    fun scan() {
        val required=if(Build.VERSION.SDK_INT>=31)listOf(Manifest.permission.BLUETOOTH_SCAN,Manifest.permission.BLUETOOTH_CONNECT) else listOf(Manifest.permission.ACCESS_FINE_LOCATION)
        if(required.all { ContextCompat.checkSelfPermission(context,it)==PackageManager.PERMISSION_GRANTED })repo.scan()
        else permissions.launch((required+if(Build.VERSION.SDK_INT>=33)listOf(Manifest.permission.POST_NOTIFICATIONS) else emptyList()).toTypedArray())
    }
    fun connect() { sheet="connect" }
    fun open(value: Clip) { selectedKey=value.key;page="detail";player.load(value) }
    fun openSync() { page="sync";if(connected&&!sync.running)repo.startSync() }
    fun closePage() { player.pause();if(page=="sync"&&selectedKey.isNotEmpty())page="detail" else {page="";selectedKey=""} }
    LaunchedEffect(repo) { repo.notices.collect { snack.showSnackbar(it) } }
    LaunchedEffect(Unit) {
        val permission=if(Build.VERSION.SDK_INT>=31)Manifest.permission.BLUETOOTH_CONNECT else Manifest.permission.BLUETOOTH
        if(repo.secrets.address.isNotBlank()&&ContextCompat.checkSelfPermission(context,permission)==PackageManager.PERMISSION_GRANTED&&!connected)
            startConnection(repo.secrets.address)
    }
    LaunchedEffect(clip?.key,clip?.complete,clip?.samples) { clip?.let { player.load(it) } }
    BackHandler(page.isNotEmpty()) { closePage() }
    Scaffold(containerColor=Paper,snackbarHost={SnackbarHost(snack)},topBar={
        TopAppBar(title={Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)) {
            if(page.isEmpty())Badge("语环 X1",background=if(connected)Mint else Quiet)
            Text(when(page){"detail"->"录音详情";"sync"->"同步中心";else->listOf("今天","录音","助手","设备")[tab]},style=MaterialTheme.typography.titleMedium)
        }},navigationIcon={if(page.isNotEmpty())IconButton({closePage()}) { Icon(Icons.AutoMirrored.Outlined.ArrowBack,"返回") }},
            actions={
                if(page.isEmpty())IconButton({tab=3}) { Icon(Icons.Outlined.Sensors,"设备状态",tint=Teal) }
                if(page=="detail"&&clip!=null)IconButton({dialog="edit"}) { Icon(Icons.Outlined.Edit,"编辑录音") }
            },colors=TopAppBarDefaults.topAppBarColors(containerColor=Paper))
    },bottomBar={if(page.isEmpty())NavigationBar(containerColor=Paper,tonalElevation=0.dp) {
        val icons=listOf(Icons.Outlined.CalendarToday,Icons.Outlined.GraphicEq,Icons.Outlined.AutoAwesome,Icons.Outlined.Sensors)
        listOf("今天","录音","助手","设备").forEachIndexed { i,label -> NavigationBarItem(tab==i,{tab=i},icon={Icon(icons[i],null)},label={Text(label)},colors=NavigationBarItemDefaults.colors(indicatorColor=Mint,selectedIconColor=Teal,selectedTextColor=Teal)) }
    }}) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if(busy||exporting)LinearProgressIndicator(Modifier.fillMaxWidth())
            if(libraryError.isNotEmpty())Text(libraryError,Modifier.padding(16.dp),color=Red)
            when {
                page=="detail"&&clip!=null -> DetailScreen(clip,playerState,connected,{player.toggle()},{player.seek(it)},{player.speed()},
                    {openSync()},{dialog="edit"},{
                        if(!exporting)scope.launch {
                            exporting=true
                            try {
                                val c=clip
                                val file=withContext(Dispatchers.IO) {
                                    check(c.complete) { "请先同步完整录音" }
                                    val dir=File(context.cacheDir,"exports").apply { mkdirs() }
                                    dir.listFiles()?.filter { System.currentTimeMillis()-it.lastModified()>86_400_000 }?.forEach { it.delete() }
                                    check(dir.usableSpace>c.samples*2+64L*1024*1024) { "手机空间不足，无法导出 PCM 音频" }
                                    val output=File(dir,"yuhuan-${System.currentTimeMillis()}.wav")
                                    val ctx=currentCoroutineContext()
                                    ImaWav.render(c.cuts,{repo.library.source(c.namespace,it)},output) { !ctx.isActive }
                                    output
                                }
                                val uri=FileProvider.getUriForFile(context,context.packageName+".files",file)
                                context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                                    type="audio/wav";putExtra(Intent.EXTRA_STREAM,uri);addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    clipData=android.content.ClipData.newRawUri("语环录音",uri)
                                },"分享录音"))
                            } catch(t: Throwable) { if(t !is CancellationException)notice(t.message?:"导出失败") }
                            finally { exporting=false }
                        }
                    },{dialog="delete"},{dialog="release"},exporting)
                page=="detail" -> EmptyPanel("录音列表已更新","这段内容的保存标记可能已改变，可返回录音库查看。",action="返回录音库",onClick={closePage();tab=1})
                page=="sync" -> SyncScreen(sync,connected,{repo.startSync()},{repo.pauseSync()},{dialog="wifi"})
                tab==0 -> TodayScreen(status,connected,clips,sync,{connect()},{repo.act("protect","hours" to it)},{sheet="protect"},{repo.act("manual","action" to it)},{open(it)},{openSync()})
                tab==1 -> LibraryScreen(clips,connected,{connect()},{open(it)},{openSync()})
                tab==2 -> AssistantScreen(clips) { tab=1 }
                else -> DeviceScreen(status,connected,connection,auto,repo.library.localBytes(),{connect()},{repo.setAuto(it)},{dialog="wifi"},{openSync()},{dialog="forget"})
            }
        }
    }
    val sheetHeight=LocalConfiguration.current.screenHeightDp.dp*0.85f
    if(sheet=="connect")ModalBottomSheet(onDismissRequest={sheet="";repo.ble.stopScan()},sheetState=rememberModalBottomSheetState(skipPartiallyExpanded=true),contentWindowInsets={WindowInsets.safeDrawing},containerColor=Paper) {
        LazyColumn(Modifier.fillMaxWidth().heightIn(max=sheetHeight).padding(horizontal=20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            item { Text("连接语环 X1",style=MaterialTheme.typography.headlineMedium) }
            item { Text("请打开录音卡并靠近手机。允许附近设备权限后搜索，连接时确认系统配对提示。",style=MaterialTheme.typography.bodyMedium,color=Slate) }
            if(Build.VERSION.SDK_INT<31)item { InfoLine("这个 Android 版本使用定位权限搜索蓝牙，App 不收集位置。") }
            item { InfoLine("连接服务使用持续通知，以便后台继续同步。不会申请手机麦克风权限。") }
            item { Button({scan()},Modifier.fillMaxWidth(),enabled=!scanning&&!busy) { Text(if(scanning)"正在搜索…" else "搜索附近的语环") } }
            if(scanning)item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            if(found.isEmpty()&&!scanning)item { Text("尚未发现设备，请确认录音卡已开机、手机蓝牙已打开。",style=MaterialTheme.typography.bodySmall,color=Slate) }
            items(found,key={it.address}) { d -> OutlinedCard(onClick={repo.ble.stopScan();sheet="";startConnection(d.address)},modifier=Modifier.fillMaxWidth()) {
                Row(Modifier.padding(16.dp),verticalAlignment=Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Bluetooth,null,tint=Teal)
                    Column(Modifier.weight(1f).padding(start=12.dp)) { Text(d.name,style=MaterialTheme.typography.titleMedium);Text(d.address,style=MaterialTheme.typography.bodySmall,color=Slate) }
                    Icon(Icons.Outlined.ChevronRight,"连接")
                }
            } }
            item { Spacer(Modifier.height(32.dp)) }
        }
    }
    if(sheet=="protect") {
        var hours by rememberSaveable { mutableIntStateOf(1) }
        ModalBottomSheet(onDismissRequest={sheet=""},sheetState=rememberModalBottomSheetState(skipPartiallyExpanded=true),contentWindowInsets={WindowInsets.safeDrawing},containerColor=Paper) {
            Column(Modifier.padding(20.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(16.dp)) {
                Text("把刚才留下来",style=MaterialTheme.typography.headlineMedium)
                Text("选择过去的时长，保存在设备中防止循环覆盖。",color=Slate)
                Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)) { listOf(1,2,3,6).forEach { n -> FilterChip(hours==n,{hours=n},label={Text("$n 小时")}) } }
                Text("自定义：$hours 小时",style=MaterialTheme.typography.titleMedium)
                Slider(hours.toFloat(),{hours=it.toInt().coerceIn(1,24)},valueRange=1f..24f,steps=22)
                InfoLine("设备录音不足时，只保存已有内容。断电期间不会计入设备的录音时长。")
                Button({repo.act("protect","hours" to hours);sheet=""},Modifier.fillMaxWidth(),enabled=connected) { Text("保存过去 $hours 小时") }
                Spacer(Modifier.height(12.dp))
            }
        }
    }
    if(dialog=="wifi")WifiDialog({dialog=""}) { ssid,password -> repo.act("wifi","ssid" to ssid,"password" to password,"enabled" to true);dialog="" }
    if(dialog=="edit"&&clip!=null)EditDialog(clip,{dialog=""}) { title,note ->
        scope.launch { runCatching { withContext(Dispatchers.IO) { repo.library.edit(clip.key,title,note) } }.onFailure { notice(it.message?:"保存失败") };dialog="" }
    }
    if(dialog=="delete"&&clip!=null)ConfirmDialog("删除手机副本？","本次录音关联的手机音频片段将被删除；使用这些片段的其他录音也需重新同步。设备音频和保存标记保持。","删除副本",{dialog=""}) {
        player.stop();val c=clip;dialog=""
        scope.launch { runCatching { withContext(Dispatchers.IO) { repo.library.deleteLocal(c) } }.onFailure { notice(it.message?:"删除失败") };closePage() }
    }
    if(dialog=="release"&&clip!=null)ConfirmDialog("解除设备保存标记？","手机副本保留。设备内容恢复为循环录音，超出保留窗口后可能被覆盖。","解除标记",{dialog=""}) { repo.release(clip);dialog="" }
    if(dialog=="forget")ConfirmDialog("断开并忘记此设备？","手机已有录音保留。此操作清除 App 的连接记录；Android 系统配对记录可在系统蓝牙设置中管理。","断开并忘记",{dialog=""}) { stopService();repo.forget();dialog="" }
}

@Composable private fun ConfirmDialog(title: String,body: String,button: String,onDismiss: ()->Unit,onConfirm: ()->Unit) {
    AlertDialog(onDismissRequest=onDismiss,title={Text(title)},text={Text(body)},confirmButton={TextButton(onConfirm,colors=ButtonDefaults.textButtonColors(contentColor=Red)) { Text(button) }},dismissButton={TextButton(onDismiss) { Text("取消") }})
}
@Composable private fun EditDialog(clip: Clip,onDismiss: ()->Unit,onSave: (String,String)->Unit) {
    var title by remember(clip.key) { mutableStateOf(clip.title) };var note by remember(clip.key) { mutableStateOf(clip.note) }
    AlertDialog(onDismissRequest=onDismiss,title={Text("编辑录音")},text={Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(title,{title=it.take(120)},label={Text("录音名称")},singleLine=true)
        OutlinedTextField(note,{note=it.take(4000)},label={Text("备注")},minLines=3,maxLines=6)
    }},confirmButton={TextButton({onSave(title,note)},enabled=title.isNotBlank()) { Text("保存") }},dismissButton={TextButton(onDismiss) { Text("取消") }})
}
@Composable private fun WifiDialog(onDismiss: ()->Unit,onSave: (String,String)->Unit) {
    var ssid by remember { mutableStateOf("") };var password by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest=onDismiss,title={Text("连接同一个 Wi-Fi")},text={Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        Text("使用 2.4GHz 网络。凭据通过已配对蓝牙发送到录音卡，App 不保存 Wi-Fi 密码。",style=MaterialTheme.typography.bodyMedium,color=Slate)
        OutlinedTextField(ssid,{ssid=it},label={Text("网络名称 SSID")},singleLine=true)
        OutlinedTextField(password,{password=it},label={Text("网络密码")},visualTransformation=PasswordVisualTransformation(),singleLine=true)
        Text("配置后可在同步中心重试，设备联网需要几秒钟。",style=MaterialTheme.typography.bodySmall,color=Slate)
    }},confirmButton={TextButton({onSave(ssid,password)},enabled=ssid.toByteArray().size in 1..32 && password.toByteArray().size<=63) { Text("发送配置") }},dismissButton={TextButton(onDismiss) { Text("取消") }})
}
