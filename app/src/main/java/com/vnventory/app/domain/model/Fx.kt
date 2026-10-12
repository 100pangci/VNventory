package com.vnventory.app.domain.model

import com.vnventory.app.domain.text.MessageException
import com.vnventory.app.domain.text.MessageKey
import com.vnventory.app.domain.text.message
import com.vnventory.app.domain.text.requireMessage
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode

/**
 * 汇率定格工具。
 *
 * 汇率以 Long 定点存储：`rateScaled` 表示「1 单位原币 = rateScaled / 10^9 单位目标币」，
 * 与金额一样不使用浮点；只在输入 / 展示时与十进制字符串互转。
 *
 * 定格是购买事实的一部分：一旦写入，之后行情波动不影响历史；换算金额实时由
 * 「定格汇率 × 当前金额」算出，不单独落库。
 */
object Fx {

    const val SCALE = 1_000_000_000L
    const val MAX_DECIMALS = 9
    /** 数据源标识：自动获取的固定为数据源名，手动填写为 `manual`。 */
    const val SOURCE_MANUAL = "manual"

    private val rateInput = Regex("(?:[0-9]+(?:\\.[0-9]*)?|\\.[0-9]+)")

    /** 用户输入解析：必须 > 0、最多 9 位小数；非法返回 null（不静默取整）。 */
    fun parse(input: String): Long? {
        val cleaned = input.trim()
            .replace(",", "").replace("，", "")
            .replace("。", ".").replace("．", ".")
        if (cleaned.isEmpty() || cleaned.length > 32 || !rateInput.matches(cleaned)) return null
        val bd = runCatching { BigDecimal(cleaned) }.getOrNull() ?: return null
        if (bd.signum() <= 0) return null
        if (bd.stripTrailingZeros().scale() > MAX_DECIMALS) return null
        return runCatching { bd.movePointRight(MAX_DECIMALS).setScale(0, RoundingMode.UNNECESSARY).longValueExact() }
            .getOrNull()?.takeIf { it > 0 }
    }

    /** 数据源返回的十进制字符串：按 9 位小数四舍五入（half-up）；<=0 或不可解析返回 null。 */
    fun fromDecimal(input: String): Long? {
        val cleaned = input.trim()
        if (cleaned.isEmpty() || cleaned.length > 64) return null
        val bd = runCatching { BigDecimal(cleaned) }.getOrNull() ?: return null
        if (bd.signum() <= 0) return null
        return runCatching {
            bd.setScale(MAX_DECIMALS, RoundingMode.HALF_UP).movePointRight(MAX_DECIMALS).longValueExact()
        }.getOrNull()?.takeIf { it > 0 }
    }

    /** 定点换算，四舍五入到目标币种最小单位；金额或结果溢出时拒绝。 */
    fun convert(amountMinor: Long, fromCurrency: String, rateScaled: Long, toCurrency: String): Long {
        requireMessage(amountMinor >= 0) { message(MessageKey.AMOUNT_NEGATIVE) }
        requireMessage(rateScaled > 0) { message(MessageKey.FX_RATE_INVALID) }
        val fromDecimals = Money.decimals(fromCurrency)
        val toDecimals = Money.decimals(toCurrency)
        val numerator = BigDecimal.valueOf(amountMinor)
            .multiply(BigDecimal.valueOf(rateScaled))
            .multiply(BigDecimal.TEN.pow(toDecimals))
        val denominator = BigDecimal.TEN.pow(fromDecimals).multiply(BigDecimal.valueOf(SCALE))
        val result = numerator.divide(denominator, 0, RoundingMode.HALF_UP)
        return try {
            result.longValueExact()
        } catch (_: ArithmeticException) {
            throw MessageException(message(MessageKey.FX_CONVERT_OVERFLOW))
        }
    }

    /** 展示用汇率文本，例：`1 JPY = 0.0423 CNY`（最多 4 位有效数字）。 */
    fun format(rateScaled: Long, fromCurrency: String, toCurrency: String): String =
        "1 ${Money.normalize(fromCurrency)} = ${formatValue(rateScaled)} ${Money.normalize(toCurrency)}"

    fun formatValue(rateScaled: Long): String =
        BigDecimal.valueOf(rateScaled, MAX_DECIMALS)
            .round(MathContext(4, RoundingMode.HALF_UP))
            .stripTrailingZeros()
            .toPlainString()
}
