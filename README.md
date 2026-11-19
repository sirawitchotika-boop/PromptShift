# PromptShift

Lightweight native Android floating clipboard & direct-AI streaming helper for transferring large multi-prompt batches (`===PROMPT===`) from any AI assistant or OpenAI-compatible API into your target app without clipboard truncation.

- **Copy-only & Banking-app safe:** Zero `AccessibilityService` permissions.
- **Provider-agnostic Direct AI (v2.0):** Save multiple OpenAI-compatible Provider Profiles (API keys encrypted locally via Android Keystore) and reusable Instruction Profiles (JSON export/import without API keys).
- **Live SSE Slot Unlock:** Launch Quick-Direction from the **`[AI]`** button on the floating bubble or Main Hub, stream prompts via SSE (auto-stripping `<think>` reasoning tags), and start copying `#1`, `#2`, `#3` as they unlock — with 1-tap `Stop` cancellation and uncopied-batch overwrite guard.
- **Android 14 compliant:** Uses `specialUse` foreground service and a 1-frame translucent clipboard trampoline for manual `[Load]` anytime.
