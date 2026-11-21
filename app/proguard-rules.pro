# osmdroid 部分模块通过反射加载瓦片源
-keep class org.osmdroid.** { *; }
-dontwarn org.osmdroid.**

# ExifInterface 以字符串名索引标签
-keep class androidx.exifinterface.** { *; }

-dontwarn org.jetbrains.annotations.**

# ExifWriter 虽是直接调用，但整类都是字节拼装逻辑，保留一份防止被后续重构裁掉
-keep class com.lucifer.exifstudio.data.ExifWriter { *; }
