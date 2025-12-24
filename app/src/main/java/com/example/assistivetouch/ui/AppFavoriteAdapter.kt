package com.example.assistivetouch.ui

import android.content.pm.ResolveInfo
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.assistivetouch.R
import com.google.android.material.checkbox.MaterialCheckBox

class AppFavoriteAdapter(
    private val apps: List<ResolveInfo>,
    private val favoritePackages: Set<String>,
    private val onItemClick: (ResolveInfo, Boolean) -> Unit
) : RecyclerView.Adapter<AppFavoriteAdapter.ViewHolder>() {

    private val checkedPackages = favoritePackages.toMutableSet()

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val icon: ImageView = itemView.findViewById(R.id.imageAppIcon)
        val name: TextView = itemView.findViewById(R.id.textAppName)
        val packageName: TextView = itemView.findViewById(R.id.textPackageName)
        val checkbox: MaterialCheckBox = itemView.findViewById(R.id.checkboxFavorite)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_app_favorite, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val app = apps[position]
        val pm = holder.itemView.context.packageManager
        val pkg = app.activityInfo.packageName

        holder.name.text = app.loadLabel(pm)
        holder.packageName.text = pkg
        holder.icon.setImageDrawable(app.loadIcon(pm))
        holder.checkbox.isChecked = checkedPackages.contains(pkg)

        holder.itemView.setOnClickListener {
            val isChecked = !holder.checkbox.isChecked
            holder.checkbox.isChecked = isChecked
            if (isChecked) {
                checkedPackages.add(pkg)
            } else {
                checkedPackages.remove(pkg)
            }
            onItemClick(app, isChecked)
        }
    }

    override fun getItemCount() = apps.size

    fun getCheckedPackages(): Set<String> = checkedPackages.toSet()
}

