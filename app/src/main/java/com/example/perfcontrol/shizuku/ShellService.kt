package com.example.perfcontrol.shizuku

import com.example.perfcontrol.IShellService
import kotlin.system.exitProcess

/** تعمل في عملية منفصلة بصلاحيات الشل عبر Shizuku. */
class ShellService : IShellService.Stub() {
    override fun destroy() {
        exitProcess(0)
    }

    override fun exec(command: String): String {
        return try {
            val p = ProcessBuilder("sh", "-c", command).redirectErrorStream(true).start()
            val out = p.inputStream.bufferedReader().readText()
            val code = p.waitFor()
            "$out\n__EXIT__:$code"
        } catch (e: Exception) {
            "${e.message}\n__EXIT__:-1"
        }
    }
}
