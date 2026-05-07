package com.android.assignment

import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import android.widget.Toast

@OptIn(ExperimentalStdlibApi::class)
abstract class BaseActivity : AppCompatActivity() {

    abstract val requiredPermissions: Array<String>
    abstract val permissionRationale: String

    private lateinit var permissionLauncher: ActivityResultLauncher<Array<String>>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        permissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { permissions ->
            val allGranted = permissions.entries.all { it.value }
            if (allGranted) {
                onPermissionsGranted()
            } else {
                onPermissionsDenied()
            }
        }
        requestPermissionsIfNecessary()
    }

    private fun requestPermissionsIfNecessary() {
        val permissionsToRequest = requiredPermissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }.toTypedArray()

        if (permissionsToRequest.isNotEmpty()) {
            if (shouldShowRequestPermissionRationale(permissionsToRequest[0])) {
                Toast.makeText(this, permissionRationale, Toast.LENGTH_LONG).show()
            }
            permissionLauncher.launch(permissionsToRequest)
        } else {
            onPermissionsGranted()
        }
    }

    open fun onPermissionsGranted() {
        // To be overridden by subclasses if needed
    }

    open fun onPermissionsDenied() {
        Toast.makeText(this, "Permissions denied. Please grant them in settings.", Toast.LENGTH_LONG).show()
    }

    fun navigateToFragment(fragment: Fragment, containerId: Int = R.id.fragment_container, addToBackStack: Boolean = true) {
        val transaction = supportFragmentManager.beginTransaction()
        transaction.replace(containerId, fragment)
        if (addToBackStack) {
            transaction.addToBackStack(fragment.javaClass.simpleName)
        }
        transaction.commit()
    }

    fun addFragment(fragment: Fragment, containerId: Int = R.id.fragment_container, addToBackStack: Boolean = true) {
        val transaction = supportFragmentManager.beginTransaction()
        transaction.add(containerId, fragment)
        if (addToBackStack) {
            transaction.addToBackStack(fragment.javaClass.simpleName)
        }
        transaction.commit()
    }

}