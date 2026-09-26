package com.example

import com.example.controller.CharacterMovementConfig
import com.example.controller.KinematicCharacterController
import com.example.engine.collision.AABB
import com.example.engine.collision.ColliderType
import com.example.engine.collision.ObstacleCollider
import com.example.engine.collision.SweptCollision
import com.example.engine.input.BufferableAction
import com.example.engine.input.GameInputSnapshot
import com.example.engine.input.InputBufferQueue
import com.example.engine.input.InputButton
import com.example.engine.math.Vector2
import com.example.fsm.CharacterStateType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {

    @Test
    fun testSweptAabbPreventsHighVelocityTunneling() {
        // Test a high-velocity knockback projectile moving at 3000 px/sec towards a 20px thin wall
        val box = AABB(minX = 0f, minY = 0f, maxX = 40f, maxY = 60f)
        val pos = Vector2(20f, 30f)
        val vel = Vector2(3000f, 0f) // 3000 px/sec
        val dt = 1.0f / 60.0f // 50px displacement per frame

        // Wall placed at X = 50f (only 10px in front of box.maxX)
        val wall = ObstacleCollider(
            bounds = AABB(minX = 50f, minY = -100f, maxX = 70f, maxY = 100f),
            type = ColliderType.SOLID
        )

        var hitGrounded = false
        var hitWall = false
        SweptCollision.moveAndSlide(
            box = box,
            position = pos,
            velocity = vel,
            dt = dt,
            obstacles = listOf(wall),
            oneWayDropThrough = false,
            onGrounded = { hitGrounded = true },
            onWallContact = { _, _ -> hitWall = true },
            onCeilingContact = {}
        )

        // Box must NOT tunnel past X = 50f!
        assertTrue("Swept collision must detect wall impact at high velocity", hitWall)
        assertTrue("Box.maxX (${box.maxX}) must not penetrate through Wall.minX (${wall.bounds.minX})", box.maxX <= wall.bounds.minX + 0.01f)
        assertEquals(0f, vel.x, 0.001f)
    }

    @Test
    fun testInputBufferQueueFrameAgingAndConsumption() {
        val queue = InputBufferQueue(defaultBufferWindowFrames = 5)

        // Enqueue Jump action
        queue.enqueueAction(BufferableAction.JUMP)
        assertTrue(queue.hasBufferedAction(BufferableAction.JUMP))

        // Age by 3 frames: should still be present
        queue.tickPhysicsFrame()
        queue.tickPhysicsFrame()
        queue.tickPhysicsFrame()
        assertTrue(queue.hasBufferedAction(BufferableAction.JUMP))

        // Consume action
        val consumed = queue.consumeAction(BufferableAction.JUMP)
        assertTrue("Buffered action must be consumed", consumed)
        assertFalse("Consumed action must no longer be present", queue.hasBufferedAction(BufferableAction.JUMP))

        // Re-enqueue and age past window
        queue.enqueueAction(BufferableAction.JUMP)
        for (i in 0 until 6) {
            queue.tickPhysicsFrame()
        }
        assertFalse("Action must expire after window", queue.hasBufferedAction(BufferableAction.JUMP))
    }

    @Test
    fun testCharacterControllerJumpAndDashTransitions() {
        val config = CharacterMovementConfig()
        val controller = KinematicCharacterController(config = config)

        // Floor obstacle
        val floor = ObstacleCollider(
            bounds = AABB(minX = 0f, minY = 200f, maxX = 400f, maxY = 300f),
            type = ColliderType.SOLID
        )

        controller.setSpawnPosition(100f, 150f)
        val dt = 1.0f / 60.0f

        // Fall onto floor
        for (i in 0 until 30) {
            controller.fixedUpdate(dt, listOf(floor))
        }

        assertTrue("Controller must be grounded on floor", controller.isGrounded)
        assertEquals(CharacterStateType.IDLE, controller.stateMachine.getCurrentStateType())

        // Buffer Jump
        controller.inputQueue.enqueueAction(BufferableAction.JUMP)
        controller.fixedUpdate(dt, listOf(floor))

        assertEquals("Controller must enter JUMP state", CharacterStateType.JUMP, controller.stateMachine.getCurrentStateType())
        assertTrue("Velocity Y must be upward", controller.velocity.y < 0f)
    }

    @Test
    fun testDashInvulnerabilityFrames() {
        val controller = KinematicCharacterController()
        val floor = ObstacleCollider(
            bounds = AABB(minX = 0f, minY = 200f, maxX = 400f, maxY = 300f),
            type = ColliderType.SOLID
        )
        controller.setSpawnPosition(100f, 200f - controller.config.colliderHeight * 0.5f)
        val dt = 1.0f / 60.0f

        // Initial ticks to detect floor and transition from initial FALL to IDLE
        controller.fixedUpdate(dt, listOf(floor))
        controller.fixedUpdate(dt, listOf(floor))
        assertEquals(CharacterStateType.IDLE, controller.stateMachine.getCurrentStateType())

        // Trigger Dash
        controller.inputQueue.enqueueAction(BufferableAction.DASH)
        controller.fixedUpdate(dt, listOf(floor)) // Frame 1 of dash

        assertEquals(CharacterStateType.DASH, controller.stateMachine.getCurrentStateType())

        // Frame 2..10 should have invincibility active
        controller.fixedUpdate(dt, listOf(floor)) // Frame 2
        assertTrue("Invincibility should be active during dash", controller.isInvincible)
    }

    @Test
    fun testSecureSaveSystemEncryptionAndTamperDetection() {
        val tempDir = java.nio.file.Files.createTempDirectory("brawl_test_save").toFile()
        try {
            val saveSystem = com.example.persistence.SecureSaveSystem(
                saveDirectory = tempDir,
                devicePassphrase = "TEST_AES_GCM_SECRET_KEY_123"
            )

            // 1. Initial save
            val testData = com.example.persistence.GameSaveData(
                totalMatchesPlayed = 42,
                totalWins = 30,
                totalLosses = 12,
                totalKOs = 88,
                highestDamageDealt = 450.5f,
                currentWinStreak = 5,
                bestWinStreak = 9,
                selectedCharacterId = "cyber_monk",
                selectedStageId = "crystal_spire"
            )

            val saved = saveSystem.save(testData)
            assertTrue("Save must succeed atomically", saved)

            // 2. Read back and verify all fields preserved
            val loaded = saveSystem.load()
            assertEquals(42, loaded.totalMatchesPlayed)
            assertEquals(30, loaded.totalWins)
            assertEquals(12, loaded.totalLosses)
            assertEquals(88, loaded.totalKOs)
            assertEquals(450.5f, loaded.highestDamageDealt, 0.01f)
            assertEquals(5, loaded.currentWinStreak)
            assertEquals(9, loaded.bestWinStreak)
            assertEquals("cyber_monk", loaded.selectedCharacterId)
            assertEquals("crystal_spire", loaded.selectedStageId)

            // 3. Tamper detection test: Modify bytes in the save file
            val saveFile = java.io.File(tempDir, "savegame.dat")
            assertTrue("Save file must exist on disk", saveFile.exists())
            val fileBytes = saveFile.readBytes()
            // Flip bits in the middle of ciphertext
            val tamperedByteIndex = fileBytes.size - 20
            fileBytes[tamperedByteIndex] = (fileBytes[tamperedByteIndex].toInt() xor 0xFF).toByte()
            saveFile.writeBytes(fileBytes)

            // 4. Attempt load on tampered file: Must detect tampering and safely fallback to backup or default
            val recovered = saveSystem.load()
            // Because backup was created before tampering, it recovers original 30 wins
            assertEquals("Must recover from backup on tampered file", 30, recovered.totalWins)

            // 5. Corrupt both primary and backup
            if (saveFile.exists()) {
                val primBytes = saveFile.readBytes()
                primBytes[primBytes.size - 20] = (primBytes[primBytes.size - 20].toInt() xor 0xFF).toByte()
                saveFile.writeBytes(primBytes)
            }
            val backupFile = java.io.File(tempDir, "savegame.dat.bak")
            if (backupFile.exists()) {
                val bakBytes = backupFile.readBytes()
                bakBytes[bakBytes.size - 20] = (bakBytes[bakBytes.size - 20].toInt() xor 0xFF).toByte()
                backupFile.writeBytes(bakBytes)
            }

            val defaultRecovered = saveSystem.load()
            // When all copies are tampered, safe default is produced without crashing
            assertEquals("Must fall back to clean default profile when all copies are corrupted", 0, defaultRecovered.totalMatchesPlayed)
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun testLocalizationManagerMultiLanguageTranslation() {
        val loc = com.example.localization.LocalizationManager.instance

        loc.setLanguage(com.example.localization.SupportedLanguage.ENGLISH)
        assertEquals("Brawl Arena", loc.getString(com.example.localization.StringKey.APP_NAME))
        assertEquals("PLAY BRAWL", loc.getString(com.example.localization.StringKey.MENU_PLAY))

        loc.setLanguage(com.example.localization.SupportedLanguage.SPANISH)
        assertEquals("JUGAR COMBATE", loc.getString(com.example.localization.StringKey.MENU_PLAY))

        loc.setLanguage(com.example.localization.SupportedLanguage.JAPANESE)
        assertEquals("対戦開始", loc.getString(com.example.localization.StringKey.MENU_PLAY))

        // Reset back to English for consistent tests
        loc.setLanguage(com.example.localization.SupportedLanguage.ENGLISH)
    }

    @Test
    fun testUIManagerScreenStackAndHexColors() {
        val ui = com.example.ui.UIManager.instance

        // Verify explicit Hex color contracts
        assertEquals("#FF4500", com.example.ui.UIManager.HEX_PRIMARY_ACTION)
        assertEquals("#00FFCC", com.example.ui.UIManager.HEX_ACTIVE_SELECTION)
        assertEquals("#070B14", com.example.ui.UIManager.HEX_BACKGROUND_DARK)
        assertEquals("#121A2E", com.example.ui.UIManager.HEX_SURFACE_CARD)

        // Screen stack operations
        ui.clearScreenStack()
        assertEquals(0, ui.currentStackDepth.value)

        ui.pushScreen("main_menu")
        ui.pushScreen("character_select")
        assertEquals(2, ui.currentStackDepth.value)
        assertEquals("character_select", ui.peekScreen())

        val popped = ui.popScreen()
        assertEquals("character_select", popped)
        assertEquals(1, ui.currentStackDepth.value)
        assertEquals("main_menu", ui.peekScreen())

        ui.clearScreenStack()
        assertEquals(0, ui.currentStackDepth.value)

        // Dialog state
        ui.showDialog(com.example.ui.DialogType.SETTINGS)
        assertEquals(com.example.ui.DialogType.SETTINGS, ui.activeDialog.value)
        ui.dismissDialog()
        assertEquals(com.example.ui.DialogType.NONE, ui.activeDialog.value)
    }
}

