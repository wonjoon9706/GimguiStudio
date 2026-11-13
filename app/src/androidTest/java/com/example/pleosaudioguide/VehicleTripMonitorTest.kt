package com.example.pleosaudioguide

import android.content.pm.PackageManager
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

/** 실제 Pleos 차량 서비스의 읽기 경로만 확인한다. 기어/속도를 변경하지 않는다. */
class VehicleTripMonitorTest {
    @Test fun readsGearSpeedAndUxFromActualCarService() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        assertTrue(context.packageManager.hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE))
        // 설치된 앱에 일반 런타임 속도 권한을 승인한다. 숨겨진/특권 API 권한을 부여하지 않는다.
        instrumentation.uiAutomation.grantRuntimePermission(context.packageName, "android.car.permission.CAR_SPEED")
        val readings = LinkedBlockingQueue<VehicleReading>()
        lateinit var monitor: VehicleTripMonitor
        instrumentation.runOnMainSync { monitor = VehicleTripMonitor(context) { r, _ -> readings.offer(r) } }
        try {
            val limit = System.currentTimeMillis() + 15_000
            var valid: VehicleReading? = null
            var last: VehicleReading? = null
            while (valid == null && System.currentTimeMillis() < limit) {
                last = readings.poll(1, TimeUnit.SECONDS) ?: continue
                if (last.valid) valid = last
            }
            assertNotNull("실제 차량 신호를 읽지 못함: $last", valid)
            assertNotNull(valid!!.gear)
            assertTrue(valid.speedMps!!.isFinite())
        } finally { monitor.close() }
    }
}
