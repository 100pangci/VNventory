package com.vnventory.app.domain.model

enum class TitleDisplayMode { ORIGINAL, ROMANIZED }

/** No transliteration or language guessing. Legacy values remain unclassified. */
fun String?.normalizedTitle(): String? = this?.trim()?.takeIf(String::isNotEmpty)

fun displayTitle(mode: TitleDisplayMode, original: String?, romanized: String?, legacy: String?, placeholder: String): String =
    (if (mode == TitleDisplayMode.ORIGINAL) listOf(original, romanized) else listOf(romanized, original))
        .plus(listOf(legacy, placeholder)).firstNotNullOfOrNull { it.normalizedTitle() } ?: "—"
