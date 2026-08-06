# Tink reflectively loads its registered primitive/key-manager classes.
-keep class com.google.crypto.tink.** { *; }
-keepclassmembers class * extends com.google.crypto.tink.proto.MessageLite { *; }

# SQLCipher native bridge.
-keep class net.sqlcipher.** { *; }
