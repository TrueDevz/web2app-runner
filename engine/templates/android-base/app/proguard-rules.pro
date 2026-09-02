# Proguard rules for WebToApp
-keepattributes JavascriptInterface
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}
-keep class com.webtoapp.base.WebAppInterface { *; }
-dontwarn com.google.android.gms.ads.**
