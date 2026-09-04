# LibreOfficeKit JNI classes must be kept
-keep class org.libreoffice.kit.** {
    *;
}
-keepclassmembers class org.libreoffice.kit.** {
    *;
}
-keepclassmembernames class org.libreoffice.kit.** {
    *;
}

# Keep the Document and Office handles
-keepclassmembers class org.libreoffice.kit.Office {
    private java.nio.ByteBuffer handle;
}
-keepclassmembers class org.libreoffice.kit.Document {
    private java.nio.ByteBuffer handle;
}

# General JNI rules
-keepclasseswithmembernames class * {
    native <methods>;
}

# TIFF Renderer JNI rules
-keep class io.github.lucf15.tiffrenderer.** {
    *;
}
-keepclassmembers class io.github.lucf15.tiffrenderer.** {
    *;
}
-keepclassmembernames class io.github.lucf15.tiffrenderer.** {
    *;
}
