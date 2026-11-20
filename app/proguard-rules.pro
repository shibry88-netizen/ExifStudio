# osmdroid 部分模块通过反射加载瓦片源
-keep class org.osmdroid.** { *; }
-dontwarn org.osmdroid.**

# ExifInterface 以字符串名索引标签
-keep class androidx.exifinterface.** { *; }

-dontwarn org.jetbrains.annotations.**
