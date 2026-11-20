# PromptShift

Lightweight native Android floating clipboard & direct-AI streaming helper for transferring large multi-prompt batches (`===PROMPT===`) from any AI assistant or OpenAI-compatible API into your target app without clipboard truncation.

- **Copy-only & Banking-app safe:** Zero `AccessibilityService` permissions.
- **1-Tap Auto-Insert Keyboard (v2.1):** Banking-app-safe `InputMethodService` mini-bar inserts full 2,000-char prompts right at the blinking cursor (`Replace` or `Append` mode, optional `Auto-Next`, WebView clipboard-paste fallback, and 1-tap `🌐` switch back to Gboard) — synced live with the Floating Bubble.
- **Provider-agnostic Direct AI (v2.0):** Save multiple OpenAI-compatible Provider Profiles (API keys encrypted locally via Android Keystore) and reusable Instruction Profiles (JSON export/import without API keys).
- **Live SSE Slot Unlock:** Launch Quick-Direction from the **`[AI]`** button on the floating bubble or Main Hub, stream prompts via SSE (auto-stripping `<think>` reasoning tags), and start copying `#1`, `#2`, `#3` as they unlock — with 1-tap `Stop` cancellation and uncopied-batch overwrite guard.
- **Power-User Toolkit (v2.2):** Pinned Trigger Words (auto-joined with `", "` and synced with the 2,000-char counter + AI system rule), Saved Snippets & Recent 10 (1-tap smart-join auto-type on the Mini-Keyboard and `[📌]` Floating Bubble popup), per-provider Favorite AI Models + `Fetch Models`, Reasoning Effort (`Default / Low / Medium / High`), wide 14–18 line Instruction editor with Expand/Collapse Hub cards, simple `[⏱]` Countdown Timer (~4s auto-stop vibration + heads-up alert), and expanded Safe JSON Backup (zero API keys).
- **Android 14 compliant:** Uses `specialUse` foreground service and a 1-frame translucent clipboard trampoline for manual `[Load]` anytime.
