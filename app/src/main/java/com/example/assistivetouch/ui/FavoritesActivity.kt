package com.example.assistivetouch.ui

import android.content.Intent
import android.content.pm.ResolveInfo
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ListView
import androidx.appcompat.app.AppCompatActivity
import com.example.assistivetouch.R
import com.example.assistivetouch.prefs.FavoritesManager

class FavoritesActivity : AppCompatActivity() {

    private lateinit var listView: ListView
    private lateinit var buttonSave: Button

    private val apps = mutableListOf<ResolveInfo>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_favorites)

        listView = findViewById(R.id.listApps)
        buttonSave = findViewById(R.id.buttonSaveFavorites)

        loadApps()

        buttonSave.setOnClickListener {
            saveSelection()
            finish()
        }
    }

    private fun loadApps() {
        val pm = packageManager
        val intent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        val resolveInfos = pm.queryIntentActivities(intent, 0)
        apps.clear()
        apps.addAll(resolveInfos.sortedBy { it.loadLabel(pm).toString() })

        val labels = apps.map { it.loadLabel(pm).toString() }
        val adapter = ArrayAdapter(
            this,
            android.R.layout.simple_list_item_multiple_choice,
            labels
        )
        listView.adapter = adapter
        listView.choiceMode = ListView.CHOICE_MODE_MULTIPLE

        val favoritePackages = FavoritesManager.getFavoritePackages(this)
        apps.forEachIndexed { index, resolveInfo ->
            val pkg = resolveInfo.activityInfo.packageName
            if (favoritePackages.contains(pkg)) {
                listView.setItemChecked(index, true)
            }
        }
    }

    private fun saveSelection() {
        val checked = mutableSetOf<String>()
        for (i in apps.indices) {
            if (listView.isItemChecked(i)) {
                checked.add(apps[i].activityInfo.packageName)
            }
        }
        FavoritesManager.setFavoritePackages(this, checked)
    }
}


