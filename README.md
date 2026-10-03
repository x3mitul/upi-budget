# UPI Budget

A personal, private Android app that tracks UPI spending against a monthly allowance, with as little effort as possible.

It reads bank SMS, notifications from UPI apps and, optionally, your own bank alert emails from Gmail (read-only). Each payment is categorized automatically; when the app cannot tell what a payment was, it asks once and remembers the answer. Savings (RD, SIP) and subscriptions are set aside up front so the "left this month" number only shows money you can actually spend.

Everything stays on the phone. There is no server, analytics or crash reporting. The only network use is Google's Gmail API, to read your own mailbox. See [PRIVACY.md](PRIVACY.md).

## Build

Requires JDK 17 and the Android SDK (platform 35).

```bash
echo "sdk.dir=$HOME/Android/Sdk" > local.properties
./gradlew testDebugUnitTest      # unit tests
./gradlew assembleDebug          # app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Sideloaded builds on Android 13 and newer need "Allow restricted settings" (App info, ⋮ menu) before SMS and notification access can be granted. Setup inside the app walks through it, including battery and autostart settings for phones that stop background apps.

## Gmail (optional)

Gmail reading uses your own Google Cloud project, so no shared credentials exist:

1. Create a Google Cloud project and enable the Gmail API.
2. Configure the OAuth consent screen (External), add the `gmail.readonly` scope, and publish it.
3. Create an OAuth client of type **Android** with package `dev.mitul.upibudget` and the SHA-1 of the key you sign the APK with (`keytool -list -v -keystore ~/.android/debug.keystore -alias androiddebugkey -storepass android -keypass android`).
4. In the app: Settings, Gmail, Connect.

## How it works

- `parse/` turns SMS, notifications and emails into payments. Pure Kotlin with unit tests.
- `categorize/` matches payee names: learned exact names, your keyword rules, then about 2,400 built-in keywords (`app/src/main/assets/categories.json`).
- `budget/` computes what is left: allowance plus paybacks, minus spending, savings and money set aside for upcoming auto-debits.
- `ingest/` is the pipeline: parse, drop duplicates across sources, categorize, store, notify.
- `data/` is the Room database and JSON backup/restore.

Settings has a Debug log that keeps raw text the parsers could not read, so new message formats can be added.
