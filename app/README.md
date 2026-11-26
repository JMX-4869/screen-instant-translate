# 独立版（v2）· 不依赖电脑

识别模型和 OCR 引擎都在手机里跑，**只有翻译文字走网络**。电脑不用开，出门只连 4G 也能用。

## 装

从 [Releases](../../releases) 下 `screen-translate-v2.0.apk`（约 17MB，仅 arm64）。

## 首次使用

1. 打开 App，「识别模型」里点下载：
   - **文字检测**（4.5MB，必装，所有语种共用）
   - 日文 9.3MB / 简体中文 10.4MB / 英文 7.3MB / 繁体中文 10.6MB——按需下，默认建议日+中
2. 「翻译接口」填硅基流动 API Key，模型 `Qwen/Qwen3.5-9B`，保存
3. 点「开始悬浮翻译」→ 授权悬浮窗 + 录屏
4. 任意 App 里点右侧「译」球，1 秒左右出译文；再点关闭

模型存在 App 私有目录，随时删了重下。

## 编译

```bat
build.bat D:\android-sdk
```

依赖：
- Android SDK build-tools 34 / platforms;android-34
- JDK 17
- `onnxruntime-android-1.18.0.aar`（从 maven 取，见 build.bat 注释）

不用 Gradle：aapt2 → javac → d8 → 塞 .so → zipalign → apksigner。

## 结构

```
src/com/si/ocr/     纯 Java OCR 核心（检测 DB 后处理 + CTC 解码），Kotlin/Android 无关
src/com/si/screen/  App：UI、模型下载、翻译请求、悬浮球与译文层
```

OCR 核心与桌面验证程序共用同一份代码，PC 上测通再打包。

## 已知限制

- 仅 arm64-v8a（换 arm32 需要换 .so）
- Android 14 每次重开 App 需重新授权录屏
- 首次识别要加载模型，约 1s；之后每页 0.4~1s
