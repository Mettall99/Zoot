package com.zooot.vpn.vpn

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.system.OsConstants
import android.util.Log
import androidx.core.app.NotificationCompat
import com.zooot.vpn.R
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import java.net.Inet6Address
import java.net.InterfaceAddress
import java.net.NetworkInterface
import java.security.KeyStore
import java.util.Base64
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

class ZootVpnService : VpnService() {
    private var tunFd: ParcelFileDescriptor? = null
    private var runtimeHandle: SingBoxRuntimeHandle? = null
    private var worker: Thread? = null

    override fun onCreate() {
        super.onCreate()
        safeLogDebug("onCreate")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        safeLogDebug("onStartCommand action=${intent?.action.orEmpty()} extras_present=${intent?.extras != null}")
        when (intent?.action) {
            ACTION_START_REALITY -> startRealityService(intent.getStringExtra(EXTRA_CONFIG).orEmpty())
            ACTION_STOP_REALITY -> stopRealityService()
        }
        return Service.START_NOT_STICKY
    }

    override fun onDestroy() {
        stopRealityService()
        super.onDestroy()
    }

    override fun onRevoke() {
        stopRealityService()
        super.onRevoke()
    }

    private fun startRealityService(config: String) {
        safeLogDebug("startReality config_present=${config.isNotBlank()}")
        startLatch.set(CountDownLatch(1))
        stopLatch.set(CountDownLatch(1))
        lastError.set(null)
        if (config.isBlank()) {
            setStopped("empty sing-box config")
            stopSelf()
            return
        }
        if (running.get()) {
            safeLogDebug("running=true already_started=true")
            startLatch.get().countDown()
            return
        }
        try {
            showForeground()
        } catch (e: Throwable) {
            val message = "showForeground failed: ${realityErrorMessage(e)}"
            safeLogDebug(message)
            setStopped(message)
            stopSelf()
            return
        }
        worker = Thread({
            startRuntimeSafely(
                runtimeFactory = {
                    val platform = LibboxPlatformProxy(this)
                    safeLogDebug("core start begin")
                    LibboxRuntimeSupport.start(config, platform.proxy, logger = { message -> safeLogDebug(message) }).also { runtimeHandle = it }
                },
                onFailureCleanup = {
                    runCatching { runtimeHandle?.close() }
                    runtimeHandle = null
                    runCatching { tunFd?.close() }
                    tunFd = null
                }
            )
        }, "zooot-sing-box-reality")
        worker?.start()
    }

    private fun stopRealityService() {
        safeLogDebug("stop begin")
        val runtime = runtimeHandle
        runtimeHandle = null
        val stopFailure = runCatching { runtime?.close() }.exceptionOrNull()
        val tunFailure = runCatching { tunFd?.close() }.exceptionOrNull()
        tunFd = null
        running.set(false)
        if (stopFailure != null || tunFailure != null) {
            val failure = stopFailure ?: tunFailure
            val message = realityErrorMessage(failure ?: IllegalStateException("unknown stop failure"))
            lastError.set(message)
            safeLogDebug("stop failure $message")
        } else {
            safeLogDebug("stop success")
        }
        stopLatch.get().countDown()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun setStopped(message: String?) = setStoppedState(message)

    private fun showForeground() {
        safeLogDebug("showForeground start")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "Zooot Reality VPN", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Zooot VPN")
            .setContentText("Reality/TCP tunnel running")
            .setOngoing(true)
            .build()
        startForeground(NOTIFICATION_ID, notification)
        safeLogDebug("showForeground success")
    }

    private class LibboxPlatformProxy(private val service: ZootVpnService) : InvocationHandler {
        val proxy: Any = Proxy.newProxyInstance(
            Class.forName("io.nekohasekai.libbox.PlatformInterface").classLoader,
            arrayOf(Class.forName("io.nekohasekai.libbox.PlatformInterface")),
            this
        )

        override fun invoke(proxy: Any, method: Method, args: Array<out Any?>?): Any? = when (method.name) {
            "usePlatformAutoDetectInterfaceControl" -> true
            "autoDetectInterfaceControl" -> service.protect((args?.get(0) as Number).toInt()).let { Unit }
            "openTun" -> runCatching {
                safeLogDebug("openTun called")
                val fd = service.openTun(args?.get(0) ?: error("missing tun options"))
                if (method.returnType == java.lang.Long.TYPE) fd.toLong() else fd
            }.getOrElse {
                safeLogDebug("createTun failure ${LibboxRuntimeSupport.sanitize(it.message)}")
                throw IllegalStateException("openTun failed: ${LibboxRuntimeSupport.sanitize(it.message)}", it)
            }
            "useProcFS" -> Build.VERSION.SDK_INT < Build.VERSION_CODES.Q
            "findConnectionOwner" -> newConnectionOwner(ProcessUid.INVALID)
            "packageNameByUid" -> packageNameByUid((args?.get(0) as Number).toInt())
            "uIDByPackageName" -> service.packageManager.getApplicationInfo(args?.get(0) as String, 0).uid
            "uidByPackageName" -> service.packageManager.getApplicationInfo(args?.get(0) as String, 0).uid
            "startDefaultInterfaceMonitor" -> Unit
            "closeDefaultInterfaceMonitor" -> Unit
            "getInterfaces" -> networkInterfacesIterator()
            "underNetworkExtension" -> false
            "includeAllNetworks" -> false
            "readWIFIState" -> null
            "localDNSTransport" -> null
            "systemCertificates" -> stringIterator(loadSystemCertificates().iterator())
            "clearDNSCache" -> Unit
            "writeLog" -> Unit
            "sendNotification" -> Unit
            "startNeighborMonitor" -> Unit
            "registerMyInterface" -> Unit
            "closeNeighborMonitor" -> Unit
            "equals" -> proxy === args?.get(0)
            "hashCode" -> System.identityHashCode(proxy)
            "toString" -> "ZoootLibboxPlatformProxy"
            else -> defaultValue(method.returnType)
        }

        private fun ZootVpnService.openTun(options: Any): Int {
            safeLogDebug("createTun begin")
            val mtu = callInt(options, "getMTU", 0)
            val builder = Builder().setSession("Zooot VPN")
            if (mtu > 0) builder.setMtu(mtu)
            var addressCount = 0
            var routeCount = 0
            addressCount += addAddresses(builder, options, "getInet4Address", ipv6 = false)
            addressCount += addAddresses(builder, options, "getInet6Address", ipv6 = true)
            if (callBoolean(options, "getAutoRoute", true)) {
                routeCount += addRoutes(builder, options, "getInet4RouteRange", ipv6 = false)
                routeCount += addRoutes(builder, options, "getInet6RouteRange", ipv6 = true)
            }
            routeCount += addRoutes(builder, options, "getInet4RouteAddress", ipv6 = false)
            routeCount += addRoutes(builder, options, "getInet6RouteAddress", ipv6 = true)
            if (addressCount == 0) builder.addAddress("172.19.0.1", 30)
            if (routeCount == 0) builder.addRoute("0.0.0.0", 0)
            forEachString(call(options, "getDNSServerAddress")) { builder.addDnsServer(it) }
            applyPackageRules(builder, options)
            runCatching { builder.addDisallowedApplication(packageName) }
            val pfd = builder.establish() ?: error("VpnService.Builder.establish() returned null")
            safeLogDebug("tun fd created fd=${pfd.fd}")
            tunFd?.close()
            tunFd = pfd
            safeLogDebug("createTun success")
            return pfd.fd
        }

        private fun addAddresses(builder: Builder, options: Any, method: String, ipv6: Boolean): Int {
            var count = 0
            forEachRoutePrefix(call(options, method)) { address, prefix ->
                if (address.contains(":") == ipv6) { builder.addAddress(address, prefix); count++ }
            }
            return count
        }

        private fun addRoutes(builder: Builder, options: Any, method: String, ipv6: Boolean): Int {
            var count = 0
            forEachRoutePrefix(call(options, method)) { address, prefix ->
                if (address.contains(":") == ipv6) { builder.addRoute(address, prefix); count++ }
            }
            return count
        }

        private fun forEachRoutePrefix(iterator: Any?, block: (String, Int) -> Unit) {
            if (iterator == null) return
            if (iterator is String) { parseRoutePrefix(iterator)?.let { (address, bits) -> block(address, bits) }; return }
            while (callBoolean(iterator, "hasNext", false)) {
                val prefix = call(iterator, "next") ?: break
                if (prefix is String) { parseRoutePrefix(prefix)?.let { (address, bits) -> block(address, bits) }; continue }
                val address = call(prefix, "address") as? String ?: continue
                val bits = callInt(prefix, "prefix", -1)
                if (bits >= 0) block(address, bits)
            }
        }

        private fun parseRoutePrefix(value: String): Pair<String, Int>? {
            val address = value.substringBefore("/").ifBlank { return null }
            val bits = value.substringAfter("/", "").toIntOrNull() ?: if (address.contains(":")) 128 else 32
            return address to bits
        }

        private fun applyPackageRules(builder: Builder, options: Any) {
            forEachString(call(options, "getIncludePackage")) { pkg -> runCatching { builder.addAllowedApplication(pkg) } }
            forEachString(call(options, "getExcludePackage")) { pkg -> runCatching { builder.addDisallowedApplication(pkg) } }
        }

        private fun forEachString(value: Any?, block: (String) -> Unit) {
            if (value == null) return
            if (value is String) { if (value.isNotBlank()) block(value); return }
            while (callBoolean(value, "hasNext", false)) {
                val item = call(value, "next")?.toString().orEmpty()
                if (item.isNotBlank()) block(item)
            }
        }

        private fun callStringBox(target: Any, method: String): String? = runCatching {
            val box = call(target, method) ?: return null
            call(box, "value") as? String ?: box.toString()
        }.getOrNull()

        private fun networkInterfacesIterator(): Any {
            val connectivity = service.getSystemService(ConnectivityManager::class.java)
            val javaInterfaces = NetworkInterface.getNetworkInterfaces().toList()
            val items = connectivity.allNetworks.mapNotNull { network ->
                val link = connectivity.getLinkProperties(network) ?: return@mapNotNull null
                val caps = connectivity.getNetworkCapabilities(network) ?: return@mapNotNull null
                val name = link.interfaceName ?: return@mapNotNull null
                val javaInterface = javaInterfaces.firstOrNull { it.name == name } ?: return@mapNotNull null
                newNetworkInterface(name, javaInterface, caps, link.dnsServers.mapNotNull { it.hostAddress })
            }
            return objectIterator("io.nekohasekai.libbox.NetworkInterfaceIterator", items.iterator())
        }

        private fun newNetworkInterface(name: String, javaInterface: NetworkInterface, caps: NetworkCapabilities, dns: List<String>): Any {
            val clazz = Class.forName("io.nekohasekai.libbox.NetworkInterface")
            val item = clazz.getDeclaredConstructor().newInstance()
            set(item, "name", name)
            set(item, "index", javaInterface.index)
            set(item, "mtu", runCatching { javaInterface.mtu }.getOrDefault(1500))
            set(item, "addresses", stringIterator(javaInterface.interfaceAddresses.map { it.toPrefix() }.iterator()))
            set(item, "dnsServer", stringIterator(dns.iterator()))
            set(item, "type", interfaceType(caps))
            set(item, "flags", interfaceFlags(javaInterface, caps))
            set(item, "metered", !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED))
            return item
        }

        private fun interfaceType(caps: NetworkCapabilities): Int = when {
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> getLibboxInt("InterfaceTypeWIFI", 1)
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> getLibboxInt("InterfaceTypeCellular", 2)
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> getLibboxInt("InterfaceTypeEthernet", 3)
            else -> getLibboxInt("InterfaceTypeOther", 0)
        }

        private fun interfaceFlags(javaInterface: NetworkInterface, caps: NetworkCapabilities): Int {
            var flags = 0
            if (caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) flags = flags or OsConstants.IFF_UP or OsConstants.IFF_RUNNING
            if (javaInterface.isLoopback) flags = flags or OsConstants.IFF_LOOPBACK
            if (javaInterface.isPointToPoint) flags = flags or OsConstants.IFF_POINTOPOINT
            if (javaInterface.supportsMulticast()) flags = flags or OsConstants.IFF_MULTICAST
            return flags
        }

        private fun packageNameByUid(uid: Int): String = service.packageManager.getPackagesForUid(uid)?.joinToString(",").orEmpty()

        private fun newConnectionOwner(uid: Int): Any {
            val owner = Class.forName("io.nekohasekai.libbox.ConnectionOwner").getDeclaredConstructor().newInstance()
            set(owner, "userId", uid)
            set(owner, "userName", packageNameByUid(uid))
            runCatching { owner.javaClass.getMethod("setAndroidPackageNames", Class.forName("io.nekohasekai.libbox.StringIterator")).invoke(owner, stringIterator(emptyList<String>().iterator())) }
            return owner
        }

        private fun loadSystemCertificates(): List<String> = runCatching {
            val keyStore = KeyStore.getInstance("AndroidCAStore")
            keyStore.load(null, null)
            keyStore.aliases().toList().mapNotNull { alias ->
                keyStore.getCertificate(alias)?.encoded?.let { encoded ->
                    "-----BEGIN CERTIFICATE-----\n${Base64.getEncoder().encodeToString(encoded)}\n-----END CERTIFICATE-----"
                }
            }
        }.getOrDefault(emptyList())

        private fun stringIterator(iterator: Iterator<String>): Any = objectIterator("io.nekohasekai.libbox.StringIterator", iterator, len = 0)

        private fun objectIterator(interfaceName: String, iterator: Iterator<*>, len: Int? = null): Any {
            val iface = Class.forName(interfaceName)
            return Proxy.newProxyInstance(iface.classLoader, arrayOf(iface)) { proxy, method, args ->
                when (method.name) {
                    "hasNext" -> iterator.hasNext()
                    "next" -> iterator.next()
                    "len" -> len ?: 0
                    "equals" -> proxy === args?.get(0)
                    "hashCode" -> System.identityHashCode(proxy)
                    "toString" -> "ZoootIteratorProxy"
                    else -> defaultValue(method.returnType)
                }
            }
        }

        private fun InterfaceAddress.toPrefix(): String = if (address is Inet6Address) {
            "${Inet6Address.getByAddress(address.address).hostAddress}/$networkPrefixLength"
        } else {
            "${address.hostAddress}/$networkPrefixLength"
        }

        private fun call(target: Any, method: String): Any? = runCatching { target.javaClass.methods.first { it.name == method && it.parameterCount == 0 }.invoke(target) }.getOrNull()
        private fun callInt(target: Any, method: String, fallback: Int): Int = runCatching { (call(target, method) as Number).toInt() }.getOrDefault(fallback)
        private fun callBoolean(target: Any, method: String, fallback: Boolean): Boolean = runCatching { call(target, method) as Boolean }.getOrDefault(fallback)
        private fun set(target: Any, name: String, value: Any?) = runCatching { target.javaClass.getField(name).set(target, value) }
        private fun getLibboxInt(name: String, fallback: Int): Int = runCatching { Class.forName("io.nekohasekai.libbox.Libbox").getField(name).getInt(null) }.getOrDefault(fallback)
        private fun defaultValue(type: Class<*>): Any? = when (type) { java.lang.Boolean.TYPE -> false; java.lang.Integer.TYPE -> 0; java.lang.Long.TYPE -> 0L; java.lang.Void.TYPE -> Unit; else -> null }
    }

    companion object {
        private const val ACTION_START_REALITY = "com.zooot.vpn.action.START_REALITY"
        private const val ACTION_STOP_REALITY = "com.zooot.vpn.action.STOP_REALITY"
        private const val EXTRA_CONFIG = "sing_box_config"
        private const val CHANNEL_ID = "reality_vpn"
        private const val NOTIFICATION_ID = 1001
        private const val TAG = "ZootVpnService"
        private val running = java.util.concurrent.atomic.AtomicBoolean(false)
        private val lastError = AtomicReference<String?>(null)
        private val startLatch = AtomicReference(CountDownLatch(0))
        private val stopLatch = AtomicReference(CountDownLatch(0))

        fun startReality(context: Context, config: String) {
            val intent = Intent(context, ZootVpnService::class.java).setAction(ACTION_START_REALITY).putExtra(EXTRA_CONFIG, config)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(intent) else context.startService(intent)
        }

        fun stopReality(context: Context) {
            context.startService(Intent(context, ZootVpnService::class.java).setAction(ACTION_STOP_REALITY))
        }

        private fun safeLogDebug(message: String) { runCatching { Log.d(TAG, LibboxRuntimeSupport.sanitize(message)) } }
        internal fun inspectLibboxRuntime(): LibboxRuntimeInspection = LibboxRuntimeSupport.inspect()

        internal fun startRuntimeForTest(runtimeFactory: () -> SingBoxRuntimeHandle) {
            startLatch.set(CountDownLatch(1))
            stopLatch.set(CountDownLatch(1))
            lastError.set(null)
            running.set(false)
            startRuntimeSafely(runtimeFactory, onFailureCleanup = {})
        }

        private fun setStoppedState(message: String?) {
            lastError.set(message)
            running.set(false)
            safeLogDebug("running=false lastRealityError=${message ?: "none"}")
            startLatch.get().countDown()
            stopLatch.get().countDown()
        }

        private fun startRuntimeSafely(runtimeFactory: () -> SingBoxRuntimeHandle, onFailureCleanup: () -> Unit) {
            try {
                val runtime = runtimeFactory()
                runtimeHandleForLogging(runtime)
                safeLogDebug("runtime start called")
                runtime.start()
                running.set(true)
                lastError.set(null)
                safeLogDebug("runtime start success")
                safeLogDebug("running=true lastRealityError=none")
                startLatch.get().countDown()
            } catch (e: Throwable) {
                val message = realityErrorMessage(e)
                safeLogDebug("runtime start exception class=${e::class.java.simpleName} message=$message")
                onFailureCleanup()
                setStoppedState(message)
            }
        }

        private fun runtimeHandleForLogging(runtime: SingBoxRuntimeHandle) {
            safeLogDebug("runtime object created type=${runtime::class.java.simpleName}")
        }

        internal fun realityErrorMessage(e: Throwable): String {
            if (e is NoSuchMethodException) {
                val sanitized = LibboxRuntimeSupport.sanitize(e.message)
                return "NoSuchMethodException: $sanitized"
            }
            val message = e.message.orEmpty()
            if (message.startsWith(LibboxRuntimeSupport.UNSUPPORTED_CORE_MESSAGE) ||
                message.startsWith(LibboxRuntimeSupport.COMMAND_SERVER_ONLY_MESSAGE) ||
                message.startsWith("NoSuchMethodException:") ||
                message.startsWith("InvocationTargetException cause:") ||
                message.startsWith("openTun failed:") ||
                message.startsWith("showForeground failed:")
            ) return LibboxRuntimeSupport.sanitize(message)
            val cause = e.cause
            if (cause != null && cause !== e) {
                return "${e::class.java.simpleName} cause: ${cause::class.java.simpleName}: ${LibboxRuntimeSupport.sanitize(cause.message)}"
            }
            return "${e::class.java.simpleName}: ${LibboxRuntimeSupport.sanitize(e.message)}"
        }

        fun awaitRealityRunning(timeoutMs: Long): Boolean = startLatch.get().await(timeoutMs, TimeUnit.MILLISECONDS) && running.get()
        fun awaitRealityStopped(timeoutMs: Long): Boolean = stopLatch.get().await(timeoutMs, TimeUnit.MILLISECONDS) && !running.get()
        fun isRealityRunning(): Boolean = running.get()
        fun lastRealityError(): String? = lastError.get()
    }

    private object ProcessUid { const val INVALID = -1 }
}
