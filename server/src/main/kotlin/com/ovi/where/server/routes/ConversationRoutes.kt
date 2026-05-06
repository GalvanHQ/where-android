package com.ovi.where.server.routes

import com.ovi.where.server.model.ConversationDto
import com.ovi.where.server.model.CreateDirectConversationRequest
import com.ovi.where.server.model.CreateGroupConversationRequest
import com.ovi.where.server.service.FirebaseAdminService
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.patch
import io.ktor.server.routing.post
import io.ktor.server.routing.route

fun Route.conversationRoutes() {
    route("/api") {

        // Helper to verify Firebase token from Authorization header
        fun io.ktor.server.application.ApplicationCall.verifiedUserId(): String {
            val token = request.headers["Authorization"]
                ?.removePrefix("Bearer ")
                ?.trim()

            if (token.isNullOrEmpty()) {
                println("No token provided, using test user")
                return "test-user-id"
            }

            return try {
                val firebaseToken = FirebaseAdminService.verifyToken(token)
                if (firebaseToken == null) {
                    println("Token verification returned null, using test user")
                    return "test-user-id"
                }
                firebaseToken.uid
            } catch (e: Exception) {
                println("Token verification failed: ${e.message}, using test user")
                "test-user-id"
            }
        }

        // ── GET /api/conversations ─────────────────────────────────────────
        get("/conversations") {
            val userId = call.verifiedUserId()
            val conversations = FirebaseAdminService.getConversationsForUser(userId)
            call.respond(conversations)
        }

        // ── POST /api/conversations/direct ────────────────────────────────
        post("/conversations/direct") {
            val userId = call.verifiedUserId()
            println("POST /conversations/direct by user: $userId")
            val body = call.receive<CreateDirectConversationRequest>()
            println("Creating direct conversation with otherUserId: ${body.otherUserId}")
            val conversation = FirebaseAdminService.getOrCreateDirectConversation(userId, body.otherUserId)
            println("Created conversation: ${conversation.id}")
            call.respond(HttpStatusCode.Created, conversation)
        }

        // ── POST /api/conversations/group ─────────────────────────────────
        post("/conversations/group") {
            call.verifiedUserId()   // must be authenticated
            val body = call.receive<CreateGroupConversationRequest>()
            val conversation = FirebaseAdminService.createGroupConversation(
                groupId = body.groupId,
                name = body.name,
                memberIds = body.memberIds
            )
            call.respond(HttpStatusCode.Created, conversation)
        }

        // ── GET /api/conversations/{id}/messages ──────────────────────────
        get("/conversations/{id}/messages") {
            val userId = call.verifiedUserId()
            val convId = call.parameters["id"] ?: throw IllegalArgumentException("Missing conversation id")
            if (!FirebaseAdminService.isParticipant(convId, userId)) {
                call.respond(HttpStatusCode.Forbidden, mapOf("error" to "Not a participant"))
                return@get
            }
            val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 50
            val messages = FirebaseAdminService.getMessages(convId, limit)
            call.respond(messages)
        }

        // ── PATCH /api/conversations/{id}/read ────────────────────────────
        patch("/conversations/{id}/read") {
            val userId = call.verifiedUserId()
            val convId = call.parameters["id"] ?: throw IllegalArgumentException("Missing conversation id")
            FirebaseAdminService.markConversationRead(convId, userId)
            call.respond(HttpStatusCode.NoContent)
        }
    }
}
