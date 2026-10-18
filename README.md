# CCompiler Pro

An offline C editor and compiler for Android. Write C, tap **Run**, see the output – no internet, no account, no ads.

* **Package:** `com.ccompiler.pro` &nbsp;|&nbsp; **Developer:** Md Asifur Rahman (Bangladesh)
* **License:** MIT (see `LICENSE`) – third-party components keep their own licenses (see below)

## Privacy and safety
* **No network access:** the app does not request the INTERNET permission.
* **No permissions at all.** Files stay in the app's private storage; you move files in and out with Android's file picker.
* **No ads, no analytics, no trackers, no third-party SDKs.** Details: [`PRIVACY.md`](PRIVACY.md).
* Every release is built in public by GitHub Actions and ships with a `TRUST_REPORT.md` (permissions, tracker scan, bundled native files, libraries), the APK checksum and the signing-certificate fingerprint. The build **fails automatically** if a permission, a tracker SDK or a debuggable flag appears.
* Some antivirus engines may show a generic warning because the app bundles a compiler executable (`libtccexe.so`) and runs it as a child process. That is how it works; the build scripts are in this repository.

## Features
* Editor: syntax highlighting, auto-indent, code formatting, hints, symbol bar, line numbers, undo/redo (100 levels), pinch-zoom, find/replace (plain or regex), go to line.
* **Run:** the code is type-checked first (errors are underlined; tap an error to jump to its line, with a plain-English hint), then executed in a separate process.
* **Interactive input:** programs that use `scanf` can be answered while they run; an input row (with **Send** and **EOF**) appears only while the program waits.
* Split view (draggable divider) or Flip view (swipe between editor and output), 5 themes, 4 fonts, auto-save, 15 sample programs, file manager with folders, import/export, share as text or image, intro tour.
* Run limits: 30 s of run time (time spent waiting for input does not count), 256 MB memory (best effort), output size cap. Basic source checks block `system()`, `exec*`, `fork`, sockets and `/proc`, `/sys`, `/dev` – a convenience, not a security boundary; Android's app sandbox is the real isolation.

## Compatibility
| | |
|---|---|
| Android | **8.0 (API 26) and newer** |
| Processor | **64-bit ARM (arm64-v8a) only.** 32-bit-only phones cannot install it. |
| Tested by the developer on | Android 15 |
| Android 16 | built to run (targets API 34); not yet tested on a real device |

## The C compiler
The compiler is [TinyCC](https://repo.or.cz/tinycc.git) (LGPL), cross-compiled for Android by `scripts/build_tcc.sh` during every build. Programs are compiled to memory and run by `tcc -run`. Consequences:
* Fast to start and small, but **no optimization**, and some newer C features (for example `_Complex`) are unsupported.
* The standard headers are provided by the app (`stdio.h`, `stdlib.h`, `string.h`, `math.h`, `ctype.h`, `limits.h`, `stdint.h`, `time.h`, `errno.h`, `assert.h`) plus TinyCC's `stdbool.h`, `stdarg.h`, `stddef.h`, `float.h`.
* Known gap: `#include "myfile.h"` for files outside the single open program is not supported yet.

## Build it yourself (GitHub Actions)
1. Fork or clone this repository.
2. Create a signing key (once) and store it as the repository secrets `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`. Never commit the key.
3. Push to `main`. The workflow *Build CCompiler Pro APK* builds TinyCC, builds and signs the release APK, writes the trust report and uploads the artifact `ccompiler-pro-release`.

## Releases and updates
* **Users:** *Settings → Check for updates* opens the latest release page on GitHub in the browser; download the APK there and install it over the old version. The app itself never touches the network, so it cannot tell you whether a newer version exists – compare the installed version (shown in that row and in *About*) with the release page.
* **Publisher:** raise `versionCode` and `versionName` in `app/build.gradle.kts`, commit, then create a tag that matches the version: `git tag v1.1.2 && git push origin v1.1.2`. The workflow builds, signs, runs the trust checks and publishes a GitHub Release with the APK, checksum, trust report and certificate fingerprint. Android only accepts an update that is signed with the **same key** and has a higher `versionCode`.

## Third-party components
TinyCC (LGPL-2.1, built from source by the build script – the exact upstream commit is recorded in each release's trust report) · AndroidX and Material Components (Apache-2.0) · Kotlin coroutines (Apache-2.0) · fonts Roboto Mono (Apache-2.0), Fira Code, JetBrains Mono and Courier Prime (SIL Open Font License), downloaded at build time.

## Contact
asifur2580@gmail.com
