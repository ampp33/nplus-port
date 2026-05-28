package com.nplus.tools

import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.tools.texturepacker.TexturePacker
import java.io.File

fun main(args: Array<String>) {
    check(args.size >= 2) { "Usage: PackAtlas <spritesDir> <outputDir>" }

    val assetsDir = File(args[0]).parentFile

    // ragdoll.atlas — ragdoll limb sprites, Linear filtering (rendered at 0.2× scale).
    TexturePacker.process(TexturePacker.Settings().apply {
        maxWidth              = 4096
        maxHeight             = 4096
        rotation              = false
        combineSubdirectories = true
        stripWhitespaceX      = false
        stripWhitespaceY      = false
        flattenPaths          = false
        useIndexes            = false
        paddingX              = 2
        paddingY              = 2
        edgePadding           = true
        filterMin             = Texture.TextureFilter.Linear
        filterMag             = Texture.TextureFilter.Linear
    }, assetsDir.resolve("ragdoll_sprites").absolutePath, args[1], "ragdoll")
    println("Packed: ${args[1]}/ragdoll.atlas")

    // fx.atlas — particle effect sprites, Nearest filtering.
    TexturePacker.process(TexturePacker.Settings().apply {
        maxWidth              = 4096
        maxHeight             = 4096
        rotation              = false
        combineSubdirectories = true
        stripWhitespaceX      = false
        stripWhitespaceY      = false
        flattenPaths          = false
        useIndexes            = false
        paddingX              = 2
        paddingY              = 2
        edgePadding           = true
        filterMin             = Texture.TextureFilter.Nearest
        filterMag             = Texture.TextureFilter.Nearest
    }, assetsDir.resolve("fx_sprites").absolutePath, args[1], "fx")
    println("Packed: ${args[1]}/fx.atlas")

    // sprites.atlas — all sprites, Nearest filtering.
    // Tiles/entities drawn at ~1:1 scale; Nearest gives exact sampling with no bleeding.
    // Ninja regions are included but unused at runtime (ninja.atlas takes precedence).
    TexturePacker.process(TexturePacker.Settings().apply {
        maxWidth              = 4096
        maxHeight             = 4096
        rotation              = false
        combineSubdirectories = true
        stripWhitespaceX      = false
        stripWhitespaceY      = false
        flattenPaths          = false
        useIndexes            = false
        paddingX              = 2
        paddingY              = 2
        edgePadding           = true
        filterMin             = Texture.TextureFilter.Nearest
        filterMag             = Texture.TextureFilter.Nearest
    }, args[0], args[1], "sprites")
    println("Packed: ${args[1]}/sprites.atlas")

    // ninja.atlas — ninja frames only, Linear filtering.
    // Ninja sprites are 187×140 px Flash vector exports rendered at 0.2× (~37 px).
    // Linear filtering preserves stroke weight on the 5× downscale.
    TexturePacker.process(TexturePacker.Settings().apply {
        maxWidth              = 4096
        maxHeight             = 4096
        rotation              = false
        combineSubdirectories = false
        stripWhitespaceX      = false
        stripWhitespaceY      = false
        flattenPaths          = false
        useIndexes            = false
        paddingX              = 2
        paddingY              = 2
        edgePadding           = true
        filterMin             = Texture.TextureFilter.Linear
        filterMag             = Texture.TextureFilter.Linear
    }, File(args[0], "ninja").absolutePath, args[1], "ninja")
    println("Packed: ${args[1]}/ninja.atlas")
}
