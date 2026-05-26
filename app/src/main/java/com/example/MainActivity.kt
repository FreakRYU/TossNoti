package com.example

import android.Manifest
import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.example.apps.InstalledAppsRepository
import com.example.database.NotificationLog
import com.example.database.NotificationRepository
import com.example.relay.AppMetadataHelper
import com.example.relay.RelaySender
import com.example.service.AlarmReceiverService
import com.example.ui.AppPickerSheet
import com.example.ui.SelectedAppsStrip
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ──────────────────────────────────────────────────────────────────────────
// Design tokens (Coinbase-inspired clean light fintech style)
// ──────────────────────────────────────────────────────────────────────────
internal object TN {
    val Bg = Color(0xFFFFFFFF)
    val Surface = Color(0xFFF3F4F8)         // chip bg, stat card bg
    val SurfaceStrong = Color(0xFFEAECF2)   // selected stat card bg
    val Border = Color(0xFFE5E7EB)
    val Primary = Color(0xFF0052FF)
    val OnPrimary = Color(0xFFFFFFFF)
    val TextPrimary = Color(0xFF0A0B0D)
    val TextSecondary = Color(0xFF5B616E)
    val TextMuted = Color(0xFF8A8F98)
    val Positive = Color(0xFF05A569)
    val Negative = Color(0xFFCF202F)
    val WarningBg = Color(0xFFFFF6E5)
    val WarningFg = Color(0xFFA85800)
    val DangerBg = Color(0xFFFDECEE)
    val DangerFg = Color(0xFFB1232E)
    val SuccessBg = Color(0xFFE9F8EE)
    val SuccessFg = Color(0xFF05A569)
}

class MainActivity : ComponentActivity() {

    private val viewModel: MainActivityViewModel by viewModels {
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                val app = application as AlarmTossApplication
                return MainActivityViewModel(app, app.repository) as T
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        requestNotificationPermission()

        setContent {
            MyApplicationTheme {
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    containerColor = TN.Bg
                ) { innerPadding ->
                    TossNotiScreen(
                        viewModel = viewModel,
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshState()
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val status = ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            if (status != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(
                    this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 101
                )
            }
        }
    }
}

class MainActivityViewModel(
    application: Application,
    private val repository: NotificationRepository
) : AndroidViewModel(application) {

    private val context: Context get() = getApplication()
    private val sharedPref = context.getSharedPreferences("AlarmTossPref", Context.MODE_PRIVATE)

    private val _isSenderMode = MutableStateFlow(sharedPref.getBoolean("is_sender_mode", true))
    val isSenderMode: StateFlow<Boolean> = _isSenderMode

    private val _senderPin = MutableStateFlow(sharedPref.getString("sender_pin", "") ?: "")
    val senderPin: StateFlow<String> = _senderPin

    private val _receiverPin = MutableStateFlow(sharedPref.getString("receiver_pin", "") ?: "")
    val receiverPin: StateFlow<String> = _receiverPin

    private val _targetApps = MutableStateFlow(
        sharedPref.getStringSet("target_apps", setOf("com.instagram.android")) ?: setOf("com.instagram.android")
    )
    val targetApps: StateFlow<Set<String>> = _targetApps

    private val _isReceiverServiceRunning = MutableStateFlow(AlarmReceiverService.isRunning())
    val isReceiverServiceRunning: StateFlow<Boolean> = _isReceiverServiceRunning

    private val _lastCrashLog = MutableStateFlow(sharedPref.getString("last_crash_log", "") ?: "")
    val lastCrashLog: StateFlow<String> = _lastCrashLog

    private val _isListenerEnabled = MutableStateFlow(isNotificationListenerEnabled(context))
    val isListenerEnabled: StateFlow<Boolean> = _isListenerEnabled

    val notificationLogs: StateFlow<List<NotificationLog>> = repository.allLogs
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun refreshState() {
        _isReceiverServiceRunning.value = AlarmReceiverService.isRunning()
        _lastCrashLog.value = sharedPref.getString("last_crash_log", "") ?: ""
        _isListenerEnabled.value = isNotificationListenerEnabled(context)
    }

    fun clearCrashLog() {
        _lastCrashLog.value = ""
        sharedPref.edit().remove("last_crash_log").apply()
    }

    fun setMode(isSender: Boolean) {
        _isSenderMode.value = isSender
        sharedPref.edit().putBoolean("is_sender_mode", isSender).apply()
    }

    fun generateNewSenderPin() {
        val randomPin = (100000..999999).random().toString()
        _senderPin.value = randomPin
        sharedPref.edit().putString("sender_pin", randomPin).apply()
    }

    fun setReceiverPin(pin: String) {
        if (pin.length > 6) return
        if (!pin.all { it.isDigit() }) return
        _receiverPin.value = pin
        sharedPref.edit().putString("receiver_pin", pin).apply()
    }

    fun setTargetApps(packages: Set<String>) {
        _targetApps.value = packages
        sharedPref.edit().putStringSet("target_apps", packages).apply()
    }

    fun removeTargetApp(packageName: String) {
        val current = _targetApps.value.toMutableSet()
        current.remove(packageName)
        _targetApps.value = current
        sharedPref.edit().putStringSet("target_apps", current).apply()
    }

    fun startReceiverService(pin: String) {
        if (pin.length != 6 || !pin.all { it.isDigit() }) {
            Toast.makeText(context, "PIN 번호는 6자리 숫자여야 합니다.", Toast.LENGTH_SHORT).show()
            return
        }
        val intent = Intent(context, AlarmReceiverService::class.java).apply {
            putExtra("pin", pin)
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
            _isReceiverServiceRunning.value = true
            Toast.makeText(context, "수신 서비스를 시작했습니다.", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Log.e("TossNoti", "Failed to start receiver", e)
            Toast.makeText(context, "수신 서비스 시작 실패: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
            _isReceiverServiceRunning.value = false
        }
    }

    fun stopReceiverService() {
        try {
            context.stopService(Intent(context, AlarmReceiverService::class.java))
            _isReceiverServiceRunning.value = false
            Toast.makeText(context, "수신 서비스를 정지했습니다.", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Log.e("TossNoti", "Failed to stop receiver", e)
        }
    }

    fun deleteLog(id: Int) {
        viewModelScope.launch { repository.deleteLog(id) }
    }

    fun clearAllLogs() {
        viewModelScope.launch { repository.clearLogs() }
    }

    fun injectDemoLog(isSent: Boolean) {
        viewModelScope.launch {
            if (isSent) {
                val pin = _senderPin.value
                if (pin.length != 6 || !pin.all { it.isDigit() }) {
                    Toast.makeText(
                        context,
                        "송신 PIN을 먼저 생성해야 테스트 전송이 가능합니다.",
                        Toast.LENGTH_SHORT
                    ).show()
                    return@launch
                }
                Toast.makeText(context, "테스트 전송 중...", Toast.LENGTH_SHORT).show()
                // Pick a demo source app — Instagram if available, otherwise our own
                // package so the receiver still sees a real label.
                val demoPkg = listOf("com.instagram.android", context.packageName)
                    .firstOrNull {
                        try { context.packageManager.getApplicationInfo(it, 0); true }
                        catch (_: Exception) { false }
                    } ?: context.packageName
                val demoLabel = AppMetadataHelper.loadLabel(context, demoPkg)
                val result = RelaySender.sendNotification(
                    title = "TossNoti 테스트 전송",
                    text = "이 메시지가 수신 기기에 표시되면 정상 동작입니다.",
                    packageName = demoPkg,
                    pin = pin,
                    repository = repository,
                    appLabel = demoLabel
                )
                when (result) {
                    is RelaySender.Result.Success -> Toast.makeText(
                        context,
                        "전송 성공 — 수신 기기를 확인하세요.",
                        Toast.LENGTH_SHORT
                    ).show()
                    is RelaySender.Result.Failure -> Toast.makeText(
                        context,
                        "전송 실패: ${result.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            } else {
                // Receiver-side demo: just inject a local "received" row so the user
                // can see the UI render. Doesn't exercise the network path.
                val demoPin = _receiverPin.value.ifEmpty { "123456" }
                repository.insertLog(
                    NotificationLog(
                        title = "Instagram (테스트 수신)",
                        message = "TossNoti 데모 메시지: 로컬 표시 확인.",
                        packageName = "com.instagram.android",
                        pin = demoPin,
                        isSent = false,
                        appLabel = "Instagram"
                    )
                )
            }
        }
    }
}

// ──────────────────────────────────────────────────────────────────────────
// Main screen
// ──────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TossNotiScreen(
    viewModel: MainActivityViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val imeVisible = WindowInsets.isImeVisible

    // Drop focus on the PIN field the moment the soft keyboard closes (back
    // button, swipe-down gesture, or system close). Otherwise the TextField
    // stays focused and the cursor keeps blinking until you tap something else.
    LaunchedEffect(imeVisible) {
        if (!imeVisible) {
            focusManager.clearFocus()
        }
    }

    val isSenderMode by viewModel.isSenderMode.collectAsStateWithLifecycle()
    val senderPin by viewModel.senderPin.collectAsStateWithLifecycle()
    val receiverPin by viewModel.receiverPin.collectAsStateWithLifecycle()
    val targetApps by viewModel.targetApps.collectAsStateWithLifecycle()
    val isReceiverRunning by viewModel.isReceiverServiceRunning.collectAsStateWithLifecycle()
    val logs by viewModel.notificationLogs.collectAsStateWithLifecycle()
    val lastCrashLog by viewModel.lastCrashLog.collectAsStateWithLifecycle()
    val isListenerEnabled by viewModel.isListenerEnabled.collectAsStateWithLifecycle()

    var showRegenerateConfirm by remember { mutableStateOf(false) }
    var showAppPicker by remember { mutableStateOf(false) }
    val installedAppsRepo = remember(context) { InstalledAppsRepository(context) }

    if (showRegenerateConfirm) {
        AlertDialog(
            onDismissRequest = { showRegenerateConfirm = false },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.generateNewSenderPin()
                    showRegenerateConfirm = false
                }) { Text("재생성", color = TN.Primary, fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { showRegenerateConfirm = false }) {
                    Text("취소", color = TN.TextSecondary)
                }
            },
            containerColor = TN.Bg,
            titleContentColor = TN.TextPrimary,
            textContentColor = TN.TextSecondary,
            title = { Text("PIN을 새로 생성할까요?") },
            text = {
                Text(
                    "새 PIN을 생성하면 기존 수신 기기는 더 이상 알림을 받지 못합니다.\n" +
                            "수신 스마트폰에서도 새 PIN으로 다시 등록해야 합니다."
                )
            }
        )
    }

    if (showAppPicker) {
        AppPickerSheet(
            initialSelected = targetApps,
            onDismiss = { showAppPicker = false },
            onConfirm = { newSelection ->
                viewModel.setTargetApps(newSelection)
                showAppPicker = false
            }
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(TN.Bg)
            // Tap on any empty space anywhere on the screen → drop focus +
            // close the keyboard. The ripple is suppressed to keep the
            // background feel clean.
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { focusManager.clearFocus() }
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
    ) {
        Spacer(modifier = Modifier.height(12.dp))

        // ── Brand header
        Header(isSenderMode, isListenerEnabled, isReceiverRunning, senderPin)

        Spacer(modifier = Modifier.height(24.dp))

        // ── Mode switch (two stat-card tabs)
        ModeTabs(
            isSender = isSenderMode,
            senderPin = senderPin,
            targetAppsCount = targetApps.size,
            isReceiverRunning = isReceiverRunning,
            onSelectSender = { viewModel.setMode(true) },
            onSelectReceiver = { viewModel.setMode(false) }
        )

        // ── Optional crash banner
        if (lastCrashLog.isNotEmpty()) {
            Spacer(modifier = Modifier.height(16.dp))
            CrashBanner(lastCrashLog) { viewModel.clearCrashLog() }
        }

        AnimatedVisibility(visible = isSenderMode, enter = fadeIn(), exit = fadeOut()) {
            SenderSection(
                senderPin = senderPin,
                isListenerEnabled = isListenerEnabled,
                targetApps = targetApps,
                installedAppsRepo = installedAppsRepo,
                onRegenerate = {
                    if (senderPin.isEmpty()) viewModel.generateNewSenderPin()
                    else showRegenerateConfirm = true
                },
                onOpenSettings = { openListenerSettings(context) },
                onPickApps = { showAppPicker = true },
                onRemoveApp = { pkg -> viewModel.removeTargetApp(pkg) },
                onInjectDemo = { viewModel.injectDemoLog(true) }
            )
        }

        AnimatedVisibility(visible = !isSenderMode, enter = fadeIn(), exit = fadeOut()) {
            ReceiverSection(
                receiverPin = receiverPin,
                isReceiverRunning = isReceiverRunning,
                onPinChange = { viewModel.setReceiverPin(it) },
                onStart = { viewModel.startReceiverService(receiverPin) },
                onStop = { viewModel.stopReceiverService() },
                onInjectDemo = { viewModel.injectDemoLog(false) }
            )
        }

        // ── Activity (shared between modes)
        Spacer(modifier = Modifier.height(28.dp))
        ActivitySection(
            logs = logs,
            onDelete = { viewModel.deleteLog(it) },
            onClearAll = { viewModel.clearAllLogs() }
        )

        Spacer(modifier = Modifier.height(28.dp))
    }
}

@Composable
private fun Header(
    isSenderMode: Boolean,
    isListenerEnabled: Boolean,
    isReceiverRunning: Boolean,
    senderPin: String
) {
    val (statusText, statusColor) = when {
        isSenderMode && isListenerEnabled && senderPin.isNotEmpty() -> "Live" to TN.Positive
        isSenderMode -> "Setup" to TN.WarningFg
        !isSenderMode && isReceiverRunning -> "Live" to TN.Positive
        else -> "Idle" to TN.TextMuted
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column {
            Text(
                text = "TossNoti",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = TN.TextPrimary,
                letterSpacing = (-0.5).sp
            )
            Text(
                text = "Cross-device notification relay",
                fontSize = 13.sp,
                color = TN.TextSecondary
            )
        }
        StatusPill(text = statusText, color = statusColor)
    }
}

@Composable
private fun StatusPill(text: String, color: Color) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(TN.Surface)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(text, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = TN.TextPrimary)
    }
}

@Composable
private fun ModeTabs(
    isSender: Boolean,
    senderPin: String,
    targetAppsCount: Int,
    isReceiverRunning: Boolean,
    onSelectSender: () -> Unit,
    onSelectReceiver: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        StatCard(
            icon = Icons.Default.Send,
            label = "Sender",
            value = if (senderPin.isEmpty()) "—" else senderPin,
            sublabel = "$targetAppsCount apps targeted",
            selected = isSender,
            onClick = onSelectSender,
            modifier = Modifier.weight(1f)
        )
        StatCard(
            icon = Icons.Default.Notifications,
            label = "Receiver",
            value = if (isReceiverRunning) "Live" else "Off",
            valueColor = if (isReceiverRunning) TN.Positive else TN.TextPrimary,
            sublabel = "Phone listens",
            selected = !isSender,
            onClick = onSelectReceiver,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun StatCard(
    icon: ImageVector,
    label: String,
    value: String,
    sublabel: String? = null,
    valueColor: Color = TN.TextPrimary,
    selected: Boolean = false,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bg = if (selected) TN.SurfaceStrong else TN.Surface
    val borderColor = if (selected) TN.Primary else Color.Transparent
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(bg)
            .border(BorderStroke(if (selected) 2.dp else 0.dp, borderColor), RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(Color.White),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, null, tint = TN.TextSecondary, modifier = Modifier.size(16.dp))
            }
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = value,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = valueColor,
                letterSpacing = 0.5.sp
            )
        }
        Spacer(modifier = Modifier.height(10.dp))
        Text(label, fontSize = 13.sp, color = TN.TextSecondary, fontWeight = FontWeight.Medium)
        if (sublabel != null) {
            Text(sublabel, fontSize = 11.sp, color = TN.TextMuted)
        }
    }
}

// ──────────────────────────────────────────────────────────────────────────
// Sender section
// ──────────────────────────────────────────────────────────────────────────
@Composable
private fun SenderSection(
    senderPin: String,
    isListenerEnabled: Boolean,
    targetApps: Set<String>,
    installedAppsRepo: InstalledAppsRepository,
    onRegenerate: () -> Unit,
    onOpenSettings: () -> Unit,
    onPickApps: () -> Unit,
    onRemoveApp: (String) -> Unit,
    onInjectDemo: () -> Unit
) {
    Column {
        Spacer(modifier = Modifier.height(28.dp))
        SectionHeader("Manage")

        // Pairing PIN
        ListCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBubble(Icons.Default.Lock)
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("Pairing PIN", fontSize = 13.sp, color = TN.TextSecondary)
                    Text(
                        text = senderPin.ifEmpty { "------" },
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        color = TN.TextPrimary,
                        letterSpacing = 4.sp
                    )
                }
                PillButton(
                    text = if (senderPin.isEmpty()) "Generate" else "Regenerate",
                    icon = Icons.Default.Refresh,
                    onClick = onRegenerate
                )
            }
            if (senderPin.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "재생성하면 수신 기기도 새 PIN으로 다시 등록해야 합니다.",
                    fontSize = 11.sp,
                    color = TN.TextMuted
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Target apps
        ListCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBubble(Icons.Default.Notifications)
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("Target apps", fontSize = 13.sp, color = TN.TextSecondary)
                    Text(
                        text = "${targetApps.size} selected",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = TN.TextPrimary
                    )
                }
                PillButton(
                    text = if (targetApps.isEmpty()) "Choose" else "Edit",
                    icon = Icons.Default.Add,
                    onClick = onPickApps
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            SelectedAppsStrip(
                selected = targetApps,
                repo = installedAppsRepo,
                onRemove = onRemoveApp
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Notification listener permission
        if (!isListenerEnabled) {
            BannerCard(
                bg = TN.DangerBg,
                fg = TN.DangerFg,
                icon = Icons.Default.Warning,
                title = "Notification access required",
                body = "TossNoti needs notification access to intercept and relay notifications.\n수동 경로: 설정 → 앱 → 특수 액세스 → 알림 액세스 → TossNoti",
                actionText = "Open settings",
                onAction = onOpenSettings
            )
        } else {
            BannerCard(
                bg = TN.SuccessBg,
                fg = TN.SuccessFg,
                icon = Icons.Default.CheckCircle,
                title = "Notification access granted",
                body = "Set the PIN on your receiver device to start relaying.",
                actionText = null,
                onAction = {}
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            TextButton(onClick = onInjectDemo) {
                Text("🧪 수신 기기로 테스트 전송", color = TN.TextSecondary, fontSize = 12.sp)
            }
        }
    }
}

// ──────────────────────────────────────────────────────────────────────────
// Receiver section
// ──────────────────────────────────────────────────────────────────────────
@Composable
private fun ReceiverSection(
    receiverPin: String,
    isReceiverRunning: Boolean,
    onPinChange: (String) -> Unit,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onInjectDemo: () -> Unit
) {
    Column {
        Spacer(modifier = Modifier.height(28.dp))
        SectionHeader("Pair this device")

        ListCard {
            Text("Enter sender's PIN", fontSize = 13.sp, color = TN.TextSecondary)
            Spacer(modifier = Modifier.height(10.dp))

            TextField(
                value = receiverPin,
                onValueChange = onPinChange,
                placeholder = { Text("------", color = TN.TextMuted, fontSize = 28.sp, letterSpacing = 4.sp) },
                singleLine = true,
                textStyle = LocalTextStyle.current.copy(
                    color = TN.TextPrimary,
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Start,
                    letterSpacing = 4.sp
                ),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = TN.Surface,
                    unfocusedContainerColor = TN.Surface,
                    focusedIndicatorColor = TN.Primary,
                    unfocusedIndicatorColor = Color.Transparent,
                    focusedTextColor = TN.TextPrimary,
                    unfocusedTextColor = TN.TextPrimary,
                    cursorColor = TN.Primary
                ),
                shape = RoundedCornerShape(16.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            if (!isReceiverRunning) {
                PrimaryButton(
                    text = "Start receiving",
                    icon = Icons.Default.PlayArrow,
                    onClick = onStart,
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                Button(
                    onClick = onStop,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = TN.DangerBg,
                        contentColor = TN.DangerFg
                    ),
                    shape = RoundedCornerShape(28.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                ) {
                    Icon(Icons.Default.Close, null, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Stop receiving", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            TextButton(onClick = onInjectDemo) {
                Text("🧪 로컬 수신 로그 주입 (UI 테스트)", color = TN.TextSecondary, fontSize = 12.sp)
            }
        }
    }
}

// ──────────────────────────────────────────────────────────────────────────
// Activity section (shared)
// ──────────────────────────────────────────────────────────────────────────
@Composable
private fun ActivitySection(
    logs: List<NotificationLog>,
    onDelete: (Int) -> Unit,
    onClearAll: () -> Unit
) {
    var typeFilter by remember { mutableStateOf("All") } // All / Sent / Received

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom
    ) {
        Text(
            text = "Activity",
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = TN.TextPrimary
        )
        if (logs.isNotEmpty()) {
            TextButton(onClick = onClearAll) {
                Text("Clear all", color = TN.Negative, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }

    Spacer(modifier = Modifier.height(8.dp))

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Chip(label = typeFilter, onClick = {
            typeFilter = when (typeFilter) {
                "All" -> "Sent"
                "Sent" -> "Received"
                else -> "All"
            }
        }, prefix = "Type")
    }

    Spacer(modifier = Modifier.height(12.dp))

    val filtered = remember(logs, typeFilter) {
        when (typeFilter) {
            "Sent" -> logs.filter { it.isSent }
            "Received" -> logs.filter { !it.isSent }
            else -> logs
        }
    }

    if (filtered.isEmpty()) {
        ListCard {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    Icons.Default.Info,
                    null,
                    tint = TN.TextMuted,
                    modifier = Modifier.size(36.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    if (logs.isEmpty()) "No activity yet" else "No $typeFilter activity",
                    color = TN.TextSecondary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    "Intercepted and relayed notifications will appear here.",
                    color = TN.TextMuted,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 4.dp, start = 20.dp, end = 20.dp)
                )
            }
        }
    } else {
        ListCard(padding = 0.dp) {
            Column {
                filtered.forEachIndexed { idx, log ->
                    ActivityRow(log = log, onDelete = { onDelete(log.id) })
                    if (idx != filtered.lastIndex) {
                        HorizontalDivider(
                            color = TN.Border,
                            modifier = Modifier.padding(start = 64.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ActivityRow(log: NotificationLog, onDelete: () -> Unit) {
    // Prefer the label captured at intercept time (the sender device knows the
    // real display name). Fall back to the package-name suffix capitalized
    // for old rows that predate the appLabel column.
    val appLabel = log.appLabel.ifBlank {
        log.packageName.substringAfterLast(".")
            .replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
    }
    val timeFormat = remember { SimpleDateFormat("MMM d, HH:mm", Locale.getDefault()) }
    val formattedTime = remember(log.timestamp) { timeFormat.format(Date(log.timestamp)) }

    val accent = if (log.isSent) TN.Primary else TN.Positive
    val accentSoft = if (log.isSent) Color(0xFFE6EEFF) else TN.SuccessBg

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(accentSoft),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                if (log.isSent) Icons.Default.Send else Icons.Default.Notifications,
                null,
                tint = accent,
                modifier = Modifier.size(18.dp)
            )
        }
        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = appLabel,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TN.TextPrimary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "· PIN ${log.pin}",
                    fontSize = 11.sp,
                    color = TN.TextMuted
                )
            }
            if (log.title.isNotBlank() || log.message.isNotBlank()) {
                Text(
                    text = listOf(log.title, log.message).filter { it.isNotBlank() }.joinToString(" — "),
                    fontSize = 13.sp,
                    color = TN.TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = if (log.isSent) "Sent" else "Received",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = accent
            )
            Text(formattedTime, fontSize = 11.sp, color = TN.TextMuted)
        }

        Spacer(modifier = Modifier.width(4.dp))

        Icon(
            Icons.Default.Delete,
            contentDescription = "Delete",
            tint = TN.TextMuted,
            modifier = Modifier
                .size(18.dp)
                .clickable { onDelete() }
        )
    }
}

// ──────────────────────────────────────────────────────────────────────────
// Reusable atoms
// ──────────────────────────────────────────────────────────────────────────

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        fontSize = 22.sp,
        fontWeight = FontWeight.Bold,
        color = TN.TextPrimary,
        modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
    )
}

@Composable
private fun ListCard(
    padding: Dp = 16.dp,
    content: @Composable () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = TN.Surface,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(padding)) {
            content()
        }
    }
}

@Composable
private fun IconBubble(icon: ImageVector) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(Color.White),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, null, tint = TN.TextSecondary, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun PillButton(
    text: String,
    icon: ImageVector? = null,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(TN.Primary)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(icon, null, tint = TN.OnPrimary, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
        }
        Text(text, color = TN.OnPrimary, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
    }
}

@Composable
private fun PrimaryButton(
    text: String,
    icon: ImageVector? = null,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(containerColor = TN.Primary, contentColor = TN.OnPrimary),
        shape = RoundedCornerShape(28.dp),
        contentPadding = PaddingValues(horizontal = 24.dp),
        modifier = modifier.height(56.dp)
    ) {
        if (icon != null) {
            Icon(icon, null, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(8.dp))
        }
        Text(text, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
    }
}

@Composable
private fun Chip(label: String, prefix: String? = null, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(TN.Surface)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (prefix != null) {
            Text("$prefix: ", color = TN.TextSecondary, fontSize = 13.sp)
        }
        Text(label, color = TN.TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        Spacer(modifier = Modifier.width(2.dp))
        Icon(Icons.Default.ArrowDropDown, null, tint = TN.TextSecondary, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun BannerCard(
    bg: Color,
    fg: Color,
    icon: ImageVector,
    title: String,
    body: String,
    actionText: String?,
    onAction: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = bg,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, tint = fg, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(title, color = TN.TextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(body, color = TN.TextSecondary, fontSize = 12.sp, lineHeight = 17.sp)
            if (actionText != null) {
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(Color.White)
                        .clickable(onClick = onAction)
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Settings, null, tint = fg, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(actionText, color = fg, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun CrashBanner(crashLog: String, onClose: () -> Unit) {
    var showDetail by remember { mutableStateOf(false) }
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = TN.DangerBg,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Warning, null, tint = TN.DangerFg, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "Previous crash detected",
                    color = TN.TextPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = { showDetail = !showDetail }) {
                    Text(if (showDetail) "Hide" else "Details", color = TN.DangerFg, fontSize = 12.sp)
                }
                TextButton(onClick = onClose) {
                    Text("Dismiss", color = TN.TextSecondary, fontSize = 12.sp)
                }
            }
            if (showDetail) {
                Spacer(modifier = Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 200.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.White)
                        .padding(10.dp)
                ) {
                    LazyColumn {
                        items(listOf(crashLog)) {
                            Text(it, color = TN.TextSecondary, fontSize = 10.sp, lineHeight = 13.sp)
                        }
                    }
                }
            }
        }
    }
}

// ──────────────────────────────────────────────────────────────────────────
// Helpers
// ──────────────────────────────────────────────────────────────────────────

private fun openListenerSettings(context: Context) {
    val tries = listOf(
        Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS),
        Intent("android.settings.NOTIFICATION_LISTENER_SETTINGS"),
        Intent(Settings.ACTION_SETTINGS)
    )
    for (intent in tries) {
        try {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            return
        } catch (_: Exception) {
        }
    }
    Toast.makeText(
        context,
        "알림 접근 설정 화면을 자동으로 열 수 없습니다. 설정 → 알림 → 특수 액세스에서 TossNoti를 켜주세요.",
        Toast.LENGTH_LONG
    ).show()
}

fun isNotificationListenerEnabled(context: Context): Boolean {
    return try {
        NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)
    } catch (e: Exception) {
        Log.e("TossNoti", "Error checking notification listener: ${e.message}", e)
        false
    }
}
