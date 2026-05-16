# OpenLumen ProGuard / R8 rules

# Keep ColorEngine reflection target for ColorDisplayManager path
-keepclassmembers class android.hardware.display.ColorDisplayManager {
    public *;
}

# Compose / Material 3 — handled by AGP defaults

# Hilt
-keep class dagger.hilt.** { *; }
-keep class * extends dagger.hilt.android.internal.GeneratedComponent { *; }

# kotlinx.serialization — keep serializer classes
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.SerializationKt
-keep,includedescriptorclasses class com.openlumen.**$$serializer { *; }
-keepclassmembers class com.openlumen.** {
    *** Companion;
}
-keepclasseswithmembers class com.openlumen.** {
    kotlinx.serialization.KSerializer serializer(...);
}
