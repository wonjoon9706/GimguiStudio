package com.example.pleosaudioguide

import org.junit.Assert.*
import org.junit.Test

class TripDetectionTest {
    private val park = VehicleReading(4, 0f, true)
    private val moving = VehicleReading(8, 5f, false)

    @Test fun startupParkAndTrafficLightNeverEndTrip() {
        val d = TripDetection()
        assertEquals(TripDetection.Action.NONE, d.update(park, 0))
        assertEquals(TripDetection.Action.NONE, d.update(park, 9000))
        assertEquals(TripDetection.Action.START, d.update(moving, 10_000))
        assertEquals(TripDetection.Action.NONE, d.update(VehicleReading(8, 0f, false), 11_000))
        assertEquals(TripDetection.Action.NONE, d.update(VehicleReading(8, 0f, false), 90_000))
        assertTrue(d.active)
    }
    @Test fun parkedMustStaySafeForFiveSecondsAndOnlyEndsOnce() {
        val d = TripDetection()
        d.update(moving, 0)
        assertEquals(TripDetection.Action.NONE, d.update(park, 1000))
        assertEquals(TripDetection.Action.NONE, d.update(park, 5999))
        assertEquals(TripDetection.Action.END, d.update(park, 6000))
        assertEquals(TripDetection.Action.NONE, d.update(park, 20_000))
        assertEquals(TripDetection.Action.START, d.update(moving, 21_000))
    }
    @Test fun unknownRestrictedAndReverseResetParkingConfirmation() {
        val d = TripDetection(true)
        d.update(park, 0)
        for (r in listOf(VehicleReading(), park.copy(uxAllowed = false),
            park.copy(gear = 2), park.copy(speedMps = Float.NaN), park.copy(error = "disconnected"))) {
            assertFalse(r.parked)
            assertEquals(TripDetection.Action.NONE, d.update(r, 4999))
            assertEquals(TripDetection.Action.NONE, d.update(park, 5000))
        }
        assertEquals(TripDetection.Action.END, d.update(park, 10_000))
    }
}
