package chat.mural.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

/** Einfache Online-Prüfung für Voice/STT/LLM (echtes Offline-STT ohne Cloud ist nicht vorgesehen). */
object NetworkStatus {
    fun isOnline(context: Context): Boolean {
        val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = manager.activeNetwork ?: return false
        val caps = manager.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }
}
