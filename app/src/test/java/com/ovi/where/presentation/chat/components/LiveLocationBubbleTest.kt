package com.ovi.where.presentation.chat.components

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

/**
 * Unit tests for LiveLocationBubble helper functions.
 *
 * Validates: Requirements 2.3, 2.6
 */
class LiveLocationBubbleTest : StringSpec({

    "formatDuration returns Xm for durations under 60 minutes" {
        formatDuration(1) shouldBe "1m"
        formatDuration(15) shouldBe "15m"
        formatDuration(30) shouldBe "30m"
        formatDuration(59) shouldBe "59m"
    }

    "formatDuration returns Xh Ym for durations of 60 minutes or more" {
        formatDuration(60) shouldBe "1h"
        formatDuration(61) shouldBe "1h 1m"
        formatDuration(90) shouldBe "1h 30m"
        formatDuration(120) shouldBe "2h"
        formatDuration(150) shouldBe "2h 30m"
        formatDuration(480) shouldBe "8h"
    }

    "formatDuration handles exact hour boundaries without trailing 0m" {
        formatDuration(60) shouldBe "1h"
        formatDuration(120) shouldBe "2h"
        formatDuration(180) shouldBe "3h"
    }

    "formatDuration handles edge case of 0 minutes" {
        formatDuration(0) shouldBe "0m"
    }
})
