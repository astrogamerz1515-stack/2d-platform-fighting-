package com.example.ui

import android.view.MotionEvent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.controller.KinematicCharacterController
import com.example.core.GameManager
import com.example.core.MatchState
import com.example.core.SceneManager
import com.example.core.SceneType
import com.example.engine.collision.ColliderType
import com.example.engine.input.GameInputSnapshot
import com.example.engine.input.InputButton
import com.example.fsm.CharacterStateType
import com.example.game.BrawlhallaGameEngine
import com.example.game.ParticleType
import com.example.localization.LocalizationManager
import com.example.localization.StringKey
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Main game screen integrating the 60Hz fixed-timestep game engine,
 * interactive multi-touch controls, real-time telemetry HUD, and 2D Canvas renderer.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun GameScreen() {
    val gameManager = GameManager.instance
    val sceneManager = SceneManager.instance
    val loc = LocalizationManager.instance

    val matchState by gameManager.matchState.collectAsState()
    val stocks by gameManager.playerStocks.collectAsState()
    val damageDealt by gameManager.matchDamageDealt.collectAsState()
    val kos by gameManager.matchKOCount.collectAsState()
    val timeRemaining by gameManager.matchTimeRemainingSeconds.collectAsState()

    val engine = remember {
        BrawlhallaGameEngine(
            onPlayerKO = { gameManager.recordPlayerKO() }
        )
    }

    // Register cleanup with SceneManager
    LaunchedEffect(Unit) {
        sceneManager.registerMemoryCleanup {
            engine.clearAllEffects()
        }
        if (matchState != MatchState.IN_PROGRESS && matchState != MatchState.PAUSED) {
            gameManager.startMatch()
        }
    }

    // Sync pause state with engine
    engine.isPaused = (matchState == MatchState.PAUSED)

    // Android Hardware/Gesture Back Handler
    BackHandler {
        if (matchState == MatchState.IN_PROGRESS) {
            gameManager.pauseMatch()
        } else if (matchState == MatchState.PAUSED) {
            gameManager.resumeMatch()
        } else {
            sceneManager.transitionTo(SceneType.MAIN_MENU)
        }
    }

    // Multi-touch input states
    var rawButtons by remember { mutableIntStateOf(InputButton.NONE) }
    var stickDeltaX by remember { mutableFloatStateOf(0f) }
    var stickDeltaY by remember { mutableFloatStateOf(0f) }

    // Telemetry display state (updated for UI reading)
    var hudState by remember { mutableStateOf(CharacterStateType.FALL) }
    var hudVelX by remember { mutableFloatStateOf(0f) }
    var hudVelY by remember { mutableFloatStateOf(0f) }
    var hudGrounded by remember { mutableStateOf(false) }
    var hudWallLeft by remember { mutableStateOf(false) }
    var hudWallRight by remember { mutableStateOf(false) }
    var hudAirJumps by remember { mutableIntStateOf(2) }
    var hudWallSlip by remember { mutableIntStateOf(0) }
    var hudFastFall by remember { mutableStateOf(false) }
    var hudFps by remember { mutableFloatStateOf(60f) }
    var hudRenderTick by remember { mutableLongStateOf(0L) }

    // Game Loop driven by Choreographer / withFrameNanos for tear-free 60/120Hz display refresh
    LaunchedEffect(Unit) {
        var lastFrameTimeNanos = 0L
        val inputSnapshot = GameInputSnapshot()

        while (true) {
            withFrameNanos { currentFrameTimeNanos ->
                if (lastFrameTimeNanos == 0L) {
                    lastFrameTimeNanos = currentFrameTimeNanos
                }
                val deltaSeconds = (currentFrameTimeNanos - lastFrameTimeNanos) / 1_000_000_000f
                lastFrameTimeNanos = currentFrameTimeNanos

                // Push current touch inputs into engine if not paused
                if (!engine.isPaused) {
                    inputSnapshot.buttons = rawButtons
                    inputSnapshot.stickX = stickDeltaX
                    inputSnapshot.stickY = stickDeltaY
                    inputSnapshot.timestampNanos = currentFrameTimeNanos
                    engine.processTouchInput(inputSnapshot)

                    gameManager.updateMatchTimer(deltaSeconds)
                }

                // Step simulation
                engine.update(deltaSeconds)

                // Update HUD telemetry
                hudState = engine.player.stateMachine.getCurrentStateType()
                hudVelX = engine.player.velocity.x
                hudVelY = engine.player.velocity.y
                hudGrounded = engine.player.isGrounded
                hudWallLeft = engine.player.isTouchingWallLeft
                hudWallRight = engine.player.isTouchingWallRight
                hudAirJumps = engine.player.remainingAirJumps
                hudWallSlip = engine.player.wallSlipTouches
                hudFastFall = engine.player.isFastFalling
                hudFps = engine.currentFps
                hudRenderTick = engine.totalFramesRendered
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF070B14))
    ) {
        // 1. Game Canvas: Renders Arena, Platforms, Character, Particles, Blast Zones
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .testTag("game_viewport_canvas")
        ) {
            val tick = hudRenderTick
            if (tick >= 0) {
                renderGameWorld(engine, size.width, size.height)
            }
        }

        // 2. Telemetry Overlay (Top Bar with Stocks & Pause button)
        TelemetryHeader(
            state = hudState,
            velX = hudVelX,
            velY = hudVelY,
            isGrounded = hudGrounded,
            isWallLeft = hudWallLeft,
            isWallRight = hudWallRight,
            airJumps = hudAirJumps,
            wallSlip = hudWallSlip,
            isFastFall = hudFastFall,
            fps = hudFps,
            stocks = stocks,
            kos = kos,
            timeRemaining = timeRemaining,
            isPaused = matchState == MatchState.PAUSED,
            onTogglePause = {
                if (matchState == MatchState.PAUSED) {
                    gameManager.resumeMatch()
                } else {
                    gameManager.pauseMatch()
                }
            },
            onResetStage = { engine.resetPlayerSpawn() },
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        )

        // 3. Bottom Controls Area (Virtual Joystick on Left, Action Buttons on Right)
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 18.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom
        ) {
            VirtualJoystick(
                onStickMoved = { x, y ->
                    stickDeltaX = x
                    stickDeltaY = y
                },
                modifier = Modifier.size(150.dp)
            )

            ActionButtonsPad(
                onButtonStateChanged = { mask, isPressed ->
                    rawButtons = if (isPressed) {
                        rawButtons or mask
                    } else {
                        rawButtons and mask.inv()
                    }
                }
            )
        }

        // 4. In-Game Pause Overlay Menu
        if (matchState == MatchState.PAUSED) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xB3030712))
                    .testTag("pause_menu_overlay"),
                contentAlignment = Alignment.Center
            ) {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xF00F172A)),
                    modifier = Modifier
                        .width(340.dp)
                        .border(1.dp, Color(0xFF38BDF8), RoundedCornerShape(16.dp))
                        .padding(16.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = loc.getString(StringKey.MATCH_PAUSED),
                            color = Color.White,
                            fontWeight = FontWeight.Black,
                            fontSize = 18.sp,
                            letterSpacing = 1.sp
                        )

                        Button(
                            onClick = { gameManager.resumeMatch() },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(44.dp)
                                .testTag("btn_pause_resume")
                        ) {
                            Text(loc.getString(StringKey.RESUME), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }

                        Button(
                            onClick = {
                                gameManager.startMatch()
                                engine.resetPlayerSpawn()
                                gameManager.resumeMatch()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B)),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(44.dp)
                                .border(1.dp, Color(0xFF475569), RoundedCornerShape(8.dp))
                                .testTag("btn_pause_restart")
                        ) {
                            Text(loc.getString(StringKey.RESTART), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }

                        Button(
                            onClick = {
                                sceneManager.transitionTo(SceneType.MAIN_MENU)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF334155)),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(44.dp)
                                .testTag("btn_pause_main_menu")
                        ) {
                            Text(loc.getString(StringKey.QUIT_TO_MENU), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }
                }
            }
        }
    }
}

/**
 * Top Telemetry HUD displaying physics invariants, FSM state, and performance metrics.
 */
@Composable
private fun TelemetryHeader(
    state: CharacterStateType,
    velX: Float,
    velY: Float,
    isGrounded: Boolean,
    isWallLeft: Boolean,
    isWallRight: Boolean,
    airJumps: Int,
    wallSlip: Int,
    isFastFall: Boolean,
    fps: Float,
    stocks: Int,
    kos: Int,
    timeRemaining: Float,
    isPaused: Boolean,
    onTogglePause: () -> Unit,
    onResetStage: () -> Unit,
    modifier: Modifier = Modifier
) {
    val minutes = (timeRemaining / 60f).toInt()
    val seconds = (timeRemaining % 60f).toInt()
    val formattedTime = String.format("%02d:%02d", minutes, seconds)

    Card(
        modifier = modifier.testTag("telemetry_header_card"),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xCC0F172A)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Left block: FSM State & Stocks
            Row(verticalAlignment = Alignment.CenterVertically) {
                // State Badge
                val stateColor = when (state) {
                    CharacterStateType.IDLE -> Color(0xFF10B981)
                    CharacterStateType.RUN -> Color(0xFF3B82F6)
                    CharacterStateType.JUMP -> Color(0xFF8B5CF6)
                    CharacterStateType.FALL -> Color(0xFFF59E0B)
                    CharacterStateType.DASH -> Color(0xFFEC4899)
                    CharacterStateType.WALL_CLING -> Color(0xFF06B6D4)
                    CharacterStateType.WALL_SLIDE -> Color(0xFF14B8A6)
                    CharacterStateType.HURT -> Color(0xFFEF4444)
                    CharacterStateType.KNOCKBACK -> Color(0xFFDC2626)
                }

                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = stateColor.copy(alpha = 0.25f),
                    modifier = Modifier.padding(end = 10.dp)
                ) {
                    Text(
                        text = state.name,
                        color = stateColor,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }

                // Stocks indicator
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(end = 12.dp)
                ) {
                    for (i in 0 until 3) {
                        Text(
                            text = if (i < stocks) "🛡" else "💀",
                            fontSize = 13.sp,
                            modifier = Modifier.padding(end = 2.dp)
                        )
                    }
                }

                Text(
                    text = "Vx: ${velX.roundToInt()} | Vy: ${velY.roundToInt()}",
                    color = Color.White,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(end = 10.dp)
                )

                Text(
                    text = "AirJumps: $airJumps/2",
                    color = if (airJumps > 0) Color(0xFF67E8F9) else Color(0xFF94A3B8),
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(end = 8.dp)
                )

                if (wallSlip > 0) {
                    Text(
                        text = "Slip: ${"!".repeat(wallSlip)}",
                        color = Color(0xFFF87171),
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                }

                if (isFastFall) {
                    Text(
                        text = "FAST-FALL",
                        color = Color(0xFFF59E0B),
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            // Right block: Timer, Contact indicators, Respawn & Pause Buttons
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Match Timer
                Text(
                    text = "⏱ $formattedTime",
                    color = Color(0xFFFCD34D),
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(end = 10.dp)
                )

                Text(
                    text = if (isGrounded) "GND" else if (isWallLeft) "WALL-L" else if (isWallRight) "WALL-R" else "AIR",
                    color = if (isGrounded) Color(0xFF34D399) else if (isWallLeft || isWallRight) Color(0xFF38BDF8) else Color(0xFFCBD5E1),
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(end = 10.dp)
                )

                Text(
                    text = "${fps.roundToInt()} FPS",
                    color = Color(0xFFE2E8F0),
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(end = 10.dp)
                )

                Button(
                    onClick = onResetStage,
                    shape = RoundedCornerShape(6.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF334155),
                        contentColor = Color.White
                    ),
                    modifier = Modifier
                        .height(30.dp)
                        .padding(end = 6.dp)
                        .testTag("reset_stage_button")
                ) {
                    Text("Respawn", fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                }

                Button(
                    onClick = onTogglePause,
                    shape = RoundedCornerShape(6.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isPaused) Color(0xFF10B981) else Color(0xFF0284C7),
                        contentColor = Color.White
                    ),
                    modifier = Modifier
                        .height(30.dp)
                        .testTag("btn_toggle_pause")
                ) {
                    Text(if (isPaused) "▶" else "⏸", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

/**
 * Virtual multi-touch joystick for 360-degree analog movement.
 */
@Composable
private fun VirtualJoystick(
    onStickMoved: (Float, Float) -> Unit,
    modifier: Modifier = Modifier
) {
    var thumbOffset by remember { mutableStateOf(Offset.Zero) }
    val maxRadiusPx = 110f

    Box(
        modifier = modifier
            .testTag("virtual_joystick_base")
            .clip(CircleShape)
            .background(Color(0x331E293B))
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { startOffset ->
                        val center = Offset(size.width / 2f, size.height / 2f)
                        val delta = startOffset - center
                        val dist = delta.getDistance()
                        val clamped = if (dist > maxRadiusPx) delta * (maxRadiusPx / dist) else delta
                        thumbOffset = clamped
                        onStickMoved(clamped.x / maxRadiusPx, clamped.y / maxRadiusPx)
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        val newOffset = thumbOffset + dragAmount
                        val dist = newOffset.getDistance()
                        val clamped = if (dist > maxRadiusPx) newOffset * (maxRadiusPx / dist) else newOffset
                        thumbOffset = clamped
                        onStickMoved(clamped.x / maxRadiusPx, clamped.y / maxRadiusPx)
                    },
                    onDragEnd = {
                        thumbOffset = Offset.Zero
                        onStickMoved(0f, 0f)
                    },
                    onDragCancel = {
                        thumbOffset = Offset.Zero
                        onStickMoved(0f, 0f)
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        // Outer ring
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawCircle(
                color = Color(0x66475569),
                radius = maxRadiusPx,
                style = Stroke(width = 2.dp.toPx())
            )
            // Center deadzone marker
            drawCircle(
                color = Color(0x3394A3B8),
                radius = 16f
            )
        }

        // Thumbstick knob
        Box(
            modifier = Modifier
                .offset { IntOffset(thumbOffset.x.roundToInt(), thumbOffset.y.roundToInt()) }
                .size(54.dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(Color(0xFF38BDF8), Color(0xFF0284C7))
                    )
                )
                .testTag("joystick_thumb_knob")
        )
    }
}

/**
 * On-screen fighting game action buttons (Jump, Dash/Dodge, Fast-Fall).
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun ActionButtonsPad(
    onButtonStateChanged: (Int, Boolean) -> Unit
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        // Fast-Fall / Down Button
        ArcadeActionButton(
            label = "DROP",
            subLabel = "FAST-FALL",
            color = Color(0xFFF59E0B),
            size = 64.dp,
            testTag = "button_fast_fall",
            onPressed = { onButtonStateChanged(InputButton.DOWN, true) },
            onReleased = { onButtonStateChanged(InputButton.DOWN, false) }
        )

        // Dash / Dodge Button
        ArcadeActionButton(
            label = "DASH",
            subLabel = "DODGE",
            color = Color(0xFFEC4899),
            size = 68.dp,
            testTag = "button_dash",
            onPressed = { onButtonStateChanged(InputButton.DASH, true) },
            onReleased = { onButtonStateChanged(InputButton.DASH, false) }
        )

        // Jump Button (Primary)
        ArcadeActionButton(
            label = "JUMP",
            subLabel = "SHORT/FULL",
            color = Color(0xFF38BDF8),
            size = 78.dp,
            testTag = "button_jump",
            onPressed = { onButtonStateChanged(InputButton.JUMP, true) },
            onReleased = { onButtonStateChanged(InputButton.JUMP, false) }
        )
    }
}

/**
 * Low-latency arcade button that accurately fires on Down and Up touch events.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun ArcadeActionButton(
    label: String,
    subLabel: String,
    color: Color,
    size: androidx.compose.ui.unit.Dp,
    testTag: String,
    onPressed: () -> Unit,
    onReleased: () -> Unit
) {
    var isPressed by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .size(size)
            .testTag(testTag)
            .clip(CircleShape)
            .background(
                Brush.radialGradient(
                    colors = if (isPressed) {
                        listOf(color, color.copy(alpha = 0.7f))
                    } else {
                        listOf(color.copy(alpha = 0.85f), color.copy(alpha = 0.35f))
                    }
                )
            )
            .pointerInteropFilter { motionEvent ->
                when (motionEvent.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        isPressed = true
                        onPressed()
                        true
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        isPressed = false
                        onReleased()
                        true
                    }
                    else -> false
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = label,
                color = Color.White,
                fontWeight = FontWeight.Black,
                fontSize = 13.sp,
                fontFamily = FontFamily.SansSerif
            )
            Text(
                text = subLabel,
                color = Color.White.copy(alpha = 0.8f),
                fontSize = 8.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

/**
 * Renders the entire platform fighter stage, camera transformation, player sprite, and VFX.
 */
private fun DrawScope.renderGameWorld(
    engine: BrawlhallaGameEngine,
    viewWidth: Float,
    viewHeight: Float
) {
    val player = engine.player
    val stage = engine.stage
    val cam = engine.cameraPosition

    // Calculate Camera viewport matrix
    val zoom = 0.95f
    val offsetX = viewWidth * 0.5f - cam.x * zoom
    val offsetY = viewHeight * 0.5f - cam.y * zoom

    fun worldToScreenX(wx: Float): Float = wx * zoom + offsetX
    fun worldToScreenY(wy: Float): Float = wy * zoom + offsetY
    fun worldToScreenW(w: Float): Float = w * zoom
    fun worldToScreenH(h: Float): Float = h * zoom

    // 1. Decorative Stage Background (Cyber/Mythical Arena Sky)
    drawRect(
        brush = Brush.verticalGradient(
            colors = listOf(Color(0xFF070B14), Color(0xFF0F172A), Color(0xFF1E1B4B))
        ),
        topLeft = Offset.Zero,
        size = Size(viewWidth, viewHeight)
    )

    // Blast Zone Warning Perimeter (Brawlhalla red boundary)
    val bzLeft = worldToScreenX(stage.blastZoneLeft)
    val bzTop = worldToScreenY(stage.blastZoneTop)
    val bzRight = worldToScreenX(stage.blastZoneRight)
    val bzBottom = worldToScreenY(stage.blastZoneBottom)

    drawRect(
        color = Color(0x33EF4444),
        topLeft = Offset(bzLeft, bzTop),
        size = Size(bzRight - bzLeft, bzBottom - bzTop),
        style = Stroke(width = 3.dp.toPx())
    )

    // 2. Render Stage Colliders (Platforms & Main Stage)
    for (col in stage.colliders) {
        val b = col.bounds
        val sx = worldToScreenX(b.minX)
        val sy = worldToScreenY(b.minY)
        val sw = worldToScreenW(b.width)
        val sh = worldToScreenH(b.height)

        if (col.type == ColliderType.SOLID) {
            // Main Stage Island (Stone & Crystal Platform)
            drawRoundRect(
                brush = Brush.verticalGradient(
                    colors = listOf(Color(0xFF1E293B), Color(0xFF0F172A))
                ),
                topLeft = Offset(sx, sy),
                size = Size(sw, sh),
                cornerRadius = CornerRadius(8.dp.toPx())
            )
            // Ledge / Top Edge Rim
            drawRoundRect(
                color = Color(0xFF38BDF8),
                topLeft = Offset(sx, sy),
                size = Size(sw, 6.dp.toPx()),
                cornerRadius = CornerRadius(4.dp.toPx())
            )
            // Vertical Wall Cliff Trim
            drawRect(
                color = Color(0xFF0284C7),
                topLeft = Offset(sx, sy),
                size = Size(4.dp.toPx(), sh)
            )
            drawRect(
                color = Color(0xFF0284C7),
                topLeft = Offset(sx + sw - 4.dp.toPx(), sy),
                size = Size(4.dp.toPx(), sh)
            )
        } else if (col.type == ColliderType.ONE_WAY_PLATFORM) {
            // Floating Soft Platform (Semi-transparent energy platform)
            drawRoundRect(
                brush = Brush.horizontalGradient(
                    colors = listOf(Color(0x3338BDF8), Color(0xAA38BDF8), Color(0x3338BDF8))
                ),
                topLeft = Offset(sx, sy),
                size = Size(sw, sh),
                cornerRadius = CornerRadius(4.dp.toPx())
            )
            // Surface Top Line
            drawLine(
                color = Color(0xFF7DD3FC),
                start = Offset(sx, sy),
                end = Offset(sx + sw, sy),
                strokeWidth = 3.dp.toPx()
            )
        }
    }

    // 3. Render Visual Particles (Dust, Sparks, Jump Rings)
    for (p in engine.particleSystem.particles) {
        if (!p.active) continue
        val px = worldToScreenX(p.x)
        val py = worldToScreenY(p.y)
        val pSize = p.size * zoom * p.life
        val alpha = (p.life).coerceIn(0f, 1f)

        when (p.type) {
            ParticleType.DUST -> {
                drawCircle(
                    color = Color(0xFF94A3B8).copy(alpha = alpha * 0.6f),
                    radius = pSize,
                    center = Offset(px, py)
                )
            }
            ParticleType.JUMP_RING -> {
                drawCircle(
                    color = Color(0xFF38BDF8).copy(alpha = alpha),
                    radius = (24f * (1.0f - p.life) + 10f) * zoom,
                    center = Offset(px, py),
                    style = Stroke(width = 2.dp.toPx())
                )
            }
            ParticleType.DASH_GHOST -> {
                drawCircle(
                    color = Color(0xFFEC4899).copy(alpha = alpha * 0.45f),
                    radius = pSize,
                    center = Offset(px, py)
                )
            }
            ParticleType.WALL_SPARK -> {
                drawCircle(
                    color = Color(0xFFFBBF24).copy(alpha = alpha),
                    radius = pSize,
                    center = Offset(px, py)
                )
            }
        }
    }

    // 4. Render Player Character
    val pw = worldToScreenW(player.config.colliderWidth)
    val ph = worldToScreenH(player.config.colliderHeight)
    val px = worldToScreenX(player.bounds.minX)
    val py = worldToScreenY(player.bounds.minY)

    // Character Invulnerability Aura (during active dash frames)
    if (player.isInvincible) {
        drawCircle(
            color = Color(0x66FDE047),
            radius = max(pw, ph) * 0.85f,
            center = Offset(px + pw * 0.5f, py + ph * 0.5f)
        )
    }

    // Character Body Silhouette & Color based on State
    val state = player.stateMachine.getCurrentStateType()
    val bodyColor = when (state) {
        CharacterStateType.IDLE -> Color(0xFF0EA5E9)
        CharacterStateType.RUN -> Color(0xFF2563EB)
        CharacterStateType.JUMP -> Color(0xFF8B5CF6)
        CharacterStateType.FALL -> Color(0xFFF59E0B)
        CharacterStateType.DASH -> Color(0xFFEC4899)
        CharacterStateType.WALL_CLING -> Color(0xFF06B6D4)
        CharacterStateType.WALL_SLIDE -> Color(0xFF14B8A6)
        CharacterStateType.HURT -> Color(0xFFEF4444)
        CharacterStateType.KNOCKBACK -> Color(0xFFDC2626)
    }

    // Main Torso / Armor
    drawRoundRect(
        color = bodyColor,
        topLeft = Offset(px, py),
        size = Size(pw, ph),
        cornerRadius = CornerRadius(10.dp.toPx())
    )

    // Visor / Eyes indicating facing direction
    val visorWidth = pw * 0.4f
    val visorHeight = ph * 0.16f
    val visorX = if (player.facingDirection > 0f) {
        px + pw * 0.55f
    } else {
        px + pw * 0.05f
    }
    val visorY = py + ph * 0.22f

    drawRoundRect(
        color = Color(0xFFF8FAFC),
        topLeft = Offset(visorX, visorY),
        size = Size(visorWidth, visorHeight),
        cornerRadius = CornerRadius(4.dp.toPx())
    )

    // Fast-Fall Downward Fire Trail
    if (player.isFastFalling) {
        drawLine(
            color = Color(0xFFEF4444),
            start = Offset(px + pw * 0.25f, py + ph),
            end = Offset(px + pw * 0.25f, py + ph + 28.dp.toPx()),
            strokeWidth = 3.dp.toPx()
        )
        drawLine(
            color = Color(0xFFF59E0B),
            start = Offset(px + pw * 0.5f, py + ph),
            end = Offset(px + pw * 0.5f, py + ph + 38.dp.toPx()),
            strokeWidth = 4.dp.toPx()
        )
        drawLine(
            color = Color(0xFFEF4444),
            start = Offset(px + pw * 0.75f, py + ph),
            end = Offset(px + pw * 0.75f, py + ph + 28.dp.toPx()),
            strokeWidth = 3.dp.toPx()
        )
    }

    // Brawlhalla Wall Slip Exclamation Indicator (!, !!, !!!)
    if (player.wallSlipTouches > 0) {
        val slipCount = player.wallSlipTouches
        val exMarkColor = if (slipCount >= 3) Color(0xFFEF4444) else Color(0xFFFBBF24)
        val textPaint = android.graphics.Paint().apply {
            color = exMarkColor.hashCode()
            textSize = 28f * zoom
            isFakeBoldText = true
            textAlign = android.graphics.Paint.Align.CENTER
        }
        drawContext.canvas.nativeCanvas.drawText(
            "!".repeat(slipCount),
            px + pw * 0.5f,
            py - 12.dp.toPx(),
            textPaint
        )
    }
}
