# ProGuard rules for Filigram
-keepattributes *Annotation*
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}
-keep class com.filigram.cinema.** { *; }
