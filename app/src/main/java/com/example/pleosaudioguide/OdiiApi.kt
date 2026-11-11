package com.example.pleosaudioguide

import android.util.Log
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap
import org.json.JSONArray
import org.json.JSONObject

/**
 * 한국관광공사_관광지 오디오 가이드정보_GW (data.go.kr 15101971) 연동.
 * 포털에 응답 필드 명세가 없으므로 필드명은 실제 응답으로 확인 전까지 미검증이다.
 * 확인 전까지 처음 받은 항목의 필드 목록을 로그로 남긴다(인증키는 남기지 않는다).
 */
object OdiiApi {
    const val CREDIT = "한국관광공사 오디(Odii)"
    private const val BASE = "https://apis.data.go.kr/B551011/Odii"
    private const val TAG = "AudioGuideOdii"

    class OdiiException(message: String) : Exception(message)

    /** 목적지 좌표 주변 이야기. mapX=경도, mapY=위도(WGS84). */
    fun storiesNear(serviceKey: String, latitude: Double, longitude: Double, radiusMeters: Int = 5000) =
        request(serviceKey, "storyLocationBasedList", mapOf(
            "mapX" to longitude.toString(), "mapY" to latitude.toString(),
            "radius" to radiusMeters.coerceIn(1, 20000).toString()))

    fun storiesByKeyword(serviceKey: String, keyword: String) =
        request(serviceKey, "storySearchList", mapOf("keyword" to keyword))

    private fun request(serviceKey: String, operation: String, params: Map<String, String>): OdiiParser.Result {
        if (serviceKey.isBlank()) throw OdiiException("인증키 미설정 · local.properties에 ODII_SERVICE_KEY를 추가하세요")
        val query = (mapOf("MobileOS" to "AND", "MobileApp" to "POTQUEST", "_type" to "json",
            "langCode" to "ko", "numOfRows" to "20", "pageNo" to "1") + params)
            .entries.joinToString("&") { "${it.key}=${encode(it.value)}" }
        Log.i(TAG, "$operation?$query")
        val connection = URL("$BASE/$operation?serviceKey=${encode(serviceKey)}&$query")
            .openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 8000
            connection.readTimeout = 8000
            val code = connection.responseCode
            val body = (if (code in 200..299) connection.inputStream else connection.errorStream)
                ?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) throw OdiiException("HTTP $code ${OdiiParser.gatewayError(body) ?: ""}".trim())
            return OdiiParser.parse(body).also { result ->
                result.firstItemFields?.let { Log.i(TAG, "$operation item fields: $it") }
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun encode(value: String) = URLEncoder.encode(value, "UTF-8")
}

/** Android 의존성이 없는 순수 변환 규칙. 단위 테스트 대상. */
object OdiiParser {
    data class Result(val contents: List<AudioContent>, val skippedNoAudio: Int, val firstItemFields: String?)

    fun parse(body: String): Result {
        val trimmed = body.trim()
        // 인증키 오류 등 게이트웨이 오류는 _type=json이어도 XML로 올 수 있다.
        if (!trimmed.startsWith("{")) throw OdiiApi.OdiiException(gatewayError(trimmed) ?: "JSON이 아닌 응답")
        val response = JSONObject(trimmed).optJSONObject("response")
            ?: throw OdiiApi.OdiiException("response 없음")
        val header = response.optJSONObject("header")
        val resultCode = header?.optString("resultCode").orEmpty()
        if (resultCode.isNotEmpty() && resultCode != "0000" && resultCode != "00") {
            throw OdiiApi.OdiiException("결과 코드 $resultCode ${header?.optString("resultMsg").orEmpty()}".trim())
        }
        // 결과 0건이면 items가 빈 문자열, 1건이면 item이 객체로 올 수 있다.
        val itemValue = response.optJSONObject("body")?.optJSONObject("items")?.opt("item")
        val items = when (itemValue) {
            is JSONArray -> (0 until itemValue.length()).mapNotNull { itemValue.optJSONObject(it) }
            is JSONObject -> listOf(itemValue)
            else -> emptyList()
        }
        val contents = items.mapNotNull(::toContent).distinctBy { it.id }
        return Result(contents, items.size - contents.size, items.firstOrNull()?.keys()?.asSequence()?.sorted()?.joinToString())
    }

    fun gatewayError(body: String): String? =
        Regex("<returnAuthMsg>(.*?)</returnAuthMsg>").find(body)?.groupValues?.get(1)
            ?: Regex("<resultMsg>(.*?)</resultMsg>").find(body)?.groupValues?.get(1)

    private fun toContent(item: JSONObject): AudioContent? {
        // 외부 임의 URI 재생을 막기 위해 https 음원만 등록한다.
        val audioUrl = item.text("audioUrl")?.takeIf { it.startsWith("https://") } ?: return null
        val storyId = item.text("stid") ?: return null
        val title = item.text("audioTitle") ?: item.text("title") ?: return null
        return AudioContent(
            id = "odii:$storyId:${item.text("stlid") ?: item.text("langCode") ?: "ko"}",
            title = title,
            category = "관광·역사",
            placeTags = listOfNotNull(item.text("title")),
            script = item.text("script").orEmpty(),
            // playTime 단위(초)는 실제 응답과 음원 길이로 확인해야 한다.
            estimatedSeconds = item.text("playTime")?.toIntOrNull() ?: 0,
            audioUri = audioUrl,
            credit = OdiiApi.CREDIT,
            imageUri = item.text("imageUrl")?.takeIf { it.startsWith("https://") }
        )
    }

    private fun JSONObject.text(name: String) = optString(name).trim().takeIf { it.isNotEmpty() && it != "null" }
}

/** 조회한 오디 콘텐츠를 앱 화면과 MediaLibraryService가 같은 ID로 찾도록 프로세스 메모리에 보관한다. */
object OdiiStore {
    private val byId = ConcurrentHashMap<String, AudioContent>()
    val all: List<AudioContent> get() = byId.values.sortedBy { it.title }
    fun remember(contents: List<AudioContent>) = contents.forEach { byId[it.id] = it }
    fun find(id: String?): AudioContent? = id?.let { byId[it] }
}
