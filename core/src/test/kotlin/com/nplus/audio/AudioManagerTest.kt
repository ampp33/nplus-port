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
        mgr.startLoopSound("wallslide")
        mgr.stopLoopSound("wallslide")
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

    test("SOUND_MAP assigns player sounds to playerMC range (727-734)") {
        val playerSounds = listOf("jump", "land", "explode1", "explode2", "fall", "laser", "shot1", "shot2")
        for (name in playerSounds) {
            val path = LibGdxAudioManager.SOUND_MAP[name]
            val num = path?.removePrefix("sounds/")?.removeSuffix(".wav")?.toIntOrNull()
            (num != null && num in 727..734) shouldBe true
        }
    }

    test("SOUND_MAP assigns ragdoll sounds to confirmed range") {
        // User-confirmed: 525-529 = hard/medium impacts, 532-533 = zap; 531 = soft (best-guess)
        val ragdollSounds = listOf("hard1", "hard2", "hard3", "med1", "med2", "zap1", "zap2")
        for (name in ragdollSounds) {
            val path = LibGdxAudioManager.SOUND_MAP[name]
            val num = path?.removePrefix("sounds/")?.removeSuffix(".wav")?.toIntOrNull()
            (num != null && num in 525..533) shouldBe true
        }
    }

    test("SOUND_MAP contains entity sounds") {
        val entitySounds = listOf(
            "launchpad", "mine_explode", "rocket_fire", "turret_prefire",
            "guard_chase", "drone_chase", "laser_charge", "exit_switch"
        )
        for (name in entitySounds) {
            (LibGdxAudioManager.SOUND_MAP[name] != null) shouldBe true
        }
    }

    test("LOOP_MAP contains wallslide and skid") {
        LibGdxAudioManager.LOOP_MAP.containsKey("wallslide") shouldBe true
        LibGdxAudioManager.LOOP_MAP.containsKey("skid") shouldBe true
        // Loop sounds come from playerLoopMC (symbol 712), embedded sounds 710-711
        for ((_, path) in LibGdxAudioManager.LOOP_MAP) {
            val num = path.removePrefix("sounds/").removeSuffix(".wav").toIntOrNull()
            (num != null && num in 710..711) shouldBe true
        }
    }

    test("PRELOAD_EFFECTS list contains unique paths") {
        val paths = LibGdxAudioManager.PRELOAD_EFFECTS
        paths.size shouldBe paths.distinct().size
    }

    test("PRELOAD_EFFECTS contains the gold sound path") {
        LibGdxAudioManager.PRELOAD_EFFECTS.contains("sounds/gold.wav") shouldBe true
    }

    test("PRELOAD_EFFECTS includes loop sound paths") {
        LibGdxAudioManager.PRELOAD_EFFECTS.contains("sounds/710.wav") shouldBe true
        LibGdxAudioManager.PRELOAD_EFFECTS.contains("sounds/711.wav") shouldBe true
    }

    test("Tracking fake AudioManager receives correct calls") {
        val received = mutableListOf<String>()
        val fake = object : AudioManager {
            override fun tick() { received += "tick" }
            override fun playSound(name: String) { received += "sound:$name" }
            override fun playGold() { received += "gold" }
            override fun startLoopSound(name: String) { received += "loop_start:$name" }
            override fun stopLoopSound(name: String) { received += "loop_stop:$name" }
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
