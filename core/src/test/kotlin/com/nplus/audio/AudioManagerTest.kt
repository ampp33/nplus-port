package com.nplus.audio

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

class AudioManagerTest : FunSpec({

    test("NULL manager compiles and is a no-op") {
        val mgr = AudioManager.NULL
        mgr.tick()
        mgr.playSound("jump")
        mgr.playGold()
        mgr.playMusic("game")
        mgr.stopMusic()
        mgr.setVolume(0.5f)
        mgr.pause()
        mgr.resume()
        mgr.dispose()
        // If we get here without exception, the NULL manager is correct
        true shouldBe true
    }

    test("SOUND_MAP contains gold") {
        LibGdxAudioManager.SOUND_MAP["gold"] shouldNotBe null
        LibGdxAudioManager.SOUND_MAP["gold"] shouldBe "sounds/gold.wav"
    }

    test("SOUND_MAP contains all expected death sounds") {
        val expected = listOf("explode1", "explode2", "fall", "laser", "zap1", "zap2", "shot1", "shot2")
        for (name in expected) {
            (LibGdxAudioManager.SOUND_MAP[name] != null) shouldBe true
        }
    }

    test("SOUND_MAP contains all ragdoll impact sounds") {
        val expected = listOf("hard1", "hard2", "hard3", "med1", "med2", "soft1", "soft2")
        for (name in expected) {
            (LibGdxAudioManager.SOUND_MAP[name] != null) shouldBe true
        }
    }

    test("SOUND_MAP contains player movement sounds") {
        val expected = listOf("jump", "land")
        for (name in expected) {
            (LibGdxAudioManager.SOUND_MAP[name] != null) shouldBe true
        }
    }

    test("PRELOAD_EFFECTS list contains unique paths") {
        val paths = LibGdxAudioManager.PRELOAD_EFFECTS
        paths.size shouldBe paths.distinct().size
    }

    test("PRELOAD_EFFECTS contains the gold sound path") {
        LibGdxAudioManager.PRELOAD_EFFECTS.contains("sounds/gold.wav") shouldBe true
    }

    test("Tracking fake AudioManager receives correct calls") {
        val received = mutableListOf<String>()
        val fake = object : AudioManager {
            override fun tick() { received += "tick" }
            override fun playSound(name: String) { received += "sound:$name" }
            override fun playGold() { received += "gold" }
            override fun playMusic(name: String) { received += "music:$name" }
            override fun stopMusic() { received += "stop_music" }
            override fun setVolume(volume: Float) { received += "volume:$volume" }
            override fun pause() { received += "pause" }
            override fun resume() { received += "resume" }
            override fun dispose() { received += "dispose" }
        }

        fake.tick()
        fake.playGold()
        fake.playSound("jump")
        fake.playMusic("game")
        fake.stopMusic()

        received shouldBe listOf("tick", "gold", "sound:jump", "music:game", "stop_music")
    }
})
