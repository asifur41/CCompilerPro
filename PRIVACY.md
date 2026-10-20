# CCompiler Pro – Privacy

**Short version:** CCompiler Pro does not collect, send or share any data. It cannot connect to the internet.

* **No network access.** The app does not declare the INTERNET permission, so Android does not let it use the network.
* **No permissions.** It requests no permissions (no storage, contacts, location, camera, microphone). Files are kept in the app's private storage; moving files in or out happens only through Android's file picker, which you control.
* **No accounts, no analytics, no ads, no tracking, no crash reporting, no third-party SDKs.** The only libraries are AndroidX / Material components and Kotlin coroutines (see the dependency list in each release's TRUST_REPORT).
* **Your code stays on your phone.** Programs are compiled and run locally by a bundled C compiler (TinyCC) running as a child process of the app.
* **Actions you start yourself:** "Visit Developer Website", "Donate", "Join Community" (WhatsApp), "Download the latest APK", "Visit GitHub releases" and "Leave a review" open your browser or WhatsApp; "Report a Bug" opens your email app with a draft containing Android version, device model and app version (you can edit or cancel it); "Share Code" opens Android's share sheet.
* **Auto-save** writes only to the app's private storage.
* **If the app crashes,** a short technical log (error text with class names and line numbers, never your code or files) is saved on the phone only. It is attached to a bug-report email only if you decide to send one.

## Verify it yourself
Every release is built in public by GitHub Actions from this repository and published with:
* `CCompilerPro.apk.sha256` – checksum of the APK
* `TRUST_REPORT.md` – the permissions list, a scan for tracker/ads SDKs, the web addresses found in the code, the bundled native files and the dependency list. The build **fails automatically** if any permission, tracker SDK or debuggable flag appears.
* `SIGNING_CERT.txt` – the signing certificate fingerprint. Every genuine release has the same fingerprint.

Some antivirus engines may show a generic warning for apps that bundle a native compiler executable (libtccexe.so) and run it as a child process. That is how the compiler works; the source and build scripts are open for inspection.

Contact: asifur2580@gmail.com
