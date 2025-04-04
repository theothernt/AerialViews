# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# Firebase Crashlytics
-keepattributes SourceFile,LineNumberTable        # Keep file names and line numbers.
-keep public class * extends java.lang.Exception  # Optional: Keep custom exceptions.

# Mostly data class and json types
-keep class com.neilturner.aerialviews.models.videos.* { <fields>; }
-keep class com.neilturner.aerialviews.models.immich.* { <fields>; }
-keep class com.neilturner.aerialviews.utils.* { <fields>; }
-keep class com.neilturner.aerialviews.ui.settings.* { <fields>; }
-keep class com.neilturner.aerialviews.ui.sources.* { <fields>; }

# Retain generic signatures of TypeToken and its subclasses with R8 version 3.0 and higher.
-keep,allowobfuscation,allowshrinking class com.google.gson.reflect.TypeToken
-keep,allowobfuscation,allowshrinking class * extends com.google.gson.reflect.TypeToken

# Sardine Android / XmlPullParser
# -dontwarn org.xmlpull.v1.**
-keep class org.xmlpull.v1.** { *; }
-dontwarn android.content.res.XmlResourceParser

# Added to suppress build warnings
-dontwarn javax.el.BeanELResolver**
-dontwarn javax.el.ELContext**
-dontwarn javax.el.ELResolver**
-dontwarn javax.el.ExpressionFactory**
-dontwarn javax.el.FunctionMapper**
-dontwarn javax.el.ValueExpression**
-dontwarn javax.el.VariableMapper**
-dontwarn org.ietf.jgss.GSSContext**
-dontwarn org.ietf.jgss.GSSCredential**
-dontwarn org.ietf.jgss.GSSException**
-dontwarn org.ietf.jgss.GSSManager**
-dontwarn org.ietf.jgss.GSSName**
-dontwarn org.ietf.jgss.Oid**
-dontwarn org.slf4j.impl.StaticLoggerBinder**