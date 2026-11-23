package site.kentok.lists.data.sync

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import site.kentok.lists.data.remote.ApiClient

/** Онлайн/офлайн; без сети — проверка каждые 5 секунд. */
class ConnectivityMonitor(
    context: Context,
    private val api: ApiClient,
    private val scope: CoroutineScope
) {
    private val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private val _online = MutableStateFlow(false)
    val online: StateFlow<Boolean> = _online.asStateFlow()

    /** true короткое время после перехода offline → online */
    private val _justCameOnline = MutableStateFlow(false)
    val justCameOnline: StateFlow<Boolean> = _justCameOnline.asStateFlow()

    private var pollJob: Job? = null
    private var greenJob: Job? = null

    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            scope.launch(Dispatchers.IO) { refresh() }
        }
        override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
            if (caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) {
                scope.launch(Dispatchers.IO) { refresh() }
            }
        }
        override fun onLost(network: Network) {
            scope.launch(Dispatchers.IO) {
                // перепроверим: мог остаться другой интерфейс
                refresh()
            }
        }
    }

    fun start() {
        val req = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        try {
            cm.registerNetworkCallback(req, callback)
        } catch (_: Exception) {}
        try {
            cm.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    scope.launch(Dispatchers.IO) { refresh() }
                }
                override fun onLost(network: Network) {
                    scope.launch(Dispatchers.IO) { refresh() }
                }
            })
        } catch (_: Exception) {}
        scope.launch(Dispatchers.IO) { refresh() }
    }

    fun stop() {
        pollJob?.cancel()
        greenJob?.cancel()
        try { cm.unregisterNetworkCallback(callback) } catch (_: Exception) {}
    }

    /** @return текущее состояние online после проверки */
    suspend fun refresh(): Boolean = withContext(Dispatchers.IO) {
        val sys = hasSystemNetwork()
        val pingOk = if (sys) {
            try { api.ping() } catch (_: Exception) { false }
        } else false
        // Системная сеть = online (вход/синхронизация пробуют сервер сами).
        // Ping только подтверждает сервер; его сбой не должен «глушить» интернет.
        val ok = sys || pingOk
        val was = _online.value
        _online.value = ok
        if (ok && !was) {
            _justCameOnline.value = true
            greenJob?.cancel()
            greenJob = scope.launch {
                delay(8_000)
                _justCameOnline.value = false
            }
        }
        if (!ok) {
            _justCameOnline.value = false
            startPolling()
        } else {
            pollJob?.cancel()
        }
        ok
    }

    fun hasSystemNetwork(): Boolean {
        val n = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(n) ?: return false
        val internet = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        val validated = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        // VALIDATED может быть false на корпоративных/медленных сетях — достаточно INTERNET
        return internet || validated
    }

    private fun startPolling() {
        if (pollJob?.isActive == true) return
        pollJob = scope.launch(Dispatchers.IO) {
            while (isActive && !_online.value) {
                delay(5_000)
                refresh()
            }
        }
    }
}
