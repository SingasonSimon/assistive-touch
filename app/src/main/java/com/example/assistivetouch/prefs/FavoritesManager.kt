package com.example.assistivetouch.prefs

import android.content.Context

object FavoritesManager {

    private const val PREFS_NAME = "assistive_touch_prefs"
    private const val KEY_FAVORITES = "favorite_apps"

    fun getFavoritePackages(context: Context): Set<String> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getStringSet(KEY_FAVORITES, emptySet()) ?: emptySet()
    }

    fun setFavoritePackages(context: Context, packages: Set<String>) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putStringSet(KEY_FAVORITES, packages).apply()
    }
}


