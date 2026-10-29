package com.droid.fetch.data

import java.io.File

/**
 * System properties read once through `getprop`, which apps may run; the reflective
 * SystemProperties API is hidden and can be blocked on newer releases.
 */
object SysProps {
    private val props: Map<String, String> by lazy { load() }

    operator fun get(key: String): String? = props[key]?.takeIf { it.isNotBlank() }

    fun bool(key: String): Boolean? = when (get(key)) {
        "true", "1" -> true
        "false", "0" -> false
        else -> null
    }

    private fun load(): Map<String, String> = runCatching {
        val line = Regex("""^\[(.+?)]: \[(.*)]$""")
        Runtime.getRuntime().exec(arrayOf("getprop")).inputStream.bufferedReader().useLines { lines ->
            lines.mapNotNull { line.find(it)?.destructured?.let { (k, v) -> k to v } }.toMap()
        }
    }.getOrDefault(emptyMap())
}

/** First line of a sysfs/procfs file, or null when it's missing or unreadable. */
fun readLine(path: String): String? = runCatching { File(path).bufferedReader().use { it.readLine() } }
    .getOrNull()?.trim()?.takeIf { it.isNotEmpty() }

fun runCommand(vararg cmd: String): String? = runCatching {
    Runtime.getRuntime().exec(cmd).inputStream.bufferedReader().use { it.readText() }.trim()
}.getOrNull()?.takeIf { it.isNotEmpty() }
