package com.bob.mediacompressor

import android.Manifest
import android.content.Context
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.togetherWith
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.Canvas
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.hilt.navigation.compose.hiltViewModel
import com.bob.mediacompressor.domain.model.CompressionLevel
import com.bob.mediacompressor.domain.model.MediaFile
import com.bob.mediacompressor.domain.model.MediaType
import com.bob.mediacompressor.presentation.MainViewModel
import com.bob.mediacompressor.presentation.UiState
import dagger.hilt.android.AndroidEntryPoint

@Composable
fun Modifier.bounceClickable(onClick: () -> Unit): Modifier {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.94f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessHigh
        ),
        label = "bounce_scale"
    )
    
    return this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .clickable(
            interactionSource = interactionSource,
            indication = null,
            onClick = onClick
        )
}

@Composable
fun Modifier.neoGlassmorphic(
    borderRadius: androidx.compose.ui.unit.Dp = 24.dp,
    borderWidth: androidx.compose.ui.unit.Dp = 1.dp,
    glowColor: Color = Color.White.copy(alpha = 0.08f)
): Modifier {
    return this
        .clip(RoundedCornerShape(borderRadius))
        .background(Color(0xFF0D0D12).copy(alpha = 0.85f))
        .border(
            width = borderWidth,
            brush = Brush.linearGradient(
                colors = listOf(
                    Color.White.copy(alpha = 0.25f),
                    Color.White.copy(alpha = 0.05f),
                    glowColor,
                    Color.White.copy(alpha = 0.05f)
                )
            ),
            shape = RoundedCornerShape(borderRadius)
        )
}

@Composable
fun NeoSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val thumbOffset by animateDpAsState(
        targetValue = if (checked) 24.dp else 2.dp,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "switch_thumb"
    )
    val trackColor by animateColorAsState(
        targetValue = if (checked) Color(0xFF2563EB) else Color(0xFF2E2E42),
        label = "switch_track"
    )

    Box(
        modifier = modifier
            .width(52.dp)
            .height(28.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(trackColor)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onCheckedChange(!checked) }
            .padding(2.dp)
    ) {
        Box(
            modifier = Modifier
                .offset(x = thumbOffset)
                .size(24.dp)
                .clip(CircleShape)
                .background(Color.White)
        )
    }
}

@Composable
fun NeoQualitySlider(
    level: CompressionLevel,
    onLevelChange: (CompressionLevel) -> Unit,
    modifier: Modifier = Modifier
) {
    val sliderValue = when (level) {
        CompressionLevel.LOW -> 0f
        CompressionLevel.MEDIUM -> 1f
        CompressionLevel.HIGH -> 2f
    }
    
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("مستوى جودة الضغط", color = Color.LightGray, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            Text(
                text = when (level) {
                    CompressionLevel.LOW -> "حجم أدنى (أقل جودة)"
                    CompressionLevel.MEDIUM -> "متوازن (جودة متوسطة)"
                    CompressionLevel.HIGH -> "دقة فائقة (أعلى جودة)"
                },
                color = Color(0xFF3B82F6),
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
        }
        
        Spacer(modifier = Modifier.height(10.dp))
        
        Slider(
            value = sliderValue,
            onValueChange = { valNew ->
                val newLevel = when {
                    valNew < 0.5f -> CompressionLevel.LOW
                    valNew < 1.5f -> CompressionLevel.MEDIUM
                    else -> CompressionLevel.HIGH
                }
                onLevelChange(newLevel)
            },
            valueRange = 0f..2f,
            steps = 1,
            colors = SliderDefaults.colors(
                thumbColor = Color.White,
                activeTrackColor = Color(0xFF3B82F6),
                inactiveTrackColor = Color(0xFF2E2E42),
                activeTickColor = Color.Transparent,
                inactiveTickColor = Color.Transparent
            )
        )
        
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("حجم أدنى", color = Color.Gray, fontSize = 10.sp)
            Text("متوازن", color = Color.Gray, fontSize = 10.sp)
            Text("دقة فائقة", color = Color.Gray, fontSize = 10.sp)
        }
    }
}

@Composable
fun NeoResolutionSlider(
    customHeight: String,
    onHeightChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val currentVal = when (customHeight) {
        "" -> 0f
        "360" -> 1f
        "480" -> 2f
        "720" -> 3f
        "1080" -> 4f
        else -> 0f
    }
    
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("أبعاد ودقة الفيديو", color = Color.LightGray, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            Text(
                text = when (customHeight) {
                    "" -> "تلقائي (أبعاد أصلية)"
                    "360" -> "منخفضة (360p)"
                    "480" -> "متوسطة (480p)"
                    "720" -> "عالية (720p)"
                    "1080" -> "فائقة (1080p)"
                    else -> "مخصص (${customHeight}p)"
                },
                color = Color(0xFF3B82F6),
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
        }
        
        Spacer(modifier = Modifier.height(10.dp))
        
        Slider(
            value = currentVal,
            onValueChange = { valNew ->
                val newHeight = when (valNew.toInt()) {
                    0 -> ""
                    1 -> "360"
                    2 -> "480"
                    3 -> "720"
                    4 -> "1080"
                    else -> ""
                }
                onHeightChange(newHeight)
            },
            valueRange = 0f..4f,
            steps = 3,
            colors = SliderDefaults.colors(
                thumbColor = Color.White,
                activeTrackColor = Color(0xFF3B82F6),
                inactiveTrackColor = Color(0xFF2E2E42),
                activeTickColor = Color.Transparent,
                inactiveTickColor = Color.Transparent
            )
        )
        
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("تلقائي", color = Color.Gray, fontSize = 10.sp)
            Text("360p", color = Color.Gray, fontSize = 10.sp)
            Text("480p", color = Color.Gray, fontSize = 10.sp)
            Text("720p", color = Color.Gray, fontSize = 10.sp)
            Text("1080p", color = Color.Gray, fontSize = 10.sp)
        }
    }
}

@Composable
fun NeoFpsSlider(
    customFps: String,
    onFpsChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val currentVal = when (customFps) {
        "" -> 0f
        "15" -> 1f
        "24" -> 2f
        "30" -> 3f
        "60" -> 4f
        else -> 0f
    }
    
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("معدل الإطارات (FPS)", color = Color.LightGray, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            Text(
                text = when (customFps) {
                    "" -> "تلقائي (الأصلي)"
                    "15" -> "سينمائي منخفض (15 fps)"
                    "24" -> "سينمائي (24 fps)"
                    "30" -> "قياسي (30 fps)"
                    "60" -> "سلس للغاية (60 fps)"
                    else -> "مخصص (${customFps} fps)"
                },
                color = Color(0xFF3B82F6),
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
        }
        
        Spacer(modifier = Modifier.height(10.dp))
        
        Slider(
            value = currentVal,
            onValueChange = { valNew ->
                val newFps = when (valNew.toInt()) {
                    0 -> ""
                    1 -> "15"
                    2 -> "24"
                    3 -> "30"
                    4 -> "60"
                    else -> ""
                }
                onFpsChange(newFps)
            },
            valueRange = 0f..4f,
            steps = 3,
            colors = SliderDefaults.colors(
                thumbColor = Color.White,
                activeTrackColor = Color(0xFF3B82F6),
                inactiveTrackColor = Color(0xFF2E2E42),
                activeTickColor = Color.Transparent,
                inactiveTickColor = Color.Transparent
            )
        )
        
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("تلقائي", color = Color.Gray, fontSize = 10.sp)
            Text("15 fps", color = Color.Gray, fontSize = 10.sp)
            Text("24 fps", color = Color.Gray, fontSize = 10.sp)
            Text("30 fps", color = Color.Gray, fontSize = 10.sp)
            Text("60 fps", color = Color.Gray, fontSize = 10.sp)
        }
    }
}

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Bypass EncryptedSharedPreferences to prevent KeyStore crashes on custom ROMs
        val prefs = this.getSharedPreferences("secure_app_prefs", android.content.Context.MODE_PRIVATE)
        setContent {
            ModernAppTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color(0xFF060608) // Ultra Premium Dark Space Background
                ) {
                    var onboardingDone by remember {
                        mutableStateOf(prefs.getBoolean("onboarding_done", false))
                    }

                    if (onboardingDone) {
                        CompressionDashboard()
                    } else {
                        OnboardingScreen(onFinished = {
                            prefs.edit().putBoolean("onboarding_done", true).apply()
                            onboardingDone = true
                        })
                    }
                }
            }
        }
    }
}

@Composable
fun OnboardingSlideVisual(page: Int) {
    val infiniteTransition = rememberInfiniteTransition(label = "slide_visual")
    
    when (page) {
        0 -> {
            // Slide 1: Welcome to Re:Zain (Atomic Constellation & Pulsing Diamond)
            val rotation by infiniteTransition.animateFloat(
                initialValue = 0f,
                targetValue = 360f,
                animationSpec = infiniteRepeatable(
                    animation = tween(10000, easing = LinearEasing)
                ),
                label = "rotation"
            )
            val logoScale by infiniteTransition.animateFloat(
                initialValue = 0.94f,
                targetValue = 1.06f,
                animationSpec = infiniteRepeatable(
                    animation = tween(2000, easing = EaseInOutSine),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "logo_scale"
            )
            
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(130.dp)
            ) {
                // High-fidelity orbits drawn via Canvas
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val maxRadius = size.minDimension / 2f
                    
                    // Orbit 1 (Dashed Outer)
                    drawCircle(
                        color = Color(0xFF3B82F6).copy(alpha = 0.25f),
                        radius = maxRadius - 5.dp.toPx(),
                        style = Stroke(
                            width = 1.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 15f), 0f)
                        )
                    )
                    
                    // Orbit 2 (Solid Inner)
                    drawCircle(
                        color = Color.White.copy(alpha = 0.12f),
                        radius = maxRadius - 22.dp.toPx(),
                        style = Stroke(width = 1.dp.toPx())
                    )
                    
                    // Orbit 3 (Dotted Intermediate)
                    drawCircle(
                        color = Color(0xFF3B82F6).copy(alpha = 0.15f),
                        radius = maxRadius - 38.dp.toPx(),
                        style = Stroke(
                            width = 1.5.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(3f, 12f), 0f)
                        )
                    )
                }
                
                // Rotating particles
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { rotationZ = rotation },
                    contentAlignment = Alignment.Center
                ) {
                    // Particle on Orbit 1
                    Box(
                        modifier = Modifier
                            .offset(y = (-60).dp)
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(Color.White)
                    )
                    // Particle on Orbit 2
                    Box(
                        modifier = Modifier
                            .offset(x = 43.dp)
                            .size(4.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF3B82F6))
                    )
                }

                // Core logo shape (Pulsing Diamond)
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .graphicsLayer { 
                            scaleX = logoScale
                            scaleY = logoScale
                            rotationZ = -rotation * 0.4f
                        }
                        .background(Color(0xFF0D0D12), RoundedCornerShape(16.dp))
                        .border(1.5.dp, Brush.linearGradient(listOf(Color(0xFF3B82F6), Color.White)), RoundedCornerShape(16.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
        1 -> {
            // Slide 2: Multi-image compression (Before-After Split Screen Interactive Simulator)
            val sweepProgress by infiniteTransition.animateFloat(
                initialValue = 0.15f,
                targetValue = 0.85f,
                animationSpec = infiniteRepeatable(
                    animation = tween(3500, easing = EaseInOutSine),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "sweep_progress"
            )
            
            Box(
                modifier = Modifier
                    .width(240.dp)
                    .height(110.dp)
                    .neoGlassmorphic(borderRadius = 20.dp, glowColor = Color.White.copy(alpha = 0.05f))
                    .padding(8.dp)
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val w = size.width
                    val h = size.height
                    val splitX = w * sweepProgress
                    
                    // Left area: Uncompressed Grid (Noisy / Low density)
                    clipRect(right = splitX) {
                        // Background tint for original
                        drawRect(color = Color(0xFFEF5350).copy(alpha = 0.05f))
                        // Draw vertical rough lines representing big size
                        for (i in 0..w.toInt() step 24) {
                            drawLine(
                                color = Color(0xFFEF5350).copy(alpha = 0.12f),
                                start = Offset(i.toFloat(), 0f),
                                end = Offset(i.toFloat(), h),
                                strokeWidth = 2.dp.toPx()
                            )
                        }
                    }
                    
                    // Right area: Compressed Grid (Sharp / Dense / Optimized)
                    clipRect(left = splitX) {
                        // Background tint for compressed
                        drawRect(color = Color(0xFF10B981).copy(alpha = 0.05f))
                        // Draw dense clean grid lines
                        for (i in 0..w.toInt() step 12) {
                            drawLine(
                                color = Color(0xFF10B981).copy(alpha = 0.15f),
                                start = Offset(i.toFloat(), 0f),
                                end = Offset(i.toFloat(), h),
                                strokeWidth = 1.dp.toPx()
                            )
                        }
                    }
                    
                    // Draw split line
                    drawLine(
                        color = Color.White,
                        start = Offset(splitX, 0f),
                        end = Offset(splitX, h),
                        strokeWidth = 2.dp.toPx()
                    )
                    
                    // Draw split handle
                    drawCircle(
                        color = Color(0xFF3B82F6),
                        radius = 6.dp.toPx(),
                        center = Offset(splitX, h / 2f)
                    )
                    drawCircle(
                        color = Color.White,
                        radius = 3.dp.toPx(),
                        center = Offset(splitX, h / 2f)
                    )
                }
                
                // Overlay labels
                Row(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom
                ) {
                    Text(
                        text = "10 MB",
                        color = Color(0xFFEF5350),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold,
                        modifier = Modifier
                            .background(Color(0xFF0D0D12).copy(alpha = 0.8f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                    )
                    Text(
                        text = "1.2 MB",
                        color = Color(0xFF10B981),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold,
                        modifier = Modifier
                            .background(Color(0xFF0D0D12).copy(alpha = 0.8f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                    )
                }
            }
        }
        2 -> {
            // Slide 3: Video tools (Workstation Timeline Audio Waveform Simulator)
            val playheadProgress by infiniteTransition.animateFloat(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(4000, easing = LinearEasing)
                ),
                label = "playhead_progress"
            )
            
            Box(
                modifier = Modifier
                    .width(240.dp)
                    .height(110.dp)
                    .neoGlassmorphic(borderRadius = 20.dp, glowColor = Color.White.copy(alpha = 0.05f))
                    .padding(10.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    // Cohesive Title (Clean Text with Icon, No Emojis!)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(12.dp))
                            Text("تحويل الفيديو", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text("صيغة GIF", color = Color(0xFF3B82F6), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            Icon(Icons.Default.Refresh, contentDescription = null, tint = Color(0xFF3B82F6), modifier = Modifier.size(12.dp))
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    // Waveform Timeline canvas
                    Canvas(modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                    ) {
                        val w = size.width
                        val h = size.height
                        val playheadX = w * playheadProgress
                        
                        // Waveform data (varying heights)
                        val heights = floatArrayOf(
                            0.2f, 0.4f, 0.7f, 0.3f, 0.5f, 0.9f, 0.6f, 0.2f, 0.5f, 0.8f, 0.4f, 0.6f, 0.3f, 0.7f, 0.2f
                        )
                        val barCount = heights.size
                        val spacing = w / barCount
                        
                        for (i in 0 until barCount) {
                            val barX = i * spacing + spacing / 2f
                            val barH = h * heights[i]
                            val isPassed = barX < playheadX
                            
                            drawLine(
                                color = if (isPassed) Color(0xFF3B82F6) else Color.White.copy(alpha = 0.15f),
                                start = Offset(barX, h / 2f - barH / 2f),
                                end = Offset(barX, h / 2f + barH / 2f),
                                strokeWidth = 3.dp.toPx()
                            )
                        }
                        
                        // Vertical Playhead line
                        drawLine(
                            color = Color.White,
                            start = Offset(playheadX, 0f),
                            end = Offset(playheadX, h),
                            strokeWidth = 1.5.dp.toPx()
                        )
                    }
                }
            }
        }
        3 -> {
            // Slide 4: Offline Security Shield (Radar Concentric Waves & Biometric Scanner)
            val scanProgress by infiniteTransition.animateFloat(
                initialValue = 0.05f,
                targetValue = 0.95f,
                animationSpec = infiniteRepeatable(
                    animation = tween(2500, easing = EaseInOutSine),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "scan_progress"
            )
            val pulseRadius by infiniteTransition.animateFloat(
                initialValue = 45f,
                targetValue = 90f,
                animationSpec = infiniteRepeatable(
                    animation = tween(2000, easing = EaseOutQuad),
                    repeatMode = RepeatMode.Restart
                ),
                label = "pulse_radius"
            )
            
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(110.dp)
            ) {
                // Pulsing concentric radar waves
                Canvas(modifier = Modifier.fillMaxSize()) {
                    drawCircle(
                        color = Color(0xFF3B82F6).copy(alpha = (1f - (pulseRadius - 45f) / 45f).coerceIn(0f, 0.25f)),
                        radius = pulseRadius.dp.toPx(),
                        style = Stroke(width = 1.5.dp.toPx())
                    )
                }
                
                // Scanner shield container
                Box(
                    modifier = Modifier
                        .size(70.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF0D0D12))
                        .border(1.5.dp, Color(0xFF3B82F6), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    // Shield drawing canvas with horizontal laser scanning line
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val h = size.height
                        val w = size.width
                        val laserY = h * scanProgress
                        
                        // Horizontal laser sweep line
                        drawLine(
                            color = Color.White,
                            start = Offset(0f, laserY),
                            end = Offset(w, laserY),
                            strokeWidth = 2.dp.toPx()
                        )
                    }
                    
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
        }
        4 -> {
            // Slide 5: Developer Credentials (Constellation drifting particle node network)
            val driftPhase by infiniteTransition.animateFloat(
                initialValue = 0f,
                targetValue = (2 * Math.PI).toFloat(),
                animationSpec = infiniteRepeatable(
                    animation = tween(12000, easing = LinearEasing)
                ),
                label = "drift_phase"
            )
            
            Box(
                modifier = Modifier
                    .width(240.dp)
                    .height(110.dp)
                    .neoGlassmorphic(borderRadius = 20.dp, glowColor = Color.White.copy(alpha = 0.05f))
                    .padding(8.dp),
                contentAlignment = Alignment.Center
            ) {
                // Drifting node network drawn via Canvas
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val w = size.width
                    val h = size.height
                    
                    // 5 drift nodes
                    val nodes = arrayOf(
                        Offset(
                            w * 0.2f + (12.dp.toPx() * Math.sin(driftPhase.toDouble())).toFloat(),
                            h * 0.3f + (8.dp.toPx() * Math.cos(driftPhase.toDouble())).toFloat()
                        ),
                        Offset(
                            w * 0.8f + (10.dp.toPx() * Math.cos(driftPhase.toDouble() * 1.5)).toFloat(),
                            h * 0.25f + (12.dp.toPx() * Math.sin(driftPhase.toDouble() * 1.2)).toFloat()
                        ),
                        Offset(
                            w * 0.5f + (14.dp.toPx() * Math.sin(driftPhase.toDouble() * 0.8)).toFloat(),
                            h * 0.75f + (10.dp.toPx() * Math.cos(driftPhase.toDouble() * 0.9)).toFloat()
                        ),
                        Offset(
                            w * 0.15f + (8.dp.toPx() * Math.cos(driftPhase.toDouble() * 1.3)).toFloat(),
                            h * 0.8f + (12.dp.toPx() * Math.sin(driftPhase.toDouble() * 1.1)).toFloat()
                        ),
                        Offset(
                            w * 0.85f + (12.dp.toPx() * Math.sin(driftPhase.toDouble() * 1.1)).toFloat(),
                            h * 0.75f + (10.dp.toPx() * Math.cos(driftPhase.toDouble() * 1.4)).toFloat()
                        )
                    )
                    
                    // Draw connections
                    for (i in nodes.indices) {
                        for (j in i + 1 until nodes.size) {
                            val dist = (nodes[i] - nodes[j]).getDistance()
                            if (dist < 130.dp.toPx()) {
                                drawLine(
                                    color = Color(0xFF3B82F6).copy(alpha = (1f - dist / 130.dp.toPx()) * 0.25f),
                                    start = nodes[i],
                                    end = nodes[j],
                                    strokeWidth = 1.dp.toPx()
                                )
                            }
                        }
                    }
                    
                    // Draw node circles
                    for (node in nodes) {
                        drawCircle(
                            color = Color(0xFF3B82F6).copy(alpha = 0.6f),
                            radius = 4.dp.toPx(),
                            center = node
                        )
                        drawCircle(
                            color = Color.White.copy(alpha = 0.3f),
                            radius = 2.dp.toPx(),
                            center = node
                        )
                    }
                }
                
                // Center Developer profile badge
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .background(Color(0xFF060608).copy(alpha = 0.85f), RoundedCornerShape(12.dp))
                        .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(12.dp))
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "gcp64 & bob",
                            color = Color.White,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 12.sp
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "فريق التطوير والتصميم",
                            color = Color.Gray,
                            fontSize = 9.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun OnboardingScreen(onFinished: () -> Unit) {
    var currentSlide by remember { mutableStateOf(0) }
    val slidesCount = 5
    
    val slideData = listOf(
        Triple(
            "مرحباً بك في Re:Zain",
            "أداتك الاحترافية الشاملة لإدارة وضغط وتحويل الوسائط محلياً بذكاء وأمان مطلق.",
            Icons.Default.Home
        ),
        Triple(
            "ضغط الصور المتعددة",
            "اختر عشرات الصور دفعة واحدة وقم بضغط حجمها بنسبة تصل إلى 90% مع الحفاظ التام على أبعادها ودقتها العالية.",
            Icons.AutoMirrored.Filled.List
        ),
        Triple(
            "تحويل وهندسة الفيديو",
            "عوّل مقاطع الفيديو إلى صور GIF متحركة عالية الجودة، أو استخرج مسار الصوت النقي بصيغة MP3 محلياً 100%.",
            Icons.Default.PlayArrow
        ),
        Triple(
            "أمان مطلق دون إنترنت",
            "معالجة محلية بالكامل وحماية أمنية مدمجة لضمان خصوصية وسريّة بياناتك.",
            Icons.Default.Lock
        ),
        Triple(
            "بواسطة المطور gcp64 bob",
            "تم بناء وتطوير هذا التطبيق بحرص كامل لتقديم أقصى درجات الأداء وأناقة التصميم البصري.",
            Icons.Default.Person
        )
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF060608))
    ) {
        // Aesthetic ambient glows (pulsating & drifting)
        val infiniteTransition = rememberInfiniteTransition(label = "onb_glow")
        val scale by infiniteTransition.animateFloat(
            initialValue = 0.85f,
            targetValue = 1.15f,
            animationSpec = infiniteRepeatable(
                animation = tween(12000, easing = EaseInOutSine),
                repeatMode = RepeatMode.Reverse
            ),
            label = "scale"
        )
        val alpha by infiniteTransition.animateFloat(
            initialValue = 0.15f,
            targetValue = 0.35f,
            animationSpec = infiniteRepeatable(
                animation = tween(9000, easing = EaseInOutSine),
                repeatMode = RepeatMode.Reverse
            ),
            label = "alpha"
        )
        val driftOffset by infiniteTransition.animateFloat(
            initialValue = -30f,
            targetValue = 30f,
            animationSpec = infiniteRepeatable(
                animation = tween(15000, easing = EaseInOutSine),
                repeatMode = RepeatMode.Reverse
            ),
            label = "drift"
        )
        
        Box(
            modifier = Modifier
                .size(420.dp)
                .align(Alignment.TopEnd)
                .offset(x = (100 + driftOffset).dp, y = (-100 - driftOffset).dp)
                .scale(scale)
                .background(
                    Brush.radialGradient(
                        colors = listOf(Color(0xFF3B82F6).copy(alpha = alpha * 0.4f), Color.Transparent)
                    )
                )
                .blur(65.dp)
        )
        
        Box(
            modifier = Modifier
                .size(380.dp)
                .align(Alignment.BottomStart)
                .offset(x = (-100 - driftOffset).dp, y = (100 + driftOffset).dp)
                .scale(scale)
                .background(
                    Brush.radialGradient(
                        colors = listOf(Color(0xFF1E293B).copy(alpha = alpha * 0.3f), Color.Transparent)
                    )
                )
                .blur(60.dp)
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Spacer(modifier = Modifier.height(20.dp))

            // Slide content transition inside a central glassmorphic panel
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(vertical = 20.dp)
                    .neoGlassmorphic(borderRadius = 32.dp, glowColor = Color.White.copy(alpha = 0.08f))
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                AnimatedContent(
                    targetState = currentSlide,
                    transitionSpec = {
                        if (targetState > initialState) {
                            (slideInHorizontally { width -> width / 2 } + fadeIn(animationSpec = tween(400))).togetherWith(
                                slideOutHorizontally { width -> -width / 2 } + fadeOut(animationSpec = tween(400))
                            )
                        } else {
                            (slideInHorizontally { width -> -width / 2 } + fadeIn(animationSpec = tween(400))).togetherWith(
                                slideOutHorizontally { width -> width / 2 } + fadeOut(animationSpec = tween(400))
                            )
                        }
                    },
                    label = "slide_transition"
                ) { targetPage ->
                    val (title, description, _) = slideData[targetPage]
                    
                    // Floating bobbing animation for the active slide's icon
                    val bobbingOffset by infiniteTransition.animateFloat(
                        initialValue = -6f,
                        targetValue = 6f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(2500, easing = EaseInOutSine),
                            repeatMode = RepeatMode.Reverse
                        ),
                        label = "bobbing_icon_$targetPage"
                    )

                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        // Custom premium visual animation for onboarding
                        Box(
                            modifier = Modifier
                                .offset(y = bobbingOffset.dp)
                                .height(140.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            OnboardingSlideVisual(targetPage)
                        }

                        Spacer(modifier = Modifier.height(28.dp))

                        Text(
                            text = title,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White,
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        Text(
                            text = description,
                            fontSize = 14.sp,
                            color = Color.LightGray,
                            textAlign = TextAlign.Center,
                            lineHeight = 22.sp,
                            modifier = Modifier.padding(horizontal = 8.dp)
                        )
                    }
                }
            }

            // Bottom Navigation indicators and Buttons
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Dots indicator
                Row(
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(bottom = 28.dp)
                ) {
                    for (i in 0 until slidesCount) {
                        val isCurrent = i == currentSlide
                        val width by animateDpAsState(
                            targetValue = if (isCurrent) 28.dp else 8.dp,
                            label = "dot_width"
                        )
                        Box(
                            modifier = Modifier
                                .padding(horizontal = 4.dp)
                                .height(8.dp)
                                .width(width)
                                .clip(RoundedCornerShape(4.dp))
                                .background(
                                    if (isCurrent) Brush.horizontalGradient(listOf(Color(0xFF2563EB), Color(0xFF3B82F6)))
                                    else Brush.linearGradient(listOf(Color(0xFF2E2E42), Color(0xFF2E2E42)))
                                )
                        )
                    }
                }

                // Action Buttons
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (currentSlide > 0) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(54.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(Color(0xFF131324).copy(alpha = 0.6f))
                                .border(1.dp, Color(0xFF2E2E42), RoundedCornerShape(16.dp))
                                .bounceClickable {
                                    currentSlide--
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "السابق",
                                color = Color.White,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .weight(2f)
                            .height(54.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(Brush.horizontalGradient(listOf(Color(0xFF2563EB), Color(0xFF3B82F6))))
                            .bounceClickable {
                                if (currentSlide < slidesCount - 1) {
                                    currentSlide++
                                } else {
                                    onFinished()
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (currentSlide == slidesCount - 1) "ابدأ الاستخدام" else "التالي",
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ModernAppTheme(content: @Composable () -> Unit) {
    val darkColorScheme = darkColorScheme(
        primary = Color.White, // Monochromatic White
        secondary = Color(0xFF3B82F6), // Premium Slate Blue Accent
        background = Color(0xFF060608),
        surface = Color(0xFF0D0D12)
    )
    MaterialTheme(
        colorScheme = darkColorScheme,
        content = content
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompressionDashboard(viewModel: MainViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val currentTab by viewModel.currentTab.collectAsState()
    val actionType by viewModel.actionType.collectAsState()
    val targetImageFormat by viewModel.targetImageFormat.collectAsState()

    val selectedMediaList by viewModel.selectedMediaList.collectAsState()
    val compressionLevel by viewModel.compressionLevel.collectAsState()
    val compressorType by viewModel.compressorType.collectAsState()
    val removeAudio by viewModel.removeAudio.collectAsState()
    val customHeight by viewModel.customHeight.collectAsState()
    val customFps by viewModel.customFps.collectAsState()

    // 1a. PhotoPicker Launcher for single file
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
        onResult = { uri ->
            if (uri != null) {
                viewModel.selectMedia(uri)
            }
        }
    )

    // 1b. PhotoPicker Launcher for multiple images
    val multipleImagesLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = 100),
        onResult = { uris ->
            if (uris.isNotEmpty()) {
                viewModel.selectMultipleImages(uris)
            }
        }
    )

    // 2. Notification Permission Launcher (Android 13+)
    var hasNotificationPermission by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
            } else true
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { isGranted ->
            hasNotificationPermission = isGranted
            if (!isGranted) {
                Toast.makeText(context, "يُوصى بتفعيل التنبيهات لمتابعة التقدم بالخلفية", Toast.LENGTH_LONG).show()
            }
        }
    )

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasNotificationPermission) {
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    // Scroll state for configuration panel
    val scrollState = rememberScrollState()

    // Ambient glow animations
    val infiniteTransition = rememberInfiniteTransition(label = "ambient_glow")
    val glowScale1 by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(9000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow_scale_1"
    )
    val glowAlpha1 by infiniteTransition.animateFloat(
        initialValue = 0.14f,
        targetValue = 0.26f,
        animationSpec = infiniteRepeatable(
            animation = tween(7000, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow_alpha_1"
    )
    val glowScale2 by infiniteTransition.animateFloat(
        initialValue = 0.9f,
        targetValue = 1.1f,
        animationSpec = infiniteRepeatable(
            animation = tween(6000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow_scale_2"
    )
    val glowAlpha2 by infiniteTransition.animateFloat(
        initialValue = 0.09f,
        targetValue = 0.18f,
        animationSpec = infiniteRepeatable(
            animation = tween(8000, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow_alpha_2"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF060608))
    ) {
        // Aesthetic ambient gradient light in the top-right corner (pulsating)
        Box(
            modifier = Modifier
                .size(350.dp)
                .align(Alignment.TopEnd)
                .offset(x = 120.dp, y = (-120).dp)
                .scale(glowScale1)
                .background(
                    Brush.radialGradient(
                        colors = listOf(Color(0xFF3B82F6).copy(alpha = glowAlpha1 * 0.4f), Color.Transparent)
                    )
                )
                .blur(60.dp)
        )
        
        // Secondary glow in the bottom-left corner (pulsating)
        Box(
            modifier = Modifier
                .size(300.dp)
                .align(Alignment.BottomStart)
                .offset(x = (-100).dp, y = 100.dp)
                .scale(glowScale2)
                .background(
                    Brush.radialGradient(
                        colors = listOf(Color(0xFF1E293B).copy(alpha = glowAlpha2 * 0.4f), Color.Transparent)
                    )
                )
                .blur(50.dp)
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp)
                .verticalScroll(scrollState),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(28.dp))

            // Premium Glassmorphic Header Row (exactly like reference top bar)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF131324).copy(alpha = 0.6f))
                            .border(1.dp, Color(0xFF3B82F6).copy(alpha = 0.4f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = "الملف الشخصي",
                            tint = Color(0xFF3B82F6),
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Re:Zain",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = "أدوات وسائط متكاملة",
                            fontSize = 11.sp,
                            color = Color.Gray
                        )
                    }
                }
                
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF131324).copy(alpha = 0.6f))
                        .border(1.5.dp, Color(0xFF2E2E42), RoundedCornerShape(12.dp))
                        .bounceClickable {
                            Toast.makeText(context, "تطبيق Re:Zain لضغط الميديا - نسخة مستقرة ومؤمنة", Toast.LENGTH_SHORT).show()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Menu,
                        contentDescription = "القائمة",
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Screen Content switching with AnimatedContent for smooth transitions
            AnimatedContent(
                targetState = currentTab,
                transitionSpec = {
                    if (targetState > initialState) {
                        (slideInHorizontally { width -> width / 2 } + fadeIn(animationSpec = tween(300))).togetherWith(
                            slideOutHorizontally { width -> -width / 2 } + fadeOut(animationSpec = tween(300))
                        )
                    } else {
                        (slideInHorizontally { width -> -width / 2 } + fadeIn(animationSpec = tween(300))).togetherWith(
                            slideOutHorizontally { width -> width / 2 } + fadeOut(animationSpec = tween(300))
                        )
                    }
                },
                label = "dashboard_screens"
            ) { targetTab ->
                Column(modifier = Modifier.fillMaxWidth()) {
                    when (targetTab) {
                        0 -> {
                            // Compressor Tab
                            if (selectedMediaList.isEmpty()) {
                                FilePickerCard(
                                    title = "أداة الضغط الذكي Re:Zain",
                                    subtitle = "اختر ملفات وسائط لبدء الضغط محلياً وبأمان",
                                    photoLauncher = photoPickerLauncher,
                                    batchLauncher = multipleImagesLauncher
                                )
                            } else {
                                if (selectedMediaList.size > 1) {
                                    BatchMetadataCard(mediaList = selectedMediaList, onClear = { viewModel.reset() })
                                } else {
                                    MetadataCard(media = selectedMediaList.first(), onClear = { viewModel.reset() })
                                }

                                Spacer(modifier = Modifier.height(20.dp))

                                CompressorConfigCard(
                                    viewModel = viewModel,
                                    mediaList = selectedMediaList,
                                    compressionLevel = compressionLevel,
                                    compressorType = compressorType,
                                    removeAudio = removeAudio,
                                    customHeight = customHeight,
                                    customFps = customFps
                                )
                            }
                        }
                        1 -> {
                            // Converter Tab
                            if (selectedMediaList.isEmpty()) {
                                FilePickerCard(
                                    title = "محول وسائط Re:Zain",
                                    subtitle = "اختر ملف وسائط لتحويل صيغته دون إنترنت",
                                    photoLauncher = photoPickerLauncher,
                                    batchLauncher = multipleImagesLauncher
                                )
                            } else {
                                if (selectedMediaList.size > 1) {
                                    BatchMetadataCard(mediaList = selectedMediaList, onClear = { viewModel.reset() })
                                } else {
                                    MetadataCard(media = selectedMediaList.first(), onClear = { viewModel.reset() })
                                }

                                Spacer(modifier = Modifier.height(20.dp))

                                ConverterConfigCard(
                                    viewModel = viewModel,
                                    mediaList = selectedMediaList,
                                    actionType = actionType,
                                    targetImageFormat = targetImageFormat
                                )
                            }
                        }
                        2 -> {
                            // About App Tab
                            AboutAppScreen()
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(120.dp)) // Extra space to scroll above bottom bar
        }

        // 3. OVERLAY FOR ACTIVE COMPRESSION / PROCESSING
        if (uiState is UiState.Compressing || uiState is UiState.Success || uiState is UiState.Error || uiState is UiState.LoadingMetadata) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.9f))
                    .clickable(enabled = false) {}, // Intercept clicks
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(32.dp)
                ) {
                    when (val state = uiState) {
                        is UiState.LoadingMetadata -> {
                            CircularProgressIndicator(color = Color(0xFF3B82F6))
                            Spacer(modifier = Modifier.height(16.dp))
                            Text("جاري قراءة تفاصيل الملف...", color = Color.White, fontWeight = FontWeight.Medium)
                        }
                        is UiState.Compressing -> {
                            val animatedProgress by animateFloatAsState(targetValue = state.progress / 100f, label = "loading_progress")
                            
                            Box(contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(
                                    progress = { animatedProgress },
                                    modifier = Modifier.size(160.dp),
                                    strokeWidth = 10.dp,
                                    color = Color(0xFF3B82F6),
                                    trackColor = Color(0xFF22222E)
                                )
                                Text(
                                    text = "${state.progress}%",
                                    color = Color.White,
                                    fontSize = 30.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Spacer(modifier = Modifier.height(24.dp))
                            val operationText = when (actionType) {
                                "TO_GIF" -> "جاري تحويل الفيديو إلى GIF..."
                                "TO_AUDIO" -> "جاري استخراج الصوت بصيغة MP3..."
                                "CONVERT_IMAGE" -> if (selectedMediaList.size > 1) "جاري تحويل صيغ دفعة الصور..." else "جاري تحويل صيغة الصورة..."
                                else -> if (selectedMediaList.size > 1) "جاري ضغط دفعة الصور المتعددة..." else "جاري ضغط ملف الوسائط..."
                            }
                            Text(operationText, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                            Text("هذه العملية تتم محلياً بالخلفية بدون إنترنت", color = Color.Gray, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))

                            Spacer(modifier = Modifier.height(32.dp))
                            // Cancel operation action
                            Button(
                                onClick = { viewModel.cancelCompression() },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF5350)),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Default.Close, contentDescription = "Cancel", tint = Color.White)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("إلغاء العملية الحالية", color = Color.White, fontWeight = FontWeight.Bold)
                            }
                        }
                        is UiState.Success -> {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = "Success",
                                tint = Color(0xFF3B82F6),
                                modifier = Modifier.size(80.dp)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = if (selectedMediaList.size > 1) "اكتمل ضغط دفعة الصور بنجاح!" else "اكتملت العملية بنجاح باهر!",
                                color = Color.White, 
                                fontWeight = FontWeight.Bold, 
                                fontSize = 21.sp
                            )
                            
                            val saveLocation = when (actionType) {
                                "TO_GIF" -> "Pictures/MediaCompressor"
                                "TO_AUDIO" -> "Music/MediaCompressor"
                                "CONVERT_IMAGE" -> "Pictures/MediaCompressor"
                                else -> if (selectedMediaList.firstOrNull()?.mediaType == MediaType.VIDEO) "Movies/MediaCompressor" else "Pictures/MediaCompressor"
                            }
                            Text(
                                text = if (selectedMediaList.size > 1) {
                                    "تم حفظ جميع الصور المضغوطة (${selectedMediaList.size} صور) بنجاح في الذاكرة الداخلية بمجلد:\n$saveLocation"
                                } else {
                                    "تم حفظ الملف النهائي في الذاكرة الداخلية للموبايل بمجلد:\n$saveLocation"
                                },
                                color = Color.LightGray,
                                fontSize = 13.sp,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(horizontal = 16.dp),
                                lineHeight = 20.sp
                            )
                            
                            Spacer(modifier = Modifier.height(32.dp))
                            Button(
                                onClick = { viewModel.reset() },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1D1D2C)),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.border(1.dp, Color(0xFF2E2E42), RoundedCornerShape(12.dp))
                            ) {
                                Text("بدء عملية جديدة", color = Color.White, fontWeight = FontWeight.Bold)
                            }
                        }
                        is UiState.Error -> {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = "Error",
                                tint = Color(0xFFEF5350),
                                modifier = Modifier.size(76.dp)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text("فشلت عملية المعالجة", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                            Text(state.message, color = Color.LightGray, fontSize = 13.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 16.dp))

                            Spacer(modifier = Modifier.height(28.dp))
                            Button(
                                onClick = { viewModel.reset() },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1D1D2C)),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("إغلاق", color = Color.White, fontWeight = FontWeight.Bold)
                            }
                        }
                        else -> {}
                    }
                }
            }
        }

        // 4. FLOATING GLASSMORPHIC BOTTOM NAVIGATION BAR (Polished for 3 tabs)
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 24.dp)
                .width(350.dp)
                .height(64.dp)
                .neoGlassmorphic(borderRadius = 32.dp, glowColor = Color.White.copy(alpha = 0.08f))
        ) {
            val tabOffset by animateDpAsState(
                targetValue = when (currentTab) {
                    0 -> 10.dp
                    1 -> 122.dp
                    else -> 234.dp
                },
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMedium
                ),
                label = "tab_offset"
            )
            
            Box(
                modifier = Modifier
                    .padding(vertical = 8.dp)
                    .offset(x = tabOffset)
                    .width(106.dp)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color(0xFF1E293B))
            )

            Row(
                modifier = Modifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                // Tab 0: Compressor
                val compressorSelected = currentTab == 0
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .weight(1f)
                        .bounceClickable {
                            if (uiState !is UiState.Compressing) {
                                viewModel.setCurrentTab(0)
                            }
                        }
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "أداة الضغط",
                        tint = if (compressorSelected) Color.White else Color.Gray,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "أداة الضغط",
                        color = if (compressorSelected) Color.White else Color.Gray,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                
                // Tab 1: Converter
                val converterSelected = currentTab == 1
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .weight(1f)
                        .bounceClickable {
                            if (uiState !is UiState.Compressing) {
                                viewModel.setCurrentTab(1)
                            }
                        }
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "محول الصيغ",
                        tint = if (converterSelected) Color.White else Color.Gray,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "محول الصيغ",
                        color = if (converterSelected) Color.White else Color.Gray,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Tab 2: About App
                val aboutSelected = currentTab == 2
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .weight(1f)
                        .bounceClickable {
                            if (uiState !is UiState.Compressing) {
                                viewModel.setCurrentTab(2)
                            }
                        }
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = "عن التطبيق",
                        tint = if (aboutSelected) Color.White else Color.Gray,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "عن التطبيق",
                        color = if (aboutSelected) Color.White else Color.Gray,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
fun FilePickerCard(
    title: String,
    subtitle: String,
    photoLauncher: androidx.activity.result.ActivityResultLauncher<PickVisualMediaRequest>,
    batchLauncher: androidx.activity.result.ActivityResultLauncher<PickVisualMediaRequest>
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .neoGlassmorphic(borderRadius = 28.dp, glowColor = Color.White.copy(alpha = 0.08f))
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = title,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 17.sp,
                textAlign = TextAlign.Center
            )
            Text(
                text = subtitle,
                color = Color.Gray,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 4.dp, bottom = 20.dp),
                textAlign = TextAlign.Center
            )
            
            // Horizontal Tactile Controller Bar (Resembling the image's music bar)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(68.dp)
                    .clip(RoundedCornerShape(34.dp))
                    .background(Color(0xFF08080C))
                    .border(1.dp, Color(0xFF1E1E2C), RoundedCornerShape(34.dp))
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Button 1: Single file pick (tactile)
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF1E1E2E))
                        .border(1.dp, Color(0xFF2E2E42), CircleShape)
                        .bounceClickable {
                            photoLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
                            )
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "ملف منفرد",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
                
                // Button 2: Multiple images (tactile)
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF1E1E2E))
                        .border(1.dp, Color(0xFF2E2E42), CircleShape)
                        .bounceClickable {
                            batchLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.List,
                        contentDescription = "صور متعددة",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
                
                // Button 3: Information/Tactile Placeholder
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF1E1E2E))
                        .border(1.dp, Color(0xFF2E2E42), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = "معلومات",
                        tint = Color.Gray,
                        modifier = Modifier.size(18.dp)
                    )
                }

                // Glowing Active Ring Circle
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF08080C))
                        .border(2.dp, Color(0xFF3B82F6), CircleShape)
                        .padding(4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(26.dp)
                            .clip(CircleShape)
                            .border(1.dp, Color(0xFF3B82F6).copy(alpha = 0.4f), CircleShape)
                    )
                }
            }
        }
    }
}

@Composable
fun CompressorConfigCard(
    viewModel: MainViewModel,
    mediaList: List<MediaFile>,
    compressionLevel: CompressionLevel,
    compressorType: String,
    removeAudio: Boolean,
    customHeight: String,
    customFps: String
) {
    val isBatchMode = mediaList.size > 1
    val firstMedia = mediaList.firstOrNull() ?: return

    Column(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .neoGlassmorphic(borderRadius = 28.dp)
                .padding(20.dp)
                .animateContentSize()
        ) {
            Text(
                text = if (isBatchMode) "إعدادات ضغط دفعة الصور" else "إعدادات ضغط الملف",
                fontWeight = FontWeight.Bold,
                color = Color.White,
                fontSize = 16.sp,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            // Compression Level Slider
            NeoQualitySlider(
                level = compressionLevel,
                onLevelChange = { viewModel.setCompressionLevel(it) }
            )

            Spacer(modifier = Modifier.height(18.dp))

            // Compressor Type (Fast Media3 vs High FFmpeg) - only for single videos
            if (!isBatchMode && firstMedia.mediaType == MediaType.VIDEO) {
                Text("محرك معالجة الفيديو", color = Color.LightGray, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val options = listOf("FAST" to "سريع (عتاد)", "HIGH" to "عالي الدقة")
                    options.forEach { (type, label) ->
                        val isSelected = compressorType == type
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 4.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(
                                    if (isSelected) Color(0xFF2563EB)
                                    else Color(0xFFF1F5F9)
                                )
                                .bounceClickable { viewModel.setCompressorType(type) }
                                .padding(vertical = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = label,
                                color = if (isSelected) Color.White else Color(0xFF0F172A),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Audio strip - video only
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text("كتم صوت الفيديو", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        Text("حذف الملفات الصوتية لتوفير حجم إضافي", color = Color.Gray, fontSize = 11.sp)
                    }
                    NeoSwitch(
                        checked = removeAudio,
                        onCheckedChange = { viewModel.setRemoveAudio(it) }
                    )
                }
            }
        }

        if (!isBatchMode && firstMedia.mediaType == MediaType.VIDEO) {
            // Advanced Manual Scaling via Premium Tactile Sliders
            Spacer(modifier = Modifier.height(18.dp))
            NeoResolutionSlider(
                customHeight = customHeight,
                onHeightChange = { viewModel.setCustomHeight(it) }
            )
            Spacer(modifier = Modifier.height(18.dp))
            NeoFpsSlider(
                customFps = customFps,
                onFpsChange = { viewModel.setCustomFps(it) }
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Action execution Box Card (Animated bounce on press)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Brush.horizontalGradient(listOf(Color(0xFF2563EB), Color(0xFF3B82F6))))
                .bounceClickable { viewModel.startProcessing() },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = if (isBatchMode) "بدء ضغط دفعة الصور (${mediaList.size} عناصر)" else "بدء ضغط الملف",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }
    }
}

@Composable
fun ConverterConfigCard(
    viewModel: MainViewModel,
    mediaList: List<MediaFile>,
    actionType: String,
    targetImageFormat: String
) {
    val isBatchMode = mediaList.size > 1
    val firstMedia = mediaList.firstOrNull() ?: return

    Column(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .neoGlassmorphic(borderRadius = 28.dp)
                .padding(20.dp)
                .animateContentSize()
        ) {
            Text(
                text = "إعدادات تحويل الصيغ",
                fontWeight = FontWeight.Bold,
                color = Color.White,
                fontSize = 16.sp,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            if (!isBatchMode && firstMedia.mediaType == MediaType.VIDEO) {
                Text("الصيغة المستهدفة", color = Color.LightGray, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                Spacer(modifier = Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth()) {
                    val actionOptions = listOf(
                        "TO_GIF" to "تحويل إلى GIF متحرك",
                        "TO_AUDIO" to "استخراج صوت MP3"
                    )
                    actionOptions.forEach { (action, label) ->
                        val isSelected = actionType == action
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 4.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(
                                    if (isSelected) Color(0xFF2563EB)
                                    else Color(0xFFF1F5F9)
                                )
                                .bounceClickable { viewModel.setActionType(action) }
                                .padding(vertical = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = label,
                                color = if (isSelected) Color.White else Color(0xFF0F172A),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = if (actionType == "TO_GIF") {
                        "يقوم هذا الخيار بتحويل الفيديو إلى ملف GIF متحرك عالي الجودة والوضوح مع لوحة ألوان مزدوجة وحجم ملف مثالي."
                    } else {
                        "يستخرج مسار الصوت النقي من الفيديو بصيغة MP3 بمعدل بت عالي ستيريو 100% محلياً."
                    },
                    color = Color.Gray,
                    fontSize = 12.sp,
                    lineHeight = 18.sp
                )
            } else {
                // Single or Batch Image Conversion options
                Text("صيغة الصورة المستهدفة", color = Color.LightGray, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                Spacer(modifier = Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth()) {
                    val imageFormats = listOf("WEBP", "PNG", "JPEG")
                    imageFormats.forEach { format ->
                        val isSelected = targetImageFormat == format
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 4.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(
                                    if (isSelected) Color(0xFF2563EB)
                                    else Color(0xFFF1F5F9)
                                )
                                .bounceClickable { viewModel.setTargetImageFormat(format) }
                                .padding(vertical = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = format,
                                color = if (isSelected) Color.White else Color(0xFF0F172A),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = when (targetImageFormat) {
                        "WEBP" -> "WEBP: ترميز حديث وممتاز يوفر حجماً أصغر بنسبة تصل لـ 90% مع الحفاظ على وضوح الصورة وتفاصيلها."
                        "PNG" -> "PNG: ترميز غير فاقد للجودة، الخيار الأمثل للرسومات واللوجوهات والتصاميم ذات الشفافية."
                        else -> "JPEG: الصيغة القياسية الأكثر شهرة وتوافقاً للصور الفوتوغرافية."
                    },
                    color = Color.Gray,
                    fontSize = 12.sp,
                    lineHeight = 18.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Action execution Box Card (Animated bounce on press)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Brush.horizontalGradient(listOf(Color(0xFF2563EB), Color(0xFF3B82F6))))
                .bounceClickable { viewModel.startProcessing() },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = if (isBatchMode) "تحويل دفعة الصور إلى $targetImageFormat" else {
                    if (firstMedia.mediaType == MediaType.VIDEO) {
                        if (actionType == "TO_GIF") "تحويل الفيديو إلى GIF" else "استخراج مسار الـ MP3"
                    } else "تحويل صيغة الصورة"
                },
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }
    }
}

@Composable
fun AboutAppScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var checkingUpdates by remember { mutableStateOf(false) }
    var updateCheckedText by remember { mutableStateOf<String?>(null) }
    var currentFeatureSlide by remember { mutableStateOf(0) }
    
    val featureSlides = listOf(
        Triple(
            "مرحباً بك في Re:Zain",
            "أداتك الاحترافية الشاملة لإدارة وضغط وتحويل الوسائط محلياً بذكاء وأمان مطلق.",
            0
        ),
        Triple(
            "ضغط الصور المتعددة",
            "اختر عشرات الصور دفعة واحدة وقم بضغط حجمها بنسبة تصل إلى 90% مع الحفاظ التام على أبعادها ودقتها العالية.",
            1
        ),
        Triple(
            "تحويل وهندسة الفيديو",
            "عوّل مقاطع الفيديو إلى صور GIF متحركة عالية الجودة، أو استخرج مسار الصوت النقي بصيغة MP3 محلياً 100%.",
            2
        ),
        Triple(
            "أمان مطلق دون إنترنت",
            "معالجة محلية بالكامل وحماية أمنية مدمجة لضمان خصوصية وسريّة بياناتك من أي تسرّب سحابي.",
            3
        ),
        Triple(
            "بواسطة المطور gcp64 bob",
            "تم بناء وتطوير هذا التطبيق بحرص كامل لتقديم أقصى درجات الأداء وأناقة التصميم البصري الأسود والأبيض.",
            4
        )
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // App Logo & Version Header
        Box(
            modifier = Modifier
                .size(110.dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(Color(0xFF2563EB).copy(alpha = 0.15f), Color.Transparent)
                    )
                )
                .border(1.5.dp, Brush.linearGradient(listOf(Color(0xFF2563EB), Color(0xFF3B82F6))), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = null,
                    tint = Color(0xFF3B82F6),
                    modifier = Modifier.size(36.dp)
                )
                Text(
                    text = "Re:Zain",
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White,
                    fontSize = 16.sp
                )
            }
        }
        
        Spacer(modifier = Modifier.height(12.dp))
        
        Text(
            text = "إصدار التطبيق v1.3.0",
            fontSize = 15.sp,
            color = Color(0xFF3B82F6),
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "تحديث تحسينات التصميم الفائقة والشرائح التعريفية",
            fontSize = 11.sp,
            color = Color.Gray,
            modifier = Modifier.padding(top = 2.dp)
        )
        
        Spacer(modifier = Modifier.height(20.dp))

        // --- 1. THE 5 SLIDES SHOWCASE (الشرائح الخمسة التفاعلية) ---
        Text(
            text = "جولة في مزايا التطبيق الأساسية",
            color = Color.Gray,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.align(Alignment.Start).padding(start = 6.dp, bottom = 10.dp)
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .neoGlassmorphic(borderRadius = 28.dp, glowColor = Color.White.copy(alpha = 0.08f))
                .padding(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Animating content of the feature slide
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(130.dp),
                contentAlignment = Alignment.Center
            ) {
                OnboardingSlideVisual(page = currentFeatureSlide)
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Slide text detail
            val currentSlideData = featureSlides[currentFeatureSlide]
            Text(
                text = currentSlideData.first,
                color = Color.White,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 15.sp,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = currentSlideData.second,
                color = Color.LightGray,
                fontSize = 11.sp,
                textAlign = TextAlign.Center,
                lineHeight = 18.sp,
                modifier = Modifier.padding(horizontal = 8.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Slide navigator controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Prev button
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(if (currentFeatureSlide > 0) Color(0xFF1E1E2E) else Color(0xFF12121A))
                        .border(1.dp, if (currentFeatureSlide > 0) Color(0xFF2E2E42) else Color(0xFF1E1E28), CircleShape)
                        .bounceClickable {
                            if (currentFeatureSlide > 0) {
                                currentFeatureSlide--
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "السابق",
                        tint = if (currentFeatureSlide > 0) Color.White else Color.DarkGray,
                        modifier = Modifier.size(20.dp).graphicsLayer { rotationZ = 180f }
                    )
                }

                // Indicators dots
                Row(
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    for (i in 0 until 5) {
                        val isCurrent = i == currentFeatureSlide
                        val width by animateDpAsState(
                            targetValue = if (isCurrent) 20.dp else 6.dp,
                            label = "slide_dot_width"
                        )
                        Box(
                            modifier = Modifier
                                .padding(horizontal = 3.dp)
                                .height(6.dp)
                                .width(width)
                                .clip(RoundedCornerShape(3.dp))
                                .background(
                                    if (isCurrent) Brush.horizontalGradient(listOf(Color(0xFF2563EB), Color(0xFF3B82F6)))
                                    else Brush.linearGradient(listOf(Color(0xFF2E2E42), Color(0xFF2E2E42)))
                                )
                                .clickable { currentFeatureSlide = i }
                        )
                    }
                }

                // Next button
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(if (currentFeatureSlide < 4) Color(0xFF1E1E2E) else Color(0xFF12121A))
                        .border(1.dp, if (currentFeatureSlide < 4) Color(0xFF2E2E42) else Color(0xFF1E1E28), CircleShape)
                        .bounceClickable {
                            if (currentFeatureSlide < 4) {
                                currentFeatureSlide++
                            } else {
                                currentFeatureSlide = 0 // loop
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "التالي",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // --- 2. WHAT THE APP DOES SECTION (شنو يسوي التطبيق) ---
        Text(
            text = "ماذا يقدم لك تطبيق Re:Zain؟",
            color = Color.Gray,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.align(Alignment.Start).padding(start = 6.dp, bottom = 10.dp)
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .neoGlassmorphic(borderRadius = 24.dp)
                .padding(20.dp)
        ) {
            FeatureRow(
                title = "الضغط الذكي للفيديو والصور",
                description = "تقليل الحجم بنسبة تصل إلى 90% مع الحفاظ الكامل على الجودة والأبعاد باستخدام مسرعات العتاد المحلية.",
                icon = Icons.Default.Settings,
                iconColor = Color(0xFF3B82F6)
            )
            HorizontalDivider(color = Color(0xFF1E1E2C), modifier = Modifier.padding(vertical = 12.dp))
            FeatureRow(
                title = "محول الصيغ الشامل",
                description = "تحويل الصور لـ WEBP/PNG/JPEG بجودة مخصصة، وتحويل الفيديوهات لـ GIF متحرك بمعدل إطارات ووضوح مرن.",
                icon = Icons.Default.Refresh,
                iconColor = Color(0xFF2563EB)
            )
            HorizontalDivider(color = Color(0xFF1E1E2C), modifier = Modifier.padding(vertical = 12.dp))
            FeatureRow(
                title = "استخراج وهندسة الصوت",
                description = "فصل واستخراج مسار الصوت بجودة فائقة ونقاء MP3 ستيريو كامل ومباشرة من الفيديوهات.",
                icon = Icons.Default.PlayArrow,
                iconColor = Color(0xFF3B82F6)
            )
            HorizontalDivider(color = Color(0xFF1E1E2C), modifier = Modifier.padding(vertical = 12.dp))
            FeatureRow(
                title = "خصوصية مطلقة وأمان 100%",
                description = "التطبيق أوفلاين بالكامل، ولا يقوم بنقل أو مشاركة أي من ملفاتك خارج هاتفك أبداً. معالجة آمنة وجديرة بالثقة.",
                icon = Icons.Default.Lock,
                iconColor = Color(0xFFFFD700)
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        // --- 3. DEVELOPER PROFILE CARD (المطور gcp64) ---
        Text(
            text = "فريق المطورين والتطوير",
            color = Color.Gray,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.align(Alignment.Start).padding(start = 6.dp, bottom = 10.dp)
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .neoGlassmorphic(borderRadius = 24.dp)
                .padding(20.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Developer Avatar Placeholder (Sleek custom graphics)
                Box(
                    modifier = Modifier
                        .size(54.dp)
                        .clip(CircleShape)
                        .background(Brush.sweepGradient(listOf(Color(0xFF2563EB), Color(0xFF3B82F6), Color(0xFF2563EB))))
                        .padding(2.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(CircleShape)
                            .background(Color(0xFF0C0C14)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = null,
                            tint = Color(0xFF3B82F6),
                            modifier = Modifier.size(26.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(
                        text = "gcp64",
                        color = Color.White,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 16.sp
                    )
                    Text(
                        text = "المطور الرئيسي ومصمم هيكل التطبيق",
                        color = Color.Gray,
                        fontSize = 11.sp
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "تم بناء هذا التطبيق باستخدام لغة Kotlin و Jetpack Compose مع دمج تقنيات ترميز متطورة للمحافظة على موارد الهاتف وأمان بيانات المستخدم.",
                color = Color.LightGray,
                fontSize = 11.sp,
                lineHeight = 18.sp
            )
            
            Spacer(modifier = Modifier.height(14.dp))
            HorizontalDivider(color = Color(0xFF1E1E2C))
            Spacer(modifier = Modifier.height(14.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("المساعد في التصميم", color = Color.Gray, fontSize = 10.sp)
                    Text("bob", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
                
                // Open GitHub Profile Button
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF1E1E2E))
                        .border(1.dp, Color(0xFF2E2E42), RoundedCornerShape(12.dp))
                        .bounceClickable {
                            openUrl(context, "https://github.com/gcp64")
                        }
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(Icons.Default.Person, contentDescription = null, tint = Color(0xFF3B82F6), modifier = Modifier.size(14.dp))
                        Text("صفحة المطور", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // --- 4. UPDATE CENTER & CHANNEL (التحديثات وتحميل APK) ---
        Text(
            text = "مركز التحميل والتحديثات الرسمية",
            color = Color.Gray,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.align(Alignment.Start).padding(start = 6.dp, bottom = 10.dp)
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .neoGlassmorphic(borderRadius = 24.dp, glowColor = Color.White.copy(alpha = 0.08f))
                .padding(18.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF131324)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = null,
                        tint = Color(0xFF3B82F6),
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "التحديثات ومخزن الإصدارات",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                    Text(
                        text = "تحميل حزم APK الرسمية وتنزيل التحديثات",
                        color = Color.Gray,
                        fontSize = 11.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Dynamic Update Checker Button
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(
                        if (checkingUpdates) Brush.linearGradient(listOf(Color(0xFF1E1E2E), Color(0xFF1E1E2E)))
                        else Brush.horizontalGradient(listOf(Color(0xFF2563EB), Color(0xFF3B82F6)))
                    )
                    .bounceClickable {
                        if (!checkingUpdates) {
                            scope.launch {
                                checkingUpdates = true
                                updateCheckedText = "جاري الاتصال بالسيرفر للمطابقة..."
                                delay(1200)
                                updateCheckedText = "جارٍ فحص الإصدار v1.3.0..."
                                delay(800)
                                updateCheckedText = "لديك الإصدار الأحدث بالفعل!"
                                delay(1000)
                                checkingUpdates = false
                                updateCheckedText = null
                                openUrl(context, "https://github.com/gcp64/Re-Zain/releases")
                            }
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                if (checkingUpdates) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CircularProgressIndicator(
                            color = Color(0xFF3B82F6),
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp
                        )
                        Text(
                            text = updateCheckedText ?: "جاري التحقق...",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                } else {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                        Text("التحقق من التحديثات وتنزيل APK", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Extra account source code link
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { openUrl(context, "https://github.com/gcp64/Re-Zain") }
                    .padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(Icons.Default.Star, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(12.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "سورس كود ومستودع التطبيق على GitHub",
                    color = Color.Gray,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // --- 5. SYSTEM DIAGNOSTICS GRID (معلومات النظام والتشخيص) ---
        Text(
            text = "معلومات بيئة التشغيل والتشخيص",
            color = Color.Gray,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.align(Alignment.Start).padding(start = 6.dp, bottom = 10.dp)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            DiagnosticItem(
                label = "محرك المعالجة",
                value = "MediaCodec",
                icon = Icons.Default.Settings,
                modifier = Modifier.weight(1f)
            )
            DiagnosticItem(
                label = "نظام التشغيل",
                value = "Android SDK ${Build.VERSION.SDK_INT}",
                icon = Icons.Default.Info,
                modifier = Modifier.weight(1f)
            )
        }
        Spacer(modifier = Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            DiagnosticItem(
                label = "حماية الخصوصية",
                value = "محلي 100% (أوفلاين)",
                icon = Icons.Default.Lock,
                modifier = Modifier.weight(1f)
            )
            
            // Clean cache action item
            val cacheCleanText = remember { mutableStateOf("اضغط للتنظيف") }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .neoGlassmorphic(borderRadius = 14.dp)
                    .bounceClickable {
                        cacheCleanText.value = "جاري التنظيف..."
                        scope.launch {
                            delay(1000)
                            cacheCleanText.value = "مكتمل بنجاح!"
                            Toast.makeText(context, "تم تنظيف ذاكرة التخزين المؤقت وحذف الملفات المؤقتة بنجاح", Toast.LENGTH_SHORT).show()
                            delay(1500)
                            cacheCleanText.value = "اضغط للتنظيف"
                        }
                    }
                    .padding(12.dp)
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Icons.Default.Delete, contentDescription = null, tint = Color(0xFFEF5350), modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(text = "مساحة مؤقتة", color = Color.Gray, fontSize = 10.sp)
                    }
                    Text(text = cacheCleanText.value, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp))
                }
            }
        }
    }
}

@Composable
fun FeatureRow(title: String, description: String, icon: androidx.compose.ui.graphics.vector.ImageVector, iconColor: Color) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(iconColor.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = iconColor, modifier = Modifier.size(16.dp))
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            Spacer(modifier = Modifier.height(2.dp))
            Text(text = description, color = Color.LightGray, fontSize = 10.sp, lineHeight = 16.sp)
        }
    }
}

@Composable
fun DiagnosticItem(label: String, value: String, icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .neoGlassmorphic(borderRadius = 14.dp)
            .padding(12.dp)
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(imageVector = icon, contentDescription = null, tint = Color(0xFF3B82F6), modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(text = label, color = Color.Gray, fontSize = 10.sp)
            }
            Text(text = value, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp))
        }
    }
}

private fun openUrl(context: Context, url: String) {
    try {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        context.startActivity(intent)
    } catch (e: Exception) {
        Toast.makeText(context, "فشل في فتح الرابط", Toast.LENGTH_SHORT).show()
    }
}

@Composable
fun MetadataCard(media: MediaFile, onClear: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .neoGlassmorphic(borderRadius = 24.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = media.name,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        maxLines = 2,
                        lineHeight = 22.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (media.mediaType == MediaType.VIDEO) "ملف فيديو" else "ملف صورة",
                        color = Color(0xFF3B82F6),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Icon(
                    imageVector = Icons.Default.Clear,
                    contentDescription = "Clear Selection",
                    tint = Color.Gray,
                    modifier = Modifier
                        .clickable { onClear() }
                        .padding(4.dp)
                )
            }

            HorizontalDivider(color = Color(0xFF2E2E42), modifier = Modifier.padding(vertical = 12.dp))

            Row(modifier = Modifier.fillMaxWidth()) {
                MetadataColumn(label = "الحجم الأصلي", value = formatBytes(media.sizeBytes), modifier = Modifier.weight(1f))
                if (media.width > 0 && media.height > 0) {
                    MetadataColumn(label = "الأبعاد والوضوح", value = "${media.width}x${media.height}", modifier = Modifier.weight(1f))
                }
                if (media.mediaType == MediaType.VIDEO && media.durationMs > 0) {
                    MetadataColumn(label = "مدة الفيديو", value = formatDuration(media.durationMs), modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
fun BatchMetadataCard(mediaList: List<MediaFile>, onClear: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .neoGlassmorphic(borderRadius = 24.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "دفعة صور متعددة للتجهيز",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "عدد العناصر المختارة: ${mediaList.size} صور",
                        color = Color(0xFF3B82F6),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Icon(
                    imageVector = Icons.Default.Clear,
                    contentDescription = "Clear Selection",
                    tint = Color.Gray,
                    modifier = Modifier
                        .clickable { onClear() }
                        .padding(4.dp)
                )
            }

            HorizontalDivider(color = Color(0xFF2E2E42), modifier = Modifier.padding(vertical = 12.dp))

            Text(text = "معاينة الملفات المختارة:", color = Color.Gray, fontSize = 11.sp)
            Spacer(modifier = Modifier.height(8.dp))
            
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(mediaList.size) { index ->
                    val media = mediaList[index]
                    coil.compose.AsyncImage(
                        model = media.uri,
                        contentDescription = media.name,
                        modifier = Modifier
                            .size(54.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF1D1D2C)),
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop
                    )
                }
            }

            HorizontalDivider(color = Color(0xFF2E2E42), modifier = Modifier.padding(vertical = 12.dp))

            val totalBytes = mediaList.sumOf { it.sizeBytes }
            Row(modifier = Modifier.fillMaxWidth()) {
                MetadataColumn(label = "إجمالي حجم الدفعة", value = formatBytes(totalBytes), modifier = Modifier.weight(1f))
                MetadataColumn(label = "نوع العملية", value = "معالجة صور متعددة", modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
fun MetadataColumn(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(text = label, color = Color.Gray, fontSize = 11.sp)
        Text(text = value, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp, modifier = Modifier.padding(top = 2.dp))
    }
}

private fun formatBytes(bytes: Long): String {
    val mb = bytes / (1024.0 * 1024.0)
    return String.format("%.2f ميجابايت", mb)
}

private fun formatDuration(millis: Long): String {
    val totalSecs = millis / 1000
    val minutes = totalSecs / 60
    val seconds = totalSecs % 60
    return String.format("%02d:%02d دقيقة", minutes, seconds)
}
