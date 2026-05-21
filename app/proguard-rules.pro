# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# Firebase Crashlytics
 -keep class * extends java.lang.Exception  # Optional: Keep custom exceptions.

# Kotlin Serialization
-keepclassmembers class ** {
    *** Companion;
}
-keepnames class ** {
    @kotlinx.serialization.Serializable *;
}
-keepclassmembers class ** {
    *** serializer(...);
}

# Keep our Data Models (Retrofit/Room/Serialization)
-keep class com.neilturner.aerialviews.models.** { *; }
-keep class com.neilturner.aerialviews.data.** { *; }
-keep class com.neilturner.aerialviews.providers.** { *; }

# Sardine Android / XmlPullParser
-keep class org.xmlpull.v1.** { *; }
-dontwarn android.content.res.XmlResourceParser

-dontwarn org.apache.log4j.Level
-dontwarn org.apache.log4j.Logger
-dontwarn org.apache.log4j.Priority
-dontwarn org.apache.logging.log4j.Level
-dontwarn org.apache.logging.log4j.LogManager
-dontwarn org.apache.logging.log4j.Logger
-dontwarn org.apache.logging.log4j.message.MessageFactory
-dontwarn org.apache.logging.log4j.spi.ExtendedLogger
-dontwarn org.apache.logging.log4j.spi.ExtendedLoggerWrapper
-dontwarn org.slf4j.impl.StaticLoggerBinder**

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

-dontwarn timber.log.Timber**

-dontwarn java.lang.management.ManagementFactory
-dontwarn java.lang.management.RuntimeMXBean
-dontwarn reactor.blockhound.integration.BlockHoundIntegration