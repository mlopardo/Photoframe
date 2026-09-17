package com.zambiotica.photoframe

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat

/**
 * Ajustes. Se cierra sola tras un rato sin uso y vuelve al marco: si alguien abre los
 * Ajustes y se va, el portarretrato no se queda para siempre en esta pantalla.
 */
class SettingsActivity : AppCompatActivity() {

    companion object {
        private const val AUTO_CLOSE_MS = 2 * 60 * 1000L
    }

    private val handler = Handler(Looper.getMainLooper())
    private val autoClose = Runnable { finish() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportFragmentManager.beginTransaction()
            .replace(android.R.id.content, SettingsFragment())
            .commit()
    }

    override fun onResume() {
        super.onResume()
        scheduleAutoClose()
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(autoClose)
    }

    override fun onUserInteraction() {
        super.onUserInteraction()
        scheduleAutoClose()
    }

    private fun scheduleAutoClose() {
        handler.removeCallbacks(autoClose)
        handler.postDelayed(autoClose, AUTO_CLOSE_MS)
    }
}

class SettingsFragment : PreferenceFragmentCompat() {

    private val pickFolder = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) {
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
            requireContext().contentResolver.takePersistableUriPermission(uri, flags)
            Prefs.setFolderUri(requireContext(), uri.toString())
            updateFolderSummary()
        }
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.prefs, rootKey)

        // El valor por defecto de Ken Burns depende del equipo, así que se fija acá.
        findPreference<androidx.preference.SwitchPreferenceCompat>(Prefs.KEY_KEN_BURNS)?.let {
            if (!preferenceManager.sharedPreferences!!.contains(Prefs.KEY_KEN_BURNS)) {
                it.isChecked = Prefs.defaultKenBurns(requireContext())
            }
        }

        findPreference<Preference>(Prefs.KEY_FOLDER_URI)?.setOnPreferenceClickListener {
            pickFolder.launch(null)
            true
        }
        updateFolderSummary()
    }

    private fun updateFolderSummary() {
        val preference = findPreference<Preference>(Prefs.KEY_FOLDER_URI) ?: return
        val stored = Prefs.folderUri(requireContext())
        val label = when {
            stored != null -> Uri.decode(stored.substringAfterLast('/'))
            else -> PhotoScanner.DEFAULT_DIR
        }
        preference.summary = getString(R.string.pref_folder_summary, label)
    }
}
