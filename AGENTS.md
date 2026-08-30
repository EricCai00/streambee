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
.\gradlew.bat :app:assembleGithubRelease
& 'C:\Users\Eric\AppData\Local\Android\Sdk\platform-tools\adb.exe' install -r `
  '.\app\build\outputs\apk\github\release\app-github-release.apk'
```

This requires a signing configuration compatible with the already installed
app. If the signature is unavailable or incompatible, do not uninstall the
existing app or erase its data; report the blocker. Use `githubDebug` only when
the user explicitly requests a side-by-side `.dev` app. Do not silently
substitute an emulator. If no authorized USB device is available, or it is
`unauthorized`/`offline`, report that deployment could not be completed.
