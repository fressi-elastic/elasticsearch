/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the "Elastic License
 * 2.0", the "GNU Affero General Public License v3.0 only", and the "Server Side
 * Public License v 1"; you may not use this file except in compliance with, at
 * your election, the "Elastic License 2.0", the "GNU Affero General Public
 * License v3.0 only", or the "Server Side Public License, v 1".
 */

package org.elasticsearch.entitlement.bridge;

import java.io.InputStream;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.net.ContentHandlerFactory;
import java.net.DatagramSocketImplFactory;
import java.net.FileNameMap;
import java.net.SocketImplFactory;
import java.net.URL;
import java.net.URLStreamHandlerFactory;
import java.util.List;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;

@SuppressWarnings("unused") // Called from instrumentation code inserted by the Entitlements agent
public interface EntitlementChecker {

    ////////////////////
    //
    // Exit the JVM process
    //

    void check$java_lang_Runtime$exit(Class<?> callerClass, Runtime runtime, int status);

    void check$java_lang_Runtime$halt(Class<?> callerClass, Runtime runtime, int status);

    void check$java_lang_System$$exit(Class<?> callerClass, int status);

    ////////////////////
    //
    // ClassLoader ctor
    //

    void check$java_lang_ClassLoader$(Class<?> callerClass);

    void check$java_lang_ClassLoader$(Class<?> callerClass, ClassLoader parent);

    void check$java_lang_ClassLoader$(Class<?> callerClass, String name, ClassLoader parent);

    ////////////////////
    //
    // SecureClassLoader ctor
    //

    void check$java_security_SecureClassLoader$(Class<?> callerClass);

    void check$java_security_SecureClassLoader$(Class<?> callerClass, ClassLoader parent);

    void check$java_security_SecureClassLoader$(Class<?> callerClass, String name, ClassLoader parent);

    ////////////////////
    //
    // URLClassLoader constructors
    //

    void check$java_net_URLClassLoader$(Class<?> callerClass, URL[] urls);

    void check$java_net_URLClassLoader$(Class<?> callerClass, URL[] urls, ClassLoader parent);

    void check$java_net_URLClassLoader$(Class<?> callerClass, URL[] urls, ClassLoader parent, URLStreamHandlerFactory factory);

    void check$java_net_URLClassLoader$(Class<?> callerClass, String name, URL[] urls, ClassLoader parent);

    void check$java_net_URLClassLoader$(Class<?> callerClass, String name, URL[] urls, ClassLoader parent, URLStreamHandlerFactory factory);

    ////////////////////
    //
    // "setFactory" methods
    //

    void check$javax_net_ssl_HttpsURLConnection$setSSLSocketFactory(Class<?> callerClass, HttpsURLConnection conn, SSLSocketFactory sf);

    void check$javax_net_ssl_HttpsURLConnection$$setDefaultSSLSocketFactory(Class<?> callerClass, SSLSocketFactory sf);

    void check$javax_net_ssl_HttpsURLConnection$$setDefaultHostnameVerifier(Class<?> callerClass, HostnameVerifier hv);

    void check$javax_net_ssl_SSLContext$$setDefault(Class<?> callerClass, SSLContext context);

    ////////////////////
    //
    // Process creation
    //

    void check$java_lang_ProcessBuilder$start(Class<?> callerClass, ProcessBuilder that);

    void check$java_lang_ProcessBuilder$$startPipeline(Class<?> callerClass, List<ProcessBuilder> builders);

    ////////////////////
    //
    // JVM-wide state changes
    //

    void check$java_lang_System$$setIn(Class<?> callerClass, InputStream in);

    void check$java_lang_System$$setOut(Class<?> callerClass, PrintStream out);

    void check$java_lang_System$$setErr(Class<?> callerClass, PrintStream err);

    void check$java_lang_Runtime$addShutdownHook(Class<?> callerClass, Runtime runtime, Thread hook);

    void check$java_lang_Runtime$removeShutdownHook(Class<?> callerClass, Runtime runtime, Thread hook);

    void check$jdk_tools_jlink_internal_Jlink$(Class<?> callerClass);

    void check$jdk_tools_jlink_internal_Main$$run(Class<?> callerClass, PrintWriter out, PrintWriter err, String... args);

    void check$jdk_vm_ci_services_JVMCIServiceLocator$$getProviders(Class<?> callerClass, Class<?> service);

    void check$jdk_vm_ci_services_Services$$load(Class<?> callerClass, Class<?> service);

    void check$jdk_vm_ci_services_Services$$loadSingle(Class<?> callerClass, Class<?> service, boolean required);

    void check$com_sun_tools_jdi_VirtualMachineManagerImpl$$virtualMachineManager(Class<?> callerClass);

    void check$java_lang_Thread$$setDefaultUncaughtExceptionHandler(Class<?> callerClass, Thread.UncaughtExceptionHandler ueh);

    void check$java_util_spi_LocaleServiceProvider$(Class<?> callerClass);

    void check$java_text_spi_BreakIteratorProvider$(Class<?> callerClass);

    void check$java_text_spi_CollatorProvider$(Class<?> callerClass);

    void check$java_text_spi_DateFormatProvider$(Class<?> callerClass);

    void check$java_text_spi_DateFormatSymbolsProvider$(Class<?> callerClass);

    void check$java_text_spi_DecimalFormatSymbolsProvider$(Class<?> callerClass);

    void check$java_text_spi_NumberFormatProvider$(Class<?> callerClass);

    void check$java_util_spi_CalendarDataProvider$(Class<?> callerClass);

    void check$java_util_spi_CalendarNameProvider$(Class<?> callerClass);

    void check$java_util_spi_CurrencyNameProvider$(Class<?> callerClass);

    void check$java_util_spi_LocaleNameProvider$(Class<?> callerClass);

    void check$java_util_spi_TimeZoneNameProvider$(Class<?> callerClass);

    void check$java_util_logging_LogManager$(Class<?> callerClass);

    void check$java_net_DatagramSocket$$setDatagramSocketImplFactory(Class<?> callerClass, DatagramSocketImplFactory fac);

    void check$java_net_HttpURLConnection$$setFollowRedirects(Class<?> callerClass, boolean set);

    void check$java_net_ServerSocket$$setSocketFactory(Class<?> callerClass, SocketImplFactory fac);

    void check$java_net_Socket$$setSocketImplFactory(Class<?> callerClass, SocketImplFactory fac);

    void check$java_net_URL$$setURLStreamHandlerFactory(Class<?> callerClass, URLStreamHandlerFactory fac);

    void check$java_net_URLConnection$$setFileNameMap(Class<?> callerClass, FileNameMap map);

    void check$java_net_URLConnection$$setContentHandlerFactory(Class<?> callerClass, ContentHandlerFactory fac);

}
