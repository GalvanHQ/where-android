import java.text.SimpleDateFormat
import java.util.Date
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
    alias(libs.plugins.google.services)
    alias(libs.plugins.firebase.crashlytics)
}

android {
    namespace = "com.ovi.where"
    compileSdk = 35

    // Read API keys from local.properties or environment variables
    val localProperties = Properties()
    val localPropertiesFile = rootProject.file("local.properties")
    if (localPropertiesFile.exists()) {
        localProperties.load(localPropertiesFile.inputStream())
    }
    val mapsApiKey = localProperties.getProperty("MAPS_API_KEY")
        ?: System.getenv("MAPS_API_KEY")
        ?: ""

    defaultConfig {
        applicationId = "com.ovi.where"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Chat server URLs – defaults (overridden per build type below)
        buildConfigField("String", "CHAT_SERVER_HTTP_URL", "\"http://10.0.2.2:8080\"")
        buildConfigField("String", "CHAT_SERVER_WS_URL",   "\"http://10.0.2.2:8080\"")

        // Google Maps API key from local.properties or environment variable
        buildConfigField("String", "MAPS_API_KEY", "\"$mapsApiKey\"")
        manifestPlaceholders["MAPS_API_KEY"] = mapsApiKey
    }

    signingConfigs {
        create("release") {
            storeFile = file("release-keystore.jks")
            storePassword = System.getenv("KEYSTORE_PASSWORD") ?: "CHANGE_ME"
            keyAlias = "where-app"
            keyPassword = System.getenv("KEY_PASSWORD") ?: "CHANGE_ME"
        }
    }

    buildTypes {
        debug {
            isDebuggable = true
            // Cloud Run server URL
            buildConfigField("String", "CHAT_SERVER_HTTP_URL", "\"https://where-chat-server-node-zgzelfwe5q-uc.a.run.app\"")
            buildConfigField("String", "CHAT_SERVER_WS_URL",   "\"https://where-chat-server-node-zgzelfwe5q-uc.a.run.app\"")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            isDebuggable = false
            signingConfig = signingConfigs.getByName("release")
            // Cloud Run server URL
            buildConfigField("String", "CHAT_SERVER_HTTP_URL", "\"https://where-chat-server-node-zgzelfwe5q-uc.a.run.app\"")
            buildConfigField("String", "CHAT_SERVER_WS_URL", "\"https://where-chat-server-node-zgzelfwe5q-uc.a.run.app\"")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    kotlinOptions {
        jvmTarget = "21"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "/META-INF/INDEX.LIST"
            excludes += "/META-INF/io.netty.versions.properties"
        }
    }

    testOptions {
        unitTests.all {
            // Run JUnit 5 (Kotest) tests while keeping existing JUnit 4 tests alive
            // via the vintage engine.
            it.useJUnitPlatform()
        }
    }
}

// ── Audit Engine: Custom Gradle Task ─────────────────────────────────────────
abstract class AuditCodebaseTask : DefaultTask() {

    @get:InputDirectory
    abstract val sourceDir: DirectoryProperty

    @get:OutputFile
    abstract val outputFile: RegularFileProperty

    @TaskAction
    fun execute() {
        val srcDir = sourceDir.get().asFile
        val output = outputFile.get().asFile
        val rootDir = project.rootDir

        // Collect all .kt files under the main source set, excluding generated/build/test dirs
        val kotlinFiles = srcDir.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .filter { file ->
                val relativePath = file.relativeTo(srcDir).path.replace("\\", "/")
                !relativePath.contains("/build/") &&
                !relativePath.contains("/generated/") &&
                !relativePath.startsWith("build/") &&
                !relativePath.startsWith("generated/")
            }
            .toList()

        logger.lifecycle("AuditCodebase: Scanning ${kotlinFiles.size} Kotlin source files...")

        // ── Data classes for scan results ────────────────────────────────────
        data class CodeMarker(val file: String, val line: Int, val marker: String, val text: String)
        data class CommentBlock(val file: String, val startLine: Int, val endLine: Int, val lineCount: Int)
        data class DebugLog(val file: String, val line: Int, val statement: String)
        data class NetworkCall(val file: String, val line: Int, val detail: String)
        data class ChannelEntry(val channelId: String, val name: String, val importance: String)
        data class PermissionUsage(val file: String, val line: Int, val context: String)
        data class ParseError(val file: String, val error: String)
        data class ScreenState(
            val name: String,
            var hasLoading: Boolean = false,
            var hasError: Boolean = false,
            var hasEmpty: Boolean = false,
            var hasContent: Boolean = false
        )

        // ── Regex patterns ───────────────────────────────────────────────────
        val todoCommentRegex = Regex("""//.*\b(TODO|FIXME|HACK|STUB)\b(.*)""", RegexOption.IGNORE_CASE)
        val todoBlockCommentRegex = Regex("""/\*.*\b(TODO|FIXME|HACK|STUB)\b(.*)""", RegexOption.IGNORE_CASE)
        val todoFunctionBodyRegex = Regex("""^\s*TODO\(\s*(".*?")?\s*\)\s*$""")
        val debugLogRegex = Regex("""(Timber\.d\(|Log\.d\()(.*)""")
        val commentLineRegex = Regex("""^\s*//""")
        val importRegex = Regex("""^\s*import\s+(.+)$""")
        val retrofitAnnotationRegex = Regex("""@(GET|POST|PUT|DELETE|PATCH|HTTP)\s*\(""")
        val firestoreQueryRegex = Regex("""\.(collection|document)\s*\(""")
        val socketIoRegex = Regex("""\.(on|emit)\s*\(""")
        val screenComposableRegex = Regex("""@Composable\s+fun\s+(\w*Screen)\s*\(""")

        // Loading state indicators
        val loadingIndicators = listOf(
            "isLoading", "loading", "Loading", "CircularProgressIndicator",
            "ShimmerPlaceholder", "shimmer", "Resource.Loading", "UiState.Loading"
        )
        // Error state indicators
        val errorIndicators = listOf(
            "isError", "error", "Error", "ErrorView", "errorMessage",
            "Resource.Error", "UiState.Error", "onRetry"
        )
        // Empty state indicators
        val emptyIndicators = listOf(
            "isEmpty", "empty", "Empty", "EmptyState", "emptyList",
            "UiState.Empty", "no items", "noItems"
        )
        // Content state indicators
        val contentIndicators = listOf(
            "LazyColumn", "LazyGrid", "LazyVerticalGrid", "items(",
            "Content", "data", "Resource.Success", "UiState.Success"
        )

        // ── Scan results accumulators ────────────────────────────────────────
        val todoMarkers = mutableListOf<CodeMarker>()
        val commentBlocks = mutableListOf<CommentBlock>()
        val debugLogs = mutableListOf<DebugLog>()
        val retrofitCalls = mutableListOf<NetworkCall>()
        val firestoreQueries = mutableListOf<NetworkCall>()
        val socketIoEvents = mutableListOf<NetworkCall>()
        val notificationChannels = mutableListOf<ChannelEntry>()
        val parseErrors = mutableListOf<ParseError>()
        val screenStates = mutableListOf<ScreenState>()
        val permissionUsages = mutableMapOf<String, MutableList<PermissionUsage>>()
        val unusedImports = mutableListOf<CodeMarker>()

        // ── Permission declarations from AndroidManifest.xml ─────────────────
        val manifestFile = File(rootDir, "app/src/main/AndroidManifest.xml")
        val declaredPermissions = mutableListOf<String>()
        if (manifestFile.exists()) {
            val manifestPermissionRegex = Regex("""<uses-permission\s+android:name="([^"]+)"""")
            manifestFile.readLines().forEach { line ->
                manifestPermissionRegex.find(line)?.let { match ->
                    declaredPermissions.add(match.groupValues[1])
                }
            }
        }
        // Initialize permission usage map
        declaredPermissions.forEach { perm ->
            permissionUsages[perm] = mutableListOf()
        }

        // Permission name patterns to search in code
        val permissionCodePatterns = mapOf(
            "android.permission.ACCESS_FINE_LOCATION" to Regex("""ACCESS_FINE_LOCATION|Manifest\.permission\.ACCESS_FINE_LOCATION"""),
            "android.permission.ACCESS_COARSE_LOCATION" to Regex("""ACCESS_COARSE_LOCATION|Manifest\.permission\.ACCESS_COARSE_LOCATION"""),
            "android.permission.ACCESS_BACKGROUND_LOCATION" to Regex("""ACCESS_BACKGROUND_LOCATION|Manifest\.permission\.ACCESS_BACKGROUND_LOCATION"""),
            "android.permission.POST_NOTIFICATIONS" to Regex("""POST_NOTIFICATIONS|Manifest\.permission\.POST_NOTIFICATIONS"""),
            "android.permission.INTERNET" to Regex("""INTERNET|Manifest\.permission\.INTERNET"""),
            "android.permission.ACCESS_NETWORK_STATE" to Regex("""ACCESS_NETWORK_STATE|Manifest\.permission\.ACCESS_NETWORK_STATE"""),
            "android.permission.FOREGROUND_SERVICE" to Regex("""FOREGROUND_SERVICE|Manifest\.permission\.FOREGROUND_SERVICE"""),
            "android.permission.FOREGROUND_SERVICE_LOCATION" to Regex("""FOREGROUND_SERVICE_LOCATION|Manifest\.permission\.FOREGROUND_SERVICE_LOCATION"""),
            "android.permission.VIBRATE" to Regex("""VIBRATE|Manifest\.permission\.VIBRATE""")
        )

        // Importance level mapping
        val importanceMap = mapOf(
            "NotificationManager.IMPORTANCE_HIGH" to "High",
            "IMPORTANCE_HIGH" to "High",
            "NotificationManager.IMPORTANCE_DEFAULT" to "Default",
            "IMPORTANCE_DEFAULT" to "Default",
            "NotificationManager.IMPORTANCE_LOW" to "Low",
            "IMPORTANCE_LOW" to "Low",
            "NotificationManager.IMPORTANCE_MIN" to "Min",
            "IMPORTANCE_MIN" to "Min",
            "NotificationManager.IMPORTANCE_NONE" to "None",
            "IMPORTANCE_NONE" to "None"
        )

        // ── Scan each Kotlin file ────────────────────────────────────────────
        for (file in kotlinFiles) {
            val relativePath = file.relativeTo(rootDir).path.replace("\\", "/")
            try {
                val lines = file.readLines()
                val content = lines.joinToString("\n")

                // ── Screen state detection ───────────────────────────────────
                val screenMatches = screenComposableRegex.findAll(content)
                for (match in screenMatches) {
                    val screenName = match.groupValues[1]
                    // Get the function body (approximate: from match to end of file or next top-level fun)
                    val startIdx = match.range.last
                    val bodyContent = content.substring(startIdx, minOf(startIdx + 5000, content.length))

                    val state = ScreenState(name = screenName)
                    state.hasLoading = loadingIndicators.any { indicator -> bodyContent.contains(indicator) }
                    state.hasError = errorIndicators.any { indicator -> bodyContent.contains(indicator) }
                    state.hasEmpty = emptyIndicators.any { indicator -> bodyContent.contains(indicator) }
                    state.hasContent = contentIndicators.any { indicator -> bodyContent.contains(indicator) }
                    screenStates.add(state)
                }

                // ── Line-by-line scanning ────────────────────────────────────
                var consecutiveCommentStart = -1
                var consecutiveCommentCount = 0

                for ((index, line) in lines.withIndex()) {
                    val lineNum = index + 1

                    // TODO/FIXME/HACK/STUB markers in comments
                    val todoMatch = todoCommentRegex.find(line) ?: todoBlockCommentRegex.find(line)
                    todoMatch?.let { match ->
                        val marker = match.groupValues[1].uppercase()
                        val text = match.groupValues[2].trim().take(100)
                        todoMarkers.add(CodeMarker(relativePath, lineNum, marker, text))
                    }

                    // TODO() function body detection
                    if (todoFunctionBodyRegex.matches(line.trim())) {
                        val text = line.trim().take(100)
                        todoMarkers.add(CodeMarker(relativePath, lineNum, "TODO", text))
                    }

                    // Debug log statements
                    debugLogRegex.find(line)?.let { match ->
                        val statement = line.trim().take(120)
                        debugLogs.add(DebugLog(relativePath, lineNum, statement))
                    }

                    // Commented-out code blocks (3+ consecutive lines starting with //)
                    if (commentLineRegex.matches(line)) {
                        if (consecutiveCommentStart == -1) {
                            consecutiveCommentStart = lineNum
                            consecutiveCommentCount = 1
                        } else {
                            consecutiveCommentCount++
                        }
                    } else {
                        if (consecutiveCommentCount >= 3) {
                            commentBlocks.add(
                                CommentBlock(
                                    relativePath,
                                    consecutiveCommentStart,
                                    consecutiveCommentStart + consecutiveCommentCount - 1,
                                    consecutiveCommentCount
                                )
                            )
                        }
                        consecutiveCommentStart = -1
                        consecutiveCommentCount = 0
                    }

                    // Retrofit annotations
                    retrofitAnnotationRegex.find(line)?.let {
                        val detail = line.trim().take(120)
                        retrofitCalls.add(NetworkCall(relativePath, lineNum, detail))
                    }

                    // Firestore queries
                    firestoreQueryRegex.find(line)?.let {
                        val detail = line.trim().take(120)
                        firestoreQueries.add(NetworkCall(relativePath, lineNum, detail))
                    }

                    // Socket.IO events
                    socketIoRegex.find(line)?.let {
                        // Only match if it looks like a socket.io call (has a string event name)
                        if (line.contains("socket") || line.contains("Socket") || line.contains("mSocket") || line.contains("io.")) {
                            val detail = line.trim().take(120)
                            socketIoEvents.add(NetworkCall(relativePath, lineNum, detail))
                        }
                    }

                    // Notification channels — detect NotificationChannel( constructor calls
                    // Handle both inline strings and constant references across multiple lines
                    if (line.contains("NotificationChannel(") && !line.trimStart().startsWith("//") && !line.contains("createNotificationChannel")) {
                        // Look ahead up to 5 lines to capture the full constructor call
                        val lookAheadEnd = minOf(index + 6, lines.size)
                        val constructorBlock = lines.subList(index, lookAheadEnd).joinToString(" ")
                        
                        // Try to extract channel info from the block
                        // Pattern 1: inline strings - NotificationChannel("id", "name", importance)
                        val inlineMatch = Regex("""NotificationChannel\(\s*"([^"]+)"\s*,\s*"([^"]+)"\s*,\s*([^)]+)\)""").find(constructorBlock)
                        if (inlineMatch != null) {
                            val channelId = inlineMatch.groupValues[1]
                            val channelName = inlineMatch.groupValues[2]
                            val importanceRaw = inlineMatch.groupValues[3].trim()
                            val importance = importanceMap.entries
                                .firstOrNull { (key, _) -> importanceRaw.contains(key) }
                                ?.value ?: importanceRaw
                            notificationChannels.add(ChannelEntry(channelId, channelName, importance))
                        } else {
                            // Pattern 2: constant references - NotificationChannel(CHANNEL_X, "name"/"getString(...)", importance)
                            val constMatch = Regex("""NotificationChannel\(\s*(\w+)\s*,\s*(.+?)\s*,\s*(.+?)[\s)]+""").find(constructorBlock)
                            if (constMatch != null) {
                                val channelIdRef = constMatch.groupValues[1].trim()
                                val channelNameRaw = constMatch.groupValues[2].trim()
                                val importanceRaw = constMatch.groupValues[3].trim()
                                
                                // Resolve channel ID from constant name
                                val channelId = when {
                                    channelIdRef.contains("MESSAGES") -> "messages"
                                    channelIdRef.contains("SOCIAL") -> "social"
                                    channelIdRef.contains("LOCATION") -> "location_updates"
                                    channelIdRef.contains("GROUP") -> "group_activity"
                                    channelIdRef.contains("GENERAL") || channelIdRef.contains("DEFAULT") -> "general"
                                    channelIdRef.contains("NOTIFICATION_CHANNEL_ID") -> "location_tracking"
                                    else -> channelIdRef
                                }
                                
                                // Resolve channel name
                                val channelName = when {
                                    channelNameRaw.startsWith("\"") -> channelNameRaw.removeSurrounding("\"")
                                    channelNameRaw.contains("getString") -> {
                                        val resMatch = Regex("""R\.string\.(\w+)""").find(channelNameRaw)
                                        resMatch?.groupValues?.get(1)?.replace("_", " ")?.replaceFirstChar { it.uppercase() } ?: channelNameRaw
                                    }
                                    else -> channelNameRaw
                                }
                                
                                val importance = importanceMap.entries
                                    .firstOrNull { (key, _) -> importanceRaw.contains(key) }
                                    ?.value ?: importanceRaw
                                
                                // Avoid duplicates (same channelId)
                                if (notificationChannels.none { it.channelId == channelId }) {
                                    notificationChannels.add(ChannelEntry(channelId, channelName, importance))
                                }
                            }
                        }
                    }

                    // Permission usage detection
                    for ((permission, pattern) in permissionCodePatterns) {
                        if (permissionUsages.containsKey(permission) && pattern.containsMatchIn(line)) {
                            permissionUsages[permission]?.add(
                                PermissionUsage(relativePath, lineNum, line.trim().take(100))
                            )
                        }
                    }

                    // Unused imports detection (heuristic: import not referenced elsewhere in file)
                    importRegex.find(line)?.let { match ->
                        val importPath = match.groupValues[1].trim()
                        val simpleName = importPath.substringAfterLast(".")
                        // Skip wildcard imports and common framework imports
                        if (simpleName != "*" && simpleName.isNotEmpty()) {
                            // Check if the simple name is used anywhere else in the file
                            val usedElsewhere = lines.filterIndexed { i, _ -> i != index }
                                .any { otherLine -> otherLine.contains(simpleName) }
                            if (!usedElsewhere) {
                                unusedImports.add(CodeMarker(relativePath, lineNum, "import", importPath))
                            }
                        }
                    }
                }

                // Handle trailing comment block at end of file
                if (consecutiveCommentCount >= 3) {
                    commentBlocks.add(
                        CommentBlock(
                            relativePath,
                            consecutiveCommentStart,
                            consecutiveCommentStart + consecutiveCommentCount - 1,
                            consecutiveCommentCount
                        )
                    )
                }

            } catch (e: Exception) {
                parseErrors.add(ParseError(relativePath, e.message ?: "Unknown error"))
                logger.warn("AuditCodebase: Error parsing $relativePath: ${e.message}")
            }
        }

        logger.lifecycle("AuditCodebase: Found ${todoMarkers.size} TODO markers, ${debugLogs.size} debug logs, ${commentBlocks.size} comment blocks")

        // ── Build the AUDIT.md report ────────────────────────────────────────
        val report = buildString {
            appendLine("# Codebase Audit Report")
            appendLine()
            val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
            appendLine("Generated: ${dateFormat.format(Date())}")
            appendLine()
            appendLine("Source directory: `${srcDir.relativeTo(rootDir)}`")
            appendLine()
            appendLine("Total Kotlin files scanned: ${kotlinFiles.size}")
            appendLine()

            // Section: Screen States
            appendLine("---")
            appendLine()
            appendLine("## Screen States")
            appendLine()
            appendLine("| Screen | Loading | Error | Empty | Content |")
            appendLine("|--------|---------|-------|-------|---------|")
            if (screenStates.isEmpty()) {
                appendLine("| _No Screen composables found_ | - | - | - | - |")
            } else {
                for (state in screenStates.sortedBy { it.name }) {
                    val loading = if (state.hasLoading) "✅ present" else "❌ missing"
                    val error = if (state.hasError) "✅ present" else "❌ missing"
                    val empty = if (state.hasEmpty) "✅ present" else "❌ missing"
                    val content = if (state.hasContent) "✅ present" else "❌ missing"
                    appendLine("| ${state.name} | $loading | $error | $empty | $content |")
                }
            }
            appendLine()

            // Section: TODO/FIXME/HACK/STUB Markers
            appendLine("---")
            appendLine()
            appendLine("## TODO/FIXME/HACK/STUB Markers")
            appendLine()
            appendLine("Total: ${todoMarkers.size}")
            appendLine()
            appendLine("| File | Line | Marker | Text |")
            appendLine("|------|------|--------|------|")
            if (todoMarkers.isEmpty()) {
                appendLine("| _None found_ | - | - | - |")
            } else {
                for (marker in todoMarkers) {
                    appendLine("| `${marker.file}` | ${marker.line} | ${marker.marker} | ${marker.text} |")
                }
            }
            appendLine()

            // Section: Unused Code
            appendLine("---")
            appendLine()
            appendLine("## Unused Code")
            appendLine()
            appendLine("### Unused Imports")
            appendLine()
            appendLine("Total: ${unusedImports.size}")
            appendLine()
            appendLine("| File | Line | Import |")
            appendLine("|------|------|--------|")
            if (unusedImports.isEmpty()) {
                appendLine("| _None found_ | - | - |")
            } else {
                for (imp in unusedImports) {
                    appendLine("| `${imp.file}` | ${imp.line} | `${imp.text}` |")
                }
            }
            appendLine()
            appendLine("### Commented-Out Code Blocks (3+ consecutive lines)")
            appendLine()
            appendLine("Total: ${commentBlocks.size}")
            appendLine()
            appendLine("| File | Start Line | End Line | Lines |")
            appendLine("|------|------------|----------|-------|")
            if (commentBlocks.isEmpty()) {
                appendLine("| _None found_ | - | - | - |")
            } else {
                for (block in commentBlocks) {
                    appendLine("| `${block.file}` | ${block.startLine} | ${block.endLine} | ${block.lineCount} |")
                }
            }
            appendLine()

            // Section: Debug Log Statements
            appendLine("---")
            appendLine()
            appendLine("## Debug Log Statements")
            appendLine()
            appendLine("Total: ${debugLogs.size}")
            appendLine()
            appendLine("| File | Line | Statement |")
            appendLine("|------|------|-----------|")
            if (debugLogs.isEmpty()) {
                appendLine("| _None found_ | - | - |")
            } else {
                for (log in debugLogs) {
                    appendLine("| `${log.file}` | ${log.line} | `${log.statement}` |")
                }
            }
            appendLine()

            // Section: Permission Mappings
            appendLine("---")
            appendLine()
            appendLine("## Permission Mappings")
            appendLine()
            appendLine("| Permission | Manifest | Code Usages |")
            appendLine("|------------|----------|-------------|")
            if (declaredPermissions.isEmpty()) {
                appendLine("| _No permissions declared_ | - | - |")
            } else {
                for (perm in declaredPermissions) {
                    val usages = permissionUsages[perm] ?: emptyList()
                    val usageStr = if (usages.isEmpty()) {
                        "_No code references found_"
                    } else {
                        usages.joinToString("<br>") { "`${it.file}:${it.line}`" }
                    }
                    appendLine("| `$perm` | ✅ Declared | $usageStr |")
                }
            }
            appendLine()

            // Section: Network Call Patterns
            appendLine("---")
            appendLine()
            appendLine("## Network Call Patterns")
            appendLine()
            appendLine("### Retrofit Interfaces")
            appendLine()
            appendLine("Total: ${retrofitCalls.size}")
            appendLine()
            appendLine("| File | Line | Method |")
            appendLine("|------|------|--------|")
            if (retrofitCalls.isEmpty()) {
                appendLine("| _None found_ | - | - |")
            } else {
                for (call in retrofitCalls) {
                    appendLine("| `${call.file}` | ${call.line} | `${call.detail}` |")
                }
            }
            appendLine()
            appendLine("### Firestore Queries")
            appendLine()
            appendLine("Total: ${firestoreQueries.size}")
            appendLine()
            appendLine("| File | Line | Query |")
            appendLine("|------|------|-------|")
            if (firestoreQueries.isEmpty()) {
                appendLine("| _None found_ | - | - |")
            } else {
                for (query in firestoreQueries) {
                    appendLine("| `${query.file}` | ${query.line} | `${query.detail}` |")
                }
            }
            appendLine()
            appendLine("### Socket.IO Events")
            appendLine()
            appendLine("Total: ${socketIoEvents.size}")
            appendLine()
            appendLine("| File | Line | Event |")
            appendLine("|------|------|-------|")
            if (socketIoEvents.isEmpty()) {
                appendLine("| _None found_ | - | - |")
            } else {
                for (event in socketIoEvents) {
                    appendLine("| `${event.file}` | ${event.line} | `${event.detail}` |")
                }
            }
            appendLine()

            // Section: Notification Channels
            appendLine("---")
            appendLine()
            appendLine("## Notification Channels")
            appendLine()
            appendLine("Total: ${notificationChannels.size}")
            appendLine()
            appendLine("| Channel ID | Name | Importance |")
            appendLine("|------------|------|------------|")
            if (notificationChannels.isEmpty()) {
                appendLine("| _None found_ | - | - |")
            } else {
                for (channel in notificationChannels) {
                    appendLine("| `${channel.channelId}` | ${channel.name} | ${channel.importance} |")
                }
            }
            appendLine()

            // Section: Parse Errors
            appendLine("---")
            appendLine()
            appendLine("## Parse Errors")
            appendLine()
            appendLine("| File | Error |")
            appendLine("|------|-------|")
            if (parseErrors.isEmpty()) {
                appendLine("| _No parse errors_ | - |")
            } else {
                for (err in parseErrors) {
                    appendLine("| `${err.file}` | ${err.error} |")
                }
            }
            appendLine()
        }

        output.parentFile?.mkdirs()
        output.writeText(report)
        logger.lifecycle("AuditCodebase: Report written to ${output.relativeTo(rootDir)}")
    }
}

tasks.register<AuditCodebaseTask>("auditCodebase") {
    group = "verification"
    description = "Scans Kotlin source files and produces an AUDIT.md report of technical debt and patterns."

    sourceDir.set(file("src/main/java"))
    outputFile.set(rootProject.file("AUDIT.md"))
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.navigation.compose)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.navigation.compose)


    // Firebase
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.firebase.firestore)
    implementation(libs.firebase.messaging)
    implementation(libs.firebase.storage)
    implementation(libs.firebase.functions)
    implementation(libs.firebase.config)
    // TODO: Re-enable after fixing build ID issue
    implementation(libs.firebase.crashlytics)
    implementation(libs.firebase.analytics)

    // Play Services
    implementation(libs.play.services.location)
    implementation(libs.play.services.auth)

    // Google Maps
    implementation(libs.play.services.maps)
    implementation(libs.maps.compose)

    // Room
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    // WorkManager
    implementation(libs.work.runtime.ktx)
    implementation(libs.hilt.work)
    ksp(libs.hilt.work.compiler)

    // DataStore
    implementation(libs.datastore.preferences)

    // Coil
    implementation(libs.coil.compose)
    implementation(libs.androidx.ui.text.google.fonts)

    // Baseline Profile
    implementation(libs.profileinstaller)

    // App Startup
    implementation(libs.startup.runtime)

    // Timber
    implementation(libs.timber)

    // Coroutines
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.play.services)

    // ── HTTP + WebSocket Clients ─────────────────────────────────────
    implementation(libs.socketio.client)
    implementation(libs.retrofit.core)
    implementation(libs.retrofit.kotlinx.serialization)
    implementation(libs.kotlinx.serialization.json)
    
    // OkHttp logging
    implementation(libs.okhttp.logging)

    testImplementation(libs.junit)
    testImplementation(libs.hilt.android.testing)
    testImplementation(libs.mockk)
    testImplementation(libs.robolectric)
    testImplementation(libs.kotlin.test.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    // Kotest property-based testing on the JUnit 5 Platform.
    testImplementation(libs.kotest.property)
    testImplementation(libs.kotest.runner.junit5)
    testImplementation(libs.kotest.assertions.core)
    // Vintage engine keeps existing JUnit 4 tests running under JUnit Platform.
    testRuntimeOnly(libs.junit.vintage.engine)
    androidTestImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
