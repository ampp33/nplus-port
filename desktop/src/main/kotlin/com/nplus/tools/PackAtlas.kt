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

    // fx.atlas — particle effect sprites, Linear filtering (3x source, same rationale as sprites).
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
    }, assetsDir.resolve("fx_sprites").absolutePath, args[1], "fx")
    println("Packed: ${args[1]}/fx.atlas")

    // sprites.atlas — all sprites, Linear filtering.
    // Sprites are 3x resolution; at the ~2.3x viewport scale they are slightly downscaled.
    // Linear filter gives clean sampling without the pixelation of Nearest.
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
    }, args[0], args[1], "sprites")
    println("Packed: ${args[1]}/sprites.atlas")

    // ninja.atlas — ninja frames only, Linear filtering.
    // Ninja sprites are 561×420 px (3× Flash canvas) rendered at 0.2/3× scale (~37 px).
    // Source dir is assets/ninja_sprites/ (outside assets/sprites/ so they are not
    // also packed into sprites.atlas, which would waste 3 extra 4096×4096 pages).
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
    }, assetsDir.resolve("ninja_sprites").absolutePath, args[1], "ninja")
    println("Packed: ${args[1]}/ninja.atlas")
}
