# Kay's Chat Forensics

Free, on-device Android **chat export viewer** plus an optional build of the original [SecurityRonin/chat4n6](https://github.com/SecurityRonin/chat4n6) forensic command-line engine.

## Download Android app

After the **Build Kay's Chat Forensics APK** workflow succeeds, download [Kay's Chat Forensics APK](https://github.com/jettblue1488/Kays-Chat-Forensics/releases/download/android-beta/Kays-Chat-Forensics.apk). APK is a GitHub Actions **debug-signed beta** build; Android may ask to allow installation from the browser/files app. Do not install it unless you trust the repository and build.

You can also use the [Actions page](https://github.com/jettblue1488/Kays-Chat-Forensics/actions/workflows/build-apk.yml) and download the artifact named **Kays-Chat-Forensics-APK** (a ZIP containing the APK).

## Android features

- Pick your own .txt, .json, .html or .zip files using Android's file picker.
- Preview WhatsApp-style text messages, generic JSON records and exported text.
- Basic import of export files that originate from WhatsApp, Signal, Telegram, Instagram, Facebook/Messenger, Snapchat, TikTok or other sources; **format coverage varies** and some exports may not contain messages.
- Search imported text, show original timestamps where available, and export an HTML report.
- SHA-256 digest of the exact selected source file to help track provenance.
- No internet permission, backend, account, hidden scanning, or privileged app access.

**Important limits:** This app is **not** the native chat4n6 recovery engine. It cannot decrypt messages, access other apps' private databases, extract data from accounts, restore deleted chats, or prove that a recovered line is authentic. Its generic JSON extraction is a convenience viewer, not platform-specific forensic interpretation. Max 20 MB selected file, max 4 MB for an individual ZIP entry, max 4,000 imported rows (100 displayed at once), 250 ZIP entries.

## Original chat4n6 forensic engine

The upstream open-source Rust engine supports forensic investigation of properly extracted WhatsApp (Android/iOS), Signal (Android plaintext) and Telegram (Android) database images, including recoverable SQLite remnants. It does **not** support Instagram/Facebook/Snapchat/TikTok forensic database extraction. It does not automatically access live phones/accounts.

You can independently build the **unchanged upstream Linux CLI** through [Build Original chat4n6 Desktop Forensic Engine](https://github.com/jettblue1488/Kays-Chat-Forensics/actions/workflows/build-chat4n6-desktop.yml) → **Run workflow**. Download its artifact and run on a compatible Linux computer with your legally acquired image, according to the [upstream README](https://github.com/SecurityRonin/chat4n6). This executable is separate from the Android APK. For actual recoverable/deleted SQLite content, use the original tool on appropriate extracted evidence; do not use the Android export viewer as a replacement.

## Build without paying

Android GitHub Actions workflow on every push to main:
- GitHub-hosted ubuntu-24.04 runner
- JDK 17 + Gradle 8.9 + Android Gradle Plugin 8.7.3
- builds `assembleDebug`, uploads artifact and publishes a prerelease asset

A normal build should create a debug-signed installable APK with no billing configuration required for a public repository, subject to GitHub's current usage policies.

## Attribution and licensing

Original chat4n6 is copyright (c) 2026 SecurityRonin, licensed under the MIT License. Its **original full MIT text and copyright notice** are preserved in [LICENSE](LICENSE) and inside the Android app as `app/src/main/assets/CHAT4N6-LICENSE.txt`, accessible under **About • License • Limitations**. The desktop workflow also preserves the license in its artifact. Source project: https://github.com/SecurityRonin/chat4n6.

The Android app companion is independently written and does not bundle upstream Rust executable or claim to implement upstream recovery layers. Use it only for files you own or are authorized to examine.
