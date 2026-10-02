package com.vnventory.app.domain.model

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.NumberFormat
import java.util.Locale
import java.util.Currency as JavaCurrency

/**
 * 金额工具。
 *
 * 全应用金额一律以 **Long 最小货币单位** 存储（日元=1 円，人民币=1 分），
 * 避免浮点误差；只在展示/输入时与十进制字符串互转。
 */
object Money {

    /** 明确按 0 位小数处理的货币（避免依赖平台 ICU 数据差异） */
    private val zeroDecimalCurrencies = setOf("JPY", "KRW", "VND", "IDR", "CLP", "ISK")

    /** 常见货币（设置页默认货币候选、金额输入货币候选） */
    val commonCurrencies: List<String> =
        listOf("CNY", "JPY", "USD", "EUR", "GBP", "HKD", "TWD", "KRW", "SGD", "AUD", "CAD")

    fun normalize(code: String): String = code.trim().uppercase(Locale.ROOT)

    /** 货币小数位数：JPY=0，CNY=2 …未知货币走平台数据，最终回退 2 */
    fun decimals(code: String): Int {
        val c = normalize(code)
        if (c in zeroDecimalCurrencies) return 0
        return runCatching { JavaCurrency.getInstance(c).defaultFractionDigits }
            .getOrNull()
            ?.takeIf { it in 0..4 }
            ?: 2
    }

    /** 展示用符号；不确定的货币直接显示代码 */
    fun symbol(code: String): String = when (normalize(code)) {
        "CNY" -> "¥"
        "JPY" -> "¥"
        "USD" -> "$"
        "EUR" -> "€"
        "GBP" -> "£"
        "HKD" -> "HK$"
        "TWD" -> "NT$"
        "KRW" -> "₩"
        "SGD" -> "S$"
        "AUD" -> "A$"
        "CAD" -> "C$"
        else -> ""
    }

    /**
     * 格式化最小单位金额，例：`format(12345, "CNY") == "¥123.45"`；
     * `format(1234, "JPY") == "¥1,234"`。
     * 使用固定 Locale.US 千分位，保证输出稳定（UI 语言手动标注）。
     */
    fun format(minor: Long, code: String): String {
        val d = decimals(code)
        val value = BigDecimal.valueOf(minor, d)
        val nf = NumberFormat.getNumberInstance(Locale.US).apply {
            minimumFractionDigits = 0
            maximumFractionDigits = d
        }
        val formatted = nf.format(value)
        val s = symbol(code)
        return if (s.isEmpty()) "$formatted ${normalize(code)}" else "$s$formatted"
    }

    /** 带货币代码的格式化，例：`"¥123.45 CNY"`（多币种场景更明确） */
    fun formatWithCode(minor: Long, code: String): String = "${format(minor, code)} ${normalize(code)}"

    /**
     * 解析用户输入的金额为最小单位。非法输入返回 null（不静默取整）。
     *
     * 允许：千分位逗号、前置货币符号、全角逗号/句点；不允许：负数、超过货币小数位数。
     */
    fun parse(input: String, code: String): Long? {
        val d = decimals(code)
        val cleaned = input
            .trim()
            .replace(",", "")
            .replace("，", "")
            .replace("。", ".")
            .removePrefix(symbol(code))
            .replace("￥", "")
            .replace("¥", "")
            .replace("$", "")
            .replace("€", "")
            .replace("£", "")
            .replace("₩", "")
            .replace(" ", "")
            .trim()
        if (cleaned.isEmpty()) return null
        val bd = runCatching { BigDecimal(cleaned) }.getOrNull() ?: return null
        if (bd.signum() < 0) return null
        if (bd.stripTrailingZeros().scale() > d) return null
        return runCatching {
            bd.movePointRight(d).setScale(0, RoundingMode.UNNECESSARY).longValueExact()
        }.getOrNull()
    }

    /** 由最小单位还原十进制字符串（编辑框回填用），例 `12345 CNY -> "123.45"` */
    fun toEditableString(minor: Long, code: String): String {
        val d = decimals(code)
        return BigDecimal.valueOf(minor, d).stripTrailingZeros().toPlainString()
    }
}
