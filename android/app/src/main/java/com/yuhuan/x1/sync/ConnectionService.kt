package com.yuhuan.x1.sync

import android.app.*
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.yuhuan.x1.MainActivity
import com.yuhuan.x1.R
import com.yuhuan.x1.X1Application
import kotlinx.coroutines.*

/** Started only by a visible user action; no boot receiver or forced-stop bypass. */
class ConnectionService : Service() {
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main)
    private val repo get()=(application as X1Application).repository
    override fun onCreate() {
        super.onCreate()
        getSystemService(NotificationManager::class.java).createNotificationChannel(NotificationChannel("connection","设备连接与同步",NotificationManager.IMPORTANCE_LOW))
    }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if(intent?.action=="stop") { repo.disconnect();stopSelf();return START_NOT_STICKY }
        val open=PendingIntent.getActivity(this,0,Intent(this,MainActivity::class.java),PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val stop=PendingIntent.getService(this,1,Intent(this,ConnectionService::class.java).setAction("stop"),PendingIntent.FLAG_IMMUTABLE)
        startForeground(1,NotificationCompat.Builder(this,"connection").setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle("语环 X1 · 设备连接服务").setContentText("连接设备后自动同步到手机，音频不上传服务器")
            .setContentIntent(open).setOngoing(true).addAction(0,"断开并停止",stop).build())
        repo.startLoop()
        val address=intent?.getStringExtra("address")
        if(!address.isNullOrBlank())scope.launch { repo.connect(address) }
        return START_NOT_STICKY
    }
    override fun onBind(intent: Intent?): IBinder?=null
    override fun onDestroy() { scope.cancel();repo.disconnect();super.onDestroy() }
}
