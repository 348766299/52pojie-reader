# release 构建关闭了代码混淆，这里仅保留必要规则（防止将来开启后 JS 桥失效）
-keepattributes JavascriptInterface
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}
-keep class cn.read52.app.JsBridge { *; }
