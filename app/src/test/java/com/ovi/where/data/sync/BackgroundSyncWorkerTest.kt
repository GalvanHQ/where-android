package com.ovi.where.data.sync

import android.content.Context
import androidx.work.ListenableWorker
import androidx.work.WorkerParameters
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GetTokenResult
import com.ovi.where.data.local.dao.ConversationDao
import com.ovi.where.data.local.entity.ConversationEntity
import com.ovi.where.data.remote.chat.ChatApiClient
import com.ovi.where.data.remote.chat.ChatApiService
import com.ovi.where.data.remote.chat.UnreadCountDto
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import kotlinx.coroutines.test.runTest
import okhttp3.ResponseBody.Companion.toResponseBody
import retrofit2.HttpException
import retrofit2.Response

/**
 * Unit tests for [BackgroundSyncWorker].
 *
 * Validates:
 * - Successful sync updates Room with new unread counts (Req 22.2)
 * - Auth error (401/403) returns failure and cancels schedule (Req 22.7)
 * - Network failure retries up to 5 attempts (Req 22.3)
 * - Unauthenticated user returns failure (Req 22.7)
 */
class BackgroundSyncWorkerTest : StringSpec({

    lateinit var context: Context
    lateinit var workerParams: WorkerParameters
    lateinit var conversationDao: ConversationDao
    lateinit var firebaseAuth: FirebaseAuth
    lateinit var firebaseUser: FirebaseUser
    lateinit var apiService: ChatApiService

    beforeEach {
        context = mockk(relaxed = true)
        workerParams = mockk(relaxed = true)
        conversationDao = mockk(relaxed = true)
        firebaseAuth = mockk(relaxed = true)
        firebaseUser = mockk(relaxed = true)
        apiService = mockk(relaxed = true)

        every { firebaseAuth.currentUser } returns firebaseUser
        every { firebaseUser.uid } returns "user123"

        val tokenResult = mockk<GetTokenResult>()
        every { tokenResult.token } returns "test-token"
        val tokenTask = mockk<com.google.android.gms.tasks.Task<GetTokenResult>>(relaxed = true)
        every { tokenTask.isComplete } returns true
        every { tokenTask.isCanceled } returns false
        every { tokenTask.exception } returns null
        every { tokenTask.result } returns tokenResult
        every { firebaseUser.getIdToken(any()) } returns tokenTask

        mockkObject(ChatApiClient)
        every { ChatApiClient.apiService } returns apiService

        // Default: no existing conversations
        coEvery { conversationDao.getAll() } returns emptyList()
    }

    afterEach {
        unmockkObject(ChatApiClient)
    }

    fun createWorker(runAttemptCount: Int = 0): BackgroundSyncWorker {
        every { workerParams.runAttemptCount } returns runAttemptCount
        return BackgroundSyncWorker(context, workerParams, conversationDao, firebaseAuth)
    }

    "successful sync updates Room and returns success" {
        runTest {
            val unreadCounts = listOf(
                UnreadCountDto("conv1", 3),
                UnreadCountDto("conv2", 1)
            )
            coEvery { apiService.getUnreadCounts(any()) } returns unreadCounts

            val worker = createWorker()
            val result = worker.doWork()

            result shouldBe ListenableWorker.Result.success()
            coVerify { conversationDao.updateUnreadCount("conv1", 3) }
            coVerify { conversationDao.updateUnreadCount("conv2", 1) }
        }
    }

    "unauthenticated user returns failure" {
        runTest {
            every { firebaseAuth.currentUser } returns null

            val worker = createWorker()
            val result = worker.doWork()

            result shouldBe ListenableWorker.Result.failure()
        }
    }

    "auth error 401 returns failure without retry" {
        runTest {
            val response = Response.error<Any>(401, "Unauthorized".toResponseBody())
            coEvery { apiService.getUnreadCounts(any()) } throws HttpException(response)

            val worker = createWorker()
            val result = worker.doWork()

            result shouldBe ListenableWorker.Result.failure()
        }
    }

    "auth error 403 returns failure without retry" {
        runTest {
            val response = Response.error<Any>(403, "Forbidden".toResponseBody())
            coEvery { apiService.getUnreadCounts(any()) } throws HttpException(response)

            val worker = createWorker()
            val result = worker.doWork()

            result shouldBe ListenableWorker.Result.failure()
        }
    }

    "network failure retries when under max attempts" {
        runTest {
            coEvery { apiService.getUnreadCounts(any()) } throws java.io.IOException("Network error")

            val worker = createWorker(runAttemptCount = 2)
            val result = worker.doWork()

            result shouldBe ListenableWorker.Result.retry()
        }
    }

    "network failure returns failure after max attempts exhausted" {
        runTest {
            coEvery { apiService.getUnreadCounts(any()) } throws java.io.IOException("Network error")

            val worker = createWorker(runAttemptCount = 5)
            val result = worker.doWork()

            result shouldBe ListenableWorker.Result.failure()
        }
    }

    "server error 500 retries when under max attempts" {
        runTest {
            val response = Response.error<Any>(500, "Internal Server Error".toResponseBody())
            coEvery { apiService.getUnreadCounts(any()) } throws HttpException(response)

            val worker = createWorker(runAttemptCount = 0)
            val result = worker.doWork()

            result shouldBe ListenableWorker.Result.retry()
        }
    }

    "empty token returns failure" {
        runTest {
            val tokenResult = mockk<GetTokenResult>()
            every { tokenResult.token } returns null
            val tokenTask = mockk<com.google.android.gms.tasks.Task<GetTokenResult>>(relaxed = true)
            every { tokenTask.isComplete } returns true
            every { tokenTask.isCanceled } returns false
            every { tokenTask.exception } returns null
            every { tokenTask.result } returns tokenResult
            every { firebaseUser.getIdToken(any()) } returns tokenTask

            val worker = createWorker()
            val result = worker.doWork()

            result shouldBe ListenableWorker.Result.failure()
        }
    }
})
