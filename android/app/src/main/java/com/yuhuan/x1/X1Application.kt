package com.yuhuan.x1

import android.app.Application
import com.yuhuan.x1.data.Library
import com.yuhuan.x1.data.Secrets
import com.yuhuan.x1.sync.RecorderRepository

class X1Application : Application() {
    val repository by lazy { RecorderRepository(this,Library(this),Secrets(this)) }
}
