package com.zambiotica.photoframe

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat

class SettingsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportFragmentManager.beginTransaction()
            .replace(android.R.id.content, SettingsFragment())
            .commit()
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
