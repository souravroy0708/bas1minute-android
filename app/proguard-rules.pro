# Release builds are obfuscated (R8). Libraries ship their own rules; these are belt and braces.
-keep class com.bas1minute.sync.SyncWorker { <init>(...); }
-keep class com.bas1minute.sync.PrivacyActivity
-keep class com.bas1minute.sync.MainActivity
# Strip debug logging from release
-assumenosideeffects class android.util.Log { public static int d(...); public static int v(...); }
