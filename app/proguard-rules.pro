# Release builds are obfuscated (R8). Libraries ship their own rules; these are belt and braces.
-keep class com.bas1minute.sync.SyncWorker { <init>(...); }
-keep class com.bas1minute.sync.PrivacyActivity
-keep class com.bas1minute.sync.MainActivity
# Strip debug logging from release
-assumenosideeffects class android.util.Log { public static int d(...); public static int v(...); }
-keep class com.bas1minute.sync.phone.BatteryWorker { <init>(...); }
# GPS walk / run: the tracking service is started by name from notifications
-keep class com.bas1minute.sync.track.TrackingService
# osmdroid references optional classes it doesn't need on Android
-dontwarn org.osmdroid.**

# Release: drop debug/verbose/info logging (smaller, and nothing personal in logcat). Warnings and errors stay.
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
}
