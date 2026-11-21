package com.lucifer.exifstudio.data

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import androidx.core.content.ContextCompat
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * 本机定位。
 * 只做一件事：尽快拿到一个可用的当前点（或 10 分钟内的最后已知点），
 * 拿不到就返回 null，由调用方决定提示什么。
 * 不引 play-services-location，直接用系统 LocationManager，省 1.5 MB 依赖。
 */
data class Loc(
    val lat: Double,
    val lon: Double,
    val accuracy: Float,
    val time: Long,
    val fromCache: Boolean
)

object Locator {

    private const val FRESH_MS = 30_000L
    private const val CACHE_MS = 10 * 60_000L

    fun granted(context: Context): Boolean =
        ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED

    private fun manager(context: Context): LocationManager? =
        context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager

    private fun enabledProviders(lm: LocationManager): List<String> =
        listOf(
            LocationManager.GPS_PROVIDER,
            LocationManager.NETWORK_PROVIDER,
            LocationManager.PASSIVE_PROVIDER
        ).filter { p -> runCatching { lm.isProviderEnabled(p) }.getOrDefault(false) }

    @SuppressLint("MissingPermission")
    private fun lastKnown(context: Context): Loc? {
        if (!granted(context)) return null
        val lm = manager(context) ?: return null
        val best = enabledProviders(lm)
            .mapNotNull { p -> runCatching { lm.getLastKnownLocation(p) }.getOrNull() }
            .maxByOrNull { it.time }
            ?: return null
        return Loc(best.latitude, best.longitude, best.accuracy, best.time, true)
    }

    @SuppressLint("MissingPermission")
    private suspend fun awaitFix(context: Context, provider: String, timeoutMs: Long): Location? {
        if (!granted(context)) return null
        val lm = manager(context) ?: return null
        return withTimeoutOrNull(timeoutMs) {
            suspendCancellableCoroutine<Location?> { cont ->
                val listener = object : LocationListener {
                    override fun onLocationChanged(location: Location) {
                        runCatching { lm.removeUpdates(this) }
                        if (cont.isActive) cont.resume(location)
                    }

                    @Deprecated("Deprecated in Java")
                    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit

                    override fun onProviderEnabled(provider: String) = Unit

                    override fun onProviderDisabled(provider: String) = Unit
                }
                val ok = runCatching {
                    lm.requestLocationUpdates(provider, 0L, 0f, listener, Looper.getMainLooper())
                }.isSuccess
                if (!ok) {
                    if (cont.isActive) cont.resume(null)
                    return@suspendCancellableCoroutine
                }
                cont.invokeOnCancellation { runCatching { lm.removeUpdates(listener) } }
            }
        }
    }

    /**
     * 取当前位置。便宜的先来：极新的缓存 → GPS → 网络 → 过期缓存。
     * 全程最长约 13 秒，超了就给 null，不吊着 UI。
     */
    suspend fun fix(context: Context, gpsMs: Long = 8_000L, netMs: Long = 5_000L): Loc? {
        if (!granted(context)) return null
        val cache = lastKnown(context)
        val now = System.currentTimeMillis()
        if (cache != null && now - cache.time <= FRESH_MS) return cache

        val lm = manager(context)
        val providers = lm?.let { enabledProviders(it) }.orEmpty()

        if (LocationManager.GPS_PROVIDER in providers) {
            awaitFix(context, LocationManager.GPS_PROVIDER, gpsMs)?.let {
                return Loc(it.latitude, it.longitude, it.accuracy, it.time, false)
            }
        }
        if (LocationManager.NETWORK_PROVIDER in providers) {
            awaitFix(context, LocationManager.NETWORK_PROVIDER, netMs)?.let {
                return Loc(it.latitude, it.longitude, it.accuracy, it.time, false)
            }
        }
        return cache?.takeIf { now - it.time <= CACHE_MS }
    }
}
