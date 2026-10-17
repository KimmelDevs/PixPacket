package com.kimmeldevs.pixpacket

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import com.kimmeldevs.pixpacket.editor.EditorState
import com.kimmeldevs.pixpacket.io.Library
import com.kimmeldevs.pixpacket.io.ProjectStore
import com.kimmeldevs.pixpacket.model.Sprite
import com.kimmeldevs.pixpacket.settings.AppSettings
import com.kimmeldevs.pixpacket.settings.LocalSettings
import com.kimmeldevs.pixpacket.ui.EditorScreen
import com.kimmeldevs.pixpacket.ui.GalleryScreen
import com.kimmeldevs.pixpacket.ui.SettingsScreen
import com.kimmeldevs.pixpacket.ui.theme.LocalPixExtras
import com.kimmeldevs.pixpacket.ui.theme.PixPacketTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val settings = AppSettings(this)
        setContent {
            PixPacketTheme(settings.theme, settings.accent) {
                // Status/nav bar icons follow the app theme, not just the system one.
                val dark = LocalPixExtras.current.isDark
                DisposableEffect(dark) {
                    val style = if (dark) SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                    else SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
                    enableEdgeToEdge(style, style)
                    onDispose {}
                }
                val systemHaptics = LocalHapticFeedback.current
                val haptics = remember(settings.haptics, systemHaptics) {
                    if (settings.haptics) systemHaptics else object : HapticFeedback {
                        override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) {}
                    }
                }
                CompositionLocalProvider(LocalSettings provides settings, LocalHapticFeedback provides haptics) {
                    Surface(color = MaterialTheme.colorScheme.background) {
                        var editor by remember { mutableStateOf<EditorState?>(null) }
                        var showSettings by remember { mutableStateOf(false) }

                        fun openNew(name: String, sprite: Sprite, folder: String?) {
                            val id = ProjectStore.newId()
                            ProjectStore.save(this, id, name, sprite)
                            if (folder != null) Library.update(this) { it.assign(id, folder) }
                            editor = EditorState(id, sprite, name)
                        }

                        val current = editor
                        when {
                            current != null -> EditorScreen(current, onBack = { editor = null })
                            showSettings -> SettingsScreen(onBack = { showSettings = false })
                            else -> GalleryScreen(
                                onOpen = { id ->
                                    ProjectStore.load(this, id)?.let { (name, sprite) -> editor = EditorState(id, sprite, name) }
                                },
                                onCreate = { name, w, h, whiteBg, folder ->
                                    openNew(name, Sprite.blank(w, h, if (whiteBg) 0xFFFFFFFF.toInt() else 0, settings.defaultFps, settings.defaultTileGrid), folder)
                                },
                                onImported = { name, sprite, folder -> openNew(name, sprite, folder) },
                                onSettings = { showSettings = true },
                            )
                        }
                    }
                }
            }
        }
    }
}
