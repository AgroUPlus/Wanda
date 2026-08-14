@echo off
setlocal EnableDelayedExpansion
cd /d "%~dp0"

REM ---------------------------------------------------------------------------
REM Builds the RELEASE APK: R8 minified, not debuggable, ART fully optimising.
REM This is the build to judge performance on -- a debug build runs Compose
REM roughly 2-5x slower and is not representative of anything.
REM
REM Build only. Nothing is installed, and adb is never invoked: copy the APK to
REM the phone and tap it.
REM
REM Release signing is read from local.properties. For local testing this points
REM at the debug keystore, whose credentials are fixed and public. That is fine
REM for your own device. Do NOT distribute an APK signed this way.
REM ---------------------------------------------------------------------------

echo.
echo === Wanda: release APK ===
echo.

REM --- 1. Java -----------------------------------------------------------------
if defined JAVA_HOME (
    echo Java:      %JAVA_HOME%
) else (
    where java >nul 2>&1
    if errorlevel 1 (
        echo(
        echo   ERROR: no Java found. Gradle needs a JDK 17+. For example:
        echo       setx JAVA_HOME "C:\Program Files\Android\Android Studio\jbr"
        echo   Then open a NEW terminal and run this again.
        echo(
        exit /b 1
    )
    echo Java:      found on PATH
)

REM --- 2. Signing config -------------------------------------------------------
set "KEYSTORE=%USERPROFILE%\.android\debug.keystore"

findstr /b /c:"releaseStoreFile" local.properties >nul 2>&1
if errorlevel 1 (
    if not exist "%KEYSTORE%" (
        echo(
        echo   ERROR: no signing key, and no debug keystore at:
        echo       %KEYSTORE%
        echo(
        echo   An unsigned APK cannot be installed. Generate a key with:
        echo       keytool -genkeypair -v -keystore wanda-release.jks -keyalg RSA -keysize 2048 -validity 10000 -alias wanda
        echo   ...then add releaseStoreFile / releaseStorePassword /
        echo   releaseKeyAlias / releaseKeyPassword to local.properties.
        echo(
        exit /b 1
    )

    REM Gradle's file() wants forward slashes in a .properties value.
    set "KEYSTORE_FWD=%KEYSTORE:\=/%"

    echo Signing:   first run -- adding debug-key signing to local.properties
    REM Leading blank line terminates any last line lacking a trailing newline.
    echo.>> local.properties
    echo # Local release signing. Debug key: fine for testing, never for release.>> local.properties
    echo releaseStoreFile=!KEYSTORE_FWD!>> local.properties
    echo releaseStorePassword=android>> local.properties
    echo releaseKeyAlias=androiddebugkey>> local.properties
    echo releaseKeyPassword=android>> local.properties
) else (
    echo Signing:   configured in local.properties
)

REM --- 3. Build ----------------------------------------------------------------
echo.
echo Building... ^(the first release build is slow -- R8 runs full minification^)
echo.

call gradlew.bat :app:assembleRelease %*
if errorlevel 1 (
    echo(
    echo   BUILD FAILED -- see the error above.
    echo(
    exit /b 1
)

set "APK=%CD%\app\build\outputs\apk\release\app-release.apk"
if not exist "%APK%" (
    echo(
    echo   Build reported success but no APK at:
    echo       %APK%
    echo(
    exit /b 1
)

echo.
echo === Done ===
echo.
echo   APK:  %APK%
echo.
echo   To install: plug the phone in over USB, set it to "File transfer",
echo   copy the APK to Downloads, then open it with the phone's Files app
echo   and tap Install. Android will ask once for permission to install from
echo   that app -- allow it.
echo.
echo   This installs as "Wanda" ^(com.wander.android^). The debug build is a
echo   separate app ^(com.wander.android.debug^) with the same name, so uninstall
echo   that one first if two identical icons would confuse you.
echo.

REM Open the folder so the APK is right there to drag across.
explorer /select,"%APK%"

endlocal
