package org.quill.paper

import org.bukkit.plugin.java.JavaPlugin

class QuillPlugin : JavaPlugin() {
    override fun onEnable() {
        // Quill runtime will be initialized here in Chunk 4
    }

    override fun onDisable() {
        // Cleanup handled in Chunk 4
    }
}
