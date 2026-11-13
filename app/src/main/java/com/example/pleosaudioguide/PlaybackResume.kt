package com.example.pleosaudioguide

import org.json.JSONObject

/** 재개 위치는 청취 구간과 별개다. seek한 위치도 저장하지만 청취로 인정하지 않는다. */
data class PlaybackResume(val content: AudioContent, val positionMs: Long, val durationMs: Long) {
    val canResume: Boolean get() = content.audioUri != null && positionMs > 0 &&
        (durationMs <= 0 || positionMs < durationMs)

    fun encode(): String = JSONObject().put("version", 1).put("id", content.id)
        .put("title", content.title).put("category", content.category).put("script", content.script)
        .put("seconds", content.estimatedSeconds).put("audio", content.audioUri)
        .put("image", content.imageUri ?: JSONObject.NULL).put("credit", content.credit ?: JSONObject.NULL)
        .put("position", positionMs).put("duration", durationMs).toString()

    companion object {
        fun decode(text: String): PlaybackResume {
            val j = JSONObject(text)
            require(j.getInt("version") == 1)
            val content = AudioContent(j.getString("id"), j.getString("title"), j.getString("category"),
                emptyList(), j.getString("script"), j.getInt("seconds"), j.getString("audio"),
                if (j.isNull("credit")) null else j.getString("credit"),
                if (j.isNull("image")) null else j.getString("image"))
            return PlaybackResume(content, j.getLong("position").coerceAtLeast(0),
                j.getLong("duration").coerceAtLeast(0))
        }
    }
}
