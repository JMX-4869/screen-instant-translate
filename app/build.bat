@echo off
chcp 65001 >nul
rem 独立版（v2）构建：aapt2 + javac + d8 + zipalign + apksigner，不用 Gradle
rem 用法: build.bat <android-sdk> [jdk] [ort-aar]
rem 需要 onnxruntime-android AAR，例如：
rem   curl -L -o ort.aar https://maven.aliyun.com/repository/public/com/microsoft/onnxruntime/onnxruntime-android/1.18.0/onnxruntime-android-1.18.0.aar
setlocal
set SDK=%~1
if "%SDK%"=="" echo 用法: build.bat ^<android-sdk^> [jdk] [ort-aar] & exit /b 1
set JDK=%~2
if "%JDK%"=="" set JDK=%JAVA_HOME%
if "%JDK%"=="" echo 需要 JDK 17，设置 JAVA_HOME 或传参 & exit /b 1
set AAR=%~3
if "%AAR%"=="" set AAR=..\ort.aar
if not exist "%AAR%" echo 找不到 onnxruntime AAR: %AAR% & exit /b 1

set BT=%SDK%\build-tools\34.0.0
set JAR=%SDK%\platforms\android-34\android.jar
cd /d "%~dp0"
if exist build rmdir /s /q build
mkdir build\classes build\dex build\apk

rem 解 AAR
if not exist build\ort mkdir build\ort
pushd build\ort
unzip -o -q "%AAR%" classes.jar AndroidManifest.xml
mkdir jni 2>nul
popd
unzip -o -q "%AAR%" "jni/arm64-v8a/*" -d build\ort
mkdir build\ortclasses
pushd build\ortclasses
unzip -o -q ..\ort\classes.jar
popd

"%JDK%\bin\javac" -encoding UTF-8 --release 8 -cp "%JAR%;build\ort\classes.jar" -d build\classes src\com\si\ocr\*.java src\com\si\screen\*.java || exit /b 1
"%BT%\d8.bat" --lib "%JAR%" --min-api 26 --release --output build\dex build\classes build\ortclasses || exit /b 1
"%BT%\aapt2.exe" compile --dir res -o build\res.zip || exit /b 1
"%BT%\aapt2.exe" link -o build\apk\base.apk -I "%JAR%" --manifest AndroidManifest.xml -R build\res.zip --min-sdk-version 26 --target-sdk-version 34 || exit /b 1
copy /y build\apk\base.apk build\unsigned.apk >nul

python -c "import zipfile,os;z=zipfile.ZipFile(r'build\unsigned.apk','a');z.write(r'build\dex\classes.dex','classes.dex');[z.write(os.path.join(r'build\ort\jni\arm64-v8a',n),'lib/arm64-v8a/'+n) for n in ('libonnxruntime.so','libonnxruntime4j_jni.so')];z.close()" || exit /b 1
"%BT%\zipalign.exe" -f -p 4 build\unsigned.apk build\aligned.apk || exit /b 1

if not exist build\si.keystore (
  "%JDK%\bin\keytool" -genkeypair -keystore build\si.keystore -alias si -keyalg RSA -keysize 2048 -validity 10000 -storepass si123456 -keypass si123456 -dname "CN=SI,O=SI,C=CN"
)
"%BT%\apksigner.bat" sign --ks build\si.keystore --ks-pass pass:si123456 --key-pass pass:si123456 --out build\screen-translate-v2.apk build\aligned.apk || exit /b 1
echo.
echo OK: app\build\screen-translate-v2.apk
