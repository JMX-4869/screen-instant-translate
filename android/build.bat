@echo off
chcp 65001 >nul
rem 不用 Gradle：aapt2 + javac + d8 + zipalign + apksigner 手工打包
rem 用法: build.bat D:\path\to\android-sdk [jdk路径]
setlocal
set SDK=%~1
if "%SDK%"=="" (
  echo 用法: build.bat ^<android-sdk-path^> [jdk-path]
  exit /b 1
)
set JDK=%~2
if "%JDK%"=="" set JDK=%JAVA_HOME%
if "%JDK%"=="" (
  echo 需要 JDK 17，请设置 JAVA_HOME 或传第二个参数
  exit /b 1
)

set BT=%SDK%\build-tools\34.0.0
set JAR=%SDK%\platforms\android-34\android.jar
if not exist "%BT%\aapt2.exe" echo 缺少 build-tools 34.0.0 & exit /b 1
if not exist "%JAR%" echo 缺少 platforms;android-34 & exit /b 1

cd /d "%~dp0"
if exist build rmdir /s /q build
mkdir build\classes build\dex

"%JDK%\bin\javac" -encoding UTF-8 --release 8 -cp "%JAR%" -d build\classes src\com\si\screen\*.java || exit /b 1
"%BT%\d8.bat" --lib "%JAR%" --min-api 26 --release --output build\dex build\classes\com\si\screen\*.class || exit /b 1
"%BT%\aapt2.exe" link -o build\base.apk -I "%JAR%" --manifest AndroidManifest.xml --min-sdk-version 26 --target-sdk-version 34 || exit /b 1
copy /y build\base.apk build\unsigned.apk >nul

rem 把 classes.dex 塞进 apk（无 zip 命令时用 python，或改成 jar uf）
python -c "import zipfile;z=zipfile.ZipFile(r'build\unsigned.apk','a');z.write(r'build\dex\classes.dex','classes.dex');z.close()" || exit /b 1
"%BT%\zipalign.exe" -f 4 build\unsigned.apk build\aligned.apk || exit /b 1

if not exist build\si.keystore (
  "%JDK%\bin\keytool" -genkeypair -keystore build\si.keystore -alias si -keyalg RSA -keysize 2048 -validity 10000 -storepass si123456 -keypass si123456 -dname "CN=SI,O=SI,C=CN"
)
"%BT%\apksigner.bat" sign --ks build\si.keystore --ks-pass pass:si123456 --key-pass pass:si123456 --out build\screen-translate.apk build\aligned.apk || exit /b 1
"%BT%\apksigner.bat" verify build\screen-translate.apk || exit /b 1
echo.
echo OK: android\build\screen-translate.apk
