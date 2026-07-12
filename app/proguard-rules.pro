# Add project specific ProGuard rules here.
-keep class com.dezzmusic.** { *; }
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
