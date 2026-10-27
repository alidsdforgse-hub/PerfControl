package com.example.perfcontrol

import android.app.Application
import com.example.perfcontrol.shizuku.ShizukuManager
import java.io.File

class PerfApp : Application() {
    val shizuku: ShizukuManager by lazy { ShizukuManager(this) }

    override fun onCreate() {
        super.onCreate()
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            runCatching { File(filesDir, CRASH_FILE).writeText(e.stackTraceToString().take(4000)) }
            previous?.uncaughtException(t, e)
        }
    }

    companion object {
        const val CRASH_FILE = "last_crash.txt"
    }
}
