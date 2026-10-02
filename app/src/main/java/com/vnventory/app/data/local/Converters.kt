package com.vnventory.app.data.local

import androidx.room.TypeConverter
import com.vnventory.app.domain.model.AllocationMode
import com.vnventory.app.domain.model.CopyCondition
import com.vnventory.app.domain.model.ExpenseCategory
import java.time.LocalDate
import java.time.format.DateTimeParseException

/**
 * 类型转换。约定：
 * - 金额一律 Long 最小货币单位（不经过转换器）;
 * - 日期存 ISO-8601 文本，可读性好、便于 SQL 排序；
 * - 枚举存 name 字符串，容错未知值（升级后回退默认，不崩溃）。
 */
class Converters {

    @TypeConverter
    fun localDateToString(date: LocalDate?): String? = date?.toString()

    @TypeConverter
    fun stringToLocalDate(value: String?): LocalDate? {
        if (value.isNullOrBlank()) return null
        return try {
            LocalDate.parse(value)
        } catch (_: DateTimeParseException) {
            null
        }
    }

    @TypeConverter
    fun copyConditionToString(condition: CopyCondition): String = condition.name

    @TypeConverter
    fun stringToCopyCondition(value: String?): CopyCondition =
        CopyCondition.entries.firstOrNull { it.name == value } ?: CopyCondition.USED

    @TypeConverter
    fun allocationModeToString(mode: AllocationMode): String = mode.name

    @TypeConverter
    fun stringToAllocationMode(value: String?): AllocationMode =
        AllocationMode.entries.firstOrNull { it.name == value } ?: AllocationMode.EQUAL

    @TypeConverter
    fun expenseCategoryToString(category: ExpenseCategory): String = category.name

    @TypeConverter
    fun stringToExpenseCategory(value: String?): ExpenseCategory =
        ExpenseCategory.entries.firstOrNull { it.name == value } ?: ExpenseCategory.OTHER
}
