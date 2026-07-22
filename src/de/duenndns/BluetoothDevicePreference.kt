package de.duenndns

import android.app.AlertDialog
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.Context
import android.preference.ListPreference
import android.util.AttributeSet

/**
 * Kotlin port of the Java `BluetoothDevicePreference`.
 *
 * A ListPreference that populates its entries with paired Bluetooth
 * devices at dialog-show time. Used by the XML preference screens for
 * Bluetooth TNC backend configuration.
 */
class BluetoothDevicePreference : ListPreference {

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context) : this(context, null)

    override fun onPrepareDialogBuilder(builder: AlertDialog.Builder) {
        // hook into the builder to refresh the list
        val bta = BluetoothAdapter.getDefaultAdapter()
        val pairedDevices: Set<BluetoothDevice>? = bta?.bondedDevices
        if (pairedDevices.isNullOrEmpty()) {
            super.onPrepareDialogBuilder(builder)
            return
        }

        val entries = arrayOfNulls<CharSequence>(pairedDevices.size)
        val entryValues = arrayOfNulls<CharSequence>(pairedDevices.size)
        var i = 0
        for (dev in pairedDevices) {
            if (dev.address != null) {
                entries[i] = dev.name ?: "(null)"
                entryValues[i] = dev.address
                i++
            }
        }
        setEntries(entries)
        setEntryValues(entryValues)

        super.onPrepareDialogBuilder(builder)
    }
}
