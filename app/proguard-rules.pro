# Room's generated code is kept by AGP's bundled rules.

# SQLCipher makes JNI calls into these classes; R8 must not rename/strip them.
-keep class net.zetetic.database.** { *; }
-dontwarn net.zetetic.database.**

# osmdroid loads tile sources and config reflectively in places.
-keep class org.osmdroid.** { *; }
-dontwarn org.osmdroid.**
