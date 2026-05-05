package com.ovi.where.server.plugins

import com.ovi.where.server.routes.conversationRoutes
import com.ovi.where.server.routes.chatWebSocketRoute
import io.ktor.server.application.Application
import io.ktor.server.routing.routing

fun Application.configureRouting() {
    routing {
        conversationRoutes()
        chatWebSocketRoute()
    }
}
