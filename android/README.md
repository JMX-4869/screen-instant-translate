# 屏幕翻译 · 安卓端

配合 `server/` 使用：截屏 → 本机 OCR/翻译 → 全屏译文层原位覆盖。

## 编译

需要 Android SDK（build-tools 34.0.0、platforms;android-34）和 JDK 17，**不需要 Gradle**：

```bat
build.bat D:\android-sdk
```

脚本做的是：`aapt2 link` → `javac` → `d8` → `zipalign` → `apksigner`。

## 用法

1. 电脑跑起 `server/run.py`，记住手机地址
2. 装 APK，填地址，点「开始」
3. 授权悬浮窗 + 录屏
4. 任意 App 里点右侧蓝色「译」球，1 秒后译文盖上来；再点关闭

## 权限

`INTERNET`、`SYSTEM_ALERT_WINDOW`（悬浮球）、`FOREGROUND_SERVICE(_MEDIA_PROJECTION)`（抓屏前台服务）。

## 已知限制

- 需要手机与电脑同一局域网
- Android 14 每次重开 App 需重新授权录屏
- 译文层能否盖住状态栏取决于厂商实现，个别机器有几像素偏移
