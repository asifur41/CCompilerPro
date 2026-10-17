# CCompiler Pro

Offline C editor + compiler front-end for Android (Kotlin, minSdk 26, arm64-v8a).
**No INTERNET permission, no storage permissions.** Files live in app-private storage and move in/out via the Storage Access Framework.

## Read this first (honest status)

* The project was generated without an Android SDK / Gradle in the authoring environment, so **it has not been compiled or run yet**. The first GitHub Actions run is the first real build; expect to fix a few compile errors (paste the log back to your assistant).
* **No C compiler binary is bundled.** The app, UI, sanitizer, process runner, timeouts and error display are complete, but compiling requires you to drop a native toolchain into `jniLibs` (next section). Without it, Run shows a clear "No C toolchain installed" message.
* `gradle-wrapper.jar` is a binary that could not be generated offline. The workflow uses `gradle/actions/setup-gradle` (Gradle 8.5) instead, so CI works. For local builds run `gradle wrapper --gradle-version 8.5` once.

## Quick start: GitHub Actions

```bash
unzip CCompilerPro.zip && cd CCompilerPro
git init && git add . && git commit -m "CCompiler Pro"
git branch -M main
git remote add origin https://github.com/<you>/CCompilerPro.git
git push -u origin main
```
Open **Actions → Build CCompiler Pro APK → artifact `ccompiler-pro-debug-apk`** and download `app-debug.apk`.
The workflow also downloads the four fonts (`scripts/fetch_fonts.sh`) so they are **bundled in the APK** and work offline. If a download fails, the build still succeeds and the app falls back to system monospace.

## Compiler toolchain (you must supply this)

Android only allows executing files from the app's native library directory, so tools are packaged as `lib*.so`:

```
app/src/main/jniLibs/arm64-v8a/
    libchibicc.so   # REQUIRED: compiler driver, invoked as:  chibicc -o <out> <prog.c>
    libas.so        # optional helper the driver calls as `as`
    libld.so        # optional helper the driver calls as `ld`
    libcc.so        # optional (link driver), also `ar`, `strip`
```
At runtime they are symlinked under their real names into a private directory that is first on `PATH`; env vars `CC_SYSROOT` (`files/toolchain/sysroot`), `TMPDIR`, `HOME` are set. The driver must read `prog.c` from the working directory and write an **ARM64 Linux/Android executable** to the `-o` path. Compiler output (stdout+stderr) is shown in the Output pane; `prog.c:` is rewritten to your file name.

Important facts about your options:

| Option | Reality |
|---|---|
| **chibicc** | Small and MIT-licensed, **but upstream emits x86-64 assembly only** and needs an external `as`/`ld`. It will not produce ARM64 binaries unless you use an AArch64-capable fork plus ARM64 binutils and a bionic sysroot. |
| **Clang/LLVM** | Most correct, 80–150 MB+. Cross-compile with the NDK (or repackage a prebuilt ARM64 clang), put the driver in `libchibicc.so`, ship headers/libs under `files/toolchain/sysroot`. |
| **Kotlin interpreter** | Not included (would need a separate, large implementation). |

Developer shortcut: push tools with `adb` into `/data/data/com.ccompiler.pro/files/toolchain/bin/` (names `chibicc`, `as`, `ld`...). Because targetSdk is 28 this works for debug builds.

### Why targetSdk 28
Android 10+ forbids executing files from app-writable directories for apps targeting API 29+. A compiler produces such files, so the app targets 28 (still installable on current Android; not eligible for new Play Store uploads). Everything else uses compileSdk 34.

## Features
* Split view (draggable divider, ratio saved) or Flip view (450 ms slide, Back button / swipe / system back), switchable live in Settings.
* Editor: regex syntax highlighting, auto-indent, formatter, hint bar, symbol panel, line numbers, line:col status, undo/redo (100 levels), pinch zoom (12–28 sp, indicator, disabled while keyboard is open), word wrap, find/replace (plain or regex), go to line.
* Empty editor restores the default Hello World (on Clear, on Run/Save, on focus loss, and 1.5 s after it becomes empty).
* 5 themes, 4 fonts, font-size slider. Auto-save (3/5/10 s, status-bar indicator, draft for untitled files). 15 sample programs in `assets/samples/`. 4-screen onboarding. Share code as text or PNG, optionally with output.
* Files: folders, Save/Save As (overwrite warning), import `.c/.h` via SAF, export via SAF (Downloads suggested), rename/duplicate/delete (confirmations), search by name or content.
* Sandbox: compile 15 s, run 30 s timeouts, CPU `ulimit`, RSS polled every 500 ms (256 MB, best effort), output capped. `CodeSanitizer` blocks `system/exec*/popen/fork`, sockets, `/proc /sys /dev`, `##` token pasting. This regex check is best-effort; the real isolation is Android's app sandbox.
* Optional stdin: the box under the output is piped to the program.

## Differences from the requested layout
* `main_menu.xml` lives in `res/menu/` (Android requires this), not under `java/`.
* `build.gradle.kts` is in `app/`, not `src/main/`.
* Extra files: `ui/PaneLayout.kt`, `ui/OnboardingActivity.kt`, `files/FileBrowser.kt`, `utils/Prefs.kt`, `utils/Ui.kt`, `utils/ShareHelper.kt`, extra layouts/anims, `scripts/fetch_fonts.sh`.
* "Donate"/"Developer website" open the browser via `ACTION_VIEW` (no permission needed). Bug report uses `ACTION_SENDTO` to asifur2580@gmail.com.

## Notes
* Auto-save overwrites the open file in place (that is what auto-save means); turn it off in Settings if you prefer manual saves.
* Release builds are unsigned; add your own signing config before distributing.

MIT License — see `LICENSE`.
