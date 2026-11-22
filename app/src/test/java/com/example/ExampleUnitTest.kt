package com.example

import com.example.location.SpeedSmoother
import com.example.model.DistanceUnit
import com.example.model.RoomCodeGenerator
import com.example.model.UnitFormatter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {

    @Test
    fun roomCodeGenerator_producesValidSixCharCodes() {
        repeat(20) {
            val code = RoomCodeGenerator.generate()
            assertEquals(6, code.length)
            assertTrue(RoomCodeGenerator.isValid(code))
            assertFalse(code.contains('0'))
            assertFalse(code.contains('O'))
            assertFalse(code.contains('1'))
            assertFalse(code.contains('I'))
        }
    }

    @Test
    fun unitFormatter_convertsKmAndMilesIndependently() {
        // 5000 meters = 5.00 km = 3.11 mi
        assertEquals("5.00", UnitFormatter.distanceValue(5000.0, DistanceUnit.KILOMETERS))
        assertEquals("3.11", UnitFormatter.distanceValue(5000.0, DistanceUnit.MILES))

        // 3.0 m/s = 10.8 km/h = 6.7 mph
        assertEquals("10.8", UnitFormatter.speedValue(3.0, DistanceUnit.KILOMETERS))
        assertEquals("6.7", UnitFormatter.speedValue(3.0, DistanceUnit.MILES))

        // 5 km in 25:00 (1,500,000 ms) -> 5:00 /km
        assertEquals("5:00 /km", UnitFormatter.paceFormatted(5000.0, 1_500_000L, DistanceUnit.KILOMETERS))
        assertEquals("25:00", UnitFormatter.durationFormatted(1_500_000L))
    }

    @Test
    fun speedSmoother_filtersNoiseAndSpikes() {
        val smoother = SpeedSmoother()
        // Stationary drift below threshold -> 0.0
        assertEquals(0.0, smoother.addSample(1000L, 0.2), 0.001)

        // Running speed samples
        val s1 = smoother.addSample(2000L, 3.0)
        assertTrue(s1 > 0.0)
        val s2 = smoother.addSample(3000L, 3.2)
        // Unrealistic GPS teleport spike (50 m/s) should be ignored
        val s3 = smoother.addSample(3500L, 50.0)
        assertEquals(s2, s3, 0.25)
    }
}
