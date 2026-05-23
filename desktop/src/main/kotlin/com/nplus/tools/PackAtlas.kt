package com.nplus.tools

import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.tools.texturepacker.TexturePacker

fun main(args: Array<String>) {
    check(args.size >= 2) { "Usage: PackAtlas <spritesDir> <outputDir>" }
    val settings = TexturePacker.Settings().apply {
        maxWidth         = 4096
        maxHeight        = 4096
        rotation              = false
        combineSubdirectories = true
        stripWhitespaceX      = false
        stripWhitespaceY = false
        flattenPaths     = false
        useIndexes       = false
        paddingX         = 2
        paddingY         = 2
        edgePadding      = true
        filterMin        = Texture.TextureFilter.Nearest
        filterMag        = Texture.TextureFilter.Nearest
    }
    TexturePacker.process(settings, args[0], args[1], "sprites")
    println("Packed: ${args[1]}/sprites.atlas")
}
