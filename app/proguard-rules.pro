# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# Firebase Crashlytics
 -keep class * extends java.lang.Exception  # Optional: Keep custom exceptions.

# Kotlinx Serialization
-keepattributes *Annotation*, InnerClasses, Signature, Exceptions
-keepclassmembers class * {
    @kotlinx.serialization.SerialName <fields>;
}
-keepnames @kotlinx.serialization.Serializable class * { *; }
-if class * {
    @kotlinx.serialization.Serializable class *;
}
-keepclassmembers class <1> {
    *** Companion;
    *** serializer(...);
}

# Enums (Used by Kotpref enum preferences and Serialization)
-keepclassmembers enum com.neilturner.aerialviews.models.enums.** {
    public static **[] values();
    public static ** valueOf(java.lang.String);
    **[] $VALUES;
    public static final <fields>;
}

# Retrofit (ImmichApi, NCMemoriesApi, CustomFeedApi, WeatherApi)
-keepattributes RuntimeVisible*Annotations, RuntimeInvisible*Annotations
-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}

# Sardine Android / XmlPullParser / Simple XML
-keep class org.xmlpull.v1.** { *; }
-dontwarn android.content.res.XmlResourceParser
-keep class javax.xml.namespace.QName { *; }
-keep class com.thegrizzlylabs.sardineandroid.** { *; }
-keep class org.simpleframework.xml.** { *; }

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