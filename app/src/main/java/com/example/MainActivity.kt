package com.example

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

data class PatientCallRecord(
    val id: String = UUID.randomUUID().toString(),
    val patientName: String,
    val station: String,
    val timestamp: String,
    val fullAnnouncement: String
)

class MainActivity : ComponentActivity() {
    private var voiceManager: ArabicVoiceManager? = null
    private var licenseManager: LicenseManager? = null

    private val isReadyState = mutableStateOf(false)
    private val hasArabicState = mutableStateOf(false)
    private val isSpeakingState = mutableStateOf(false)

    // License reactive states
    private val isLicensedState = mutableStateOf(true)
    private val isTrialActiveState = mutableStateOf(true)
    private val remainingDaysState = mutableLongStateOf(120L)
    private val isVipState = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        licenseManager = LicenseManager(this)
        refreshLicenseState()

        voiceManager = ArabicVoiceManager(
            context = this,
            onInitComplete = { ready, hasAr ->
                isReadyState.value = ready
                hasArabicState.value = hasAr
            },
            onSpeechStatusChanged = { speaking ->
                isSpeakingState.value = speaking
            }
        )

        setContent {
            MyApplicationTheme {
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    containerColor = MaterialTheme.colorScheme.background
                ) { innerPadding ->
                    LisAnnouncerApp(
                        modifier = Modifier.padding(innerPadding),
                        isReady = isReadyState.value,
                        hasArabicOffline = hasArabicState.value,
                        isSpeaking = isSpeakingState.value,
                        isLicensed = isLicensedState.value,
                        isTrialActive = isTrialActiveState.value,
                        remainingDays = remainingDaysState.longValue,
                        isVip = isVipState.value,
                        onActivateKey = { key ->
                            val result = licenseManager?.activateCode(key) ?: Pair(false, "حدث خطأ غير متوقع")
                            refreshLicenseState()
                            result
                        },
                        onOpenWhatsApp = {
                            try {
                                val url = "https://wa.me/9647703333687?text=" + Uri.encode("السلام عليكم، أرغب في شراء كود تفعيل سنوي لنظام المختبر الطبي (LIS Announcer)")
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                startActivity(intent)
                            } catch (e: Exception) {
                                Toast.makeText(this, "تعذر فتح واتساب: 07703333687", Toast.LENGTH_LONG).show()
                            }
                        },
                        onAnnounce = { name, station, persona, speechRate, volume, doubleCall, chimeEnabled, useOnline ->
                            voiceManager?.useOnlineEngine = useOnline
                            val announcement = "المريض $name، يرجى التوجه إلى $station"
                            kotlinx.coroutines.GlobalScope.launch(Dispatchers.IO) {
                                voiceManager?.announce(
                                    text = announcement,
                                    persona = persona,
                                    speechRate = speechRate,
                                    volume = volume,
                                    repeatTwice = doubleCall,
                                    playChime = chimeEnabled
                                )
                            }
                        }
                    )
                }
            }
        }
    }

    private fun refreshLicenseState() {
        licenseManager?.let { lm ->
            isLicensedState.value = lm.isLicensed()
            isTrialActiveState.value = lm.isTrialActive()
            remainingDaysState.longValue = lm.getRemainingDays()
            isVipState.value = lm.isVip()
        }
    }

    override fun onDestroy() {
        voiceManager?.release()
        super.onDestroy()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LisAnnouncerApp(
    modifier: Modifier = Modifier,
    isReady: Boolean,
    hasArabicOffline: Boolean,
    isSpeaking: Boolean,
    isLicensed: Boolean,
    isTrialActive: Boolean,
    remainingDays: Long,
    isVip: Boolean,
    onActivateKey: (String) -> Pair<Boolean, String>,
    onOpenWhatsApp: () -> Unit,
    onAnnounce: (name: String, station: String, persona: ArabicVoicePersona, rate: Float, vol: Float, doubleCall: Boolean, chime: Boolean, useOnline: Boolean) -> Unit
) {
    val context = LocalContext.current
    var selectedTab by remember { mutableIntStateOf(0) }
    var patientNameInput by remember { mutableStateOf("") }
    val stations = remember {
        listOf(
            "كابينة سحب دم 1",
            "كابينة سحب دم 2",
            "استلام نتيجة الفحص"
        )
    }
    var selectedStation by remember { mutableStateOf(stations[0]) }
    var isStationExpanded by remember { mutableStateOf(false) }

    var selectedPersona by remember { mutableStateOf(ArabicVoicePersona.GENTLE_FEMALE) }
    var speechRate by remember { mutableFloatStateOf(1.0f) }
    var speechVolume by remember { mutableFloatStateOf(1.0f) }
    var chimeEnabled by remember { mutableStateOf(true) }
    var doubleCallEnabled by remember { mutableStateOf(false) }
    var useOnlineEngine by remember { mutableStateOf(true) }

    val callHistory = remember { mutableStateListOf<PatientCallRecord>() }
    var currentAnnouncingPatient by remember { mutableStateOf<String?>(null) }
    var showTvDialog by remember { mutableStateOf(false) }
    var showPaymentDialog by remember { mutableStateOf(false) }
    var showExpiredDialog by remember { mutableStateOf(false) }

    // Activation Key Input in UI
    var licenseKeyInput by remember { mutableStateOf("") }
    var licenseActivationMessage by remember { mutableStateOf<String?>(null) }
    var isActivationSuccess by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()

    val quickSamplePatients = listOf("أحمد عبد الله محمد", "فاطمة علي حسن", "زينب جاسم كريم", "عمر خالد رشيد")

    fun triggerCall(name: String, station: String) {
        if (!isLicensed) {
            showExpiredDialog = true
            return
        }

        val cleanName = name.trim().replace(Regex("[0-9#|;]"), "").trim()
        if (cleanName.length >= 2) {
            val time = SimpleDateFormat("hh:mm:ss a", Locale.getDefault()).format(Date())
            val record = PatientCallRecord(
                patientName = cleanName,
                station = station,
                timestamp = time,
                fullAnnouncement = "المريض $cleanName، يرجى التوجه إلى $station"
            )
            callHistory.add(0, record)
            currentAnnouncingPatient = cleanName
            onAnnounce(cleanName, station, selectedPersona, speechRate, speechVolume, doubleCallEnabled, chimeEnabled, useOnlineEngine)
            patientNameInput = ""

            scope.launch {
                delay(4000)
                if (currentAnnouncingPatient == cleanName) {
                    currentAnnouncingPatient = null
                }
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        // Top App Bar with License Badge
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Campaign,
                                contentDescription = "LIS Logo",
                                tint = Color.White
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = stringResource(R.string.title_app),
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                val statusDotColor = if (isSpeaking) {
                                    Color(0xFFF59E0B)
                                } else if (isReady) {
                                    Color(0xFF10B981)
                                } else {
                                    Color(0xFFEF4444)
                                }
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(statusDotColor)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                val statusText = if (isSpeaking) {
                                    stringResource(R.string.status_speaking)
                                } else if (isReady) {
                                    if (useOnlineEngine) stringResource(R.string.status_ready_online) else stringResource(R.string.status_ready_offline)
                                } else {
                                    stringResource(R.string.status_init)
                                }
                                Text(
                                    text = statusText,
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f)
                                )
                            }
                        }
                    }

                    IconButton(
                        onClick = { showTvDialog = true },
                        modifier = Modifier.testTag("tv_display_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Tv,
                            contentDescription = stringResource(R.string.tv_dialog_title),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // License Badge Strip
                val (badgeBg, badgeText, badgeIcon) = when {
                    isVip -> Triple(Color(0xFF8B5CF6), stringResource(R.string.license_badge_vip), Icons.Default.Star)
                    !isLicensed -> Triple(Color(0xFFEF4444), stringResource(R.string.license_badge_expired), Icons.Default.Lock)
                    isTrialActive -> Triple(Color(0xFF0284C7), stringResource(R.string.license_badge_trial, remainingDays), Icons.Default.Schedule)
                    else -> Triple(Color(0xFF10B981), stringResource(R.string.license_badge_active, remainingDays), Icons.Default.CheckCircle)
                }

                Surface(
                    color = badgeBg.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(8.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, badgeBg.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(badgeIcon, contentDescription = null, tint = badgeBg, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = badgeText,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = badgeBg
                            )
                        }

                        if (!isLicensed || isTrialActive) {
                            Text(
                                text = "تجديد / تفعيل 🔑",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .clickable { selectedTab = 1 }
                                    .padding(4.dp)
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Station Selector & Call Input Box
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    text = stringResource(R.string.station_label),
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))

                ExposedDropdownMenuBox(
                    expanded = isStationExpanded,
                    onExpandedChange = { isStationExpanded = it }
                ) {
                    OutlinedTextField(
                        value = selectedStation,
                        onValueChange = {},
                        readOnly = true,
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = isStationExpanded) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(),
                        shape = RoundedCornerShape(12.dp)
                    )
                    ExposedDropdownMenu(
                        expanded = isStationExpanded,
                        onDismissRequest = { isStationExpanded = false }
                    ) {
                        stations.forEach { station ->
                            DropdownMenuItem(
                                text = { Text(station) },
                                onClick = {
                                    selectedStation = station
                                    isStationExpanded = false
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Quick station selection chips
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    stations.forEach { station ->
                        FilterChip(
                            selected = selectedStation == station,
                            onClick = { selectedStation = station },
                            label = {
                                Text(
                                    text = station,
                                    fontSize = 12.sp,
                                    fontWeight = if (selectedStation == station) FontWeight.Bold else FontWeight.Normal
                                )
                            },
                            shape = RoundedCornerShape(8.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = patientNameInput,
                    onValueChange = { patientNameInput = it },
                    label = { Text(stringResource(R.string.patient_input_label)) },
                    placeholder = { Text(stringResource(R.string.patient_input_placeholder)) },
                    leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                    trailingIcon = {
                        if (patientNameInput.isNotEmpty()) {
                            IconButton(onClick = { patientNameInput = "" }) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear")
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("patient_input_field"),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(6.dp))

                // Fast suggestions row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    quickSamplePatients.take(3).forEach { sampleName ->
                        AssistChip(
                            onClick = { patientNameInput = sampleName },
                            label = { Text(sampleName.split(" ").take(2).joinToString(" "), fontSize = 11.sp) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Button(
                    onClick = { triggerCall(patientNameInput, selectedStation) },
                    enabled = patientNameInput.trim().length >= 2,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("call_patient_button"),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isLicensed) MaterialTheme.colorScheme.primary else Color(0xFFEF4444)
                    )
                ) {
                    Icon(if (isLicensed) Icons.Default.VolumeUp else Icons.Default.Lock, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isLicensed) stringResource(R.string.call_button_text) else "انتهت الصلاحية (اضغط للتفعيل)",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Tab Row (History vs Settings & License)
        PrimaryTabRow(
            selectedTabIndex = selectedTab,
            containerColor = Color.Transparent,
            modifier = Modifier.fillMaxWidth()
        ) {
            Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                text = { Text("${stringResource(R.string.tab_history)} (${callHistory.size})", fontWeight = FontWeight.SemiBold) }
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                text = { Text(stringResource(R.string.tab_settings), fontWeight = FontWeight.SemiBold) }
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Tab Contents
        if (selectedTab == 0) {
            if (callHistory.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.History,
                            contentDescription = null,
                            modifier = Modifier.size(54.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = stringResource(R.string.empty_history_text),
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            fontSize = 14.sp
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(callHistory, key = { it.id }) { item ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = item.patientName,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 15.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = item.timestamp,
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                        )
                                    }
                                    Text(
                                        text = item.station,
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }

                                FilledTonalButton(
                                    onClick = { triggerCall(item.patientName, item.station) },
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Icon(Icons.Default.Replay, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(stringResource(R.string.replay_button), fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
            }
        } else {
            // Settings & License Tab
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp)
            ) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // 1. License & Subscription Card (Protected: No codes shown here!)
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isLicensed) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f) else Color(0xFFFFE4E6)
                            ),
                            shape = RoundedCornerShape(12.dp),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (isLicensed) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f) else Color(0xFFE11D48)
                            )
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Default.VerifiedUser,
                                        contentDescription = null,
                                        tint = if (isLicensed) MaterialTheme.colorScheme.primary else Color(0xFFE11D48)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = stringResource(R.string.license_card_title),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = stringResource(R.string.license_card_desc),
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = stringResource(R.string.license_developer_info),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = stringResource(R.string.license_price_note),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF059669)
                                )

                                Spacer(modifier = Modifier.height(10.dp))

                                // License Key Input
                                OutlinedTextField(
                                    value = licenseKeyInput,
                                    onValueChange = { licenseKeyInput = it.uppercase() },
                                    label = { Text(stringResource(R.string.license_input_label), fontSize = 11.sp) },
                                    placeholder = { Text(stringResource(R.string.license_input_placeholder), fontSize = 12.sp) },
                                    leadingIcon = { Icon(Icons.Default.Key, contentDescription = null) },
                                    trailingIcon = {
                                        if (licenseKeyInput.isNotEmpty()) {
                                            IconButton(onClick = { licenseKeyInput = "" }) {
                                                Icon(Icons.Default.Clear, contentDescription = null)
                                            }
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(10.dp),
                                    singleLine = true
                                )

                                Spacer(modifier = Modifier.height(8.dp))

                                Button(
                                    onClick = {
                                        val result = onActivateKey(licenseKeyInput)
                                        licenseActivationMessage = result.second
                                        isActivationSuccess = result.first
                                        if (result.first) {
                                            licenseKeyInput = ""
                                        }
                                    },
                                    enabled = licenseKeyInput.trim().length >= 8,
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                                ) {
                                    Icon(Icons.Default.Check, contentDescription = null)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(stringResource(R.string.license_activate_btn), fontWeight = FontWeight.Bold)
                                }

                                if (licenseActivationMessage != null) {
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = licenseActivationMessage!!,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isActivationSuccess) Color(0xFF059669) else Color(0xFFDC2626)
                                    )
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                // WhatsApp & Payment Buttons
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Button(
                                        onClick = onOpenWhatsApp,
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(8.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF25D366))
                                    ) {
                                        Text("واتساب المبرمج 💬", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                    }

                                    OutlinedButton(
                                        onClick = { showPaymentDialog = true },
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text("طرق الدفع 💳", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }

                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    }

                    // Voice Persona Selection Section
                    item {
                        Text(
                            text = stringResource(R.string.voice_persona_title),
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = stringResource(R.string.voice_persona_desc),
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(6.dp))

                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            ArabicVoicePersona.values().forEach { persona ->
                                val isSelected = selectedPersona == persona
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { selectedPersona = persona },
                                    shape = RoundedCornerShape(10.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (isSelected) {
                                            MaterialTheme.colorScheme.primaryContainer
                                        } else {
                                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                        }
                                    ),
                                    border = if (isSelected) {
                                        androidx.compose.foundation.BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
                                    } else null
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 12.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        RadioButton(
                                            selected = isSelected,
                                            onClick = { selectedPersona = persona }
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(
                                                    text = "${persona.iconEmoji} ${persona.titleAr}",
                                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                    fontSize = 13.sp,
                                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                                )
                                            }
                                            Text(
                                                text = persona.descriptionAr,
                                                fontSize = 11.sp,
                                                color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    }

                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(stringResource(R.string.online_tts_title), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Text(stringResource(R.string.online_tts_desc), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Switch(checked = useOnlineEngine, onCheckedChange = { useOnlineEngine = it })
                        }
                        HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))
                    }

                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(stringResource(R.string.chime_title), fontWeight = FontWeight.Bold)
                                Text(stringResource(R.string.chime_desc), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Switch(checked = chimeEnabled, onCheckedChange = { chimeEnabled = it })
                        }
                        HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))
                    }

                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(stringResource(R.string.double_call_title), fontWeight = FontWeight.Bold)
                                Text(stringResource(R.string.double_call_desc), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Switch(checked = doubleCallEnabled, onCheckedChange = { doubleCallEnabled = it })
                        }
                        HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))
                    }

                    item {
                        Text("${stringResource(R.string.speech_rate_label)} ${(speechRate * 100).toInt()}%", fontWeight = FontWeight.SemiBold)
                        Slider(
                            value = speechRate,
                            onValueChange = { speechRate = it },
                            valueRange = 0.6f..1.4f
                        )
                    }

                    item {
                        Text("${stringResource(R.string.speech_volume_label)} ${(speechVolume * 100).toInt()}%", fontWeight = FontWeight.SemiBold)
                        Slider(
                            value = speechVolume,
                            onValueChange = { speechVolume = it },
                            valueRange = 0.1f..1.0f
                        )
                    }

                    item {
                        Spacer(modifier = Modifier.height(4.dp))
                        OutlinedButton(
                            onClick = { triggerCall("تجربة النبرة الصوتية", selectedStation) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.Hearing, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.test_speech_button))
                        }
                    }
                }
            }
        }
    }

    // Payment Information Dialog
    if (showPaymentDialog) {
        AlertDialog(
            onDismissRequest = { showPaymentDialog = false },
            confirmButton = {
                Button(onClick = onOpenWhatsApp) {
                    Text("تواصل عبر واتساب 💬")
                }
            },
            dismissButton = {
                TextButton(onClick = { showPaymentDialog = false }) {
                    Text(stringResource(R.string.close_button))
                }
            },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Payment, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.payment_dialog_title))
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(text = stringResource(R.string.payment_developer), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(text = stringResource(R.string.payment_zaincash), fontWeight = FontWeight.Bold, fontSize = 14.sp, color = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(text = "💳 ماستركارد / كي كارد: متاح عند الطلب", fontSize = 13.sp)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(text = "💵 السعر السنوي: 40$ دولار (أو ما يعادله بالدينار العراقي)", fontWeight = FontWeight.Bold, color = Color(0xFF059669), fontSize = 13.sp)
                        }
                    }
                    Text(text = stringResource(R.string.payment_note), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        )
    }

    // Expired Dialog (Blocks calling if trial and license are over)
    if (showExpiredDialog) {
        AlertDialog(
            onDismissRequest = { showExpiredDialog = false },
            confirmButton = {
                Button(onClick = {
                    showExpiredDialog = false
                    selectedTab = 1
                }) {
                    Text("إدخال كود التفعيل 🔑")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = onOpenWhatsApp) {
                    Text("شراء كود عبر واتساب")
                }
            },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Lock, contentDescription = null, tint = Color(0xFFEF4444))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("انتهت الفترة التجريبية المجانية")
                }
            },
            text = {
                Text(
                    "لقد انتهت فترة الـ 4 أشهر (120 يوماً) المجانية لنظام النداء بالمختبر.\n\nيرجى إدخال كود التفعيل السنوي للمتابعة، أو التواصل مع المبرمج شامل عبدالامير لتجديد الاشتراك.",
                    fontSize = 13.sp
                )
            }
        )
    }

    // Waiting Room TV Dialog
    if (showTvDialog) {
        AlertDialog(
            onDismissRequest = { showTvDialog = false },
            confirmButton = {
                TextButton(onClick = { showTvDialog = false }) { Text(stringResource(R.string.close_button)) }
            },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Tv, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.tv_dialog_title))
                }
            },
            text = {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = stringResource(R.string.tv_calling_badge),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF93C5FD)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = currentAnnouncingPatient ?: (callHistory.firstOrNull()?.patientName ?: "بانتظار النداء..."),
                            fontSize = 24.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "👉 $selectedStation",
                            fontSize = 14.sp,
                            color = Color(0xFFFDE047),
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        )
    }
}
