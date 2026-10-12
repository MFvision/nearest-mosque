package sa.zood.nearmosque.platform

import android.content.Context
import java.security.SecureRandom

/** A random value made once per install, carried by the app's own widget links (so other apps cannot pose as them). */
object LinkToken {
    const val EXTRA = "sa.zood.nearmosque.link_token"
    private const val KEY = "link_token"

    fun get(context: Context): String {
        val prefs = context.getSharedPreferences("links", Context.MODE_PRIVATE)
        prefs.getString(KEY, null)?.let { return it }
        val bytes = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val token = bytes.joinToString("") { "%02x".format(it) }
        prefs.edit().putString(KEY, token).apply()
        return token
    }
}
