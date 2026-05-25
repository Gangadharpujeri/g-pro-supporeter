package com.example.ui.screens

import android.app.Dialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.speech.tts.TextToSpeech
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.ChatMessage
import com.example.data.model.ChatSession
import com.example.ui.viewmodel.ChatViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    viewModel: ChatViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val allSessions by viewModel.allSessions.collectAsStateWithLifecycle()
    val currentSessionId by viewModel.currentSessionId.collectAsStateWithLifecycle()
    val currentMessages by viewModel.currentMessages.collectAsStateWithLifecycle()
    val userInput by viewModel.userInput.collectAsStateWithLifecycle()
    val isTyping by viewModel.isTyping.collectAsStateWithLifecycle()
    val selectedModel by viewModel.selectedModel.collectAsStateWithLifecycle()
    val systemInstruction by viewModel.systemInstruction.collectAsStateWithLifecycle()
    val lastLatencyMs by viewModel.lastLatencyMs.collectAsStateWithLifecycle()

    // Screen State variables
    var searchQuery by remember { mutableStateOf("") }
    var isSettingOpen by remember { mutableStateOf(false) }
    var isHistoryMenuMobileOpen by remember { mutableStateOf(false) }

    // Text to Speech Engine Lifecycle
    var tts by remember { mutableStateOf<TextToSpeech?>(null) }
    val isTtsReady = remember { mutableStateOf(false) }

    DisposableEffect(context) {
        var ttsInstance: TextToSpeech? = null
        try {
            ttsInstance = TextToSpeech(context) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    try {
                        val result = ttsInstance?.setLanguage(Locale.US)
                        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                            isTtsReady.value = false
                        } else {
                            isTtsReady.value = true
                        }
                    } catch (e: Exception) {
                        isTtsReady.value = false
                    }
                }
            }
        } catch (e: Exception) {
            isTtsReady.value = false
        }
        tts = ttsInstance
        onDispose {
            try {
                ttsInstance?.stop()
                ttsInstance?.shutdown()
            } catch (e: Exception) {
                // Ignore
            }
        }
    }

    fun speakText(text: String) {
        if (isTtsReady.value) {
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, null)
        } else {
            Toast.makeText(context, "Text-to-Speech initializing...", Toast.LENGTH_SHORT).show()
        }
    }

    // Adaptive Sizing Detect (Window Size Classes proxy)
    val configuration = LocalConfiguration.current
    val isExpanded = configuration.screenWidthDp >= 600

    Row(modifier = modifier.fillMaxSize()) {
        // Desktop / Tablet side-panel Workspace Details
        if (isExpanded) {
            Box(
                modifier = Modifier
                    .width(280.dp)
                    .fillMaxHeight()
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    .border(
                        width = (0.5).dp,
                        color = MaterialTheme.colorScheme.outlineVariant,
                        shape = RoundedCornerShape(0.dp)
                    )
            ) {
                SidebarContent(
                    allSessions = allSessions,
                    currentSessionId = currentSessionId,
                    onSelectSession = { viewModel.selectSession(it) },
                    onCreateSession = { viewModel.createNewSession(it) },
                    onDeleteSession = { viewModel.deleteSession(it) },
                    selectedModel = selectedModel,
                    onChangeModel = { viewModel.changeModel(it) },
                    systemInstruction = systemInstruction,
                    onChangeSystemInstruction = { viewModel.updateSystemInstruction(it) },
                    avgSlaMs = lastLatencyMs,
                    messagesCount = currentMessages.size
                )
            }
        }

        // Main Chat Canvas Area
        Scaffold(
            topBar = {
                CenterAlignedTopAppBar(
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                    ),
                    title = {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF4CAF50)) // Pulse green online
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Support Workspace",
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.SansSerif
                                )
                            }
                            Text(
                                text = "High-Speed Enterprise SLA Assistant",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    navigationIcon = {
                        if (!isExpanded) {
                            IconButton(onClick = { isHistoryMenuMobileOpen = true }) {
                                Icon(
                                    imageVector = Icons.Default.Menu,
                                    contentDescription = "Sessions history"
                                )
                            }
                        }
                    },
                    actions = {
                        IconButton(onClick = { isSettingOpen = true }) {
                            Icon(
                                imageVector = Icons.Outlined.Settings,
                                contentDescription = "System Settings"
                            )
                        }
                    }
                )
            },
            modifier = Modifier.weight(1f)
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .padding(paddingValues)
                    .fillMaxSize()
            ) {
                // Main split: Messages vs Empty view suggestions
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp)
                ) {
                    // Search interface for searching issues inside ticket history
                    if (currentMessages.isNotEmpty()) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text("Search logs...", fontSize = 13.sp) },
                            leadingIcon = { Icon(Icons.Default.Search, null, Modifier.size(16.dp)) },
                            trailingIcon = {
                                if (searchQuery.isNotEmpty()) {
                                    IconButton(onClick = { searchQuery = "" }) {
                                        Icon(Icons.Default.Clear, null, Modifier.size(16.dp))
                                    }
                                }
                            },
                            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp),
                            shape = RoundedCornerShape(24.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(46.dp)
                                .padding(vertical = 2.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                                focusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                                unfocusedBorderColor = Color.Transparent
                            )
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    // Content Area
                    val filteredMessages = remember(currentMessages, searchQuery) {
                        if (searchQuery.isBlank()) currentMessages
                        else currentMessages.filter { it.text.contains(searchQuery, ignoreCase = true) }
                    }

                    if (filteredMessages.isEmpty() && searchQuery.isNotEmpty()) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "No logs found matching '$searchQuery'",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 14.sp
                            )
                        }
                    } else if (currentMessages.isEmpty()) {
                        // Polished Empty Welcome State with Suggestion Trigger chips
                        EmptyWelcomeState(
                            modifier = Modifier.weight(1f),
                            onSuggestionClick = { presetQuery ->
                                viewModel.onUserInputChange(presetQuery)
                                viewModel.sendMessage()
                            }
                        )
                    } else {
                        // Messages flow
                        val listState = rememberLazyListState()
                        // Keep auto-scrolling to bottom on new message insertion
                        LaunchedEffect(currentMessages.size, isTyping) {
                            if (currentMessages.isNotEmpty()) {
                                listState.animateScrollToItem(currentMessages.size - 1)
                            }
                        }

                        LazyColumn(
                            state = listState,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            contentPadding = PaddingValues(vertical = 12.dp)
                        ) {
                            items(filteredMessages) { msg ->
                                MessageBubble(
                                    message = msg,
                                    onSpeak = { speakText(msg.text) },
                                    onCopy = {
                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        val clip = ClipData.newPlainText("Chatbot message", msg.text)
                                        clipboard.setPrimaryClip(clip)
                                        Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
                                    }
                                )
                            }

                            if (isTyping) {
                                item {
                                    TypingIndicatorBubble()
                                }
                            }
                        }
                    }

                    // Bottom Interface
                    ChatInputSection(
                        userInput = userInput,
                        onInputChange = { viewModel.onUserInputChange(it) },
                        onSend = { viewModel.sendMessage() },
                        isTyping = isTyping,
                        lastSlaMs = lastLatencyMs,
                        selectedModel = selectedModel,
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .imePadding()
                            .padding(bottom = 8.dp)
                    )
                }
            }
        }
    }

    // Setting Modal Dialog
    if (isSettingOpen) {
        AlertDialog(
            onDismissRequest = { isSettingOpen = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Build, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(8.dp))
                    Text("Developer SLA Settings")
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "Configure the enterprise-grade environment defaults to monitor speed, throughput, and support persona logic.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    // Model Selector Segment
                    Text("Support Model Engine Selector", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    @OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = selectedModel == "gemini-3.5-flash",
                            onClick = { viewModel.changeModel("gemini-3.5-flash") },
                            label = { Text("Gemini 3.5 Flash") },
                            leadingIcon = {
                                if (selectedModel == "gemini-3.5-flash") {
                                    Icon(Icons.Default.Check, null, Modifier.size(16.dp))
                                }
                            }
                        )
                        FilterChip(
                            selected = selectedModel == "gemini-3.1-pro-preview",
                            onClick = { viewModel.changeModel("gemini-3.1-pro-preview") },
                            label = { Text("Gemini 3.1 Pro (Analytical)") },
                            leadingIcon = {
                                if (selectedModel == "gemini-3.1-pro-preview") {
                                    Icon(Icons.Default.Check, null, Modifier.size(16.dp))
                                }
                            }
                        )
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                    // System instructions edit box
                    Text("AI Support Prompt Persona", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    OutlinedTextField(
                        value = systemInstruction,
                        onValueChange = { viewModel.updateSystemInstruction(it) },
                        textStyle = LocalTextStyle.current.copy(fontSize = 12.sp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(110.dp),
                        maxLines = 4
                    )
                }
            },
            confirmButton = {
                Button(onClick = { isSettingOpen = false }) {
                    Text("Apply & Close")
                }
            }
        )
    }

    // Mobile Navigation Drawer (Bottom sheet fallback to view history and speed indexes)
    if (isHistoryMenuMobileOpen) {
        ModalBottomSheet(
            onDismissRequest = { isHistoryMenuMobileOpen = false },
            containerColor = MaterialTheme.colorScheme.surface,
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(480.dp)
                    .padding(16.dp)
            ) {
                SidebarContent(
                    allSessions = allSessions,
                    currentSessionId = currentSessionId,
                    onSelectSession = {
                        viewModel.selectSession(it)
                        isHistoryMenuMobileOpen = false
                    },
                    onCreateSession = { viewModel.createNewSession(it) },
                    onDeleteSession = { viewModel.deleteSession(it) },
                    selectedModel = selectedModel,
                    onChangeModel = { viewModel.changeModel(it) },
                    systemInstruction = systemInstruction,
                    onChangeSystemInstruction = { viewModel.updateSystemInstruction(it) },
                    avgSlaMs = lastLatencyMs,
                    messagesCount = currentMessages.size
                )
            }
        }
    }
}

// Side pane drawer structure
@Composable
fun SidebarContent(
    allSessions: List<ChatSession>,
    currentSessionId: String?,
    onSelectSession: (String) -> Unit,
    onCreateSession: (String) -> Unit,
    onDeleteSession: (String) -> Unit,
    selectedModel: String,
    onChangeModel: (String) -> Unit,
    systemInstruction: String,
    onChangeSystemInstruction: (String) -> Unit,
    avgSlaMs: Long,
    messagesCount: Int
) {
    var isCreatingNew by remember { mutableStateOf(false) }
    var newSessionTitle by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp)
    ) {
        // App Identity Header
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(bottom = 12.dp)
        ) {
            Icon(
                imageVector = Icons.Default.SupportAgent,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Secure Support",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        HorizontalDivider(modifier = Modifier.padding(bottom = 12.dp))

        // SLA Performance Dashboard Widget
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Real-time SLA Speed",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Badge(
                        containerColor = if (avgSlaMs > 0 && avgSlaMs < 600) Color(0xFF4CAF50) else if (avgSlaMs >= 600 && avgSlaMs < 1500) Color(0xFFFF9800) else MaterialTheme.colorScheme.primary,
                        contentColor = Color.White
                    ) {
                        Text(if (avgSlaMs > 0) "Audit PASS" else "Standby", fontSize = 9.sp)
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = if (avgSlaMs > 0) "${avgSlaMs}ms" else "---",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("roundtrip", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Model: ${selectedModel.uppercase()}",
                    fontSize = 10.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Tracked in session: $messagesCount logs",
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Action Trigger
        if (!isCreatingNew) {
            Button(
                onClick = { isCreatingNew = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("new_ticket_button"),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Icon(Icons.Default.Add, contentDescription = null, Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Open Support Ticket", fontSize = 13.sp)
            }
        } else {
            Column(
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(bottom = 8.dp)
            ) {
                OutlinedTextField(
                    value = newSessionTitle,
                    onValueChange = { newSessionTitle = it },
                    placeholder = { Text("Issue description...", fontSize = 12.sp) },
                    singleLine = true,
                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.sp),
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            isCreatingNew = false
                            newSessionTitle = ""
                        },
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Text("Cancel", fontSize = 11.sp)
                    }
                    Button(
                        onClick = {
                            if (newSessionTitle.isNotBlank()) {
                                onCreateSession(newSessionTitle)
                                newSessionTitle = ""
                                isCreatingNew = false
                            }
                        },
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Text("Create", fontSize = 11.sp)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Session Listing Title
        Text(
            text = "Active Support Logs",
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = 4.dp)
        )

        // Session logs table lists
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(allSessions) { session ->
                val isSelected = session.id == currentSessionId
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                            else Color.Transparent
                        )
                        .clickable { onSelectSession(session.id) }
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (isSelected) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                            contentDescription = null,
                            tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = session.title,
                            fontSize = 13.sp,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                        )
                    }
                    if (isSelected) {
                        IconButton(
                            onClick = { onDeleteSession(session.id) },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Close ticket log",
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

// Welcoming layout suggestions flow
@Composable
fun EmptyWelcomeState(
    modifier: Modifier = Modifier,
    onSuggestionClick: (String) -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(top = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Aesthetic support badge logo
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primary,
                            MaterialTheme.colorScheme.secondary
                        )
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.ChatBubble,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(32.dp)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Welcome to Enterprise Support AI",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = "Powered by Gemini for microsecond SLA responses.",
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 24.dp)
        )

        Spacer(modifier = Modifier.height(32.dp))

        Text(
            text = "Common Inquiries (One-Click Trigger)",
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.align(Alignment.Start)
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Preset Common customer questions for seamless one click ticketing testing
        val presets = listOf(
            "📦 Where is my shipping order #A90281?",
            "⚙️ How does your developer pricing SLA work?",
            "💳 Request refund options for enterprise tiers",
            "🖥️ Guide me on triggering custom routing delays"
        )

        presets.forEach { preset ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .clickable { onSuggestionClick(preset) },
                shape = RoundedCornerShape(8.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Outlined.FlashOn,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = preset,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

// Support Bubble
@Composable
fun MessageBubble(
    message: ChatMessage,
    onSpeak: () -> Unit,
    onCopy: () -> Unit
) {
    val isUser = message.role == "user"
    var feedbackState by remember { mutableStateOf<Boolean?>(null) } // true=thumbs up, false=thumbs down

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        if (!isUser) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.2f))
                    .border(0.5.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.4f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.SmartToy,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(16.dp)
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
        }

        Column(
            horizontalAlignment = if (isUser) Alignment.End else Alignment.Start,
            modifier = Modifier.weight(1f, fill = false)
        ) {
            // Main message text bubble
            Box(
                modifier = Modifier
                    .clip(
                        RoundedCornerShape(
                            topStart = 16.dp,
                            topEnd = 16.dp,
                            bottomEnd = if (isUser) 2.dp else 16.dp,
                            bottomStart = if (isUser) 16.dp else 2.dp
                        )
                    )
                    .background(
                        if (isUser) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                    )
                    .border(
                        width = if (isUser) 0.dp else 0.5.dp,
                        color = if (isUser) Color.Transparent else MaterialTheme.colorScheme.outlineVariant,
                        shape = RoundedCornerShape(
                            topStart = 16.dp,
                            topEnd = 16.dp,
                            bottomEnd = if (isUser) 2.dp else 16.dp,
                            bottomStart = if (isUser) 16.dp else 2.dp
                        )
                    )
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                Column {
                    Text(
                        text = message.text,
                        color = if (isUser) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                        fontWeight = if (message.isError) FontWeight.SemiBold else FontWeight.Normal
                    )

                    // Diagnostic Latency indicators for BOT only
                    if (!isUser) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            val latencyText = if (message.isError) "N/A" else "${message.latencyMs}ms"
                            val bulletColor = if (message.isError) {
                                MaterialTheme.colorScheme.error
                            } else if (message.latencyMs < 600) {
                                Color(0xFF4CAF50) // Green SLA
                            } else if (message.latencyMs < 1500) {
                                Color(0xFFFFA726) // Amber SLA
                            } else {
                                Color(0xFFEF5350)
                            }

                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(bulletColor)
                            )
                            Text(
                                text = "SLA Latency: $latencyText",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
            }

            // Quick Actions segment
            if (!isUser) {
                Row(
                    modifier = Modifier.padding(top = 2.dp, start = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onCopy,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "Copy text",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(12.dp)
                        )
                    }
                    IconButton(
                        onClick = onSpeak,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.VolumeUp,
                            contentDescription = "Read aloud",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(12.dp)
                        )
                    }

                    // SLA customer experience rating log triggers
                    IconButton(
                        onClick = { feedbackState = true },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = if (feedbackState == true) Icons.Filled.ThumbUp else Icons.Outlined.ThumbUp,
                            contentDescription = "Satisfactory",
                            tint = if (feedbackState == true) Color(0xFF4CAF50) else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(12.dp)
                        )
                    }
                    IconButton(
                        onClick = { feedbackState = false },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = if (feedbackState == false) Icons.Filled.ThumbDown else Icons.Outlined.ThumbDown,
                            contentDescription = "Unsatisfactory",
                            tint = if (feedbackState == false) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(12.dp)
                        )
                    }
                }
            } else {
                Text(
                    text = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(message.timestamp)),
                    fontSize = 9.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp, end = 4.dp)
                )
            }
        }

        if (isUser) {
            Spacer(modifier = Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

// Minimal Typing animation loader
@Composable
fun TypingIndicatorBubble() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.Start
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.2f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.SmartToy,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.size(16.dp)
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomEnd = 16.dp, bottomStart = 2.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
                .padding(horizontal = 14.dp, vertical = 12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(12.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Generating response...",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                )
            }
        }
    }
}

// Input Segment
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatInputSection(
    userInput: String,
    onInputChange: (String) -> Unit,
    onSend: () -> Unit,
    isTyping: Boolean,
    lastSlaMs: Long,
    selectedModel: String,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        // Minimal horizontal meter of active performance status parameters
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp, horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Speed,
                    contentDescription = null,
                    tint = if (lastSlaMs > 0 && lastSlaMs < 600) Color(0xFF4CAF50) else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(12.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "Support SLA Average: ${if (lastSlaMs > 0) "${lastSlaMs}ms" else "Standby Mode"}",
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Text(
                text = "Model: ${selectedModel.substringBefore("-").uppercase()}",
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TextField(
                value = userInput,
                onValueChange = onInputChange,
                placeholder = { Text("Ask support anything...", fontSize = 14.sp) },
                singleLine = false,
                maxLines = 3,
                textStyle = androidx.compose.ui.text.TextStyle(fontSize = 14.sp),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    disabledIndicatorColor = Color.Transparent
                ),
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier
                    .weight(1f)
                    .testTag("chat_input_text_field"),
                enabled = !isTyping
            )

            FloatingActionButton(
                onClick = {
                    if (userInput.isNotBlank() && !isTyping) {
                        onSend()
                    }
                },
                modifier = Modifier
                    .size(48.dp)
                    .testTag("send_button"),
                shape = CircleShape,
                containerColor = if (userInput.isNotBlank() && !isTyping) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                contentColor = if (userInput.isNotBlank() && !isTyping) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Send,
                    contentDescription = "Send support text",
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}
