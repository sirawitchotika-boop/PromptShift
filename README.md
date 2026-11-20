# PromptShift (`v2.2.0`)

**PromptShift** is a lightweight native Android floating clipboard, direct-AI SSE streaming generator, and 1-tap auto-insert Mini-Keyboard built for high-volume multi-prompt workflows (`===PROMPT===`) without clipboard truncation or constant app-switching.

> **100% Banking-App Safe:** PromptShift uses standard Android `ClipboardManager`, `InputMethodService` (Keyboard API), and an Android 14 `specialUse` foreground overlay. It contains **ZERO `AccessibilityService`** references (enforced automatically on every CI build), so Thai banking apps never block your phone.

---

## Core Capabilities

### 1. Floating Overlay Bubble (`FloatingOverlayService`)
- **Expanded Pill:** `[●] [AI] [Load] [📌] [⏱] [Clear]  1840/2000  [✕]` on the top row and `[ < ] [ Copy #3/20 ] [ > ]` on the bottom row.
- **Collapsed Edge-Snap Dot:** Shrinks to a compact edge badge showing your current slot number and emerald copied status dot.
- **2,000-Char Soft Quota Warning:** Live character counter turns crimson if a slot exceeds 2,000 characters while never blocking copy/insert.
- **Accidental Overwrite & Clear Guards:** Rejects non-`===PROMPT===` clipboard text without wiping your active batch, requires a 2-tap `Confirm?` to clear, and warns before replacing uncopied slots.

### 2. 1-Tap Auto-Insert Mini-Keyboard (`PromptShiftImeService`)
- **Direct Cursor Insertion:** Inserts full 2,000+ character prompts straight into native `EditText`, Compose `TextField`, or WebViews (with automatic clipboard-paste fallback).
- **Replace / Append & Auto-Next:** Toggle `Rep / App` mode and `Auto>` (auto-advances to the next slot after insert without wrap-around), plus `[⌫]` to clear the target input box and `[🌐]` to switch back to Gboard in 1 tap.
- **Scrollable Snippets & Recent Strip:** Tap any saved snippet or recent prompt chip on the keyboard bar to auto-type at the cursor with smart `", "` joining (or toggle `Snip:Rep` to replace the box).

### 3. Direct OpenAI-Compatible AI Streaming (`v2.0+`)
- **Provider-Agnostic Profiles:** Connect OpenRouter, DeepSeek, OpenAI, or any `/v1/chat/completions` endpoint. API keys are encrypted locally via **Android Keystore (`AES/GCM/NoPadding`)** and never written to plain-text config files.
- **Favorite Models & `Fetch Models` (`v2.2`):** Store multiple favorite model IDs per provider, query `GET {baseUrl}/models` from the provider editor, and switch models in 1 tap from Quick-Direction or the Main Hub.
- **Reasoning Effort (`v2.2`):** 1-tap `Default (Off)` · `Low` · `Medium` · `High` chips send `"reasoning_effort"` while `StreamingPromptAssembler` strips `<think>...</think>` and `reasoning_content` so only clean prompts unlock into slots.
- **Live SSE Slot Unlock:** Launch **Quick-Direction** from `[AI]` on the bubble or Mini-Keyboard and start copying or inserting slot `#1` while `#2..#N` are still streaming.

### 4. Power-User Prompt Toolkit (`v2.2`)
- **Pinned Trigger Words:** Save reusable Trigger Word chips and toggle ON/OFF anytime. Automatically prepends `<Active Trigger Word>, <Main Prompt>` on Copy & Insert, includes trigger length in the `XXXX/2000` counter, and instructs the AI not to repeat the trigger word inside generated bodies.
- **Saved Snippets & Recent 10:** Manage reusable style tags or full prompt templates (with per-snippet `"Include active Trigger Word"` toggle), save the active batch slot in 1 tap via `[+Snippet]`, or re-use the last 10 copied/inserted prompts from the Hub, Mini-Keyboard, or `[📌]` Floating Bubble popup.
- **Wide Instruction Preset Editor & Expandable Cards:** 95%-width editor dialog with a 14–18 line scrollable text box, live character count, 1-tap `Paste from Clipboard`, and `Expand ▾ / Collapse ▴` preview toggles on Hub cards.
- **Simple Countdown Timer (`[⏱]`):** Enter `Min` + `Sec` from the Bubble, Mini-Keyboard, or Hub. Displays a live `MM:SS` countdown pill, vibrates in a ~4-second auto-stopping pulse pattern when time is up, and posts a `PromptShift Timer Done` heads-up notification with 1-tap `Dismiss`.
- **Safe JSON Backup (v2):** Export and import **Instruction Profiles + Saved Snippets + Trigger Word Chips + Favorite Model IDs** in a single JSON file — with **zero API keys or provider URLs** ever exported.

---

## Daily Usage Flows

1. **Direct AI Stream Flow:** Start the Floating Bubble → tap **`[AI]`** (from Bubble or Mini-Keyboard) → pick Provider, Favorite Model, Instruction Preset, Reasoning Effort, and Prompt Count → enter your direction (or tap snippet chips to append) → tap **Generate**.
2. **Manual Clipboard Batch Flow:** Copy any `===PROMPT===` block from an external AI chat → tap **`[Load]`** on the Floating Bubble, Mini-Keyboard, or Main Hub.
3. **Output into Target App:**
   - **Floating Bubble:** Tap **`Copy #k/N`** and paste into your target app, or tap **`[📌]`** for Snippets & Recent 10.
   - **PromptShift Keyboard:** Tap **`Insert #k/N`** to auto-insert directly at the cursor, or tap chips on the snippet strip to append style tags.

---

## Architecture & Package Layout

- `com.promptshift.app.domain` — Immutable state models (`PromptBatchState`, `AiConfigState`, `ProviderProfile`, `InstructionProfile`, `PromptSnippet`, `ReasoningEffort`), `PromptBatchParser`, and `PromptTextComposer` (Trigger Word & smart `", "` joiner).
- `com.promptshift.app.data` — Atomic file-backed repositories (`DefaultPromptBatchRepository`, `DefaultAiConfigRepository`), pure-Kotlin JSON codecs (`PromptJsonCodec`, `AiConfigCodec` with Safe Backup v2), and `AndroidKeystoreApiKeyStore`.
- `com.promptshift.app.ai` — `OpenAiRequestBuilder`, `HttpOpenAiStreamingClient` (SSE + `/models`), `StreamingPromptAssembler` (`<think>` stripper), and `GenerationCoordinator`.
- `com.promptshift.app.overlay` — `FloatingOverlayService` (`specialUse` FGS) and `FloatingOverlayViewController`.
- `com.promptshift.app.ime` — `PromptShiftImeService`, `PromptShiftImeViewController`, and `PromptInsertEngine` (direct `InputConnection` + WebView paste fallback).
- `com.promptshift.app.timer` — `CountdownTimerManager`, `TimerAlertNotifier` (~4s auto-stop waveform + heads-up notification), and `TimerAlertReceiver`.
- `com.promptshift.app.ui` — Jetpack Compose GT Carbon & Crimson UI (`MainActivity`, `PromptShiftHubScreen`, `Phase4HubCards`, `AiManagementDialogs`, `QuickDirectionActivity`, `SnippetsPopupActivity`, `TimerPopupActivity`, `RealmeReadinessHelper`).

---

## Build, Test & CI

GitHub Actions (`.github/workflows/android-apk.yml`) runs on every push and pull request:

1. **Banking-App Safety Gate:** Fails the build if `BIND_ACCESSIBILITY_SERVICE` or `AccessibilityService` appears anywhere in `app/src/main`.
2. **Unit Tests, Lint & Debug APK:**
   ```bash
   ./gradlew testDebugUnitTest lintDebug assembleDebug
   ```
3. **Artifact Upload:** Publishes `PromptShift-debug.apk` (`dist/PromptShift-debug.apk`) on every CI run for direct in-place installation on Android 8.0+ (`minSdk 26`, `targetSdk 34`).

---

## Release History

| Version | Highlights |
| :--- | :--- |
| **`v2.2.0` (`versionCode 5`)** | Phase 4: Pinned Trigger Words, Saved Snippets & Recent 10, Favorite AI Models + `Fetch Models`, Reasoning Effort (`efforts`), Wide Instruction Editor + Expand/Collapse cards, `[⏱]` Countdown Timer, and Safe JSON Backup v2. |
| **`v2.1.0` (`versionCode 4`)** | Phase 3: Banking-app-safe `PromptShiftImeService` Mini-Keyboard (`Replace` / `Append`, `Auto-Next`, WebView fallback, 1-tap `🌐` keyboard switch). |
| **`v2.0.0` (`versionCode 3`)** | Phase 2: Direct OpenAI-compatible SSE streaming, Android Keystore API key encryption, Provider & Instruction Profiles, and Quick-Direction popup. |
| **`v1.1.0` (`versionCode 2`)** | Provider-agnostic AI Format Helper (`===PROMPT===`). |
| **`v1.0.0` (`versionCode 1`)** | Initial GT Carbon & Crimson Floating Overlay Bubble, batch parser, 2,000-char soft quota, and Realme readiness strip. |
---

## Maintenance

PromptShift uses GitHub pull requests and automated CI checks to validate changes before merging into `main`.
