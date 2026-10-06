# LSPosed / libxposed 保留规则
-dontwarn io.github.libxposed.annotation.**
-adaptresourcefilecontents META-INF/xposed/java_init.list

# 保留入口类及其无参构造
-keep,allowoptimization,allowobfuscation public class * extends io.github.libxposed.api.XposedModule {
    public <init>();
}

# 模块 UI 组件
-keep class com.douyin.clearmode.MainActivity { *; }
-keep class com.douyin.clearmode.App { *; }
-keep class com.douyin.clearmode.ClearModePrefs { *; }

# 日志 TAG 常量保留
-keepclassmembers class com.douyin.clearmode.** {
    static final java.lang.String TAG;
}
