package com.bantje.fenfawatchmanager.watch

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.util.Log
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import java.util.concurrent.Executors

data class DiscoveredWatch(
    val name: String,
    val ip: String,
    val port: Int
)

class WatchDiscoveryService(private val context: Context) {

    companion object {
        private const val TAG = "WatchDiscoveryService"
        private const val SERVICE_TYPE = "_adb-tls-connect._tcp"
    }

    private val nsdManager = context.getSystemService(Context.NSD_SERVICE) as? NsdManager
    private val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager

    fun discoverWatches(): Flow<DiscoveredWatch> = callbackFlow {
        if (nsdManager == null) {
            close()
            return@callbackFlow
        }

        val multicastLock = wifiManager?.createMulticastLock("WatchDiscoveryLock")?.apply {
            setReferenceCounted(true)
            try {
                acquire()
                Log.d(TAG, "Acquired MulticastLock for mDNS discovery")
            } catch (e: Exception) {
                Log.w(TAG, "Could not acquire MulticastLock", e)
            }
        }

        val discoveryListener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(regType: String) {
                Log.d(TAG, "mDNS discovery started for $regType")
            }

            override fun onServiceFound(service: NsdServiceInfo) {
                Log.d(TAG, "mDNS service found: ${service.serviceName}")
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                        nsdManager.registerServiceInfoCallback(
                            service,
                            Executors.newSingleThreadExecutor(),
                            object : NsdManager.ServiceInfoCallback {
                                override fun onServiceInfoCallbackRegistrationFailed(errorCode: Int) {}
                                override fun onServiceUpdated(serviceInfo: NsdServiceInfo) {
                                    emitDiscovered(serviceInfo)
                                }
                                override fun onServiceLost() {}
                                override fun onServiceInfoCallbackUnregistered() {}
                            }
                        )
                    } else {
                        @Suppress("DEPRECATION")
                        nsdManager.resolveService(service, object : NsdManager.ResolveListener {
                            override fun onResolveFailed(serviceInfo: NsdServiceInfo?, errorCode: Int) {
                                Log.w(TAG, "Resolve failed: $errorCode")
                            }

                            override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                                emitDiscovered(serviceInfo)
                            }
                        })
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Failed resolving service", e)
                }
            }

            override fun onServiceLost(service: NsdServiceInfo) {
                Log.d(TAG, "Service lost: ${service.serviceName}")
            }

            override fun onDiscoveryStopped(serviceType: String) {
                Log.d(TAG, "Discovery stopped")
            }

            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                Log.e(TAG, "Start discovery failed: $errorCode")
                close()
            }

            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                Log.e(TAG, "Stop discovery failed: $errorCode")
            }

            private fun emitDiscovered(serviceInfo: NsdServiceInfo) {
                val host = serviceInfo.host?.hostAddress ?: return
                val port = serviceInfo.port
                if (port > 0) {
                    val item = DiscoveredWatch(
                        name = serviceInfo.serviceName ?: "Galaxy Watch",
                        ip = host,
                        port = port
                    )
                    Log.d(TAG, "Discovered device: $item")
                    trySend(item)
                }
            }
        }

        try {
            nsdManager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, discoveryListener)
        } catch (e: Exception) {
            Log.e(TAG, "discoverServices failed", e)
            close()
        }

        awaitClose {
            try {
                nsdManager.stopServiceDiscovery(discoveryListener)
            } catch (_: Exception) {}
            try {
                if (multicastLock?.isHeld == true) {
                    multicastLock.release()
                    Log.d(TAG, "Released MulticastLock")
                }
            } catch (_: Exception) {}
        }
    }
}
