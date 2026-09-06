# Local Android development

## Toolchain locations

The system-default `java` currently resolves to Java 17. Use this Java 21
installation for Gradle builds:

- Java 21 home: `C:\Users\Eric\.mbrc-tools\jdk-21\jdk-21.0.12.1+1`
- Java executable: `C:\Users\Eric\.mbrc-tools\jdk-21\jdk-21.0.12.1+1\bin\java.exe`
- Android SDK: `C:\Users\Eric\AppData\Local\Android\Sdk`
- ADB executable: `C:\Users\Eric\AppData\Local\Android\Sdk\platform-tools\adb.exe`

Initialize PowerShell builds with:

```powershell
$env:JAVA_HOME = 'C:\Users\Eric\.mbrc-tools\jdk-21\jdk-21.0.12.1+1'
$env:ANDROID_HOME = 'C:\Users\Eric\AppData\Local\Android\Sdk'
$env:PATH = "$env:JAVA_HOME\bin;$env:ANDROID_HOME\platform-tools;$env:PATH"
```

## Development versioning

- Development builds that update the installed app in place may keep the
  production application ID `com.ericcai.streambee`. Put the prerelease suffix
  directly in `appVersionName`, producing versions such as `2.1.1-dev1`,
  `2.1.1-dev2`, and so on.
- For the same stable base version, start at `dev1` and increment `N` for each
  new deployable development iteration. Rebuilding unchanged sources keeps the
  same `devN` value.
- Keep the paired Android app and MusicBee plugin on the same `X.Y.Z-devN`
  version.
- A formal release has no `-devN` suffix. Start again at `dev1` when development
  moves to the next stable base version.
- Use the `.dev` application ID only when a side-by-side installation is
  explicitly wanted. Do not install both variants by default.
- Whenever the app or paired plugin version is changed for a release, update the
  repository `CHANGELOG.md` and the app's in-app What's New resource at
  `app/src/main/res/raw/changelog.xml` in the same change. The in-app resource
  must contain a matching version entry and the release notes, not just the
  external Markdown changelog. When the plugin version changes, update its
  `CHANGELOG.md` as well.

## GitHub release publishing

- When asked to publish the Android app or the paired MusicBee plugin as a
  GitHub Release, build and package the release artifacts in the local
  workspace, then upload those exact local files with `gh release create` or
  `gh release upload`. Do not trigger or rely on GitHub Actions, GitHub-hosted
  runners, or GitHub's online build/packaging workflow to produce the release
  binaries.
- For the Android app, build the signed `githubRelease` artifact locally (and
  any explicitly requested companion artifacts), verify the outputs and
  signing, and upload the local artifacts. If local release signing
  credentials are unavailable, report the blocker; do not fall back to a
  GitHub-hosted build.
- Firebase Crashlytics mapping uploads time out in this environment. Skip the
  upload task explicitly instead of waiting for or dismissing the timeout. For
  the current variants, add
  `-x :app:uploadCrashlyticsMappingFileGithubRelease` when building the GitHub
  APK, and also add
  `-x :app:uploadCrashlyticsMappingFilePlayRelease` if Play variants are built.
  This skips only the mapping upload; do not claim that the mapping was
  uploaded. If task names change, inspect `:app:tasks --all` and exclude the
  applicable `uploadCrashlyticsMappingFile*` tasks.

## USB deployment after app changes

After changing the Android app, perform the relevant checks and then attempt an
in-place update on an authorized USB-debugging device. Check the connection
first:

```powershell
& 'C:\Users\Eric\AppData\Local\Android\Sdk\platform-tools\adb.exe' devices
```

If a device is listed with status `device`, build the signed `githubRelease`
variant and install it over the existing production-package app:

```powershell
.\gradlew.bat :app:assembleGithubRelease `
  -x :app:uploadCrashlyticsMappingFileGithubRelease
& 'C:\Users\Eric\AppData\Local\Android\Sdk\platform-tools\adb.exe' install -r `
  '.\app\build\outputs\apk\github\release\app-github-release.apk'
```

This requires a signing configuration compatible with the already installed
app. If the signature is unavailable or incompatible, do not uninstall the
existing app or erase its data; report the blocker. Use `githubDebug` only when
the user explicitly requests a side-by-side `.dev` app. Do not silently
substitute an emulator. If no authorized USB device is available, or it is
`unauthorized`/`offline`, report that deployment could not be completed.
