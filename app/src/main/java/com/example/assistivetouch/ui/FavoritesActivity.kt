package com.example.assistivetouch.ui

import android.content.Intent
import android.content.pm.ResolveInfo
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.assistivetouch.R
import com.example.assistivetouch.prefs.FavoritesManager
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText

class FavoritesActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var buttonSave: MaterialButton
    private lateinit var editSearch: TextInputEditText

    private val allApps = mutableListOf<ResolveInfo>()
    private val filteredApps = mutableListOf<ResolveInfo>()
    private lateinit var adapter: AppFavoriteAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_favorites)

        recyclerView = findViewById(R.id.recyclerApps)
        buttonSave = findViewById(R.id.buttonSaveFavorites)
        editSearch = findViewById(R.id.editSearch)

        loadApps()
        setupRecyclerView()
        setupSearch()

        buttonSave.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            saveSelection()
            finish()
            overridePendingTransition(R.anim.slide_out_left, R.anim.fade_in)
        }
    }

    private fun loadApps() {
        val pm = packageManager
        val intent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        val resolveInfos = pm.queryIntentActivities(intent, 0)
        allApps.clear()
        allApps.addAll(resolveInfos.sortedBy { it.loadLabel(pm).toString() })
        filteredApps.clear()
        filteredApps.addAll(allApps)
    }

    private fun setupRecyclerView() {
        val favoritePackages = FavoritesManager.getFavoritePackages(this)
        adapter = AppFavoriteAdapter(
            filteredApps,
            favoritePackages
        ) { _, _ ->
            // Item clicked - handled by adapter
        }
        adapter.notifyDataSetChanged()
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter
    }

    private fun setupSearch() {
        editSearch.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                editSearch.clearFocus()
                true
            } else {
                false
            }
        }

        editSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                filterApps(s?.toString() ?: "")
            }
        })
    }

    private fun filterApps(query: String) {
        filteredApps.clear()
        if (query.isBlank()) {
            filteredApps.addAll(allApps)
        } else {
            val lowerQuery = query.lowercase()
            val pm = packageManager
            filteredApps.addAll(allApps.filter {
                it.loadLabel(pm).toString().lowercase().contains(lowerQuery) ||
                it.activityInfo.packageName.lowercase().contains(lowerQuery)
            })
        }
        adapter.notifyDataSetChanged()
    }

    private fun saveSelection() {
        val checked = adapter.getCheckedPackages()
        FavoritesManager.setFavoritePackages(this, checked)
    }
}



