package com.solucionx.sxdynamic.core

import android.content.Context
import android.util.Log
import java.io.File
import java.io.FileWriter
import java.time.Instant
import java.util.ArrayDeque
import java.util.concurrent.CopyOnWriteArraySet
import java.util.concurrent.Executors

data class DiagnosticEntry(
    val at: Instant,
    val level: String,
    val source: String,
    val message: String,
)

object Diagnostics {
    private const val maxEntries = 200
    private const val maxFileBytes = 256 * 1024L
    private val lock = Any()
    private val entries = ArrayDeque<DiagnosticEntry>(maxEntries)
    private val listeners = CopyOnWriteArraySet<() -> Unit>()
    private val diskExecutor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "sx-diagnostics").apply { isDaemon = true }
    }

    @Volatile
    private var logFile: File? = null

    fun initialize(context: Context) {
        val dir = File(context.filesDir, "diagnostics")
        if (!dir.exists()) dir.mkdirs()
        logFile = File(dir, "sx-dynamic.log")
    }

    fun info(source: String, message: String) = add("INFO", source, message)
    fun warn(source: String, message: String) = add("WARN", source, message)

    fun error(source: String, message: String, throwable: Throwable? = null) {
        val suffix = throwable?.let { ": " + it.javaClass.simpleName + ": " + it.message.orEmpty() }.orEmpty()
        add("ERROR", source, message + suffix)
        Log.e("SXDynamic/" + source, message, throwable)
    }

    fun snapshot(): List<DiagnosticEntry> = synchronized(lock) { entries.toList() }

    fun addListener(listener: () -> Unit) {
        listeners += listener
    }

    fun removeListener(listener: () -> Unit) {
        listeners -= listener
    }

    fun clear() {
        synchronized(lock) { entries.clear() }
        diskExecutor.execute { runCatching { logFile?.writeText("") } }
        listeners.forEach { runCatching { it() } }
    }

    private fun add(level: String, source: String, message: String) {
        if (level != "ERROR") Log.d("SXDynamic/" + source, level + " " + message)
        val entry = DiagnosticEntry(Instant.now(), level, source, message)
        synchronized(lock) {
            if (entries.size >= maxEntries) entries.removeFirst()
            entries.addLast(entry)
        }
        persist(entry)
        listeners.forEach { runCatching { it() } }
    }

    private fun persist(entry: DiagnosticEntry) {
        val target = logFile ?: return
        diskExecutor.execute {
            runCatching {
                if (target.exists() && target.length() > maxFileBytes) target.writeText("")
                FileWriter(target, true).use { writer ->
                    writer.append(entry.at.toString())
                        .append(' ')
                        .append(entry.level)
                        .append(' ')
                        .append(entry.source)
                        .append(" - ")
                        .append(entry.message.replace('\n', ' '))
                        .append('\n')
                }
            }
        }
    }
}
