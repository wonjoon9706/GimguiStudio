package com.example.pleosaudioguide

import android.car.Car
import android.car.VehiclePropertyIds
import android.car.drivingstate.CarUxRestrictionsManager
import android.car.hardware.CarPropertyValue
import android.car.hardware.property.CarPropertyManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock

/** 실제 AAOS 신호만 사용. 권한 없음/통신 실패는 주차로 취급하지 않는다. */
class VehicleTripMonitor(context: Context, private val emit: (VehicleReading, Long) -> Unit) {
    private val thread = HandlerThread("VehicleTripMonitor").apply { start() }
    private val handler = Handler(thread.looper)
    @Volatile private var closed = false
    private var car: Car? = null
    private var properties: CarPropertyManager? = null
    private var ux: CarUxRestrictionsManager? = null
    private val callback = object : CarPropertyManager.CarPropertyEventCallback {
        override fun onChangeEvent(value: CarPropertyValue<*>) { poll() }
        override fun onErrorEvent(propId: Int, areaId: Int) {
            emit(VehicleReading(error = "차량 상태 수신 오류"), SystemClock.elapsedRealtime())
        }
    }
    private val tick = object : Runnable {
        override fun run() {
            if (closed) return
            poll()
            handler.postDelayed(this, 1000)
        }
    }

    init {
        if (!context.packageManager.hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE)) {
            emit(VehicleReading(error = "차량 상태를 지원하지 않는 기기"), SystemClock.elapsedRealtime())
        } else try {
            car = Car.createCar(context, handler, Car.CAR_WAIT_TIMEOUT_DO_NOT_WAIT) { connected, ready ->
                handler.post {
                    if (closed) return@post
                    unregister()
                    if (ready) {
                        try {
                            properties = connected.getCarManager(Car.PROPERTY_SERVICE) as CarPropertyManager
                            ux = connected.getCarManager(Car.CAR_UX_RESTRICTION_SERVICE) as CarUxRestrictionsManager
                            ux?.registerListener { handler.post { poll() } }
                            registerProperties()
                            poll()
                        } catch (_: Exception) {
                            emit(VehicleReading(error = "차량 상태 연결/권한 확인 필요"), SystemClock.elapsedRealtime())
                        }
                    } else emit(VehicleReading(error = "차량 서비스 연결 끊김"), SystemClock.elapsedRealtime())
                }
            }
        } catch (_: Exception) {
            emit(VehicleReading(error = "차량 서비스 연결 실패"), SystemClock.elapsedRealtime())
        }
        handler.post(tick)
    }

    private var registered = false
    private fun registerProperties() {
        if (registered) return
        val p = properties ?: return
        // 속도 권한 승인 후 주기 조회에서 다시 등록할 수 있다.
        val gear = p.registerCallback(callback, VehiclePropertyIds.GEAR_SELECTION, CarPropertyManager.SENSOR_RATE_ONCHANGE)
        val speed = p.registerCallback(callback, VehiclePropertyIds.PERF_VEHICLE_SPEED, CarPropertyManager.SENSOR_RATE_NORMAL)
        registered = gear && speed
    }

    private fun poll() {
        if (closed) return
        val p = properties ?: return
        var reading = VehicleReading()
        try {
            // 속도 권한이 없어도 UX 신호는 먼저 확인: 주차 후에만 권한 UI를 요청한다.
            val restrictions = ux?.currentCarUxRestrictions ?: throw IllegalStateException("Unavailable UX")
            reading = VehicleReading(
                uxAllowed = !restrictions.isRequiresDistractionOptimization && restrictions.activeRestrictions == 0,
                maxStringLength = restrictions.maxRestrictedStringLength)
            registerProperties()
            val gear = p.getProperty(Int::class.javaObjectType, VehiclePropertyIds.GEAR_SELECTION, 0)
            val speed = p.getProperty(Float::class.javaObjectType, VehiclePropertyIds.PERF_VEHICLE_SPEED, 0)
            if (gear.status != CarPropertyValue.STATUS_AVAILABLE || speed.status != CarPropertyValue.STATUS_AVAILABLE)
                throw IllegalStateException("Unavailable")
            emit(reading.copy(gear = gear.value, speedMps = speed.value), SystemClock.elapsedRealtime())
        } catch (_: Exception) {
            emit(reading.copy(error = "기어·속도 권한 또는 차량 신호 확인 필요"), SystemClock.elapsedRealtime())
        }
    }

    private fun unregister() {
        runCatching { properties?.unregisterCallback(callback) }
        runCatching { ux?.unregisterListener() }
        registered = false
        properties = null
        ux = null
    }
    fun close() {
        closed = true
        handler.post {
            handler.removeCallbacksAndMessages(null)
            unregister()
            car?.disconnect()
            thread.quitSafely()
        }
    }
}
