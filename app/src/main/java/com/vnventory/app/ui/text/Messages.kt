package com.vnventory.app.ui.text

import android.content.res.Resources
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalResources
import com.vnventory.app.R
import com.vnventory.app.domain.text.Message
import com.vnventory.app.domain.text.MessageKey

/** Resolve at display time, so a locale change also updates messages already held by ViewModels. */
@Composable
fun Message.localized(): String = LocalResources.current.resolve(this)

fun Resources.resolve(message: Message): String = when (message) {
    is Message.Literal -> message.value
    is Message.Template -> getString(message.key.resourceId, *message.arguments.map {
        if (it is Message) resolve(it) else it
    }.toTypedArray())
}

@get:StringRes
val MessageKey.resourceId: Int
    get() = when (this) {
        MessageKey.BACKUP_FEEDBACK_PRICE_DISPLAY_RESTORED -> R.string.message_backup_feedback_price_display_restored
        MessageKey.BACKUP_FEEDBACK_PRICE_DISPLAY_FAILED -> R.string.message_backup_feedback_price_display_failed
        MessageKey.ALLOCATION_PRICE_MISSING -> R.string.message_allocation_price_missing
        MessageKey.INPUT_EXPENSE_CATEGORY -> R.string.message_input_expense_category
        MessageKey.CATEGORY_INTERNATIONAL_SHIPPING -> R.string.message_category_international_shipping
        MessageKey.CATEGORY_ISLAND_SHIPPING -> R.string.message_category_island_shipping
        MessageKey.CATEGORY_DOMESTIC_SHIPPING -> R.string.message_category_domestic_shipping
        MessageKey.CATEGORY_PAYMENT_FEE -> R.string.message_category_payment_fee
        MessageKey.AMOUNT_TOTAL_OVERFLOW -> R.string.message_amount_total_overflow
        MessageKey.EXPENSE_NEGATIVE -> R.string.message_expense_negative
        MessageKey.MIXED_CURRENCY_ALLOCATION -> R.string.message_mixed_currency_allocation
        MessageKey.ALLOCATION_COPY_MISMATCH -> R.string.message_allocation_copy_mismatch
        MessageKey.ALLOCATION_NEGATIVE -> R.string.message_allocation_negative
        MessageKey.ALLOCATION_TOTAL_OVERFLOW -> R.string.message_allocation_total_overflow
        MessageKey.ALLOCATION_EXCESS -> R.string.message_allocation_excess
        MessageKey.COPY_IDS_DUPLICATE -> R.string.message_copy_ids_duplicate
        MessageKey.COPY_PRICE_NEGATIVE -> R.string.message_copy_price_negative
        MessageKey.AMOUNT_NEGATIVE -> R.string.message_amount_negative
        MessageKey.WEIGHT_NEGATIVE -> R.string.message_weight_negative
        MessageKey.CURRENCY_INVALID -> R.string.message_currency_invalid
        MessageKey.VN_ID_INVALID -> R.string.message_vn_id_invalid
        MessageKey.TITLES_REQUIRED -> R.string.message_titles_required
        MessageKey.CONDITION_NOTE_REQUIRED -> R.string.message_condition_note_required
        MessageKey.ORDER_SELECTED_MISSING -> R.string.message_order_selected_missing
        MessageKey.ORDER_MIXED_PRICE_ALLOCATION -> R.string.message_order_mixed_price_allocation
        MessageKey.ORDER_TITLE_REQUIRED -> R.string.message_order_title_required
        MessageKey.EXPENSE_MISSING -> R.string.message_expense_missing
        MessageKey.EXPENSE_ORDER_TRANSFER -> R.string.message_expense_order_transfer
        MessageKey.EXPENSE_NOT_MANUAL -> R.string.message_expense_not_manual
        MessageKey.ORDER_MISSING -> R.string.message_order_missing
        MessageKey.EXPENSE_NAME_REQUIRED -> R.string.message_expense_name_required
        MessageKey.RELEASE_VN_MISMATCH -> R.string.message_release_vn_mismatch
        MessageKey.COPY_MISSING -> R.string.message_copy_missing
        MessageKey.RELEASE_BIND_REQUIRED -> R.string.message_release_bind_required
        MessageKey.RELEASE_COPY_MISMATCH -> R.string.message_release_copy_mismatch
        MessageKey.BACKUP_IDS_INVALID -> R.string.message_backup_ids_invalid
        MessageKey.BACKUP_ORDER_TITLE_EMPTY -> R.string.message_backup_order_title_empty
        MessageKey.BACKUP_RELEASE_ID_INVALID -> R.string.message_backup_release_id_invalid
        MessageKey.BACKUP_COPY_ORDER_MISSING -> R.string.message_backup_copy_order_missing
        MessageKey.BACKUP_ALLOCATION_DUPLICATE -> R.string.message_backup_allocation_duplicate
        MessageKey.BACKUP_ALLOCATION_NOT_MANUAL -> R.string.message_backup_allocation_not_manual
        MessageKey.BACKUP_ALLOCATION_COPY_MISMATCH -> R.string.message_backup_allocation_copy_mismatch
        MessageKey.BACKUP_ALLOCATION_NEGATIVE -> R.string.message_backup_allocation_negative
        MessageKey.BACKUP_EXPENSE_ORDER_MISSING -> R.string.message_backup_expense_order_missing
        MessageKey.BACKUP_EXPENSE_NAME_EMPTY -> R.string.message_backup_expense_name_empty
        MessageKey.BACKUP_EXPORT_TOO_LARGE -> R.string.message_backup_export_too_large
        MessageKey.BACKUP_IMPORT_TOO_LARGE -> R.string.message_backup_import_too_large
        MessageKey.BACKUP_INVALID_FILE -> R.string.message_backup_invalid_file
        MessageKey.BACKUP_INVALID_UTF8 -> R.string.message_backup_invalid_utf8
        MessageKey.BACKUP_WRONG_FORMAT -> R.string.message_backup_wrong_format
        MessageKey.BACKUP_UNSUPPORTED_VERSION -> R.string.message_backup_unsupported_version
        MessageKey.BACKUP_INVALID_DATE -> R.string.message_backup_invalid_date
        MessageKey.BACKUP_INVALID_ENUM -> R.string.message_backup_invalid_enum
        MessageKey.BACKUP_EXPORT_FAILED -> R.string.message_backup_export_failed
        MessageKey.BACKUP_EXPORT_DENIED -> R.string.message_backup_export_denied
        MessageKey.BACKUP_READ_FAILED -> R.string.message_backup_read_failed
        MessageKey.BACKUP_READ_DENIED -> R.string.message_backup_read_denied
        MessageKey.VN_NOT_FOUND -> R.string.message_vn_not_found
        MessageKey.VN_PAGINATION_STALLED -> R.string.message_vn_pagination_stalled
        MessageKey.VN_HTTP_ERROR -> R.string.message_vn_http_error
        MessageKey.ERROR_HTTP -> R.string.message_error_http
        MessageKey.ERROR_PARSE -> R.string.message_error_parse
        MessageKey.ERROR_NETWORK -> R.string.message_error_network
        MessageKey.ERROR_IO -> R.string.message_error_io
        MessageKey.ERROR_UNKNOWN -> R.string.message_error_unknown
        MessageKey.NAV_ARG_MISSING -> R.string.message_nav_arg_missing
        MessageKey.INPUT_EXPENSE_NAME -> R.string.message_input_expense_name
        MessageKey.INPUT_NONNEGATIVE_AMOUNT -> R.string.message_input_nonnegative_amount
        MessageKey.INPUT_MANUAL_INVALID -> R.string.message_input_manual_invalid
        MessageKey.RELEASE_SELECTION_MISMATCH -> R.string.message_release_selection_mismatch
        MessageKey.BACKUP_PROGRESS_EXPORT -> R.string.message_backup_progress_export
        MessageKey.BACKUP_FEEDBACK_SAVED -> R.string.message_backup_feedback_saved
        MessageKey.BACKUP_PROGRESS_CHECK -> R.string.message_backup_progress_check
        MessageKey.BACKUP_PROGRESS_RESTORE -> R.string.message_backup_progress_restore
        MessageKey.BACKUP_FEEDBACK_CURRENCY_FAILED -> R.string.message_backup_feedback_currency_failed
        MessageKey.BACKUP_FEEDBACK_CURRENCY_RESTORED -> R.string.message_backup_feedback_currency_restored
        MessageKey.BACKUP_FEEDBACK_CURRENCY_UNCHANGED -> R.string.message_backup_feedback_currency_unchanged
        MessageKey.BACKUP_FEEDBACK_SHOPS_FAILED -> R.string.message_backup_feedback_shops_failed
        MessageKey.BACKUP_FEEDBACK_SHOPS_RESTORED -> R.string.message_backup_feedback_shops_restored
        MessageKey.BACKUP_FEEDBACK_SHOPS_UNCHANGED -> R.string.message_backup_feedback_shops_unchanged
        MessageKey.BACKUP_FEEDBACK_RESULT -> R.string.message_backup_feedback_result
        MessageKey.CONDITION_NEW -> R.string.message_condition_new
        MessageKey.CONDITION_USED -> R.string.message_condition_used
        MessageKey.CONDITION_UNOPENED -> R.string.message_condition_unopened
        MessageKey.CONDITION_INCOMPLETE -> R.string.message_condition_incomplete
        MessageKey.CONDITION_CUSTOM -> R.string.message_condition_custom
        MessageKey.ALLOCATION_EQUAL -> R.string.message_allocation_equal
        MessageKey.ALLOCATION_BY_PRICE -> R.string.message_allocation_by_price
        MessageKey.ALLOCATION_MANUAL -> R.string.message_allocation_manual
        MessageKey.CATEGORY_SHIPPING -> R.string.message_category_shipping
        MessageKey.CATEGORY_FEE -> R.string.message_category_fee
        MessageKey.CATEGORY_TAX -> R.string.message_category_tax
        MessageKey.CATEGORY_OTHER -> R.string.message_category_other
        MessageKey.SORT_ADDED_DESC -> R.string.message_sort_added_desc
        MessageKey.SORT_ADDED_ASC -> R.string.message_sort_added_asc
        MessageKey.SORT_TITLE_ASC -> R.string.message_sort_title_asc
        MessageKey.SORT_TITLE_DESC -> R.string.message_sort_title_desc
        MessageKey.SORT_PURCHASE_DESC -> R.string.message_sort_purchase_desc
        MessageKey.SORT_PURCHASE_ASC -> R.string.message_sort_purchase_asc
        MessageKey.SORT_PRICE_DESC -> R.string.message_sort_price_desc
        MessageKey.SORT_PRICE_ASC -> R.string.message_sort_price_asc
        MessageKey.MANUAL_RELEASE -> R.string.message_manual_release
        MessageKey.UNKNOWN_RELEASE -> R.string.message_unknown_release
        MessageKey.ALLOCATION_POOL -> R.string.message_allocation_pool
        MessageKey.ALLOCATION_ISSUE -> R.string.message_allocation_issue
        MessageKey.SHOP_NAME_REQUIRED -> R.string.message_shop_name_required
        MessageKey.SHOP_DUPLICATE -> R.string.message_shop_duplicate
        MessageKey.SHOP_MISSING -> R.string.message_shop_missing
        MessageKey.SHOP_DATA_INVALID -> R.string.message_shop_data_invalid
    }
