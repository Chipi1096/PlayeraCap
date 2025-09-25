package com.api.playeracap.data

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson

class ProductionInfoCache(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("production_info", Context.MODE_PRIVATE)
    private val gson = Gson()

    fun saveProductionInfo(info: ProductionInfo) {
        prefs.edit().putString("cached_info", gson.toJson(info)).apply()
    }

    fun getProductionInfo(): ProductionInfo? {
        val json = prefs.getString("cached_info", null)
        return if (json != null) {
            gson.fromJson(json, ProductionInfo::class.java)
        } else null
    }

    fun clear() {
        prefs.edit().remove("cached_info").apply()
    }
} 