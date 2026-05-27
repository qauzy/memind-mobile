package com.memind.mobile.core.store.room

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

internal object RoomJson {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    /**
     * 将字符串 Map 编码为 JSON。
     *
     * Room 不直接支持 Map 字段，因此统一使用 JSON 字符串落库。
     */
    fun encodeMap(value: Map<String, String>): String = json.encodeToString(value)

    /**
     * 从 JSON 解析字符串 Map。
     *
     * 解析失败时返回空 Map，避免坏数据导致整个存储读取失败。
     */
    fun decodeMap(value: String?): Map<String, String> {
        if (value.isNullOrBlank()) return emptyMap()
        return runCatching { json.decodeFromString<Map<String, String>>(value) }.getOrDefault(emptyMap())
    }

    /**
     * 将字符串列表编码为 JSON。
     *
     * 用于保存 Insight children 等轻量结构化字段。
     */
    fun encodeStringList(value: List<String>): String = json.encodeToString(value)

    /**
     * 从 JSON 解析字符串列表。
     *
     * 解析失败时返回空列表，保证 RoomStore 读取路径可降级。
     */
    fun decodeStringList(value: String?): List<String> {
        if (value.isNullOrBlank()) return emptyList()
        return runCatching { json.decodeFromString<List<String>>(value) }.getOrDefault(emptyList())
    }
}
