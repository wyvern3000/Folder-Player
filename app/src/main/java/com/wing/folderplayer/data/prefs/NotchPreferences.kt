package com.wing.folderplayer.data.prefs

import android.content.Context
import android.content.SharedPreferences

/**
 * Manages notch (display cutout) display mode preferences.
 * Options: FULLSCREEN (content extends into cutout), BLACK_BAR (black bar hides cutout)
 */
class NotchPreferences(context: Context) {
    
    companion object {
        private const val PREFS_NAME = "notch_prefs"
        private const val KEY_NOTCH_MODE = "notch_mode"
        
        const val NOTCH_FULLSCREEN = "FULLSCREEN"
        const val NOTCH_BLACK_BAR = "BLACK_BAR"
    }
    
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    
    fun getNotchMode(): String {
        return prefs.getString(KEY_NOTCH_MODE, NOTCH_FULLSCREEN) ?: NOTCH_FULLSCREEN
    }
    
    fun setNotchMode(mode: String) {
        prefs.edit().putString(KEY_NOTCH_MODE, mode).apply()
    }
}
