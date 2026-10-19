package com.yuhuan.x1.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.yuhuan.x1.core.*

@Composable fun Panel(modifier: Modifier=Modifier,content: @Composable ColumnScope.()->Unit) {
    Column(modifier.fillMaxWidth().background(Color.White,RoundedCornerShape(16.dp)).border(1.dp,Line,RoundedCornerShape(16.dp)).padding(16.dp),
        verticalArrangement=Arrangement.spacedBy(12.dp),content=content)
}
@Composable fun Badge(text: String,color: Color=Teal,background: Color=Mint,icon: ImageVector?=null) {
    Row(Modifier.background(background,RoundedCornerShape(50)).padding(horizontal=8.dp,vertical=4.dp),
        verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(4.dp)) {
        if(icon!=null)Icon(icon,null,Modifier.size(13.dp),tint=color)
        Text(text,color=color,style=MaterialTheme.typography.labelSmall)
    }
}
@Composable fun SectionTitle(text: String,tail: String="") {
    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
        Text(text,Modifier.weight(1f),style=MaterialTheme.typography.titleMedium)
        if(tail.isNotEmpty())Text(tail,style=MaterialTheme.typography.bodySmall,color=Slate)
    }
}
@Composable fun EmptyPanel(title: String,body: String,icon: ImageVector=Icons.Outlined.GraphicEq,action: String?=null,onClick: ()->Unit={}) {
    Panel {
        Spacer(Modifier.height(12.dp))
        Icon(icon,null,Modifier.align(Alignment.CenterHorizontally).size(36.dp),tint=TealMid)
        Text(title,Modifier.align(Alignment.CenterHorizontally),style=MaterialTheme.typography.titleMedium)
        Text(body,style=MaterialTheme.typography.bodyMedium,color=Slate)
        if(action!=null)Button(onClick,Modifier.fillMaxWidth()) { Text(action) }
        Spacer(Modifier.height(8.dp))
    }
}
@Composable fun InfoLine(text: String,icon: ImageVector=Icons.Outlined.Info) {
    Row(horizontalArrangement=Arrangement.spacedBy(8.dp),verticalAlignment=Alignment.Top) {
        Icon(icon,null,Modifier.size(16.dp).padding(top=1.dp),tint=Slate)
        Text(text,style=MaterialTheme.typography.bodySmall,color=Slate)
    }
}
@Composable fun Waveform(peaks: List<Float>,progress: Float=0f,modifier: Modifier=Modifier) {
    Canvas(modifier.fillMaxWidth().height(96.dp).background(Quiet,RoundedCornerShape(12.dp))
        .semantics { contentDescription=if(peaks.isEmpty())"暂无已下载音频波形" else "已下载录音的波形" }) {
        val pad=12.dp.toPx(); val width=size.width-pad*2;val height=size.height-pad*2
        drawLine(Line,Offset(pad,size.height/2),Offset(size.width-pad,size.height/2),1.dp.toPx())
        if(peaks.isNotEmpty())for((i,v) in peaks.withIndex()) {
            val x=pad+width*(i+0.5f)/peaks.size
            val h=maxOf(2.dp.toPx(),v*height*0.9f)
            drawLine(if(i.toFloat()/peaks.size<=progress)TealMid else Color(0xFFB9CECC),Offset(x,size.height/2-h/2),Offset(x,size.height/2+h/2),2.dp.toPx(),StrokeCap.Round)
        }
    }
}
@Composable fun Metric(label: String,value: String,modifier: Modifier=Modifier) {
    Column(modifier.background(Quiet,RoundedCornerShape(10.dp)).padding(12.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
        Text(label,style=MaterialTheme.typography.bodySmall,color=Slate)
        Text(value,style=MaterialTheme.typography.titleMedium)
    }
}
@Composable fun RecordingCard(clip: Clip,onOpen: ()->Unit,onPlay: ()->Unit) {
    OutlinedCard(onClick=onOpen,colors=CardDefaults.outlinedCardColors(containerColor=Color.White),border=androidx.compose.foundation.BorderStroke(1.dp,Line),shape=RoundedCornerShape(16.dp)) {
        Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment=Alignment.CenterVertically) {
                Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                    Text(clip.title,style=MaterialTheme.typography.titleMedium,maxLines=2)
                    Text("${formatDuration(clip.durationMs)} · ${when(clip.kind){1->"回溯保存";2->"手动录音";else->"循环录音"}}",style=MaterialTheme.typography.bodySmall,color=Slate)
                }
                IconButton(onClick=onPlay,modifier=Modifier.size(48.dp)) {
                    Icon(if(clip.complete)Icons.Outlined.PlayCircle else Icons.Outlined.Download,if(clip.complete)"播放 ${clip.title}" else "同步 ${clip.title}",Modifier.size(36.dp),tint=TealMid)
                }
            }
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                Badge(if(clip.complete)"已在手机" else if(clip.localCount>0)"部分已同步" else "仅在设备",icon=if(clip.complete)Icons.Outlined.PhoneAndroid else Icons.Outlined.Bluetooth)
                if(clip.kind!=0)Badge("已保存",background=Quiet,icon=Icons.Outlined.BookmarkBorder)
            }
            if(clip.note.isNotBlank())Text(clip.note,maxLines=2,color=Slate,style=MaterialTheme.typography.bodySmall)
        }
    }
}
@Composable fun DeviceDrawing() {
    Box(Modifier.fillMaxWidth().height(150.dp).background(Quiet,RoundedCornerShape(12.dp)),contentAlignment=Alignment.Center) {
        Column(Modifier.fillMaxWidth(0.85f).height(116.dp).background(Color(0xFFE1E8E6),RoundedCornerShape(14.dp)).border(1.dp,Color(0xFFD3DDDA),RoundedCornerShape(14.dp)).padding(18.dp),verticalArrangement=Arrangement.SpaceBetween) {
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                Text("Y U H U A N   X1",style=MaterialTheme.typography.labelSmall,color=Slate)
                Icon(Icons.Outlined.GraphicEq,null,Modifier.size(18.dp),tint=TealMid)
            }
            Canvas(Modifier.fillMaxWidth().height(24.dp)) {
                drawLine(Color(0xFFB8CBC6),Offset(size.width*0.25f,size.height/2),Offset(size.width*0.75f,size.height/2),3.dp.toPx(),StrokeCap.Round)
            }
            Text("把刚才留下来",style=MaterialTheme.typography.labelSmall,color=Slate)
        }
    }
}
