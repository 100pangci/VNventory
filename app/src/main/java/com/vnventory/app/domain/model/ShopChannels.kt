package com.vnventory.app.domain.model

import com.vnventory.app.domain.text.MessageKey
import com.vnventory.app.domain.text.message
import com.vnventory.app.domain.text.requireMessage
import java.util.Locale

/** Candidate names are preferences, not foreign keys: historical purchase snapshots stay untouched. */
object ShopChannels {
    private fun key(name: String): String = name.lowercase(Locale.ROOT)

    fun sameName(left: String, right: String): Boolean = key(left) == key(right)

    fun validate(names: List<String>) {
        requireMessage(names.all { it.isNotBlank() && it == it.trim() } &&
            names.map(::key).distinct().size == names.size) {
            message(MessageKey.SHOP_DATA_INVALID)
        }
    }
}
