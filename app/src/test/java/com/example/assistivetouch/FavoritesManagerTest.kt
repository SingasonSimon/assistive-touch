package com.example.assistivetouch

import android.content.Context
import android.content.SharedPreferences
import com.example.assistivetouch.prefs.FavoritesManager
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito.*
import org.mockito.junit.MockitoJUnitRunner

@RunWith(MockitoJUnitRunner::class)
class FavoritesManagerTest {

    @Mock
    private lateinit var context: Context

    @Mock
    private lateinit var sharedPreferences: SharedPreferences

    @Mock
    private lateinit var editor: SharedPreferences.Editor

    @Before
    fun setUp() {
        `when`(context.getSharedPreferences("assistive_touch_prefs", Context.MODE_PRIVATE))
            .thenReturn(sharedPreferences)
        `when`(sharedPreferences.edit()).thenReturn(editor)
        `when`(editor.putStringSet(anyString(), any())).thenReturn(editor)
    }

    @Test
    fun testGetFavoritePackages_whenEmpty_returnsEmptySet() {
        `when`(sharedPreferences.getStringSet("favorite_apps", any())).thenReturn(emptySet())

        val result = FavoritesManager.getFavoritePackages(context)

        assert(result.isEmpty())
    }

    @Test
    fun testGetFavoritePackages_whenHasPackages_returnsSet() {
        val packages = setOf("com.example.app1", "com.example.app2")
        `when`(sharedPreferences.getStringSet("favorite_apps", any())).thenReturn(packages)

        val result = FavoritesManager.getFavoritePackages(context)

        assert(result == packages)
    }

    @Test
    fun testSetFavoritePackages_savesPackages() {
        val packages = setOf("com.example.app1", "com.example.app2")

        FavoritesManager.setFavoritePackages(context, packages)

        verify(editor).putStringSet("favorite_apps", packages)
        verify(editor).apply()
    }

    @Test
    fun testSetFavoritePackages_withEmptySet_clearsPackages() {
        val packages = emptySet<String>()

        FavoritesManager.setFavoritePackages(context, packages)

        verify(editor).putStringSet("favorite_apps", packages)
        verify(editor).apply()
    }
}

