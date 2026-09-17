package com.wander.android.core.i18n

import android.app.LocaleManager
import android.content.Context
import android.content.SharedPreferences
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The app's display language, and the one place that changes it.
 *
 * Two mechanisms, because there is no single one that covers `minSdk = 26`:
 *
 * - **API 33+** delegates to [LocaleManager]. The system owns the preference there, which is what
 *   puts Wanda in the phone's own *Settings → Apps → Language* list and survives the app being
 *   killed. Storing a second copy ourselves would let the two disagree the moment someone changed
 *   it from the system screen.
 * - **API 26–32** has no such thing, so the tag is kept here and re-applied to every `Context` in
 *   [wrap] before any resource is read.
 *
 * Plain [SharedPreferences] rather than `SecureStorage`: a chosen language is not a credential,
 * and `SecureStorage` is backed by the Keystore, which cannot be read from `attachBaseContext`
 * cheaply enough to sit in front of every activity launch.
 */
@Singleton
class AppLocaleStore @Inject constructor(
    @param:ApplicationContext private val context: Context
) {

    /**
     * The chosen language tag, or empty when the app follows the system.
     *
     * Reading below API 33 goes straight to disk on purpose. It is one small preference file, read
     * once per activity creation, and caching it in a field would mean a stale value in the second
     * process the notification service runs in.
     */
    var tag: String
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.getSystemService(LocaleManager::class.java)
                ?.applicationLocales
                ?.takeIf { !it.isEmpty }
                ?.get(0)
                ?.toLanguageTag()
                .orEmpty()
        } else {
            prefs(context).getString(KEY_TAG, "").orEmpty()
        }
        set(value) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val list = if (value.isEmpty()) {
                    LocaleList.getEmptyLocaleList()
                } else {
                    LocaleList.forLanguageTags(value)
                }
                context.getSystemService(LocaleManager::class.java)?.applicationLocales = list
            } else {
                prefs(context).edit { putString(KEY_TAG, value) }
            }
        }

    /**
     * True when changing the language requires the caller to recreate the activity itself.
     *
     * API 33+ restarts the activity as part of applying the change, so doing it again would throw
     * away the back stack for nothing.
     */
    val needsManualRecreate: Boolean
        get() = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU

    companion object {
        private const val PREFS_NAME = "wander_locale"
        private const val KEY_TAG = "app_language_tag"

        private fun prefs(context: Context): SharedPreferences =
            context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        /**
         * The same context, resolving resources in the chosen language.
         *
         * Called from `attachBaseContext`, which runs before Hilt has injected anything — hence a
         * static that takes the context it is handed rather than an instance method.
         *
         * A no-op on API 33+: the platform has already applied the override by this point, and
         * layering a second one on top would pin the app to the language it launched with and stop
         * it following a change made from the system settings screen.
         */
        fun wrap(base: Context): Context {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) return base
            val tag = prefs(base).getString(KEY_TAG, "").orEmpty()
            if (tag.isEmpty()) return base

            val locale = Locale.forLanguageTag(tag)
            Locale.setDefault(locale)
            val config = Configuration(base.resources.configuration).apply {
                setLocale(locale)
                setLayoutDirection(locale)
            }
            return base.createConfigurationContext(config)
        }
    }
}
