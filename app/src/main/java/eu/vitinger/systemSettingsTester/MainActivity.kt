package eu.vitinger.systemSettingsTester

import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import kotlinx.android.synthetic.main.activity_main.*
import kotlin.reflect.KProperty

/**
 *
 * It needs to have android.permission.WRITE_SETTINGS permission (in manifest and requested in runtime).
 *
 * Many settings needs to have this permission: https://developer.android.com/reference/android/Manifest.permission#WRITE_SECURE_SETTINGS
 * And this is not for 3rd party apps :-(
 */
class MainActivity : AppCompatActivity() {

    private val types = listOf(Int::class, String::class, Long::class, Float::class)

    private val keysAdapter: ArrayAdapter<String> by lazy {
        ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, getKeysFromSystemSettings())
    }

    private val typesAdapter: ArrayAdapter<String> by lazy {
        ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, types.map { it.simpleName })
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        keysAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinner_key.adapter = keysAdapter
        spinner_key.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {

            override fun onNothingSelected(parent: AdapterView<*>?) {
            }

            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                getValueFromSettings()
            }
        }
        spinner_type.adapter = typesAdapter

        btn_set.setOnClickListener { setValueToSettings() }
    }

    override fun onResume() {
        super.onResume()
        WriteSettingsPermissionHelper.checkAndAskPermissionIfNeeded(this)
    }

    private fun getValueFromSettings() {
        val valueFromSettings = Settings.Global.getString(contentResolver, getSelectedKey())
        txt_value.setText(valueFromSettings)
    }

    private fun setValueToSettings() {
        runCatching {
            val value = txt_value.text.toString()
            when (types[spinner_type.selectedItemPosition]) {
                Int::class -> Settings.Global.putInt(contentResolver, getSelectedKey(), value.toInt())
                String::class -> Settings.Global.putString(contentResolver, getSelectedKey(), value)
                Long::class -> Settings.Global.putLong(contentResolver, getSelectedKey(), value.toLong())
                Float::class -> Settings.Global.putFloat(contentResolver, getSelectedKey(), value.toFloat())
                else -> throw IllegalArgumentException("Unknown type selected ${spinner_type.selectedItem}")
            }
        }.onFailure { ex ->
            Log.e(localClassName, "set operation failed", ex)
            Toast.makeText(this, ex.toString(), Toast.LENGTH_LONG).show()
        }
    }

    private fun getSelectedKey(): String? {
        return (spinner_key.selectedItem as String?)?.let { constantName ->
            Settings.Global::class.members.first { it.name == constantName }.call() as String
        }
    }

    private fun getKeysFromSystemSettings(): List<String> {
        return Settings.Global::class.members
                .filterIsInstance<KProperty<String>>()
                .filter { it.isConst }
                .map { it.name }
                .also {
                    Toast.makeText(this, "Found ${it.size} keys", Toast.LENGTH_LONG).show()
                }
    }
}