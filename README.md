# NEW DUBAI — Tailoring & Fabric Shop

An offline-first Android application built with Kotlin, Jetpack Compose, Room, and WorkManager.

## Main features

- Pashto, Dari, and English interface with RTL support for Pashto and Dari.
- Owner sign-in with password/PIN and optional biometric unlock.
- Customer profiles, tailoring orders, order status, delivery dates, measurements, and payment/debt tracking.
- Expanded garment catalogue: Afghan, Indian, Pakistani, Chinese, Arabic, Iranian, Korean, and Western styles for adults and children.
- Fabric inventory with 96 selectable fabric-color swatches, photos, purchases, sales, and low-stock alerts.
- Cash book, expenses, dashboard, and financial reports.
- Local backup, encrypted backup, ZIP backup, and restore.
- Animated dashboard, screen transitions, interactive cards, and a five-tab bottom navigation bar.

## Build APK with GitHub Actions

1. Upload the contents of this project to a GitHub repository.
2. Open **Actions**.
3. Select **Build NEW DUBAI APK**.
4. Tap **Run workflow** (or push a commit to `main`).
5. When the workflow succeeds, open the run and download the **NEW-DUBAI-APK** artifact.
6. Extract the artifact ZIP to get `app-debug.apk`.

The workflow uses Java 17, Android SDK 34, and Gradle 8.7.

## Important setup note

Local backup/restore works without cloud configuration. Google Drive backup requires a Google Cloud OAuth Android client configured for this app's package name (`com.newdubai.shop`) and the signing certificate SHA-1 used by the APK. Without that configuration, Drive authorization may fail.

## Data safety

The Room database is upgraded from version 1 to version 2 with a migration that adds the fabric color field while preserving existing shop records. Before installing a test build over an existing installation, keep a separate backup of important business data.
