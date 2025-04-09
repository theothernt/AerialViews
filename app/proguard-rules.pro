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
-keep class com.neilturner.aerialviews.utils.* { <fields>; }
-keep class com.neilturner.aerialviews.ui.settings.* { <fields>; }
-keep class com.neilturner.aerialviews.ui.sources.* { <fields>; }

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

# Ktor
-dontwarn io.netty.internal.tcnative.AsyncSSLPrivateKeyMethod
-dontwarn io.netty.internal.tcnative.AsyncTask
-dontwarn io.netty.internal.tcnative.Buffer
-dontwarn io.netty.internal.tcnative.CertificateCallback
-dontwarn io.netty.internal.tcnative.CertificateCompressionAlgo
-dontwarn io.netty.internal.tcnative.CertificateVerifier
-dontwarn io.netty.internal.tcnative.Library
-dontwarn io.netty.internal.tcnative.SSL
-dontwarn io.netty.internal.tcnative.SSLContext
-dontwarn io.netty.internal.tcnative.SSLPrivateKeyMethod
-dontwarn io.netty.internal.tcnative.SSLSessionCache
-dontwarn io.netty.internal.tcnative.SessionTicketKey
-dontwarn io.netty.internal.tcnative.SniHostNameMatcher
-dontwarn java.lang.management.ManagementFactory
-dontwarn java.lang.management.RuntimeMXBean
-dontwarn org.apache.log4j.Level
-dontwarn org.apache.log4j.Logger
-dontwarn org.apache.log4j.Priority
-dontwarn org.apache.logging.log4j.Level
-dontwarn org.apache.logging.log4j.LogManager
-dontwarn org.apache.logging.log4j.Logger
-dontwarn org.apache.logging.log4j.message.MessageFactory
-dontwarn org.apache.logging.log4j.spi.ExtendedLogger
-dontwarn org.apache.logging.log4j.spi.ExtendedLoggerWrapper
-dontwarn org.eclipse.jetty.npn.NextProtoNego$ClientProvider
-dontwarn org.eclipse.jetty.npn.NextProtoNego$Provider
-dontwarn org.eclipse.jetty.npn.NextProtoNego$ServerProvider
-dontwarn org.eclipse.jetty.npn.NextProtoNego
-dontwarn reactor.blockhound.integration.BlockHoundIntegration