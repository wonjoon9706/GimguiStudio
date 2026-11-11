package com.example.pleosaudioguide

data class AudioContent(
    val id: String,
    val title: String,
    val category: String,
    val placeTags: List<String>,
    val script: String,
    val estimatedSeconds: Int,
    val audioUri: String? = null,
    // 외부 공공데이터 콘텐츠의 출처 표기와 대표 이미지. 직접 등록 시연 콘텐츠는 null.
    val credit: String? = null,
    val imageUri: String? = null
)

data class GuideDestination(
    val name: String,
    val address: String,
    val latitude: Double,
    val longitude: Double,
    // SDK 단위를 실제 내비 화면과 비교하기 전에는 추천 시간 계산에 사용하지 않는다.
    val remainTimeRaw: String
)

object ContentCatalog {
    // 직접 등록한 시연용 원고. 외부 AI나 내비 SDK에서 생성한 콘텐츠가 아니다.
    val all = listOf(
        AudioContent("bulguksa", "불국사와 돌에 담긴 이야기", "관광·역사", listOf("불국사"),
            "불국사는 경주 토함산 기슭에 자리한 사찰입니다. 통일신라의 불교 문화와 건축을 함께 살펴볼 수 있는 곳이지요. "
                + "대웅전 앞에는 서로 다른 모습의 석가탑과 다보탑이 나란히 서 있습니다. "
                + "단정한 석가탑과 화려한 다보탑의 모습을 비교해 보세요. "
                + "목적지에 도착하면 돌계단과 석축이 주변 풍경과 어떻게 어우러지는지도 천천히 살펴보세요.", 45),
        AudioContent("gyeongju", "경주에서 만나는 신라", "관광·역사", listOf("경주", "첨성대", "동궁", "월지", "대릉원"),
            "경주는 오랜 시간 신라의 수도였던 도시입니다. 도시 곳곳에서 왕릉과 사찰 터, 옛 건축의 흔적을 만날 수 있습니다. "
                + "첨성대와 대릉원, 동궁과 월지는 서로 다른 모습으로 신라의 시간을 보여 줍니다. "
                + "유적 하나를 따로 보는 것도 좋지만, 주변 지형과 다른 유적의 위치를 함께 살펴보면 도시의 이야기가 더 풍부해집니다.", 40),
        AudioContent("science", "하늘은 왜 파랄까요?", "일반 상식", emptyList(),
            "햇빛은 여러 색의 빛이 섞여 있습니다. 이 빛이 대기를 지날 때 공기 분자에 의해 여러 방향으로 흩어집니다. "
                + "이를 산란이라고 합니다. 파장이 짧은 푸른빛은 붉은빛보다 더 잘 산란됩니다. "
                + "그래서 낮에는 하늘 여러 방향에서 오는 푸른빛을 볼 수 있습니다. "
                + "해 질 무렵에는 빛이 대기를 더 길게 지나면서 붉은 노을이 두드러집니다.", 40),
        AudioContent("english", "여행 영어: 주문하기", "어학 학습", emptyList(),
            "오늘은 식당에서 주문할 때 쓰는 표현을 배워 봅니다. "
                + "메뉴를 부탁할 때는, Can I see the menu, please? "
                + "주문할 때는, I'd like this, please. "
                + "물을 부탁할 때는, Could I have some water, please? "
                + "정중한 표현을 짧게 익혀 두면 여행 중 도움이 됩니다.", 35),
        AudioContent("culture", "지도와 축척", "기타 교양", emptyList(),
            "지도는 넓은 공간을 작게 줄여 표현합니다. 실제 거리와 지도 위 거리의 비율을 축척이라고 합니다. "
                + "같은 크기의 지도라도 축척에 따라 보여 주는 범위와 자세함이 달라집니다. "
                + "여행을 계획할 때는 전체 이동 경로를 보는 지도와 목적지 주변을 자세히 보는 지도를 함께 활용해 보세요.", 30)
    )
}

object ContentRecommender {
    fun recommend(destination: GuideDestination?, contents: List<AudioContent> = ContentCatalog.all): List<AudioContent> {
        if (destination == null) return emptyList()
        val name = destination.name.lowercase().replace(" ", "")
        val address = destination.address.lowercase().replace(" ", "")
        return contents.map { content ->
            val score = content.placeTags.maxOfOrNull { tag ->
                val key = tag.lowercase().replace(" ", "")
                when {
                    name.contains(key) -> 2
                    address.contains(key) -> 1
                    else -> 0
                }
            } ?: 0
            content to score
        }.filter { it.second > 0 }.sortedByDescending { it.second }.map { it.first }
    }
}
