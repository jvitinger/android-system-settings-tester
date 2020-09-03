package eu.vitinger.systemSettingsTester

import android.content.Context
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import kotlinx.android.synthetic.main.activity_main.*
import kotlin.reflect.KClass
import kotlin.reflect.KProperty
import kotlin.reflect.full.functions

/**
 *
 * It needs to have android.permission.WRITE_SETTINGS permission (in manifest and requested in runtime).
 *
 * Many settings needs to have this permission: https://developer.android.com/reference/android/Manifest.permission#WRITE_SECURE_SETTINGS
 * And this is not for 3rd party apps :-(
 */
class MainActivity : AppCompatActivity() {

    private val types = listOf(Int::class, String::class, Long::class, Float::class)

    private val keysAdapter: KeysAdapter by lazy {
        KeysAdapter(this, getKeysFromSystemSettings())
    }

    private val typesAdapter: ArrayAdapter<String> by lazy {
        ArrayAdapter<String>(
            this,
            android.R.layout.simple_spinner_item,
            types.map { it.simpleName })
    }

    class KeysAdapter(context: Context, private val keyProperties: List<SettingProperty>) :
        ArrayAdapter<String>(context, android.R.layout.simple_spinner_item) {

        init {
            addAll(keyProperties.map { "${it.clazz.simpleName} - ${it.key.name}" })
        }

        fun getSettingsProperty(position: Int): SettingProperty = keyProperties[position]
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        keysAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinner_key.adapter = keysAdapter
        spinner_key.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {

            override fun onNothingSelected(parent: AdapterView<*>?) {
            }

            override fun onItemSelected(
                parent: AdapterView<*>?,
                view: View?,
                position: Int,
                id: Long
            ) {
                getValueFromSettings(getSelectedSettingsProperty())
            }
        }
        spinner_type.adapter = typesAdapter

        btn_set.setOnClickListener { setValueToSettings(getSelectedSettingsProperty()) }
    }

    override fun onResume() {
        super.onResume()
        WriteSettingsPermissionHelper.checkAndAskPermissionIfNeeded(this)
    }

    private fun getValueFromSettings(property: SettingProperty) {
        val valueFromSettings =
            findMethod(property, "getString").call(contentResolver, property.key.call()) as String?
        txt_value.setText(valueFromSettings)
    }

    private fun findMethod(property: SettingProperty, methodName: String) =
        property.clazz.functions.first { it.name == methodName }

    private fun setValueToSettings(property: SettingProperty) {
        runCatching {
            val value = txt_value.text.toString()
            when (types[spinner_type.selectedItemPosition]) {
                Int::class -> setValueToSettings(property, "putInt", value.toInt())
                String::class -> setValueToSettings(property, "putString", value)
                Long::class -> setValueToSettings(property, "putLong", value.toLong())
                Float::class -> setValueToSettings(property, "putFloat", value.toFloat())
                else -> throw IllegalArgumentException("Unknown type selected ${spinner_type.selectedItem}")
            }
        }.onFailure { ex ->
            Log.e(localClassName, "set operation failed", ex.cause)
            Toast.makeText(this, ex.cause.toString(), Toast.LENGTH_LONG).show()
        }
    }

    private fun setValueToSettings(
        property: SettingProperty,
        methodName: String,
        value: Any
    ) = findMethod(property, methodName).call(contentResolver, property.key.call(), value)

    private fun getSelectedSettingsProperty(): SettingProperty {
        return keysAdapter.getSettingsProperty(spinner_key.selectedItemPosition)
    }

    /**
     * [Settings.Global] should be preferred over [Settings.System] in case of duplicity in the constant name.
     */
    private fun getKeysFromSystemSettings(): List<SettingProperty> {
        return getKeysFromSettings(Settings.Global::class)
            .union(getKeysFromSettings(Settings.System::class))
            .toList()
            .sortedBy { it.key.name }
            .sortedBy { it.clazz.simpleName }
            .also {
                Toast.makeText(this, "Found ${it.size} keys", Toast.LENGTH_LONG).show()
            }
    }

    private fun getKeysFromSettings(clazz: KClass<out Settings.NameValueTable>): List<SettingProperty> {
        return clazz.members
            .filterIsInstance<KProperty<String>>()
            .filter { it.isConst }
            .map { SettingProperty(clazz, it) }
    }

    /**
     * Implementation of [equals] and [hashCode] should avoid duplicities in keys in the case they are defined in more than one classes.
     */
    class SettingProperty(
        val clazz: KClass<out Settings.NameValueTable>,
        val key: KProperty<String>
    ) {
        override fun equals(other: Any?): Boolean {
            return if (other is SettingProperty) {
                key.name == other.key.name
            } else false
        }

        override fun hashCode(): Int {
            return key.name.hashCode()
        }
    }
}