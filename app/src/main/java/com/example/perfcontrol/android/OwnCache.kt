package com.example.perfcontrol.android

import android.content.Context
import java.io.File

class OwnCache(private val ctx: Context) {
    private fun dirs(): List<File> = listOfNotNull(ctx.cacheDir, ctx.externalCacheDir)

    private fun size(f: File): Long =
        if (f.isDirectory) (f.listFiles()?.sumOf { size(it) } ?: 0L) else f.length()

    fun sizeBytes(): Long = dirs().sumOf { size(it) }

    /** يحذف محتوى الكاش ويعيد عدد البايتات المحرَّرة. */
    fun clear(): Long {
        val before = sizeBytes()
        dirs().forEach { d -> d.listFiles()?.forEach { it.deleteRecursively() } }
        return (before - sizeBytes()).coerceAtLeast(0L)
    }
}
