package com.simplepdf.reader.utils

import android.app.Activity
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.os.Build

class LockScreenManager(private val activity: Activity) {
    
    private var isLocked = false
    
    fun enableLockMode() {
        isLocked = true
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            activity.startLockTask()
        }
    }
    
    fun disableLockMode() {
        isLocked = false
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            try {
                activity.stopLockTask()
            } catch (e: Exception) {
                // Handle exception if not in lock task mode
            }
        }
    }
    
    fun isLocked(): Boolean = isLocked
}
