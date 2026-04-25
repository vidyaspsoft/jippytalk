# =============================================================================
# JippyTalk ProGuard / R8 rules
# =============================================================================
# These rules are applied IN ADDITION to proguard-android-optimize.txt and
# the consumer rules bundled with each library. Add a new -keep block whenever
# a release build crashes with ClassNotFoundException or NoSuchMethodException
# on a runtime-discovered (reflective / JNI) class.
# =============================================================================

# ---- Keep stack traces useful for Crashlytics --------------------------------
# Without these, a release crash report shows obfuscated method names. Upload
# the generated mapping.txt to Crashlytics if you want symbolicated traces.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
-keepattributes Signature,*Annotation*,EnclosingMethod,InnerClasses

# ---- libsignal ---------------------------------------------------------------
# Heavy use of JNI + reflection. Strip nothing from the public API.
-keep class org.signal.** { *; }
-keep class org.whispersystems.** { *; }
-dontwarn org.signal.**
-dontwarn org.whispersystems.**

# ---- SQLCipher (zetetic) -----------------------------------------------------
# Native bindings load Java classes by name; obfuscation will break them.
-keep class net.zetetic.database.** { *; }
-keep class net.sqlcipher.** { *; }
-dontwarn net.zetetic.database.**
-dontwarn net.sqlcipher.**

# ---- Java-WebSocket ----------------------------------------------------------
-keep class org.java_websocket.** { *; }
-dontwarn org.java_websocket.**

# ---- Firebase / Google Play Services ----------------------------------------
# Crashlytics + Analytics + Messaging + Maps + Auth all rely on reflection
# and have their own consumer rules, but we explicitly silence stripped-class
# warnings to avoid R8 bailing out.
-dontwarn com.google.firebase.**
-dontwarn com.google.android.gms.**
-keep class com.google.firebase.messaging.** { *; }

# ---- Glide -------------------------------------------------------------------
-keep public class * extends com.bumptech.glide.module.AppGlideModule
-keep class com.bumptech.glide.GeneratedAppGlideModuleImpl
-keep class * implements com.bumptech.glide.module.GlideModule

# ---- Volley ------------------------------------------------------------------
-dontwarn com.android.volley.**

# ---- libphonenumber ----------------------------------------------------------
# Loads phone number metadata via reflection from generated resources.
-keep class com.google.i18n.phonenumbers.** { *; }
-dontwarn com.google.i18n.phonenumbers.**

# ---- uCrop -------------------------------------------------------------------
-keep class com.yalantis.ucrop.** { *; }
-dontwarn com.yalantis.ucrop.**

# ---- AndroidX WorkManager ----------------------------------------------------
# Workers are instantiated by class name from WorkRequest.
-keep class * extends androidx.work.Worker
-keep class * extends androidx.work.ListenableWorker { public <init>(...); }

# ---- App-specific keeps ------------------------------------------------------
# Model / DTO classes the app reflects on (org.json deserialization, data
# binding, intent extras, etc). Keep their fields & constructors so the
# decryption / parsing paths don't fail at runtime.
-keep class com.jippytalk.Messages.Model.** { *; }
-keep class com.jippytalk.Messages.Attachment.Model.** { *; }
-keep class com.jippytalk.Messages.Api.** { *; }
-keep class com.jippytalk.Chats.Model.** { *; }
-keep class com.jippytalk.Database.MessagesDatabase.** { *; }
-keep class com.jippytalk.Database.User.** { *; }
-keep class com.jippytalk.Database.SessionDatabase.** { *; }
-keep class com.jippytalk.Database.ContactsDatabase.** { *; }
-keep class com.jippytalk.Encryption.** { *; }

# Activities / Services / Receivers / Providers / Application (declared in
# AndroidManifest.xml) are kept by R8 automatically, but we explicitly keep
# Application + the FCM service to be safe.
-keep class com.jippytalk.MyApplication { *; }
-keep class com.jippytalk.FirebasePushNotifications.FirebaseMessagingService { *; }

# Keep enum value() / valueOf() — used by JSON parsing + databinding.
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# Keep Parcelable CREATOR fields (intent extras, bundles).
-keepclassmembers class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator CREATOR;
}

# Keep Serializable fields if anything implements it.
-keepclassmembers class * implements java.io.Serializable {
    static final long serialVersionUID;
    private static final java.io.ObjectStreamField[] serialPersistentFields;
    !static !transient <fields>;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
    java.lang.Object writeReplace();
    java.lang.Object readResolve();
}

# Data binding generated classes.
-keep class androidx.databinding.** { *; }
-keep class com.jippytalk.databinding.** { *; }
