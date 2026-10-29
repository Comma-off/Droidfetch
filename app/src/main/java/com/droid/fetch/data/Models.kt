// SPDX-FileCopyrightText: 2026 Comma
// SPDX-License-Identifier: GPL-3.0-or-later

package com.droid.fetch.data

/** One label/value row. */
data class InfoItem(val label: String, val value: String)

data class InfoSection(val title: String, val items: List<InfoItem>)

/** Builder so collectors read as a list of rows, skipping values the device doesn't report. */
class SectionBuilder(private val title: String) {
    private val items = mutableListOf<InfoItem>()

    fun row(label: String, value: String?) {
        if (value.isNullOrBlank()) return
        items += InfoItem(label, value)
    }

    fun build(): InfoSection? = if (items.isEmpty()) null else InfoSection(title, items)
}

fun section(title: String, block: SectionBuilder.() -> Unit): InfoSection? =
    SectionBuilder(title).apply(block).build()

/** Renders sections as plain text, used for copying and sharing a report. */
fun List<InfoSection>.toReport(): String = buildString {
    for (s in this@toReport) {
        appendLine("[${s.title}]")
        val pad = s.items.maxOf { it.label.length }
        for (item in s.items) appendLine("  ${item.label.padEnd(pad)}  ${item.value}")
        appendLine()
    }
}.trimEnd()
