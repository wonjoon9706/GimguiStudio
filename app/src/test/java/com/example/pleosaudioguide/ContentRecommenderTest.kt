package com.example.pleosaudioguide

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ContentRecommenderTest {
    private fun destination(name: String, address: String = "") =
        GuideDestination(name, address, 35.0, 129.0, "unknown")

    @Test fun noDestinationLeavesGeneralCatalogAvailable() {
        assertTrue(ContentRecommender.recommend(null).isEmpty())
        assertTrue(ContentCatalog.all.any { it.category == "일반 상식" })
        assertTrue(ContentCatalog.all.any { it.category == "어학 학습" })
    }

    @Test fun bulguksaMatchesItsStoryBeforeRegionalStory() {
        val result = ContentRecommender.recommend(destination("불국사", "경주시"))
        assertEquals(listOf("bulguksa", "gyeongju"), result.map { it.id })
    }

    @Test fun unknownPlaceDoesNotRecommendUnrelatedContent() {
        assertTrue(ContentRecommender.recommend(destination("서울역", "서울")).isEmpty())
    }

    @Test fun regionalMatchWorksWithoutKnownPlaceName() {
        assertEquals(listOf("gyeongju"),
            ContentRecommender.recommend(destination("식당", "경주시 황남동")).map { it.id })
    }
}
