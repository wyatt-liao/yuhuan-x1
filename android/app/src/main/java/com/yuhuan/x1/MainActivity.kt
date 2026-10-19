package com.yuhuan.x1

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.yuhuan.x1.audio.AudioPlayer
import com.yuhuan.x1.ui.X1App
import com.yuhuan.x1.ui.X1Theme

class MainActivity : ComponentActivity() {
    private lateinit var player: AudioPlayer
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val repo=(application as X1Application).repository
        player=AudioPlayer(this,repo.library)
        setContent { X1Theme { X1App(repo,player) } }
    }
    override fun onStop() { super.onStop();player.pause() }
    override fun onDestroy() { player.close();super.onDestroy() }
}
