package com.example.perfcontrol.core

data class CheckItem(val name: String, val command: String, val ok: Boolean, val detail: String)

/** يشغّل أوامر آمنة فقط (قراءة)، ولا يوقف أي تطبيق ولا يغيّر أي إعداد. */
class CompatibilityChecker(
    private val runner: CommandRunner,
    private val selfPackage: String,
) {
    suspend fun run(): List<CheckItem> {
        val checks = listOf(
            "قراءة العمليات (top)" to "top -b -n 1 -m 3 -o PID,RES,%CPU,ARGS",
            "قراءة المعالج (/proc/stat)" to "cat /proc/stat",
            "قراءة الذاكرة (/proc/meminfo)" to "cat /proc/meminfo",
            "تقليص الكاش (trim-caches)" to "cmd package trim-caches 1",
            "قراءة ضابط الخلفية (appops)" to "cmd appops get $selfPackage $BACKGROUND_OP",
            "قراءة التشغيل التلقائي لشاومي" to "cmd appops get $selfPackage $XIAOMI_AUTOSTART_OP",
        )
        return checks.map { (name, cmd) ->
            try {
                when (val r = runner.run(cmd)) {
                    is CommandResult.Success -> CheckItem(name, cmd, true, "نجح")
                    is CommandResult.Failure -> CheckItem(name, cmd, false, r.reason)
                }
            } catch (e: Exception) {
                CheckItem(name, cmd, false, e.message ?: "خطأ غير معروف")
            }
        }
    }
}
