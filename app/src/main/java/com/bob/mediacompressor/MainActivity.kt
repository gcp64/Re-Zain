package com.bob.mediacompressor

import android.Manifest
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
import androidx.annotation.RequiresApi
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
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
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
    glowColor: Color = Color(0xFF00F0FF).copy(alpha = 0.22f)
): Modifier {
    return this
        .clip(RoundedCornerShape(borderRadius))
        .background(Color(0xFF10101C).copy(alpha = 0.65f))
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

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        hideSystemUI()
        val prefs = com.bob.mediacompressor.security.AppSecurityManager.getEncryptedPreferences(this)
        setContent {
            ModernAppTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color(0xFF06060A) // Ultra Premium Dark Space Background
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

    private fun hideSystemUI() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(false)
            val controller = window.insetsController
            if (controller != null) {
                controller.hide(android.view.WindowInsets.Type.statusBars() or android.view.WindowInsets.Type.navigationBars())
                controller.systemBarsBehavior = android.view.WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (
                android.view.View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or android.view.View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or android.view.View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or android.view.View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or android.view.View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or android.view.View.SYSTEM_UI_FLAG_FULLSCREEN
            )
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
            Icons.Default.List
        ),
        Triple(
            "تحويل وهندسة الفيديو",
            "حوّل مقاطع الفيديو إلى صور GIF متحركة عالية الجودة، أو استخرج مسار الصوت النقي بصيغة MP3 محلياً 100%.",
            Icons.Default.PlayArrow
        ),
        Triple(
            "أمان مطلق دون إنترنت",
            "معالجة محلية بالكامل وحماية أمنية مدمجة ضد التعديل أو الاختراق لضمان خصوصية وسريّة بياناتك.",
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
            .background(Color(0xFF040407))
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
                        colors = listOf(Color(0xFF9E00FF).copy(alpha = alpha), Color.Transparent)
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
                        colors = listOf(Color(0xFF00F0FF).copy(alpha = alpha * 0.8f), Color.Transparent)
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
                    .neoGlassmorphic(borderRadius = 32.dp, glowColor = Color(0xFF2563EB).copy(alpha = 0.15f))
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
                    val (title, description, icon) = slideData[targetPage]
                    
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
                        Box(
                            modifier = Modifier
                                .offset(y = bobbingOffset.dp)
                                .size(110.dp)
                                .clip(RoundedCornerShape(32.dp))
                                .background(
                                    Brush.horizontalGradient(
                                        colors = listOf(
                                            Color(0xFF2563EB).copy(alpha = 0.25f),
                                            Color(0xFF00F0FF).copy(alpha = 0.15f)
                                        )
                                    )
                                )
                                .border(1.5.dp, Color(0xFF2E2E42), RoundedCornerShape(32.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = icon,
                                contentDescription = null,
                                tint = Color(0xFF00F0FF),
                                modifier = Modifier.size(48.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(36.dp))

                        Text(
                            text = title,
                            fontSize = 26.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White,
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(18.dp))

                        Text(
                            text = description,
                            fontSize = 15.sp,
                            color = Color.LightGray,
                            textAlign = TextAlign.Center,
                            lineHeight = 24.sp,
                            modifier = Modifier.padding(horizontal = 12.dp)
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
                                    if (isCurrent) Brush.horizontalGradient(listOf(Color(0xFF2563EB), Color(0xFF00F0FF)))
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
                            .background(Brush.horizontalGradient(listOf(Color(0xFF2563EB), Color(0xFF00F0FF))))
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
        primary = Color(0xFF9E00FF), // Neon Purple Accent
        secondary = Color(0xFF00F0FF), // Cyber Cyan Accent
        background = Color(0xFF06060A),
        surface = Color(0xFF12121E)
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

    // Breathing glow animation for selection border
    val borderGlowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.20f,
        targetValue = 0.65f,
        animationSpec = infiniteRepeatable(
            animation = tween(3500, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "border_glow_alpha"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF040407))
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
                        colors = listOf(Color(0xFF9E00FF).copy(alpha = glowAlpha1), Color.Transparent)
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
                        colors = listOf(Color(0xFF00F0FF).copy(alpha = glowAlpha2), Color.Transparent)
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

            // App Header
            Text(
                text = "Re:Zain",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                style = LocalTextStyle.current.copy(
                    brush = Brush.horizontalGradient(listOf(Color(0xFF00F0FF), Color(0xFF9E00FF)))
                ),
                textAlign = TextAlign.Center
            )
            Text(
                text = "ضغط وتحويل ملفات الصور والفيديو محلياً 100% بدون إنترنت",
                fontSize = 12.sp,
                color = Color.Gray,
                modifier = Modifier.padding(top = 6.dp),
                textAlign = TextAlign.Center,
                lineHeight = 18.sp
            )

            Spacer(modifier = Modifier.height(24.dp))

            // File selection widget / Metadata card (Localized)
            AnimatedVisibility(
                visible = selectedMediaList.isEmpty(),
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .neoGlassmorphic(borderRadius = 28.dp, glowColor = Color(0xFF00F0FF).copy(alpha = 0.15f))
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = if (currentTab == 0) "أداة الضغط الذكي Re:Zain" else "محول وسائط Re:Zain",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = if (currentTab == 0) "اختر ملفات وسائط لبدء الضغط محلياً وبأمان" else "اختر ملف وسائط لتحويل صيغته دون إنترنت",
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
                                        photoPickerLauncher.launch(
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
                                        multipleImagesLauncher.launch(
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

                            // Glowing Active Ring Circle (mimicking the glowing circle on the far right)
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF08080C))
                                    .border(2.dp, Color(0xFF00F0FF), CircleShape)
                                    .padding(4.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(26.dp)
                                        .clip(CircleShape)
                                        .border(1.dp, Color(0xFF00F0FF).copy(alpha = 0.5f), CircleShape)
                                )
                            }
                        }
                    }
                }
            }

            AnimatedVisibility(
                visible = selectedMediaList.isNotEmpty(),
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                if (selectedMediaList.size > 1) {
                    BatchMetadataCard(
                        mediaList = selectedMediaList,
                        onClear = { viewModel.reset() }
                    )
                } else if (selectedMediaList.size == 1) {
                    MetadataCard(
                        media = selectedMediaList.first(),
                        onClear = { viewModel.reset() }
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Configurations UI panel (Arabic Layout & Enhancements)
            AnimatedVisibility(
                visible = selectedMediaList.isNotEmpty(),
                enter = fadeIn(animationSpec = tween(400)) + expandVertically(animationSpec = tween(400)),
                exit = fadeOut(animationSpec = tween(300)) + shrinkVertically(animationSpec = tween(300))
            ) {
                val isBatchMode = selectedMediaList.size > 1
                val firstMedia = selectedMediaList.firstOrNull() ?: return@AnimatedVisibility

                Column(modifier = Modifier.fillMaxWidth()) {
                    if (currentTab == 0) {
                        // COMPRESSOR SCREEN (شاشة الضغط)
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

                            // Compression Level Selector
                            Text("مستوى الجودة المطلوب", color = Color.LightGray, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                val levels = listOf(
                                    CompressionLevel.LOW to "منخفض",
                                    CompressionLevel.MEDIUM to "متوازن",
                                    CompressionLevel.HIGH to "دقة فائقة"
                                )
                                levels.forEach { (level, name) ->
                                    val isSelected = compressionLevel == level
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .padding(horizontal = 4.dp)
                                            .clip(RoundedCornerShape(14.dp))
                                            .background(
                                                if (isSelected) Color(0xFF2563EB)
                                                else Color(0xFFF1F5F9)
                                            )
                                            .bounceClickable { viewModel.setCompressionLevel(level) }
                                            .padding(vertical = 12.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = name,
                                            color = if (isSelected) Color.White else Color(0xFF0F172A),
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }

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

                        Spacer(modifier = Modifier.height(16.dp))

                        if (!isBatchMode && firstMedia.mediaType == MediaType.VIDEO) {
                            // Advanced Manual Scaling (Optional)
                            Text("خيارات تحجيم الفيديو المتقدمة (اختياري)", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(10.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                OutlinedTextField(
                                    value = customHeight,
                                    onValueChange = { viewModel.setCustomHeight(it) },
                                    label = { Text("الارتفاع (مثال: 720)") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(end = 6.dp)
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(Color(0xFFF1F5F9)),
                                    singleLine = true,
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedTextColor = Color(0xFF0F172A),
                                        unfocusedTextColor = Color(0xFF0F172A),
                                        focusedBorderColor = Color(0xFF2563EB),
                                        unfocusedBorderColor = Color.Transparent,
                                        focusedLabelColor = Color(0xFF2563EB),
                                        unfocusedLabelColor = Color.Gray
                                    )
                                )
                                OutlinedTextField(
                                    value = customFps,
                                    onValueChange = { viewModel.setCustomFps(it) },
                                    label = { Text("معدل الإطارات FPS (مثال: 30)") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(start = 6.dp)
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(Color(0xFFF1F5F9)),
                                    singleLine = true,
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedTextColor = Color(0xFF0F172A),
                                        unfocusedTextColor = Color(0xFF0F172A),
                                        focusedBorderColor = Color(0xFF2563EB),
                                        unfocusedBorderColor = Color.Transparent,
                                        focusedLabelColor = Color(0xFF2563EB),
                                        unfocusedLabelColor = Color.Gray
                                    )
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
                                .background(Brush.horizontalGradient(listOf(Color(0xFF2563EB), Color(0xFF00F0FF))))
                                .bounceClickable { viewModel.startProcessing() },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (isBatchMode) "بدء ضغط دفعة الصور (${selectedMediaList.size} عناصر)" else "بدء ضغط الملف",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }

                    } else {
                        // CONVERTER SCREEN (شاشة تحويل الصيغ)
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
                                .background(Brush.horizontalGradient(listOf(Color(0xFF2563EB), Color(0xFF00F0FF))))
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
                            CircularProgressIndicator(color = Color(0xFF00F0FF))
                            Spacer(modifier = Modifier.height(16.dp))
                            Text("جاري قراءة تفاصيل الملف...", color = Color.White, fontWeight = FontWeight.Medium)
                        }
                        is UiState.Compressing -> {
                            val animatedProgress by animateFloatAsState(targetValue = state.progress / 100f)
                            
                            Box(contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(
                                    progress = { animatedProgress },
                                    modifier = Modifier.size(160.dp),
                                    strokeWidth = 10.dp,
                                    color = Color(0xFF00F0FF),
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
                                tint = Color(0xFF00F0FF),
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

        // 4. FLOATING GLASSMORPHIC BOTTOM NAVIGATION BAR
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 24.dp)
                .width(300.dp)
                .height(64.dp)
                .neoGlassmorphic(borderRadius = 32.dp, glowColor = Color(0xFF00F0FF).copy(alpha = 0.15f))
        ) {
            val tabOffset by animateDpAsState(
                targetValue = if (currentTab == 0) 10.dp else 155.dp,
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
                    .width(135.dp)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color(0xFF2563EB))
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
                        color = if (compressorSelected) Color(0xFF00F0FF) else Color.Gray,
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
                        tint = if (converterSelected) Color(0xFF00F0FF) else Color.Gray,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "محول الصيغ",
                        color = if (converterSelected) Color(0xFF00F0FF) else Color.Gray,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
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
                        color = Color(0xFF00F0FF),
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
                        color = Color(0xFF00F0FF),
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
