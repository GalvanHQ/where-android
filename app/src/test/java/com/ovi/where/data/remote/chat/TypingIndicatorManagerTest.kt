package com.ovi.where.data.remote.chat

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest

@OptIn(ExperimentalCoroutinesApi::class)
class TypingIndicatorManagerTest : FunSpec({

    lateinit var testScope: TestScope
    lateinit var emissions: MutableList<Boolean>
    lateinit var manager: TypingIndicatorManager
    var currentTime: Long = 0L

    beforeEach {
        val dispatcher = StandardTestDispatcher()
        testScope = TestScope(dispatcher)
        emissions = mutableListOf()
        currentTime = 0L
        manager = TypingIndicatorManager(
            scope = testScope,
            sendTyping = { isTyping -> emissions.add(isTyping) },
            clock = { currentTime }
        )
    }

    test("first keystroke emits typing true immediately") {
        runTest {
            val dispatcher = StandardTestDispatcher(testScheduler)
            val scope = TestScope(dispatcher)
            val emitted = mutableListOf<Boolean>()
            var time = 0L
            val mgr = TypingIndicatorManager(
                scope = scope,
                sendTyping = { emitted.add(it) },
                clock = { time }
            )

            mgr.onKeystroke()
            scope.advanceTimeBy(1) // let the coroutine run

            emitted shouldBe listOf(true)
        }
    }

    test("multiple keystrokes within 300ms window emit only one typing true") {
        runTest {
            val dispatcher = StandardTestDispatcher(testScheduler)
            val scope = TestScope(dispatcher)
            val emitted = mutableListOf<Boolean>()
            var time = 0L
            val mgr = TypingIndicatorManager(
                scope = scope,
                sendTyping = { emitted.add(it) },
                clock = { time }
            )

            mgr.onKeystroke()
            scope.advanceTimeBy(1)

            time = 100L
            mgr.onKeystroke()
            scope.advanceTimeBy(1)

            time = 200L
            mgr.onKeystroke()
            scope.advanceTimeBy(1)

            // Only one typing(true) should have been emitted
            emitted.filter { it } shouldBe listOf(true)
        }
    }

    test("keystroke after 300ms window emits another typing true") {
        runTest {
            val dispatcher = StandardTestDispatcher(testScheduler)
            val scope = TestScope(dispatcher)
            val emitted = mutableListOf<Boolean>()
            var time = 0L
            val mgr = TypingIndicatorManager(
                scope = scope,
                sendTyping = { emitted.add(it) },
                clock = { time }
            )

            mgr.onKeystroke()
            scope.advanceTimeBy(1)

            time = 300L
            mgr.onKeystroke()
            scope.advanceTimeBy(1)

            emitted.filter { it } shouldBe listOf(true, true)
        }
    }

    test("stop-typing emitted after 3 seconds of no keystrokes") {
        runTest {
            val dispatcher = StandardTestDispatcher(testScheduler)
            val scope = TestScope(dispatcher)
            val emitted = mutableListOf<Boolean>()
            var time = 0L
            val mgr = TypingIndicatorManager(
                scope = scope,
                sendTyping = { emitted.add(it) },
                clock = { time }
            )

            mgr.onKeystroke()
            scope.advanceTimeBy(1)

            // Advance past the 3-second stop-typing delay
            scope.advanceTimeBy(3000)

            emitted shouldBe listOf(true, false)
        }
    }

    test("keystroke resets the 3-second stop-typing timer") {
        runTest {
            val dispatcher = StandardTestDispatcher(testScheduler)
            val scope = TestScope(dispatcher)
            val emitted = mutableListOf<Boolean>()
            var time = 0L
            val mgr = TypingIndicatorManager(
                scope = scope,
                sendTyping = { emitted.add(it) },
                clock = { time }
            )

            mgr.onKeystroke()
            scope.advanceTimeBy(1)

            // After 2 seconds, type again (within throttle window, so no new typing(true))
            scope.advanceTimeBy(2000)
            time = 2000L
            mgr.onKeystroke()
            scope.advanceTimeBy(1)

            // After another 2 seconds (total 4s from start, but only 2s from last keystroke)
            // stop-typing should NOT have fired yet
            scope.advanceTimeBy(2000)
            emitted.filter { !it } shouldBe emptyList()

            // After another 1 second (3s from last keystroke), stop-typing fires
            scope.advanceTimeBy(1000)
            emitted shouldBe listOf(true, true, false)
        }
    }

    test("onMessageSentOrInputCleared emits stop-typing immediately") {
        runTest {
            val dispatcher = StandardTestDispatcher(testScheduler)
            val scope = TestScope(dispatcher)
            val emitted = mutableListOf<Boolean>()
            var time = 0L
            val mgr = TypingIndicatorManager(
                scope = scope,
                sendTyping = { emitted.add(it) },
                clock = { time }
            )

            mgr.onKeystroke()
            scope.advanceTimeBy(1)

            mgr.onMessageSentOrInputCleared()
            scope.advanceTimeBy(1)

            emitted shouldBe listOf(true, false)
        }
    }

    test("onMessageSentOrInputCleared cancels pending stop-typing timer") {
        runTest {
            val dispatcher = StandardTestDispatcher(testScheduler)
            val scope = TestScope(dispatcher)
            val emitted = mutableListOf<Boolean>()
            var time = 0L
            val mgr = TypingIndicatorManager(
                scope = scope,
                sendTyping = { emitted.add(it) },
                clock = { time }
            )

            mgr.onKeystroke()
            scope.advanceTimeBy(1)

            mgr.onMessageSentOrInputCleared()
            scope.advanceTimeBy(1)

            // Advance past the original 3s timer - should NOT emit another false
            scope.advanceTimeBy(5000)

            emitted shouldBe listOf(true, false)
        }
    }

    test("onMessageSentOrInputCleared does nothing if not currently typing") {
        runTest {
            val dispatcher = StandardTestDispatcher(testScheduler)
            val scope = TestScope(dispatcher)
            val emitted = mutableListOf<Boolean>()
            var time = 0L
            val mgr = TypingIndicatorManager(
                scope = scope,
                sendTyping = { emitted.add(it) },
                clock = { time }
            )

            mgr.onMessageSentOrInputCleared()
            scope.advanceTimeBy(1)

            emitted shouldBe emptyList()
        }
    }

    test("reset clears all state and cancels timers") {
        runTest {
            val dispatcher = StandardTestDispatcher(testScheduler)
            val scope = TestScope(dispatcher)
            val emitted = mutableListOf<Boolean>()
            var time = 0L
            val mgr = TypingIndicatorManager(
                scope = scope,
                sendTyping = { emitted.add(it) },
                clock = { time }
            )

            mgr.onKeystroke()
            scope.advanceTimeBy(1)

            mgr.reset()

            // Advance past the 3s timer - should NOT emit stop-typing
            scope.advanceTimeBy(5000)

            emitted shouldBe listOf(true)

            // After reset, next keystroke should emit typing(true) again immediately
            time = 5000L
            mgr.onKeystroke()
            scope.advanceTimeBy(1)

            emitted shouldBe listOf(true, true)
        }
    }

    test("rapid keystrokes across multiple throttle windows emit correct number of typing events") {
        runTest {
            val dispatcher = StandardTestDispatcher(testScheduler)
            val scope = TestScope(dispatcher)
            val emitted = mutableListOf<Boolean>()
            var time = 0L
            val mgr = TypingIndicatorManager(
                scope = scope,
                sendTyping = { emitted.add(it) },
                clock = { time }
            )

            // Window 1: 0-299ms
            mgr.onKeystroke() // time=0, emits
            scope.advanceTimeBy(1)
            time = 100L
            mgr.onKeystroke() // time=100, suppressed
            scope.advanceTimeBy(1)

            // Window 2: 300-599ms
            time = 300L
            mgr.onKeystroke() // time=300, emits
            scope.advanceTimeBy(1)
            time = 400L
            mgr.onKeystroke() // time=400, suppressed
            scope.advanceTimeBy(1)

            // Window 3: 600-899ms
            time = 600L
            mgr.onKeystroke() // time=600, emits
            scope.advanceTimeBy(1)

            emitted.filter { it } shouldBe listOf(true, true, true)
        }
    }
})
