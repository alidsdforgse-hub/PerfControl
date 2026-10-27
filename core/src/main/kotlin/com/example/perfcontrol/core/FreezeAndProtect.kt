package com.example.perfcontrol.core

object PackageListParser {
    /** "package:com.x" لكل سطر */
    fun parse(out: String): Set<String> = out.lineSequence()
        .map { it.trim() }
        .filter { it.startsWith("package:") }
        .map { it.removePrefix("package:").substringBefore(' ').trim() }
        .filter { it.isNotEmpty() }
        .toSet()
}

object DeviceIdleParser {
    /** أسطر مثل "user,com.whatsapp,10188" */
    fun whitelist(out: String): Set<String> = out.lineSequence()
        .mapNotNull { line ->
            val p = line.trim().split(",")
            if (p.size >= 2) p[1].trim() else null
        }
        .filter { it.contains('.') }
        .toSet()
}

/** تجميد تطبيق = تعطيله لهذا المستخدم (يختفي ولا يعمل) حتى يُعاد تفعيله. */
class FreezeManager(private val runner: CommandRunner, private val protection: AppProtection) {

    suspend fun frozen(): Set<String>? =
        (runner.run("pm list packages -d") as? CommandResult.Success)?.let { PackageListParser.parse(it.output) }

    suspend fun freeze(pkg: String): StartupResult {
        if (!isValidPackageName(pkg)) return StartupResult(false, "اسم حزمة غير صالح")
        if (protection.isProtected(pkg)) return StartupResult(false, "هذا تطبيق محمي ولا يمكن تجميده")
        val r = runner.run("pm disable-user --user 0 $pkg")
        if (r is CommandResult.Failure) return StartupResult(false, "فشل التجميد: ${r.reason}")
        return if (frozen()?.contains(pkg) == true) StartupResult(true, "تم تجميد التطبيق")
        else StartupResult(false, "نفّذ النظام الأمر لكن التطبيق لم يُجمَّد")
    }

    suspend fun unfreeze(pkg: String): StartupResult {
        if (!isValidPackageName(pkg)) return StartupResult(false, "اسم حزمة غير صالح")
        val r = runner.run("pm enable $pkg")
        if (r is CommandResult.Failure) return StartupResult(false, "فشلت الإعادة: ${r.reason}")
        return if (frozen()?.contains(pkg) == true) StartupResult(false, "لم يُعَد تفعيل التطبيق")
        else StartupResult(true, "تمت إعادة التطبيق")
    }
}

/** حماية إشعارات تطبيق: استثناؤه من توفير الطاقة العميق (Doze) والسماح له بالعمل في الخلفية. */
class NotificationProtector(private val runner: CommandRunner) {

    suspend fun protectedApps(): Set<String>? =
        (runner.run("cmd deviceidle whitelist") as? CommandResult.Success)?.let { DeviceIdleParser.whitelist(it.output) }

    suspend fun protect(pkg: String): StartupResult {
        if (!isValidPackageName(pkg)) return StartupResult(false, "اسم حزمة غير صالح")
        val a = runner.run("cmd deviceidle whitelist +$pkg")
        if (a is CommandResult.Failure) return StartupResult(false, "فشلت الحماية: ${a.reason}")
        runner.run("cmd appops set $pkg RUN_ANY_IN_BACKGROUND allow")
        return if (protectedApps()?.contains(pkg) == true) StartupResult(true, "تمت حماية إشعارات التطبيق")
        else StartupResult(false, "لم يقبل النظام إضافة التطبيق للاستثناء")
    }

    suspend fun unprotect(pkg: String): StartupResult {
        if (!isValidPackageName(pkg)) return StartupResult(false, "اسم حزمة غير صالح")
        val a = runner.run("cmd deviceidle whitelist -$pkg")
        if (a is CommandResult.Failure) return StartupResult(false, "فشلت الإزالة: ${a.reason}")
        runner.run("cmd appops set $pkg RUN_ANY_IN_BACKGROUND default")
        return StartupResult(true, "أُزيلت الحماية")
    }
}
