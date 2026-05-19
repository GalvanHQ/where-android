package com.ovi.where.domain.model

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

/**
 * Unit tests for [LocationUpdateFrame] validation logic.
 *
 * Tests specific examples and edge cases for the isValid() function.
 */
class LocationUpdateFrameTest : StringSpec({

    "valid frame passes validation" {
        val frame = LocationUpdateFrame(
            userId = "user123",
            latitude = 40.7128,
            longitude = -74.0060,
            accuracy = 10f,
            speed = 1.5f,
            bearing = 90f,
            timestamp = System.currentTimeMillis()
        )
        frame.isValid() shouldBe true
    }

    "frame with blank userId fails validation" {
        val frame = LocationUpdateFrame(
            userId = "",
            latitude = 40.7128,
            longitude = -74.0060,
            accuracy = 10f,
            speed = 1.5f,
            bearing = 90f,
            timestamp = System.currentTimeMillis()
        )
        frame.isValid() shouldBe false
    }

    "frame with whitespace-only userId fails validation" {
        val frame = LocationUpdateFrame(
            userId = "   ",
            latitude = 40.7128,
            longitude = -74.0060,
            accuracy = 10f,
            speed = 1.5f,
            bearing = 90f,
            timestamp = System.currentTimeMillis()
        )
        frame.isValid() shouldBe false
    }

    "frame with latitude below -90 fails validation" {
        val frame = LocationUpdateFrame(
            userId = "user123",
            latitude = -90.1,
            longitude = -74.0060,
            accuracy = 10f,
            speed = 1.5f,
            bearing = 90f,
            timestamp = System.currentTimeMillis()
        )
        frame.isValid() shouldBe false
    }

    "frame with latitude above 90 fails validation" {
        val frame = LocationUpdateFrame(
            userId = "user123",
            latitude = 90.1,
            longitude = -74.0060,
            accuracy = 10f,
            speed = 1.5f,
            bearing = 90f,
            timestamp = System.currentTimeMillis()
        )
        frame.isValid() shouldBe false
    }

    "frame with longitude below -180 fails validation" {
        val frame = LocationUpdateFrame(
            userId = "user123",
            latitude = 40.7128,
            longitude = -180.1,
            accuracy = 10f,
            speed = 1.5f,
            bearing = 90f,
            timestamp = System.currentTimeMillis()
        )
        frame.isValid() shouldBe false
    }

    "frame with longitude above 180 fails validation" {
        val frame = LocationUpdateFrame(
            userId = "user123",
            latitude = 40.7128,
            longitude = 180.1,
            accuracy = 10f,
            speed = 1.5f,
            bearing = 90f,
            timestamp = System.currentTimeMillis()
        )
        frame.isValid() shouldBe false
    }

    "frame with negative accuracy fails validation" {
        val frame = LocationUpdateFrame(
            userId = "user123",
            latitude = 40.7128,
            longitude = -74.0060,
            accuracy = -1f,
            speed = 1.5f,
            bearing = 90f,
            timestamp = System.currentTimeMillis()
        )
        frame.isValid() shouldBe false
    }

    "frame with negative speed fails validation" {
        val frame = LocationUpdateFrame(
            userId = "user123",
            latitude = 40.7128,
            longitude = -74.0060,
            accuracy = 10f,
            speed = -0.1f,
            bearing = 90f,
            timestamp = System.currentTimeMillis()
        )
        frame.isValid() shouldBe false
    }

    "frame with bearing below 0 fails validation" {
        val frame = LocationUpdateFrame(
            userId = "user123",
            latitude = 40.7128,
            longitude = -74.0060,
            accuracy = 10f,
            speed = 1.5f,
            bearing = -1f,
            timestamp = System.currentTimeMillis()
        )
        frame.isValid() shouldBe false
    }

    "frame with bearing above 360 fails validation" {
        val frame = LocationUpdateFrame(
            userId = "user123",
            latitude = 40.7128,
            longitude = -74.0060,
            accuracy = 10f,
            speed = 1.5f,
            bearing = 360.1f,
            timestamp = System.currentTimeMillis()
        )
        frame.isValid() shouldBe false
    }

    "frame with timestamp 0 fails validation" {
        val frame = LocationUpdateFrame(
            userId = "user123",
            latitude = 40.7128,
            longitude = -74.0060,
            accuracy = 10f,
            speed = 1.5f,
            bearing = 90f,
            timestamp = 0L
        )
        frame.isValid() shouldBe false
    }

    "frame with negative timestamp fails validation" {
        val frame = LocationUpdateFrame(
            userId = "user123",
            latitude = 40.7128,
            longitude = -74.0060,
            accuracy = 10f,
            speed = 1.5f,
            bearing = 90f,
            timestamp = -1L
        )
        frame.isValid() shouldBe false
    }

    // Boundary value tests
    "frame at latitude boundary -90 is valid" {
        val frame = LocationUpdateFrame(
            userId = "user123",
            latitude = -90.0,
            longitude = 0.0,
            accuracy = 0f,
            speed = 0f,
            bearing = 0f,
            timestamp = 1L
        )
        frame.isValid() shouldBe true
    }

    "frame at latitude boundary 90 is valid" {
        val frame = LocationUpdateFrame(
            userId = "user123",
            latitude = 90.0,
            longitude = 0.0,
            accuracy = 0f,
            speed = 0f,
            bearing = 0f,
            timestamp = 1L
        )
        frame.isValid() shouldBe true
    }

    "frame at longitude boundary -180 is valid" {
        val frame = LocationUpdateFrame(
            userId = "user123",
            latitude = 0.0,
            longitude = -180.0,
            accuracy = 0f,
            speed = 0f,
            bearing = 0f,
            timestamp = 1L
        )
        frame.isValid() shouldBe true
    }

    "frame at longitude boundary 180 is valid" {
        val frame = LocationUpdateFrame(
            userId = "user123",
            latitude = 0.0,
            longitude = 180.0,
            accuracy = 0f,
            speed = 0f,
            bearing = 0f,
            timestamp = 1L
        )
        frame.isValid() shouldBe true
    }

    "frame at bearing boundary 0 is valid" {
        val frame = LocationUpdateFrame(
            userId = "user123",
            latitude = 0.0,
            longitude = 0.0,
            accuracy = 0f,
            speed = 0f,
            bearing = 0f,
            timestamp = 1L
        )
        frame.isValid() shouldBe true
    }

    "frame at bearing boundary 360 is valid" {
        val frame = LocationUpdateFrame(
            userId = "user123",
            latitude = 0.0,
            longitude = 0.0,
            accuracy = 0f,
            speed = 0f,
            bearing = 360f,
            timestamp = 1L
        )
        frame.isValid() shouldBe true
    }

    "frame with zero accuracy and speed is valid" {
        val frame = LocationUpdateFrame(
            userId = "user123",
            latitude = 0.0,
            longitude = 0.0,
            accuracy = 0f,
            speed = 0f,
            bearing = 180f,
            timestamp = 1L
        )
        frame.isValid() shouldBe true
    }
})
