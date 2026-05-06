package com.ovi.where.server

import com.ovi.where.server.plugins.configureCORS
import com.ovi.where.server.plugins.configureRouting
import com.ovi.where.server.plugins.configureSerialization
import com.ovi.where.server.plugins.configureStatusPages
import com.ovi.where.server.plugins.configureWebSockets
import com.ovi.where.server.service.FirebaseAdminService
import io.ktor.server.application.Application
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty

fun main() {
    embeddedServer(
        Netty,
        port = System.getenv("PORT")?.toIntOrNull() ?: 8080,
        host = "0.0.0.0",
        module = Application::module
    ).start(wait = true)
}

fun Application.module() {
    FirebaseAdminService.init()
    configureSerialization()
    configureCORS()
    configureWebSockets()
    configureStatusPages()
    configureRouting()
}
