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

    @Test
    fun testSkeletalAnimationBridgeAndFramePerfectHitboxWindows() {
        val bridge = com.example.engine.animation.CharacterAnimationBridge()
        val controller = com.example.controller.KinematicCharacterController()
        val dt = 1.0f / 60.0f

        // Initial state should be Idle
        assertEquals(com.example.engine.animation.AnimationId.IDLE, bridge.currentAnimationId)
        assertFalse(bridge.isAttackLocked)
        assertFalse(bridge.activeHitboxes[0].isActive)

        // Trigger neutral attack
        val attackTriggered = bridge.triggerAttack(com.example.engine.animation.AnimationId.ATTACK_NEUTRAL)
        assertTrue(attackTriggered)
        assertTrue(bridge.isAttackLocked)
        assertEquals(com.example.engine.animation.AnimationId.ATTACK_NEUTRAL, bridge.currentAnimationId)

        // Advance frames 0 -> 3: Hitbox should NOT be active yet (starts at Frame 4)
        for (i in 0 until 3) {
            bridge.fixedUpdate(controller, dt)
        }
        assertFalse("Hitbox must be inactive before Frame 4", bridge.activeHitboxes[0].isActive)

        // Advance to Frame 4: Hitbox must be ACTIVATED with damage 12.0
        bridge.fixedUpdate(controller, dt)
        assertTrue("Hitbox must be active at Frame 4", bridge.activeHitboxes[0].isActive)
        assertEquals(12f, bridge.activeHitboxes[0].damage, 0.01f)

        // Advance through Frame 5 and 6: Hitbox remains active
        bridge.fixedUpdate(controller, dt) // Frame 5
        bridge.fixedUpdate(controller, dt) // Frame 6
        assertTrue("Hitbox must remain active during active window", bridge.activeHitboxes[0].isActive)

        // Advance to Frame 7+: Hitbox must be DEACTIVATED
        bridge.fixedUpdate(controller, dt) // Frame 7
        assertFalse("Hitbox must deactivate at Frame 7", bridge.activeHitboxes[0].isActive)

        // Disrupted State test: While attacking, trigger HURT/HITSTUN
        bridge.triggerAttack(com.example.engine.animation.AnimationId.ATTACK_SIDE)
        assertTrue(bridge.isAttackLocked)
        bridge.syncWithFsmState(
            state = com.example.fsm.CharacterStateType.HURT,
            vel = com.example.engine.math.Vector2(0f, 0f),
            facingDir = 1f,
            isGrounded = true,
            stickX = 0f,
            stickY = 0f,
            dt = dt,
            groundMaxSpeed = 500f
        )
        assertFalse("HitStun must immediately cancel attack lock", bridge.isAttackLocked)
        assertEquals(com.example.engine.animation.AnimationId.HITSTUN, bridge.currentAnimationId)
        assertFalse("HitStun must clear all active hitboxes", bridge.activeHitboxes[0].isActive)
    }

    @Test
    fun testSkeletalForwardKinematicsHierarchy() {
        val bridge = com.example.engine.animation.CharacterAnimationBridge()
        bridge.recalculateSkeletalMatrices(rootX = 100f, rootY = 200f, facingDir = 1.0f)

        val rootM = bridge.getWorldBoneMatrix(com.example.engine.animation.BoneId.ROOT)
        org.junit.Assert.assertNotNull(rootM)
        assertEquals(100f, rootM!!.m02, 0.01f)
        assertEquals(200f, rootM.m12, 0.01f)

        // Child Torso should have Y translation offset applied
        val torsoM = bridge.getWorldBoneMatrix(com.example.engine.animation.BoneId.TORSO)
        org.junit.Assert.assertNotNull(torsoM)
        assertTrue("Torso should be positioned above root", torsoM!!.m12 < rootM.m12)

        // Head should be positioned above Torso
        val headM = bridge.getWorldBoneMatrix(com.example.engine.animation.BoneId.HEAD)
        org.junit.Assert.assertNotNull(headM)
        assertTrue("Head should be positioned above torso", headM!!.m12 < torsoM.m12)
    }

    @Test
    fun testBrawlhallaCombatMatrixDirectionalMoveResolution() {
        val combatEngine = com.example.combat.CombatEngine()

        // Grounded Light Moves
        val nLight = combatEngine.resolveDirectionalMove(
            action = com.example.engine.input.BufferableAction.LIGHT_ATTACK,
            stickX = 0f,
            stickY = 0f,
            isGrounded = true
        )
        assertEquals(com.example.combat.BrawlhallaMoveType.NEUTRAL_LIGHT, nLight.moveType)

        val sLight = combatEngine.resolveDirectionalMove(
            action = com.example.engine.input.BufferableAction.LIGHT_ATTACK,
            stickX = 0.8f,
            stickY = 0f,
            isGrounded = true
        )
        assertEquals(com.example.combat.BrawlhallaMoveType.SIDE_LIGHT, sLight.moveType)

        val dLight = combatEngine.resolveDirectionalMove(
            action = com.example.engine.input.BufferableAction.LIGHT_ATTACK,
            stickX = 0f,
            stickY = 0.7f,
            isGrounded = true
        )
        assertEquals(com.example.combat.BrawlhallaMoveType.DOWN_LIGHT, dLight.moveType)

        // Grounded Signature Moves (Heavy)
        val nSig = combatEngine.resolveDirectionalMove(
            action = com.example.engine.input.BufferableAction.HEAVY_ATTACK,
            stickX = 0f,
            stickY = 0f,
            isGrounded = true
        )
        assertEquals(com.example.combat.BrawlhallaMoveType.NEUTRAL_SIG, nSig.moveType)

        val sSig = combatEngine.resolveDirectionalMove(
            action = com.example.engine.input.BufferableAction.HEAVY_ATTACK,
            stickX = -0.9f,
            stickY = 0f,
            isGrounded = true
        )
        assertEquals(com.example.combat.BrawlhallaMoveType.SIDE_SIG, sSig.moveType)

        val dSig = combatEngine.resolveDirectionalMove(
            action = com.example.engine.input.BufferableAction.HEAVY_ATTACK,
            stickX = 0f,
            stickY = 0.8f,
            isGrounded = true
        )
        assertEquals(com.example.combat.BrawlhallaMoveType.DOWN_SIG, dSig.moveType)

        // Aerial Moves
        val nAir = combatEngine.resolveDirectionalMove(
            action = com.example.engine.input.BufferableAction.LIGHT_ATTACK,
            stickX = 0f,
            stickY = 0f,
            isGrounded = false
        )
        assertEquals(com.example.combat.BrawlhallaMoveType.NEUTRAL_AIR, nAir.moveType)

        val sAir = combatEngine.resolveDirectionalMove(
            action = com.example.engine.input.BufferableAction.LIGHT_ATTACK,
            stickX = 0.7f,
            stickY = 0f,
            isGrounded = false
        )
        assertEquals(com.example.combat.BrawlhallaMoveType.SIDE_AIR, sAir.moveType)

        val dAir = combatEngine.resolveDirectionalMove(
            action = com.example.engine.input.BufferableAction.LIGHT_ATTACK,
            stickX = 0f,
            stickY = 0.9f,
            isGrounded = false
        )
        assertEquals(com.example.combat.BrawlhallaMoveType.DOWN_AIR, dAir.moveType)

        val recovery = combatEngine.resolveDirectionalMove(
            action = com.example.engine.input.BufferableAction.HEAVY_ATTACK,
            stickX = 0f,
            stickY = -0.5f,
            isGrounded = false
        )
        assertEquals(com.example.combat.BrawlhallaMoveType.RECOVERY, recovery.moveType)

        val groundPound = combatEngine.resolveDirectionalMove(
            action = com.example.engine.input.BufferableAction.HEAVY_ATTACK,
            stickX = 0f,
            stickY = 0.8f,
            isGrounded = false
        )
        assertEquals(com.example.combat.BrawlhallaMoveType.GROUND_POUND, groundPound.moveType)
    }

    @Test
    fun testNonBlockingMomentumPreservation() {
        val player = com.example.combat.IntegratedPlayerController()
        val dt = 1.0f / 60.0f
        val floor = ObstacleCollider(
            bounds = AABB(minX = 0f, minY = 200f, maxX = 1000f, maxY = 300f),
            type = ColliderType.SOLID
        )

        // Spawn and settle on floor
        player.setSpawnPosition(100f, 200f - player.config.colliderHeight * 0.5f)
        player.fixedUpdate(dt, listOf(floor))
        player.fixedUpdate(dt, listOf(floor))
        assertTrue("Player must be grounded", player.isGrounded)

        // Set running velocity
        player.velocity.x = 400f

        // Trigger Side Light attack while running
        player.combatEngine.recordAttackInput(
            action = com.example.engine.input.BufferableAction.LIGHT_ATTACK,
            stickX = 0.9f,
            stickY = 0f
        )

        // Tick one frame
        player.fixedUpdate(dt, listOf(floor))

        assertTrue("Player must be attacking", player.isAttacking)
        assertEquals(com.example.combat.BrawlhallaMoveType.SIDE_LIGHT, player.activeCombatMove?.moveType)

        // Momentum must be preserved and boosted by initialImpulseX rather than locked to 0!
        assertTrue(
            "Velocity must not be blocked or zeroed during attack (got ${player.velocity.x})",
            player.velocity.x > 300f
        )

        // Advance frames through active phase and verify slide deceleration
        val initialVel = player.velocity.x
        for (i in 0 until 5) {
            player.fixedUpdate(dt, listOf(floor))
        }
        assertTrue("Velocity should smoothly decay during slide", player.velocity.x < initialVel)
        assertTrue("Velocity should still remain positive during slide", player.velocity.x > 0f)
    }

    @Test
    fun testThreePhaseCombatFrameLifecycleAndHitboxWindows() {
        val player = com.example.combat.IntegratedPlayerController()
        val dt = 1.0f / 60.0f
        val floor = ObstacleCollider(
            bounds = AABB(minX = 0f, minY = 200f, maxX = 1000f, maxY = 300f),
            type = ColliderType.SOLID
        )

        // Spawn and settle on floor
        player.setSpawnPosition(100f, 200f - player.config.colliderHeight * 0.5f)
        player.fixedUpdate(dt, listOf(floor))
        player.fixedUpdate(dt, listOf(floor))
        assertTrue("Player must be grounded", player.isGrounded)

        // Trigger Neutral Light: Startup=4, Active=4, Recovery=8
        player.combatEngine.recordAttackInput(
            action = com.example.engine.input.BufferableAction.LIGHT_ATTACK,
            stickX = 0f,
            stickY = 0f
        )

        // Frame 0: Transitions to STARTUP
        player.fixedUpdate(dt, listOf(floor))
        assertEquals(com.example.combat.CombatPhase.STARTUP, player.currentCombatPhase)
        assertEquals(0, player.combatEngine.hitboxSpawner.getActiveHitboxes().size)

        // Advance remainder of startup frames
        for (i in 1 until 4) {
            player.fixedUpdate(dt, listOf(floor))
            assertEquals(com.example.combat.CombatPhase.STARTUP, player.currentCombatPhase)
        }

        // Frame 4: Transitions to ACTIVE phase, Hitbox spawns
        player.fixedUpdate(dt, listOf(floor))
        assertEquals(com.example.combat.CombatPhase.ACTIVE, player.currentCombatPhase)
        val activeHitboxes = player.combatEngine.hitboxSpawner.getActiveHitboxes()
        assertEquals(1, activeHitboxes.size)
        assertEquals(11f, activeHitboxes[0].damage, 0.01f)

        // Advance through active frames (4 frames total)
        for (i in 1 until 4) {
            player.fixedUpdate(dt, listOf(floor))
            assertEquals(com.example.combat.CombatPhase.ACTIVE, player.currentCombatPhase)
        }

        // Next frame transitions to RECOVERY, Hitboxes cleared
        player.fixedUpdate(dt, listOf(floor))
        assertEquals(com.example.combat.CombatPhase.RECOVERY, player.currentCombatPhase)
        assertEquals(0, player.combatEngine.hitboxSpawner.getActiveHitboxes().size)

        // Advance recovery frames (8 frames total)
        for (i in 1..8) {
            player.fixedUpdate(dt, listOf(floor))
        }

        // After recovery, returns to IDLE
        assertEquals(com.example.combat.CombatPhase.IDLE, player.currentCombatPhase)
        assertFalse(player.isAttacking)
    }

    @Test
    fun testCombatInputBufferQueueWindow() {
        val player = com.example.combat.IntegratedPlayerController()
        val dt = 1.0f / 60.0f
        val floor = ObstacleCollider(
            bounds = AABB(minX = 0f, minY = 200f, maxX = 1000f, maxY = 300f),
            type = ColliderType.SOLID
        )

        // Spawn and settle on floor
        player.setSpawnPosition(100f, 200f - player.config.colliderHeight * 0.5f)
        player.fixedUpdate(dt, listOf(floor))
        player.fixedUpdate(dt, listOf(floor))
        assertTrue("Player must be grounded", player.isGrounded)

        // Start Neutral Light
        player.combatEngine.recordAttackInput(
            action = com.example.engine.input.BufferableAction.LIGHT_ATTACK,
            stickX = 0f,
            stickY = 0f
        )
        player.fixedUpdate(dt, listOf(floor))
        assertTrue(player.isAttacking)

        // Advance until final recovery frames (15 frames total)
        for (i in 0 until 14) {
            player.fixedUpdate(dt, listOf(floor))
        }
        assertEquals(com.example.combat.CombatPhase.RECOVERY, player.currentCombatPhase)

        // Input an attack during recovery (should be buffered for 4-6 frames)
        player.combatEngine.recordAttackInput(
            action = com.example.engine.input.BufferableAction.LIGHT_ATTACK,
            stickX = 0.8f,
            stickY = 0f
        )

        // Advance remaining recovery frames
        player.fixedUpdate(dt, listOf(floor))
        player.fixedUpdate(dt, listOf(floor))

        // The buffered Side Light must fire immediately upon recovery completion!
        assertTrue("Buffered attack must fire on freedom frame", player.isAttacking)
        assertEquals(com.example.combat.BrawlhallaMoveType.SIDE_LIGHT, player.activeCombatMove?.moveType)
    }

    @Test
    fun testBrawlhallaKnockbackLaunchFormula() {
        val hitbox = com.example.combat.CombatHitbox().apply {
            baseKnockback = 200f
            knockbackScaling = 1.2f
            knockbackAngleDeg = 45f
            facingDirection = 1.0f
        }

        // Target at 100% damage
        val launch = hitbox.computeLaunchVector(targetDamagePercent = 100f)

        // Total magnitude = 200 + (100 * 0.1 * 1.2 * 1.2) = 200 + 14.4 = 214.4
        // With angle 45 deg: cos(45) * 214.4 = ~151.6
        val expectedMagnitude = 200f + (100f * 0.1f * 1.2f * 1.2f)
        val actualMagnitude = kotlin.math.sqrt(launch.x * launch.x + launch.y * launch.y)
        assertEquals(expectedMagnitude, actualMagnitude, 0.5f)
        assertTrue("X launch velocity must be positive when facing right", launch.x > 0f)
        assertTrue("Y launch velocity must be negative (upward)", launch.y < 0f)
    }

    @Test
    fun testAISpatialSensorStageZonesAndLineOfSightRaycast() {
        val sensor = com.example.ai.AISpatialSensor()
        val stage = com.example.game.BrawlStage()

        // 1. Test Stage Zone classification
        val centerBox = com.example.engine.collision.AABB().setFromCenter(500f, 350f, 20f, 30f)
        assertEquals(com.example.ai.StageZone.CENTER_STAGE, sensor.classifyStageZone(centerBox, stage))

        val leftLedgeBox = com.example.engine.collision.AABB().setFromCenter(250f, 370f, 20f, 30f)
        assertEquals(com.example.ai.StageZone.LEFT_LEDGE, sensor.classifyStageZone(leftLedgeBox, stage))

        val offStageLeftBox = com.example.engine.collision.AABB().setFromCenter(100f, 370f, 20f, 30f)
        assertEquals(com.example.ai.StageZone.OFFSTAGE_LEFT, sensor.classifyStageZone(offStageLeftBox, stage))

        val blastZoneBox = com.example.engine.collision.AABB().setFromCenter(500f, 750f, 20f, 30f)
        assertEquals(com.example.ai.StageZone.CRITICAL_BLAST_ZONE, sensor.classifyStageZone(blastZoneBox, stage))

        // 2. Test Line of Sight evaluation:
        // A. Clear vision on open stage
        val snapshotClear = sensor.evaluateEnvironment(
            botBounds = centerBox,
            botVelocity = com.example.engine.math.Vector2(0f, 0f),
            targetBounds = com.example.engine.collision.AABB().setFromCenter(600f, 350f, 20f, 30f),
            targetVelocity = com.example.engine.math.Vector2(0f, 0f),
            stage = stage,
            activeThreatHitboxes = emptyList()
        )
        assertTrue("Vision must be clear between two fighters on open stage", snapshotClear.hasLineOfSight)

        // B. Obstructed vision through solid main stage block
        val botUnderStage = com.example.engine.collision.AABB().setFromCenter(500f, 580f, 20f, 30f)
        val targetAboveStage = com.example.engine.collision.AABB().setFromCenter(500f, 200f, 20f, 30f)
        val snapshotObstructed = sensor.evaluateEnvironment(
            botBounds = botUnderStage,
            botVelocity = com.example.engine.math.Vector2(0f, 0f),
            targetBounds = targetAboveStage,
            targetVelocity = com.example.engine.math.Vector2(0f, 0f),
            stage = stage,
            activeThreatHitboxes = emptyList()
        )
        assertFalse("Vision must be blocked through solid stage block", snapshotObstructed.hasLineOfSight)
    }

    @Test
    fun testAIInputEmulatorReactionLatencyBuffer() {
        val config = com.example.ai.AIDifficultyConfig(
            level = com.example.ai.AIDifficultyLevel.HARD,
            reactionDelayFrames = 5,
            inputInaccuracyNoise = 0f,
            dodgeProbability = 0.8f,
            comboContinuationProbability = 0.8f,
            edgeGuardAggressiveness = 0.8f,
            signatureChargeProbability = 0.2f,
            microSpacingVariance = 0f,
            attackCommitmentRate = 0.8f
        )
        val emulator = com.example.ai.AIInputEmulator(config)
        val dt = 1.0f / 60.0f

        // Stage an intent to Jump and move right
        emulator.stagingIntent.requestJump = true
        emulator.stagingIntent.stickX = 1.0f

        // For frames 0 to 4: Output should NOT yet reflect the staged input due to 5-frame reaction delay!
        for (i in 0 until 5) {
            val snapshot = emulator.tick(dt)
            assertEquals("Jump must not be pressed before reaction latency expires", 0, snapshot.buttons and com.example.engine.input.InputButton.JUMP)
        }

        // On frame 5: Reaction latency expires, the delayed input must arrive!
        val delayedSnapshot = emulator.tick(dt)
        assertTrue("Jump must be pressed once reaction delay frames have elapsed", (delayedSnapshot.buttons and com.example.engine.input.InputButton.JUMP) != 0)
        assertEquals(1.0f, delayedSnapshot.stickX, 0.01f)
    }

    @Test
    fun testCombatAIStateTransitionsRecoveryAndAggressive() {
        val ai = com.example.ai.CombatAIController(
            difficultyLevel = com.example.ai.AIDifficultyLevel.TOURNAMENT
        )
        val stage = com.example.game.BrawlStage()
        val dt = 1.0f / 60.0f

        // Case 1: Bot is placed off-stage -> Must transition to DEFENSIVE_RECOVERY
        ai.setSpawnPosition(100f, 350f) // Far off left stage
        ai.fixedUpdate(
            dt = dt,
            targetBounds = com.example.engine.collision.AABB().setFromCenter(500f, 350f, 20f, 30f),
            targetVelocity = com.example.engine.math.Vector2(0f, 0f),
            targetIsOffStage = false,
            activeThreatHitboxes = emptyList(),
            stage = stage
        )
        assertEquals(com.example.ai.AICombatState.DEFENSIVE_RECOVERY, ai.currentState)
        assertTrue("Bot must steer horizontally back towards stage", ai.inputEmulator.stagingIntent.stickX > 0f)

        // Case 2: Bot placed close on-stage with target -> Transitions to AGGRESSIVE
        ai.setSpawnPosition(460f, 347f)
        val targetClose = com.example.engine.collision.AABB().setFromCenter(500f, 347f, 20f, 30f)
        ai.fixedUpdate(
            dt = dt,
            targetBounds = targetClose,
            targetVelocity = com.example.engine.math.Vector2(0f, 0f),
            targetIsOffStage = false,
            activeThreatHitboxes = emptyList(),
            stage = stage
        )
        assertEquals(com.example.ai.AICombatState.AGGRESSIVE, ai.currentState)
    }

    @Test
    fun testAIDifficultyProfileMatrixScaling() {
        val easy = com.example.ai.AIDifficultyConfig.getConfig(com.example.ai.AIDifficultyLevel.EASY)
        val tournament = com.example.ai.AIDifficultyConfig.getConfig(com.example.ai.AIDifficultyLevel.TOURNAMENT)

        assertTrue("Easy bot must have higher reaction delay than Tournament bot", easy.reactionDelayFrames > tournament.reactionDelayFrames)
        assertTrue("Easy bot must have higher input noise than Tournament bot", easy.inputInaccuracyNoise > tournament.inputInaccuracyNoise)
        assertTrue("Tournament bot must have higher dodge probability", tournament.dodgeProbability > easy.dodgeProbability)
        assertTrue("Tournament bot must have higher combo continuation rate", tournament.comboContinuationProbability > easy.comboContinuationProbability)
    }

    @Test
    fun testSceneManagerTransitionCancellationHandling() {
        val sceneManager = com.example.core.SceneManager.instance
        sceneManager.transitionTo(com.example.core.SceneType.MAIN_MENU, customDelayMs = 50L)
        sceneManager.transitionTo(com.example.core.SceneType.IN_GAME, customDelayMs = 50L)
        assertEquals(com.example.core.SceneType.IN_GAME, sceneManager.targetScene.value)
    }

    @Test
    fun testParallelStateDecouplingMovementWhileAttacking() {
        val controller = com.example.controller.ParallelCharacterController()
        val combatEngine = com.example.combat.BrawlhallaCombatEngine()
        val floor = com.example.engine.collision.ObstacleCollider(
            id = "floor",
            bounds = com.example.engine.collision.AABB(minX = 0f, minY = 400f, maxX = 1000f, maxY = 450f),
            type = com.example.engine.collision.ColliderType.SOLID
        )
        val obstacles = listOf(floor)
        val dt = 1.0f / 60.0f

        // Spawn on floor
        controller.setSpawnPosition(500f, 367f)
        controller.fixedUpdate(dt, obstacles)
        assertTrue("Controller must be grounded", controller.isGrounded)

        // Build running horizontal velocity
        controller.inputQueue.onRawInputUpdated(
            com.example.engine.input.GameInputSnapshot(stickX = 1.0f)
        )
        for (i in 0 until 10) {
            controller.fixedUpdate(dt, obstacles)
        }
        val runSpeedBeforeAttack = controller.velocity.x
        assertTrue("Character must have positive run speed", runSpeedBeforeAttack > 200f)

        // Initiate a Side-Light attack via Combat Engine while running
        controller.inputQueue.onRawInputUpdated(
            com.example.engine.input.GameInputSnapshot(
                buttons = com.example.engine.input.InputButton.LIGHT_ATTACK,
                stickX = 1.0f
            )
        )
        combatEngine.fixedUpdate(controller, null, dt)
        controller.fixedUpdate(dt, obstacles)

        // VERIFY PARALLEL DECOUPLING:
        // Movement state remains GROUNDED, action state becomes ATTACKING!
        assertEquals(com.example.controller.MovementTrackState.GROUNDED, controller.movementState)
        assertEquals(com.example.controller.ActionTrackState.ATTACKING, controller.actionState)
        assertTrue(
            "Horizontal velocity must NOT be clamped to zero during an attack!",
            controller.velocity.x > 150f
        )
    }

    @Test
    fun testDashJumpingVelocityTransfer() {
        val controller = com.example.controller.ParallelCharacterController()
        val floor = com.example.engine.collision.ObstacleCollider(
            id = "floor",
            bounds = com.example.engine.collision.AABB(minX = 0f, minY = 400f, maxX = 1000f, maxY = 450f),
            type = com.example.engine.collision.ColliderType.SOLID
        )
        val obstacles = listOf(floor)
        val dt = 1.0f / 60.0f

        controller.setSpawnPosition(500f, 367f)
        controller.fixedUpdate(dt, obstacles)

        // Trigger Dash on ground
        controller.inputQueue.onRawInputUpdated(
            com.example.engine.input.GameInputSnapshot(
                buttons = com.example.engine.input.InputButton.DASH,
                stickX = 1.0f
            )
        )
        controller.fixedUpdate(dt, obstacles)
        assertTrue("Character must be dashing", controller.isDashing)
        assertTrue("Dash jump must be eligible within 4-frame window", controller.dashJumpEligible)

        // Trigger Jump immediately (within 4-frame dash jump window)
        controller.inputQueue.onRawInputUpdated(
            com.example.engine.input.GameInputSnapshot(
                buttons = com.example.engine.input.InputButton.JUMP,
                stickX = 1.0f
            )
        )
        controller.fixedUpdate(dt, obstacles)

        // Verify Dash Jump velocity transfer:
        assertEquals(com.example.controller.MovementTrackState.AIRBORNE, controller.movementState)
        assertTrue("Vertical velocity must be ascending", controller.velocity.y < 0f)
        assertTrue(
            "Horizontal velocity must preserve dash speed (>600 px/s)",
            controller.velocity.x > 600f
        )
    }

    @Test
    fun testGravityCancelingAirborneFreeze() {
        val controller = com.example.controller.ParallelCharacterController()
        val combatEngine = com.example.combat.BrawlhallaCombatEngine()
        val dt = 1.0f / 60.0f

        // Spawn airborne
        controller.setSpawnPosition(500f, 200f)
        controller.fixedUpdate(dt, emptyList())
        assertEquals(com.example.controller.MovementTrackState.AIRBORNE, controller.movementState)

        // 1. Trigger Air Dodge (Spot/Directional Dodge)
        controller.inputQueue.onRawInputUpdated(
            com.example.engine.input.GameInputSnapshot(
                buttons = com.example.engine.input.InputButton.DASH
            )
        )
        controller.fixedUpdate(dt, emptyList())
        assertEquals(com.example.controller.ActionTrackState.DODGING, controller.actionState)
        assertTrue("Character must be invincible during dodge", controller.isInvincible)

        // 2. Trigger Heavy Attack within 6-frame window of dodge
        combatEngine.fixedUpdate(controller, null, dt)
        assertTrue("Gravity cancel must be eligible within 6-frame window of dodge", combatEngine.gravityCancelEligible)

        controller.inputQueue.onRawInputUpdated(
            com.example.engine.input.GameInputSnapshot(
                buttons = com.example.engine.input.InputButton.HEAVY_ATTACK
            )
        )
        combatEngine.fixedUpdate(controller, null, dt)
        controller.fixedUpdate(dt, emptyList())

        // VERIFY GRAVITY CANCEL EXECUTION:
        assertTrue("Move must be tagged as gravity-canceled", combatEngine.isGravityCanceledMove)
        assertTrue("Controller must have gravity cancel active", controller.isGravityCanceled)
        assertEquals(com.example.combat.BrawlhallaMoveType.NEUTRAL_SIG, combatEngine.activeMove?.moveType)
        assertEquals("Vertical velocity must be frozen to 0", 0f, controller.velocity.y, 0.01f)
    }

    @Test
    fun testChaseDodgeAndInvulnerabilityWindow() {
        val controller = com.example.controller.ParallelCharacterController()
        val combatEngine = com.example.combat.BrawlhallaCombatEngine()
        val dt = 1.0f / 60.0f

        controller.setSpawnPosition(500f, 300f)

        // Confirm hit grants Chase Dodge
        combatEngine.onHitConfirmed(controller)
        assertTrue("Chase dodge window must be granted (> 0)", controller.chaseDodgeWindowRemaining > 0f)

        // Trigger Dash during Chase Dodge window
        controller.inputQueue.onRawInputUpdated(
            com.example.engine.input.GameInputSnapshot(
                buttons = com.example.engine.input.InputButton.DASH,
                stickX = 1.0f
            )
        )
        controller.fixedUpdate(dt, emptyList())

        // Character must enter hyper-speed dodge and become invincible
        assertEquals(com.example.controller.ActionTrackState.DODGING, controller.actionState)
        assertTrue("Controller must be invincible during chase dodge", controller.isInvincible)
        assertTrue("Chase dodge must deliver hyper-speed (> 1000 px/s)", controller.velocity.x > 1000f)
    }

    @Test
    fun testDamageKnockbackSolverScalingAndBlastZoneKill() {
        val stage = com.example.game.BrawlStage()
        val solver = com.example.combat.DamageKnockbackSolver(stage)

        // 1. Test scaled knockback formula:
        // Knockback Vector = Base Knockback + (Player Damage % * Attack Knockback Scaling Factor) * Attacker Force Vector
        val payloadLowDamage = solver.solveKnockback(
            currentDamagePercent = 0f,
            attackDamage = 15f,
            baseKnockback = 200f,
            knockbackScaling = 1.5f,
            knockbackAngleDeg = 45f,
            attackerFacing = 1.0f
        )
        // With 15% damage: magnitude = 200 + (15 * 1.5) = 222.5
        assertEquals(15f, payloadLowDamage.resultingDamagePercent, 0.01f)
        val magLow = payloadLowDamage.knockbackVelocity.length()
        assertEquals(222.5f, magLow, 0.5f)

        val payloadHighDamage = solver.solveKnockback(
            currentDamagePercent = 100f,
            attackDamage = 20f,
            baseKnockback = 200f,
            knockbackScaling = 1.5f,
            knockbackAngleDeg = 45f,
            attackerFacing = 1.0f
        )
        // With 120% damage: magnitude = 200 + (120 * 1.5) = 380.0
        assertEquals(120f, payloadHighDamage.resultingDamagePercent, 0.01f)
        val magHigh = payloadHighDamage.knockbackVelocity.length()
        assertEquals(380.0f, magHigh, 0.5f)

        // 2. Test Blast Zone instant kill detection
        var killTriggered = false
        val outOfBoundsBox = com.example.engine.collision.AABB().setFromCenter(
            centerX = stage.blastZoneLeft - 50f,
            centerY = 500f,
            halfWidth = 20f,
            halfHeight = 30f
        )
        val wasKilled = solver.checkBlastZoneCollision(outOfBoundsBox) {
            killTriggered = true
        }
        assertTrue("Blast zone collision must return true", wasKilled)
        assertTrue("Instant kill callback must be triggered", killTriggered)
    }
}

