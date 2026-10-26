package com.example.perfcontrol.shizuku

import com.example.perfcontrol.IShellService
import com.example.perfcontrol.core.CommandResult
import com.example.perfcontrol.core.CommandRunner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withTimeoutOrNull

class ShizukuCommandRunner(
    private val service: () -> IShellService?,
    private val timeoutMs: Long = 15_000,
) : CommandRunner {

    override suspend fun run(command: String): CommandResult {
        val s = service() ?: return CommandResult.Failure("Shizuku غير جاهز")
        return try {
            val raw = withTimeoutOrNull(timeoutMs) {
                CoroutineScope(Dispatchers.IO).async { s.exec(command) }.await()
            } ?: return CommandResult.Failure("انتهت مهلة الأمر")
            val marker = raw.lastIndexOf("__EXIT__:")
            if (marker < 0) return CommandResult.Success(raw)
            val output = raw.substring(0, marker).trim()
            val code = raw.substring(marker + "__EXIT__:".length).trim().toIntOrNull() ?: -1
            if (code == 0) CommandResult.Success(output)
            else CommandResult.Failure(output.take(300).ifBlank { "رمز الخروج $code" }, code)
        } catch (e: Exception) {
            CommandResult.Failure(e.message ?: "خطأ غير معروف")
        }
    }
}
