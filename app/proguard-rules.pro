# kotlinx.serialization keeps generated serializers via its bundled rules.
# Strip verbose/debug logging from release builds.
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
}

# Glance instantiates widget action callbacks reflectively by class name.
-keep class * implements androidx.glance.appwidget.action.ActionCallback { <init>(); }
