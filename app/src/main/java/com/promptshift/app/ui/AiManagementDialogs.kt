package com.promptshift.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.promptshift.app.clipboard.ClipboardHelper
import com.promptshift.app.domain.FetchModelsResult
import com.promptshift.app.domain.InstructionProfile
import com.promptshift.app.domain.PromptSnippet
import com.promptshift.app.domain.ProviderProfile
import com.promptshift.app.ui.theme.PromptShiftColors
import kotlinx.coroutines.launch

@Composable
fun ProviderProfileEditorDialog(
    initial: ProviderProfile?,
    onDismiss: () -> Unit,
    onFetchModels: suspend (baseUrl: String, apiKeyInput: String?, existingProviderId: String?) -> FetchModelsResult,
    onSave: (
        id: String?,
        name: String,
        baseUrl: String,
        modelId: String,
        apiKeyInput: String?,
        favoriteModels: List<String>,
    ) -> Unit,
) {
    var name by remember(initial) { mutableStateOf(initial?.name.orEmpty()) }
    var baseUrl by remember(initial) { mutableStateOf(initial?.baseUrl.orEmpty()) }
    var modelId by remember(initial) { mutableStateOf(initial?.modelId.orEmpty()) }
    var apiKey by remember(initial) { mutableStateOf("") }
    var favoriteModels by remember(initial) { mutableStateOf(initial?.favoriteModels.orEmpty()) }
    var newFavoriteInput by remember { mutableStateOf("") }
    var revealKey by remember { mutableStateOf(false) }
    var validationError by remember { mutableStateOf<String?>(null) }

    var isFetchingModels by remember { mutableStateOf(false) }
    var fetchedModels by remember { mutableStateOf<List<String>>(emptyList()) }
    var fetchError by remember { mutableStateOf<String?>(null) }
    var modelSearch by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(0.94f),
            shape = RoundedCornerShape(18.dp),
            color = PromptShiftColors.CarbonSurface,
            border = BorderStroke(1.dp, PromptShiftColors.MetalBorder),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = if (initial == null) "ADD AI PROVIDER" else "EDIT AI PROVIDER",
                    color = PromptShiftColors.TextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 1.sp,
                )
                Text(
                    text = "Works with any OpenAI-compatible API endpoint. API keys are encrypted locally via Android Keystore.",
                    color = PromptShiftColors.TextMuted,
                    fontSize = 12.sp,
                    lineHeight = 16.5.sp,
                )

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it; validationError = null },
                    label = { Text("Profile Name", fontSize = 12.sp) },
                    placeholder = { Text("e.g. OpenRouter / DeepSeek / Primary", fontSize = 12.sp) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = darkInputColors(),
                    shape = RoundedCornerShape(12.dp),
                )

                OutlinedTextField(
                    value = baseUrl,
                    onValueChange = { baseUrl = it; validationError = null },
                    label = { Text("Base URL", fontSize = 12.sp) },
                    placeholder = { Text("https://api.example.com/v1", fontSize = 12.sp) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = darkInputColors(),
                    shape = RoundedCornerShape(12.dp),
                )

                OutlinedTextField(
                    value = modelId,
                    onValueChange = { modelId = it; validationError = null },
                    label = { Text("Active Model ID", fontSize = 12.sp) },
                    placeholder = { Text("e.g. gpt-4o-mini / deepseek-chat", fontSize = 12.sp) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = darkInputColors(),
                    shape = RoundedCornerShape(12.dp),
                )

                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it; validationError = null },
                    label = { Text("API Key", fontSize = 12.sp) },
                    placeholder = {
                        Text(
                            if (initial?.hasApiKey == true) {
                                "Encrypted key saved (leave blank to keep)"
                            } else {
                                "sk-..."
                            },
                            fontSize = 12.sp,
                        )
                    },
                    singleLine = true,
                    visualTransformation = if (revealKey) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        Text(
                            text = if (revealKey) "Hide" else "Show",
                            color = PromptShiftColors.TextMuted,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier
                                .clickable { revealKey = !revealKey }
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = darkInputColors(),
                    shape = RoundedCornerShape(12.dp),
                )

                // Favorite Models section
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(PromptShiftColors.ObsidianBg)
                        .border(1.dp, PromptShiftColors.MetalBorder, RoundedCornerShape(12.dp))
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "FAVORITE MODELS (${favoriteModels.size})",
                            color = PromptShiftColors.TextMuted,
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.8.sp,
                        )
                        Text(
                            text = if (isFetchingModels) "Fetching..." else "Fetch Models",
                            color = if (isFetchingModels) PromptShiftColors.TextMuted else PromptShiftColors.CopiedEmerald,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(PromptShiftColors.CarbonElevated)
                                .border(1.dp, PromptShiftColors.MetalBorder, RoundedCornerShape(8.dp))
                                .clickable(enabled = !isFetchingModels) {
                                    fetchError = null
                                    isFetchingModels = true
                                    scope.launch {
                                        when (val res = onFetchModels(baseUrl.trim(), apiKey.trim().ifEmpty { null }, initial?.id)) {
                                            is FetchModelsResult.Success -> {
                                                fetchedModels = res.models
                                                fetchError = null
                                            }
                                            is FetchModelsResult.Failure -> {
                                                fetchError = res.message
                                            }
                                        }
                                        isFetchingModels = false
                                    }
                                }
                                .padding(horizontal = 9.dp, vertical = 4.dp),
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        OutlinedTextField(
                            value = newFavoriteInput,
                            onValueChange = { newFavoriteInput = it },
                            placeholder = { Text("Add favorite model ID...", fontSize = 12.sp) },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                            colors = darkInputColors(),
                            shape = RoundedCornerShape(10.dp),
                        )
                        OutlinedButton(
                            onClick = {
                                val cleaned = newFavoriteInput.trim()
                                if (cleaned.isNotEmpty()) {
                                    favoriteModels = (favoriteModels + cleaned).distinct()
                                    if (modelId.isBlank()) modelId = cleaned
                                    newFavoriteInput = ""
                                }
                            },
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, PromptShiftColors.GtCrimson.copy(alpha = 0.7f)),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = PromptShiftColors.TextPrimary),
                        ) {
                            Text("+ Add", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    if (favoriteModels.isNotEmpty()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            favoriteModels.forEach { fav ->
                                Row(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(PromptShiftColors.CarbonElevated)
                                        .border(1.dp, PromptShiftColors.MetalBorder, RoundedCornerShape(8.dp))
                                        .padding(horizontal = 8.dp, vertical = 5.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    Text(
                                        text = fav,
                                        color = PromptShiftColors.TextPrimary,
                                        fontSize = 11.5.sp,
                                        maxLines = 1,
                                        modifier = Modifier.clickable { modelId = fav },
                                    )
                                    Text(
                                        text = "×",
                                        color = PromptShiftColors.QuotaWarnRed,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.clickable {
                                            favoriteModels = favoriteModels.filterNot { it == fav }
                                        },
                                    )
                                }
                            }
                        }
                    }

                    if (!fetchError.isNullOrBlank()) {
                        Text(
                            text = fetchError.orEmpty(),
                            color = PromptShiftColors.QuotaWarnRed,
                            fontSize = 11.5.sp,
                        )
                    }

                    if (fetchedModels.isNotEmpty()) {
                        OutlinedTextField(
                            value = modelSearch,
                            onValueChange = { modelSearch = it },
                            placeholder = { Text("Filter ${fetchedModels.size} fetched models...", fontSize = 11.5.sp) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            colors = darkInputColors(),
                            shape = RoundedCornerShape(10.dp),
                        )
                        val filtered = remember(fetchedModels, modelSearch) {
                            val q = modelSearch.trim()
                            if (q.isEmpty()) {
                                fetchedModels.take(40)
                            } else {
                                fetchedModels.filter { it.contains(q, ignoreCase = true) }.take(40)
                            }
                        }
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 150.dp)
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            filtered.forEach { item ->
                                val isFav = item in favoriteModels
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (isFav) PromptShiftColors.CopiedEmeraldBg else PromptShiftColors.CarbonSurface)
                                        .border(
                                            1.dp,
                                            if (isFav) PromptShiftColors.CopiedEmerald else PromptShiftColors.MetalBorder,
                                            RoundedCornerShape(8.dp),
                                        )
                                        .clickable {
                                            favoriteModels = if (isFav) {
                                                favoriteModels.filterNot { it == item }
                                            } else {
                                                (favoriteModels + item).distinct()
                                            }
                                            if (modelId.isBlank()) modelId = item
                                        }
                                        .padding(horizontal = 10.dp, vertical = 6.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        text = item,
                                        color = PromptShiftColors.TextPrimary,
                                        fontSize = 11.5.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f),
                                    )
                                    Text(
                                        text = if (isFav) "★ Fav" else "+ Fav",
                                        color = if (isFav) PromptShiftColors.CopiedEmerald else PromptShiftColors.TextMuted,
                                        fontSize = 10.5.sp,
                                        fontWeight = FontWeight.Bold,
                                    )
                                }
                            }
                        }
                    }
                }

                if (!validationError.isNullOrBlank()) {
                    Text(
                        text = validationError.orEmpty(),
                        color = PromptShiftColors.QuotaWarnRed,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, PromptShiftColors.MetalBorder),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = PromptShiftColors.TextMuted),
                    ) {
                        Text("Cancel", fontWeight = FontWeight.Bold)
                    }
                    Button(
                        onClick = {
                            val cleanName = name.trim()
                            val cleanUrl = baseUrl.trim()
                            val cleanModel = modelId.trim()
                            val cleanKey = apiKey.trim()
                            when {
                                cleanName.isEmpty() -> validationError = "Enter a profile name"
                                !cleanUrl.startsWith("http://") && !cleanUrl.startsWith("https://") ->
                                    validationError = "Base URL must start with https://"
                                cleanModel.isEmpty() -> validationError = "Enter a Model ID"
                                cleanKey.isEmpty() && initial?.hasApiKey != true ->
                                    validationError = "Enter an API key"
                                else -> onSave(
                                    initial?.id,
                                    cleanName,
                                    cleanUrl,
                                    cleanModel,
                                    cleanKey.ifEmpty { null },
                                    favoriteModels,
                                )
                            }
                        },
                        modifier = Modifier.weight(1.2f),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = PromptShiftColors.GtCrimson,
                            contentColor = PromptShiftColors.TextPrimary,
                        ),
                    ) {
                        Text("Save Provider", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun InstructionProfileEditorDialog(
    initial: InstructionProfile?,
    onDismiss: () -> Unit,
    onSave: (id: String?, name: String, body: String) -> Unit,
) {
    val context = LocalContext.current
    var name by remember(initial) { mutableStateOf(initial?.name.orEmpty()) }
    var body by remember(initial) { mutableStateOf(initial?.body.orEmpty()) }
    var validationError by remember { mutableStateOf<String?>(null) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(0.95f),
            shape = RoundedCornerShape(18.dp),
            color = PromptShiftColors.CarbonSurface,
            border = BorderStroke(1.dp, PromptShiftColors.MetalBorder),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = if (initial == null) "ADD INSTRUCTION PROFILE" else "EDIT INSTRUCTION PROFILE",
                    color = PromptShiftColors.TextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 1.sp,
                )
                Text(
                    text = "Save your reusable base rules. PromptShift automatically appends the strict ===PROMPT=== format rule when generating.",
                    color = PromptShiftColors.TextMuted,
                    fontSize = 12.sp,
                    lineHeight = 16.5.sp,
                )

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it; validationError = null },
                    label = { Text("Preset Name", fontSize = 12.sp) },
                    placeholder = { Text("e.g. Style A / Product Shots", fontSize = 12.sp) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = darkInputColors(),
                    shape = RoundedCornerShape(12.dp),
                )

                // Live character count + 1-tap Paste from Clipboard + Clear
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "${body.length} chars",
                        color = PromptShiftColors.TextMuted,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.5.sp,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (body.isNotEmpty()) {
                            Text(
                                text = "Clear",
                                color = PromptShiftColors.QuotaWarnRed,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(PromptShiftColors.CarbonElevated)
                                    .border(1.dp, PromptShiftColors.MetalBorder, RoundedCornerShape(8.dp))
                                    .clickable { body = "" }
                                    .padding(horizontal = 9.dp, vertical = 5.dp),
                            )
                        }
                        Text(
                            text = "Paste from Clipboard",
                            color = PromptShiftColors.TextPrimary,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(PromptShiftColors.CarbonElevated)
                                .border(1.dp, PromptShiftColors.GtCrimson.copy(alpha = 0.7f), RoundedCornerShape(8.dp))
                                .clickable {
                                    val clip = ClipboardHelper.readPlainText(context)?.trim()
                                    if (!clip.isNullOrEmpty()) {
                                        body = if (body.isBlank()) clip else "$body\n$clip"
                                        validationError = null
                                    } else {
                                        validationError = "Clipboard is empty"
                                    }
                                }
                                .padding(horizontal = 10.dp, vertical = 5.dp),
                        )
                    }
                }

                OutlinedTextField(
                    value = body,
                    onValueChange = { body = it; validationError = null },
                    label = { Text("Global Instruction", fontSize = 12.sp) },
                    placeholder = { Text("Paste your existing system/style rules here...", fontSize = 12.sp) },
                    minLines = 14,
                    maxLines = 18,
                    modifier = Modifier.fillMaxWidth(),
                    colors = darkInputColors(),
                    shape = RoundedCornerShape(12.dp),
                )

                if (!validationError.isNullOrBlank()) {
                    Text(
                        text = validationError.orEmpty(),
                        color = PromptShiftColors.QuotaWarnRed,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, PromptShiftColors.MetalBorder),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = PromptShiftColors.TextMuted),
                    ) {
                        Text("Cancel", fontWeight = FontWeight.Bold)
                    }
                    Button(
                        onClick = {
                            val cleanName = name.trim()
                            val cleanBody = body.trim()
                            when {
                                cleanName.isEmpty() -> validationError = "Enter a preset name"
                                cleanBody.isEmpty() -> validationError = "Enter instruction text"
                                else -> onSave(initial?.id, cleanName, cleanBody)
                            }
                        },
                        modifier = Modifier.weight(1.2f),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = PromptShiftColors.GtCrimson,
                            contentColor = PromptShiftColors.TextPrimary,
                        ),
                    ) {
                        Text("Save Preset", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun SnippetEditorDialog(
    initial: PromptSnippet?,
    onDismiss: () -> Unit,
    onSave: (id: String?, title: String, body: String, includeTriggerWord: Boolean) -> Unit,
) {
    var title by remember(initial) { mutableStateOf(initial?.title.orEmpty()) }
    var body by remember(initial) { mutableStateOf(initial?.body.orEmpty()) }
    var includeTrigger by remember(initial) { mutableStateOf(initial?.includeTriggerWord ?: false) }
    var validationError by remember { mutableStateOf<String?>(null) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(0.94f),
            shape = RoundedCornerShape(18.dp),
            color = PromptShiftColors.CarbonSurface,
            border = BorderStroke(1.dp, PromptShiftColors.MetalBorder),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = if (initial == null) "ADD SAVED SNIPPET" else "EDIT SAVED SNIPPET",
                    color = PromptShiftColors.TextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 1.sp,
                )
                Text(
                    text = "Reusable style tags or full prompts for 1-tap auto-type on the Mini-Keyboard and Floating Bubble.",
                    color = PromptShiftColors.TextMuted,
                    fontSize = 12.sp,
                    lineHeight = 16.5.sp,
                )

                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it; validationError = null },
                    label = { Text("Snippet Title", fontSize = 12.sp) },
                    placeholder = { Text("e.g. 35mm Film / Quality Boost", fontSize = 12.sp) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = darkInputColors(),
                    shape = RoundedCornerShape(12.dp),
                )

                OutlinedTextField(
                    value = body,
                    onValueChange = { body = it; validationError = null },
                    label = { Text("Snippet Text", fontSize = 12.sp) },
                    placeholder = { Text("e.g. cinematic lighting, ultra detailed, 8k", fontSize = 12.sp) },
                    minLines = 4,
                    maxLines = 8,
                    modifier = Modifier.fillMaxWidth(),
                    colors = darkInputColors(),
                    shape = RoundedCornerShape(12.dp),
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(PromptShiftColors.ObsidianBg)
                        .border(1.dp, PromptShiftColors.MetalBorder, RoundedCornerShape(12.dp))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Include active Trigger Word",
                            color = PromptShiftColors.TextPrimary,
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = "OFF for short style/quality tags, ON for full prompt templates.",
                            color = PromptShiftColors.TextMuted,
                            fontSize = 11.sp,
                        )
                    }
                    Switch(
                        checked = includeTrigger,
                        onCheckedChange = { includeTrigger = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = PromptShiftColors.TextPrimary,
                            checkedTrackColor = PromptShiftColors.CopiedEmerald,
                        ),
                    )
                }

                if (!validationError.isNullOrBlank()) {
                    Text(
                        text = validationError.orEmpty(),
                        color = PromptShiftColors.QuotaWarnRed,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, PromptShiftColors.MetalBorder),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = PromptShiftColors.TextMuted),
                    ) {
                        Text("Cancel", fontWeight = FontWeight.Bold)
                    }
                    Button(
                        onClick = {
                            val cleanTitle = title.trim()
                            val cleanBody = body.trim()
                            when {
                                cleanTitle.isEmpty() -> validationError = "Enter a snippet title"
                                cleanBody.isEmpty() -> validationError = "Enter snippet text"
                                else -> onSave(initial?.id, cleanTitle, cleanBody, includeTrigger)
                            }
                        },
                        modifier = Modifier.weight(1.2f),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = PromptShiftColors.GtCrimson,
                            contentColor = PromptShiftColors.TextPrimary,
                        ),
                    ) {
                        Text("Save Snippet", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
