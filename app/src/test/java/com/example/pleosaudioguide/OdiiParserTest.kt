package com.example.pleosaudioguide

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 응답 예시는 data.go.kr 공통 JSON 구조를 가정한 테스트용 값이며 실제 오디 응답이 아니다. */
class OdiiParserTest {
    private fun wrap(items: String) =
        """{"response":{"header":{"resultCode":"0000","resultMsg":"OK"},"body":{"items":$items,"totalCount":1}}}"""

    private val story = """{"stid":"101","stlid":"201","title":"대릉원","audioTitle":"대릉원 이야기",
        "script":"신라 왕릉","playTime":"180","audioUrl":"https://example.org/a.mp3","imageUrl":"https://example.org/a.jpg"}"""

    @Test fun storyBecomesPlayableContentWithCredit() {
        val content = OdiiParser.parse(wrap("""{"item":[$story]}""")).contents.single()
        assertEquals("odii:101:201", content.id)
        assertEquals("대릉원 이야기", content.title)
        assertEquals(180, content.estimatedSeconds)
        assertEquals("https://example.org/a.mp3", content.audioUri)
        assertEquals(OdiiApi.CREDIT, content.credit)
    }

    @Test fun singleItemObjectAndEmptyItemsAreHandled() {
        assertEquals(1, OdiiParser.parse(wrap("""{"item":$story}""")).contents.size)
        assertTrue(OdiiParser.parse(wrap("\"\"")).contents.isEmpty())
    }

    @Test fun nonHttpsOrMissingAudioIsSkippedAndCounted() {
        val http = story.replace("https://example.org/a.mp3", "http://example.org/a.mp3")
        val noAudio = story.replace("\"audioUrl\":\"https://example.org/a.mp3\",", "")
        val result = OdiiParser.parse(wrap("""{"item":[$http,$noAudio]}"""))
        assertTrue(result.contents.isEmpty())
        assertEquals(2, result.skippedNoAudio)
    }

    @Test fun gatewayXmlErrorIsReported() {
        val xml = "<OpenAPI_ServiceResponse><cmmMsgHeader><returnAuthMsg>SERVICE_KEY_IS_NOT_REGISTERED_ERROR</returnAuthMsg></cmmMsgHeader></OpenAPI_ServiceResponse>"
        val error = runCatching { OdiiParser.parse(xml) }.exceptionOrNull()
        assertEquals("SERVICE_KEY_IS_NOT_REGISTERED_ERROR", error?.message)
        assertNull(OdiiParser.gatewayError("{}"))
    }
}
