package com.ovi.where.server.plugins

import io.ktor.server.application.*
import io.ktor.server.plugins.*
import io.ktor.http.*
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.ConcurrentHashMap

fun Application.configureRateLimiting() {
    install(RateLimitFeature)
}

object RateLimitFeature : BaseApplicationPlugin<Application, RateLimitConfig, RateLimitFeature> {
    override val key = AttributeKey<RateLimitFeature>("RateLimiter")
    
    override fun install(pipeline: Application, configure: RateLimitConfig.() -> Unit): RateLimitFeature {
        val config = RateLimitConfig().apply(configure)
        val limiter = InMemoryRateLimiter(config.maxRequests, config.windowMs)
        
        pipeline.intercept(ApplicationCallPipeline.Call) {
            val key = call.request.headers["Authorization"]
                ?.removePrefix("Bearer ")
                ?.takeIf { it.isNotBlank() }
                ?: call.request.path()
            
            if (!limiter.tryAcquire(key)) {
                call.respond(HttpStatusCode.TooManyRequests, mapOf(
                    "error" to "Rate limit exceeded. Maximum ${config.maxRequests} requests per ${config.windowMs/1000} seconds."
                ))
                finish()
            }
        }
        
        return RateLimitFeature()
    }
}

class InMemoryRateLimiter(
    private val maxRequests: Int,
    private val windowMs: Long
) {
    private val map = ConcurrentHashMap<String, RequestWindow>()
    
    fun tryAcquire(key: String): Boolean {
        val now = System.currentTimeMillis()
        val window = map.getOrPut(key) { RequestWindow(AtomicInteger(0), now) }
        
        if (now - window.start.get() > windowMs) {
            window.count.set(0)
            window.start.set(now)
        }
        
        return window.count.getAndIncrement() < maxRequests
    }
}

class RequestWindow(
    val count: AtomicInteger,
    val start: AtomicInteger
)

class RateLimitConfig {
    var maxRequests: Int = 60
    var windowMs: Long = 60_000
}