package com.wander.android.core.security

/**
 * An in-memory stand-in for [android.content.SharedPreferences]. [SecureStorage] has no other
 * constructor reachable from a plain JVM unit test — `create(context)` needs a real Android
 * Keystore — and an in-memory map is all a preference store needs to be for a test.
 */
internal class FakeSharedPreferences : android.content.SharedPreferences {
    private val values = mutableMapOf<String, Any?>()

    override fun getAll(): MutableMap<String, *> = values
    override fun getString(key: String?, defValue: String?) = values[key] as? String ?: defValue
    @Suppress("UNCHECKED_CAST")
    override fun getStringSet(key: String?, defValues: MutableSet<String>?) =
        values[key] as? MutableSet<String> ?: defValues
    override fun getInt(key: String?, defValue: Int) = values[key] as? Int ?: defValue
    override fun getLong(key: String?, defValue: Long) = values[key] as? Long ?: defValue
    override fun getFloat(key: String?, defValue: Float) = values[key] as? Float ?: defValue
    override fun getBoolean(key: String?, defValue: Boolean) = values[key] as? Boolean ?: defValue
    override fun contains(key: String?) = values.containsKey(key)
    override fun edit(): android.content.SharedPreferences.Editor = FakeEditor()
    override fun registerOnSharedPreferenceChangeListener(
        listener: android.content.SharedPreferences.OnSharedPreferenceChangeListener?
    ) = Unit
    override fun unregisterOnSharedPreferenceChangeListener(
        listener: android.content.SharedPreferences.OnSharedPreferenceChangeListener?
    ) = Unit

    private inner class FakeEditor : android.content.SharedPreferences.Editor {
        override fun putString(key: String?, value: String?) = apply { values[key!!] = value }
        override fun putStringSet(key: String?, v: MutableSet<String>?) = apply { values[key!!] = v }
        override fun putInt(key: String?, value: Int) = apply { values[key!!] = value }
        override fun putLong(key: String?, value: Long) = apply { values[key!!] = value }
        override fun putFloat(key: String?, value: Float) = apply { values[key!!] = value }
        override fun putBoolean(key: String?, value: Boolean) = apply { values[key!!] = value }
        override fun remove(key: String?) = apply { values.remove(key) }
        override fun clear() = apply { values.clear() }
        override fun commit() = true
        override fun apply() = Unit
    }
}
