package com.wing.folderplayer.data.prefs

import android.content.Context
import android.content.SharedPreferences

/**
 * Manages screen orientation preferences.
 * Options: SYSTEM (follow system), PORTRAIT (force portrait), LANDSCAPE (force landscape)
 */
class OrientationPreferences(context: Context) {
    
    companion object {
        private const val PREFS_NAME = "orientation_prefs"
        private const val KEY_ORIENTATION = "screen_orientation"
        
        const val ORIENTATION_SYSTEM = "SYSTEM"
        const val ORIENTATION_PORTRAIT = "PORTRAIT"
        const val ORIENTATION_LANDSCAPE = "LANDSCAPE"
    }
    
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    
    fun getOrientation(): String {
        return prefs.getString(KEY_ORIENTATION, ORIENTATION_SYSTEM) ?: ORIENTATION_SYSTEM
    }
    
    fun setOrientation(orientation: String) {
        prefs.edit().putString(KEY_ORIENTATION, orientation).apply()
    }
}
