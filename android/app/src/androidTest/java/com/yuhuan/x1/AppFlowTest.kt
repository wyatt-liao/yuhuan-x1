package com.yuhuan.x1

import android.graphics.Bitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.yuhuan.x1.core.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

class AppFlowTest {
    @get:Rule val ui=createAndroidComposeRule<MainActivity>()
    private fun scrollTo(text: String) {
        ui.onNode(hasScrollToNodeAction()).performScrollToNode(hasText(text,substring=false))
    }
    private fun screenshot(name: String) {
        ui.waitForIdle()
        Thread.sleep(250) // Let the native window compositor present the settled Compose frame.
        val inst=InstrumentationRegistry.getInstrumentation()
        val file=File(inst.targetContext.getExternalFilesDir(null),"qa/$name.png").apply { parentFile?.mkdirs() }
        inst.uiAutomation.takeScreenshot().let { bitmap -> file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) };bitmap.recycle() }
    }
    @Test fun offlineLibraryPlaybackAndSettings() {
        val inst=InstrumentationRegistry.getInstrumentation()
        val repo=(ui.activity.application as X1Application).repository
        val bytes=inst.context.assets.open("firmware-adpcm.wav").use { it.readBytes() }
        val samples=ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).getInt(48).toLong()
        val segment=Segment("0000000000000001",0u,samples,1792296000000,bytes.size.toLong(),true,true)
        val mark=Mark("0000000000000001",0u,samples.toULong(),"0000000000000001",2)
        val title=timeLabel(segment.epochMs)+" · 手动录音"
        val namespace=repo.library.merge("00:11:22:33:44:55",listOf(segment),listOf(mark))
        repo.library.source(namespace,segment).apply { parentFile?.mkdirs();writeBytes(bytes) }
        repo.library.committed();repo.library.disconnect()
        ui.waitForIdle();screenshot("01-today")
        ui.onNodeWithText("录音",useUnmergedTree=true).performClick()
        scrollTo("已保存")
        ui.onAllNodesWithText("已保存",substring=false).onFirst().performClick()
        scrollTo(title)
        ui.onAllNodesWithText(title).onFirst().assertExists()
        screenshot("02-library")
        ui.onAllNodesWithText(title).onFirst().performClick()
        ui.onNodeWithText("录音详情").assertIsDisplayed()
        scrollTo("16,000 Hz")
        ui.onNodeWithText("16,000 Hz").assertExists()
        scrollTo("MONO · 单声道")
        ui.onNodeWithContentDescription("播放录音").performScrollTo().performClick()
        ui.waitUntil(5000) { ui.onAllNodesWithContentDescription("暂停播放").fetchSemanticsNodes().isNotEmpty() || ui.onAllNodesWithContentDescription("播放录音").fetchSemanticsNodes().isNotEmpty() }
        screenshot("03-detail")
        scrollTo("文字")
        ui.onNodeWithText("文字",substring=false).performClick()
        scrollTo("尚未分析")
        ui.onNodeWithText("尚未分析").assertExists()
        ui.onNodeWithContentDescription("返回").performClick()
        ui.onNodeWithText("助手",useUnmergedTree=true).performClick()
        scrollTo("手机本地 AI · 即将接入")
        ui.onNodeWithText("手机本地 AI · 即将接入").assertExists();screenshot("04-assistant")
        ui.onNodeWithText("设备",useUnmergedTree=true).performClick()
        ui.onNodeWithText("连接语环 X1").assertExists();screenshot("05-device")
        ui.onNodeWithText("连接语环 X1").performScrollTo().performClick()
        ui.onNodeWithText("搜索附近的语环").performScrollTo().assertIsDisplayed();screenshot("06-connect")
        assertFalse(repo.ble.ready.value)
    }
}
