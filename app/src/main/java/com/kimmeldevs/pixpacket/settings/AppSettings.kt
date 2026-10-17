package com.kimmeldevs.pixpacket.settings

import android.content.Context
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.staticCompositionLocalOf
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

enum class ThemeMode(val label: String) { SYSTEM("System"), LIGHT("Light"), DARK("Dark") }

enum class Accent(val label: String, val dark: Long, val light: Long) {
    WREN("Wren blue", 0xFF8AB4FF, 0xFF0055BF),
    VIOLET("Violet", 0xFFB79CFF, 0xFF6A4BC4),
    TEAL("Teal", 0xFF5FE0CF, 0xFF00796B),
    ORANGE("Orange", 0xFFFFB066, 0xFFB85C00),
    PINK("Pink", 0xFFFF8FC7, 0xFFB0306E),
    BLUE("Blue", 0xFF7CB8FF, 0xFF1F5FB8),
    GREEN("Green", 0xFF8CE08A, 0xFF2E7D32),
}

enum class CheckerSize(val label: String, val cellsAcross: Int) { SMALL("Small", 16), MEDIUM("Medium", 8), LARGE("Large", 4) }

/** All user preferences. Each property is Compose state and is persisted on write. */
class AppSettings(ctx: Context) {
    private val sp = ctx.getSharedPreferences("pixpacket_settings", Context.MODE_PRIVATE)

    private fun bool(key: String, def: Boolean) = Pref({ sp.getBoolean(key, def) }) { sp.edit().putBoolean(key, it).apply() }
    private fun int(key: String, def: Int) = Pref({ sp.getInt(key, def) }) { sp.edit().putInt(key, it).apply() }
    private fun <E : Enum<E>> enum(key: String, def: E, values: Array<E>) =
        Pref({ values.firstOrNull { it.name == sp.getString(key, def.name) } ?: def }) { sp.edit().putString(key, it.name).apply() }

    // Appearance
    var theme by enum("theme", ThemeMode.SYSTEM, ThemeMode.entries.toTypedArray())
    var accent by enum("accent", Accent.WREN, Accent.entries.toTypedArray())
    var checker by enum("checker", CheckerSize.MEDIUM, CheckerSize.entries.toTypedArray())

    // Drawing & gestures
    var haptics by bool("haptics", true)
    var gestureUndo by bool("gesture_undo", true)
    var longPressPick by bool("long_press_pick", true)
    var leftHanded by bool("left_handed", false)
    var collapseToolsOnPick by bool("collapse_tools", false)
    var keepScreenOn by bool("keep_screen_on", true)

    // New sprites
    var defaultWidth by int("default_w", 32)
    var defaultHeight by int("default_h", 32)
    var defaultFps by int("default_fps", 8)
    var defaultTileGrid by int("default_tile_grid", 8)

    // Library
    var confirmDelete by bool("confirm_delete", true)
    var showTips by bool("show_tips", true)

    private class Pref<T>(read: () -> T, val write: (T) -> Unit) : ReadWriteProperty<Any, T> {
        private val state: MutableState<T> = mutableStateOf(read())
        override fun getValue(thisRef: Any, property: KProperty<*>): T = state.value
        override fun setValue(thisRef: Any, property: KProperty<*>, value: T) { state.value = value; write(value) }
    }
}

val LocalSettings = staticCompositionLocalOf<AppSettings> { error("AppSettings not provided") }
