package org.networklens.core

data class ImportRow(val name: String, val company: String, val role: String, val category: String, val link: String)

object CsvImport {
    /** RFC 4180-style quoting, BOM, CRLF, multiline fields; reject malformed input atomically. */
    fun parse(input: String): List<ImportRow> {
        require(input.length <= 2_000_000) { "CSV exceeds 2 MB limit" }
        val rows = mutableListOf<List<String>>()
        var row = mutableListOf<String>(); val field = StringBuilder()
        var quoted = false; var closed = false; var i = 0
        val text = input.removePrefix("\uFEFF")
        fun endField() { row.add(field.toString().trim()); field.setLength(0); closed = false }
        fun endRow() { endField(); if (row.any { it.isNotBlank() }) rows.add(row); row = mutableListOf() }
        while (i < text.length) {
            val c = text[i]
            if (quoted) {
                if (c == '"') {
                    if (i + 1 < text.length && text[i + 1] == '"') { field.append('"'); i++ }
                    else { quoted = false; closed = true }
                } else field.append(c)
            } else when (c) {
                '"' -> { require(field.isEmpty() && !closed) { "Invalid CSV quoting" }; quoted = true }
                ',' -> endField()
                '\r', '\n' -> { endRow(); if (c == '\r' && i + 1 < text.length && text[i + 1] == '\n') i++ }
                else -> { require(!closed) { "Unexpected text after quoted field" }; field.append(c) }
            }
            i++
        }
        require(!quoted) { "Unclosed CSV quote" }
        if (field.isNotEmpty() || row.isNotEmpty() || closed) endRow()
        require(rows.isNotEmpty()) { "CSV is empty" }
        val header = rows.first().map { it.lowercase() }
        require(header.distinct().size == header.size) { "Duplicate column headers" }
        require("name" in header) { "CSV must have a name column" }
        require(rows.size <= 5001) { "Import at most 5,000 attendees" }
        return rows.drop(1).mapIndexed { index, values ->
            require(values.size == header.size) { "Row ${index + 2}: column count differs from header" }
            fun get(key: String) = header.indexOf(key).let { if (it >= 0) values[it] else "" }
            val name = get("name"); val category = get("category").lowercase().ifBlank { "other" }
            require(name.isNotBlank() && name.length <= 160) { "Row ${index + 2}: invalid name" }
            require(values.all { it.length <= 2000 }) { "Row ${index + 2}: field too long" }
            require(category in listOf("investor", "startup", "other")) { "Row ${index + 2}: category must be investor, startup or other" }
            val link = get("link")
            require(link.isBlank() || link.startsWith("https://")) { "Profile links must use HTTPS" }
            ImportRow(name, get("company"), get("role"), category, link)
        }.distinct()
    }
}
