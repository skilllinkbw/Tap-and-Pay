# AuthePay — Deployment Guide

## 1. Prerequisites

- **JDK:** Android Studio JBR (Java 25). On this machine: `C:\Program Files\Android\Android Studio\jbr`.
  Export before any Gradle command:
  ```bash
  export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"
  export PATH="$JAVA_HOME/bin:$PATH"
  ```
- **Android SDK:** `sdk.dir` in `local.properties`.
- **Gradle:** wrapper (9.5.0) — use `./gradlew`, not a system Gradle.
- **compileSdk / targetSdk 37, minSdk 26.**

## 2. Build Types & Security Contract

| Type | APK | `SANDBOX_PAYMENTS` | `TEST_OTP_ENABLED` | `ALLOW_CLEARTEXT` | `PRODUCTION_BUILD` | Endpoint |
|---|---|---|---|---|---|---|
| debug | `app-debug.apk` | true | true | true | false | sandbox-api.authepay.co.bw |
| staging | (add `assembleStaging`) | true | false | false | false | staging-api.authepay.co.bw |
| release | `app-release.apk` | **false** | **false** | **false** | **true** | api.authepay.co.bw |

These are enforced by `buildConfigField` and asserted by `SecurityConfigurationTest`.

## 3. Release Signing

Signing is sourced from `local.properties` / CI secrets — **never from the repo**.

`app/build.gradle` (`signingConfigs.release`):
```groovy
signingConfigs {
    release {
        def props = new Properties()
        def f = rootProject.file("local.properties")
        if (f.exists()) f.withInputStream { props.load(it) }
        if (props["RELEASE_STORE_FILE"] != null) {
            storeFile rootProject.file(props["RELEASE_STORE_FILE"])
            storePassword props["RELEASE_STORE_PASSWORD"]
            keyAlias props["RELEASE_KEY_ALIAS"]
            keyPassword props["RELEASE_KEY_PASSWORD"]
        }
    }
}
// release build type:
if (signingConfigs.findByName("release")?.storeFile != null) {
    signingConfig signingConfigs.release
}
```

If `RELEASE_STORE_FILE` is absent, `assembleRelease` is left unsigned and **fails
loudly** (intentional — do not suppress).

### Local / dev verification

`android/local.properties` (gitignored) currently wires a **dev build-verification**
keystore:
```
RELEASE_STORE_FILE=release-keystore.jks
RELEASE_STORE_PASSWORD=authepay-dev-2026
RELEASE_KEY_PASSWORD=authepay-dev-2026
RELEASE_KEY_ALIAS=authepay-release
```
`release-keystore.jks` is gitignored and **must not be used for a published build**.

### Production

1. Generate/obtain the Braincade Holdings production keystore (or use the acquirer's).
2. Set the four `RELEASE_*` values in `local.properties` (or inject via CI secret env
   mapped to the same keys). `RELEASE_STORE_FILE` is resolved relative to the project
   root (`android/`), so use a path like `../keys/prod.jks` or an absolute path.
3. `./gradlew assembleRelease` → `app/build/outputs/apk/release/app-release.apk`.

### CI example (GitHub Actions)

```yaml
- name: Build release
  env:
    RELEASE_STORE_FILE: ${{ secrets.RELEASE_STORE_FILE }}   # path to uploaded keystore
    RELEASE_STORE_PASSWORD: ${{ secrets.RELEASE_STORE_PASSWORD }}
    RELEASE_KEY_PASSWORD: ${{ secrets.RELEASE_KEY_PASSWORD }}
    RELEASE_KEY_ALIAS: ${{ secrets.RELEASE_KEY_ALIAS }}
  run: ./gradlew assembleRelease
```

## 4. Build Commands

```bash
./gradlew clean test lint assembleDebug assembleRelease   # full gate (§58)
./gradlew assembleDebug                                   # debug only
./gradlew assembleRelease                                 # release (signed if keys present)
```

## 5. Verify an APK

```bash
# signing (v2/v3 aware)
"$ANDROID_SDK/build-tools/36.0.0/apksigner.bat" verify --print-certs app/build/outputs/apk/release/app-release.apk
# security contract
./gradlew testDebugUnitTest --tests "com.getauthepay.app.SecurityConfigurationTest"
```

## 6. What Is NOT Shipped By This Build

- No real acquiring processor (release `ServiceLocator` throws until partner SDK added).
- No certified EMV/MPoC kernel (production `NfcReaderProvider` returns `CERTIFIED_KERNEL_REQUIRED`).
- See `STATUS_REPORT.md`, `CERTIFICATION_READINESS.md`.
