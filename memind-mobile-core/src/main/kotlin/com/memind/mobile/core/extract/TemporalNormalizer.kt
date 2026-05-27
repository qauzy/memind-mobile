package com.memind.mobile.core.extract

import com.memind.mobile.core.model.Message
import java.time.LocalDate
import java.time.ZoneOffset

public data class NormalizedTime(
    val occurredAt: Long? = null,
    val occurredStart: Long? = null,
    val occurredEnd: Long? = null,
    val granularity: String? = null,
)

public class TemporalNormalizer {
    private val dateRegex = Regex("""\b(\d{4})[-/](\d{1,2})[-/](\d{1,2})\b""")

    /**
     * 从文本和消息时间中解析轻量时间。
     *
     * 优先使用文本中的 yyyy-MM-dd / yyyy/MM/dd；没有显式时间时回退到消息 timestamp 或 observedAt。
     */
    public fun normalize(
        text: String,
        messages: List<Message> = emptyList(),
        observedAt: Long = System.currentTimeMillis(),
    ): NormalizedTime {
        val explicitDate = parseFirstDate(text)
        if (explicitDate != null) {
            return NormalizedTime(
                occurredAt = explicitDate,
                occurredStart = explicitDate,
                occurredEnd = explicitDate + DAY_MS - 1,
                granularity = "day",
            )
        }
        val timestamps = messages.mapNotNull { it.timestamp }
        if (timestamps.isNotEmpty()) {
            val start = timestamps.minOrNull()
            val end = timestamps.maxOrNull()
            return NormalizedTime(
                occurredAt = end,
                occurredStart = start,
                occurredEnd = end,
                granularity = "message",
            )
        }
        return NormalizedTime(
            occurredAt = observedAt,
            occurredStart = observedAt,
            occurredEnd = observedAt,
            granularity = "observed",
        )
    }

    /**
     * 解析文本中的第一个日期。
     *
     * 返回 UTC 当日零点毫秒，解析失败时返回 null。
     */
    private fun parseFirstDate(text: String): Long? {
        val match = dateRegex.find(text) ?: return null
        val year = match.groupValues[1].toIntOrNull() ?: return null
        val month = match.groupValues[2].toIntOrNull() ?: return null
        val day = match.groupValues[3].toIntOrNull() ?: return null
        return runCatching {
            LocalDate.of(year, month, day)
                .atStartOfDay()
                .toInstant(ZoneOffset.UTC)
                .toEpochMilli()
        }.getOrNull()
    }

    private companion object {
        private const val DAY_MS = 24L * 60L * 60L * 1000L
    }
}
