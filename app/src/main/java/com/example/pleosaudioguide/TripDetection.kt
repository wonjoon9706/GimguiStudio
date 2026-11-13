package com.example.pleosaudioguide

import kotlin.math.abs

data class VehicleReading(
    val gear: Int? = null,
    val speedMps: Float? = null,
    val uxAllowed: Boolean = false,
    val error: String? = null,
    val maxStringLength: Int = 40
) {
    val valid: Boolean get() = gear != null && speedMps?.isFinite() == true && error == null
    val parked: Boolean get() = valid && gear == PARK && abs(speedMps!!) <= 0.01f && uxAllowed
    val moving: Boolean get() = valid && abs(speedMps!!) > 0.1f
    companion object { const val PARK = 4 }
}

/** P 전환 직후 흔들림을 피한다. 실제 주행 관측 없이 세션을 시작/종료하지 않는다. */
class TripDetection(initiallyActive: Boolean = false) {
    var active = initiallyActive
        private set
    private var parkedSince: Long? = null
    enum class Action { NONE, START, END }
    fun update(reading: VehicleReading, elapsedMs: Long): Action {
        if (!active && reading.moving) {
            active = true
            parkedSince = null
            return Action.START
        }
        if (!active || !reading.parked) { parkedSince = null; return Action.NONE }
        val since = parkedSince ?: elapsedMs.also { parkedSince = it }
        if (elapsedMs - since < 5000) return Action.NONE
        active = false
        parkedSince = null
        return Action.END
    }
}

data class TripHistoryState(
    val activeTripId: String? = null,
    val endedTrip: TripEntity? = null,
    val records: List<ListeningRecord> = emptyList(),
    val parked: Boolean = false,
    val error: String? = null,
    val browsingAllowed: Boolean = false,
    val maxTitleLength: Int = 40,
    val permissionPromptAllowed: Boolean = false
) {
    // DB의 현재 운행 결과만 사용. 원고/재생 방식별 중복 행을 콘텐츠 수로 세지 않는다.
    val listenedContentCount: Int get() = records.filter {
        it.listenedMs > 0 || it.mode == "tts"
    }.distinctBy { it.contentId }.size
    val completedContentCount: Int get() = records.filter { it.completionEligible }
        .distinctBy { it.contentId }.size
    val quizContentCount: Int get() = records.filter { it.canUseFullScriptForQuiz }
        .distinctBy { it.contentId }.size
    val showEndScreen: Boolean get() = parked && activeTripId == null && endedTrip != null && !endedTrip.dismissed
}
