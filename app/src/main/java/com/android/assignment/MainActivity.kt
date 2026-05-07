package com.android.assignment

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class MainActivity : BaseActivity() {
    override val requiredPermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        arrayOf(
            Manifest.permission.CAMERA
        )
    } else {
        arrayOf(
            Manifest.permission.CAMERA,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.READ_EXTERNAL_STORAGE
        )
    }

    override val permissionRationale = "Camera and storage permissions are required for palm and finger detection"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        supportActionBar?.hide()
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.fragment_container)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        if (savedInstanceState == null) {
            navigateToFragment(CameraFragment(), addToBackStack = false)
        }
    }

    override fun onPermissionsGranted() {
        super.onPermissionsGranted()
        if (supportFragmentManager.findFragmentById(R.id.fragment_container) == null) {
            navigateToFragment(CameraFragment(), addToBackStack = false)
        }
    }

    override fun onPermissionsDenied() {
        super.onPermissionsDenied()
        finish()
    }
}