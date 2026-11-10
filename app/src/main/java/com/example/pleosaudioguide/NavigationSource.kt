package com.example.pleosaudioguide

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import ai.pleos.playground.navi.constants.NaviErrorCode
import ai.pleos.playground.navi.data.DestinationInfo
import ai.pleos.playground.navi.data.DestinationArrivedInfo
import ai.pleos.playground.navi.helper.NaviHelper
import ai.pleos.playground.navi.helper.listener.NaviHelperEventListener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class NavigationState(
    val status: String = "실제 내비 연결 중",
    val connected: Boolean = false,
    val destination: GuideDestination? = null
)

/** SDK 타입은 이 경계 안에서만 사용한다. 화면과 추천 규칙은 앱 모델만 사용한다. */
class NavigationSource(context: Context) {
    private val main = Handler(Looper.getMainLooper())
    private var closed = false
    private val helper = NaviHelper(context.applicationContext)
    private val mutableState = MutableStateFlow(NavigationState())
    val state = mutableState.asStateFlow()
    private val responseTimeout = Runnable {
        mutableState.value = mutableState.value.copy(status = "목적지 응답 없음 · 내비에서 경로를 설정하고 다시 조회해주세요.", destination = null)
    }
    private val listener = object : NaviHelperEventListener {
        override fun onDestinationInfo(destinationInfo: DestinationInfo) = dispatch {
            main.removeCallbacks(responseTimeout)
            Log.i("AudioGuideNavi", "DestinationInfo: $destinationInfo")
            val destination = destinationInfo.takeIf { it.name.isNotBlank() || it.address.isNotBlank() }?.let {
                GuideDestination(it.name, it.address, it.latitude, it.longitude, it.remainTime.toString())
            }
            mutableState.value = NavigationState(
                if (destination == null) "설정된 목적지 없음" else "실제 내비 목적지 수신",
                connected = true, destination = destination
            )
        }
        override fun onDestinationChanged() = dispatch {
            mutableState.value = mutableState.value.copy(destination = null)
            refresh()
        }
        override fun onRoutingCompleted() = dispatch { refresh() }
        override fun onRouteCancelled() = dispatch {
            main.removeCallbacks(responseTimeout)
            mutableState.value = mutableState.value.copy(status = "경로 취소 · 목적지 없음", destination = null)
        }
        override fun onDestinationArrived(destinationArrivedInfo: DestinationArrivedInfo) = dispatch {
            main.removeCallbacks(responseTimeout)
            mutableState.value = mutableState.value.copy(status = "목적지 도착 · 정차 상태와는 별개입니다.", destination = null)
        }
        override fun onError(errorCode: NaviErrorCode) = dispatch {
            main.removeCallbacks(responseTimeout)
            mutableState.value = mutableState.value.copy(status = "내비 오류: $errorCode", destination = null)
        }
    }

    init {
        try {
            helper.initialize(onConnected = {
                dispatch {
                    helper.addListener(listener)
                    mutableState.value = NavigationState("실제 내비 연결됨", connected = true)
                    refresh()
                }
            }, onFailed = { error ->
                dispatch { mutableState.value = NavigationState("내비 연결 실패: ${error.message}") }
            })
        } catch (error: Exception) {
            mutableState.value = NavigationState("내비 초기화 실패: ${error.message}")
        }
    }

    fun refresh() {
        if (closed || !mutableState.value.connected) return
        main.removeCallbacks(responseTimeout)
        mutableState.value = mutableState.value.copy(status = "실제 내비 목적지 조회 중", destination = null)
        main.postDelayed(responseTimeout, 10000)
        try {
            helper.getDestinationInfo()
        } catch (error: Exception) {
            main.removeCallbacks(responseTimeout)
            mutableState.value = mutableState.value.copy(status = "목적지 조회 실패: ${error.message}", destination = null)
        }
    }

    private fun dispatch(action: () -> Unit) { main.post { if (!closed) action() } }

    fun close() {
        closed = true
        main.removeCallbacksAndMessages(null)
        helper.removeListener(listener)
        helper.release()
    }
}
