package com.example.perfcontrol.core

interface StateStore {
    fun saveOriginal(pkg: String, mode: String)
    fun original(pkg: String): String?
    fun clear(pkg: String)
}

data class StartupResult(val ok: Boolean, val message: String)

const val XIAOMI_AUTOSTART_OP = "AUTO_START"
const val BACKGROUND_OP = "RUN_ANY_IN_BACKGROUND"

/** "RUN_ANY_IN_BACKGROUND: allow" -> "allow" */
fun parseAppOpMode(output: String): String? =
    Regex("[A-Z_]+:\\s*(\\w+)").find(output.trim())?.groupValues?.get(1)

class StartupManager(
    private val runner: CommandRunner,
    private val store: StateStore,
) {
    private suspend fun readMode(pkg: String, op: String): String? =
        (runner.run("cmd appops get $pkg $op") as? CommandResult.Success)?.let { parseAppOpMode(it.output) }

    suspend fun restrict(pkg: String, op: String = BACKGROUND_OP): StartupResult {
        if (!isValidPackageName(pkg)) return StartupResult(false, "اسم حزمة غير صالح")
        val before = readMode(pkg, op) ?: return StartupResult(false, "تعذرت قراءة الحالة الحالية")
        if (store.original(pkg) == null && before != "ignore" && before != "deny") {
            store.saveOriginal(pkg, before)
        }
        val set = runner.run("cmd appops set $pkg $op ignore")
        if (set is CommandResult.Failure) return StartupResult(false, "فشل التنفيذ: ${set.reason}")
        val after = readMode(pkg, op)
        return if (after == "ignore" || after == "deny") {
            StartupResult(true, "تم التقييد")
        } else {
            StartupResult(false, "نفّذ النظام الأمر لكن الحالة لم تتغير (قد لا يدعمه نظام شاومي لهذا التطبيق)")
        }
    }

    suspend fun restore(pkg: String, op: String = BACKGROUND_OP): StartupResult {
        if (!isValidPackageName(pkg)) return StartupResult(false, "اسم حزمة غير صالح")
        if (store.original(pkg) == null) return StartupResult(false, "لا توجد حالة محفوظة للتراجع عنها")
        val set = runner.run("cmd appops set $pkg $op default")
        if (set is CommandResult.Failure) return StartupResult(false, "فشل التراجع: ${set.reason}")
        store.clear(pkg)
        return StartupResult(true, "تم التراجع")
    }
}
