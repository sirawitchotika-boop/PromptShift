package com.promptshift.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.promptshift.app.domain.InstructionProfile
import com.promptshift.app.domain.ProviderProfile
import com.promptshift.app.ui.theme.PromptShiftColors

@Composable
fun ProviderProfileEditorDialog(
    initial: ProviderProfile?,
    onDismiss: () -> Unit,
    onSave: (id: String?, name: String, baseUrl: String, modelId: String, apiKeyInput: String?) -> Unit,
) {
    var name by remember(initial) { mutableStateOf(initial?.name.orEmpty()) }
    var baseUrl by remember(initial) { mutableStateOf(initial?.baseUrl.orEmpty()) }
    var modelId by remember(initial) { mutableStateOf(initial?.modelId.orEmpty()) }
    var apiKey by remember(initial) { mutableStateOf("") }
    var revealKey by remember { mutableStateOf(false) }
    var validationError by remember { mutableStateOf<String?>(null) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
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
                    label = { Text("Model ID", fontSize = 12.sp) },
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
    var name by remember(initial) { mutableStateOf(initial?.name.orEmpty()) }
    var body by remember(initial) { mutableStateOf(initial?.body.orEmpty()) }
    var validationError by remember { mutableStateOf<String?>(null) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
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

                OutlinedTextField(
                    value = body,
                    onValueChange = { body = it; validationError = null },
                    label = { Text("Global Instruction", fontSize = 12.sp) },
                    placeholder = { Text("Paste your existing system/style rules here...", fontSize = 12.sp) },
                    minLines = 5,
                    maxLines = 10,
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
