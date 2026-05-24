In the official, standalone Google Maps app, you are already "inside" the map activity from the moment the app opens (following the initial splash screen). Because you never leave that primary screen to navigate somewhere else, the map stays warm in your device's memory, giving the illusion that it never flashes or reloads.

In your app, the flash happens precisely because of **Navigation**. Here is exactly why it happens in your code, and how the official app works differently:

---

### Why it flashes in your app (The Lifecycle Problem)

When you use Jetpack Compose Navigation to switch screens (e.g., navigating from a `HomeScreen` to a `MapScreen`), this is what happens behind the scenes:

1. **Destruction:** The `HomeScreen` is removed from the screen hierarchy.
2. **Creation:** The `MapScreen` is built from scratch.
3. **The Gap:** The `GoogleMap()` composable has to spin up a brand new native Android `MapView`. For a split second, the system knows a view is supposed to go there, but the map hasn't finished fetching its vector tiles or applying the `FOLLOW_SYSTEM` color scheme yet.
4. **The Flash:** During that split second gap, Android falls back to a default system background color (which can look like a white or green box) before the map suddenly pops into view.

---

### How the official Google Maps app avoids this

The official Google Maps app uses two architectural tricks to make everything look seamless:

* **Single Activity / Constant Lifecycle:** The map is initialized once at the very bottom layer of the app. When you tap on menus, search bars, or settings, those screens are just visual overlays (like bottom sheets or dialogs) sliding *over* the map. The map underneath never gets destroyed or closed.
* **Map Caching (Pre-warming):** The map view is kept alive ("warmed up") in memory so that it is instantly ready to render frames without having to re-initialize the graphics engine.

---

### How to achieve the "Google Maps App" behavior in Compose

If you want your app to feel exactly like the official Google Maps app without the navigation flash, you have two choices depending on your app's layout:

#### Option 1: Use a `Box` with Overlays (Instead of True Navigation)

If your map is the central piece of your app, don't navigate *away* from it. Instead, keep the map in a base `Box` layout, and use Compose states to show or hide other UI elements on top of it.

```kotlin
Box(modifier = Modifier.fillMaxSize()) {
    // The map is always alive underneath
    GoogleMap(
        modifier = Modifier.fillMaxSize(),
        mapColorScheme = ComposeMapColorScheme.FOLLOW_SYSTEM
    )

    // Instead of navigating to a new screen, just overlay your UI elements
    if (showProfileOverlay) {
        UserProfileScreen(onClose = { showProfileOverlay = false })
    }
    
    if (showSearchOverlay) {
        SearchResultsList()
    }
}

```