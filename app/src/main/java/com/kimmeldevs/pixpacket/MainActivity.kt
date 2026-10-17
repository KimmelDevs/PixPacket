package com.kimmeldevs.pixpacket

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.kimmeldevs.pixpacket.editor.EditorState
import com.kimmeldevs.pixpacket.io.ProjectStore
import com.kimmeldevs.pixpacket.model.Sprite
import com.kimmeldevs.pixpacket.ui.EditorScreen
import com.kimmeldevs.pixpacket.ui.GalleryScreen
import com.kimmeldevs.pixpacket.ui.theme.PixPacketTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PixPacketTheme { Surface(color = MaterialTheme.colorScheme.background) {
                var editor by remember { mutableStateOf<EditorState?>(null) }
                val current = editor
                if (current == null) {
                    GalleryScreen(
                        onOpen = { id ->
                            ProjectStore.load(this, id)?.let { (name, sprite) -> editor = EditorState(id, sprite, name) }
                        },
                        onCreate = { name, w, h, whiteBg ->
                            val id = ProjectStore.newId()
                            val sprite = Sprite.blank(w, h, if (whiteBg) 0xFFFFFFFF.toInt() else 0)
                            ProjectStore.save(this, id, name, sprite)
                            editor = EditorState(id, sprite, name)
                        },
                    )
                } else {
                    EditorScreen(current, onBack = { editor = null })
                }
            } }
        }
    }
}
