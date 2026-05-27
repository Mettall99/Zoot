# Android client

## App flow (MVP)
1. Start screen with manual `zoootconf://...` link input.
2. Server selection screen.
3. Connection setup with protocol list.
4. Connected screen with timer and traffic placeholders.

Deep link is preserved:
`adb shell am start -a android.intent.action.VIEW -d "zoootconf://demo-token"`

Manual entry behaves the same as deep link and resolves token through backend.

WireGuard is the only active protocol in MVP. Other protocols are shown as disabled/coming soon unless configuration is available.
