package org.aprsdroid.app

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.InputFilter
import android.text.TextWatcher
import android.text.Editable
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.EditText

/**
 * Kotlin port of the Scala `PasscodeDialog`.
 *
 * Prompts the user for their callsign + APRS-IS passcode. Used both on
 * first run (with explanatory text) and from the preferences screen
 * (passcode entry only).
 *
 * Still uses the XML layout `firstrunview` for now; a future Compose
 * rewrite will replace this with a `Dialog` composable.
 */
class PasscodeDialog(
    private val act: Activity,
    private val firstrun: Boolean,
) : AlertDialog(act),
    DialogInterface.OnClickListener,
    DialogInterface.OnCancelListener,
    TextWatcher,
    View.OnFocusChangeListener {

    private val prefs = PrefsWrapper(act)

    private val inflater = act.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
    private val frView: View = inflater.inflate(R.layout.firstrunview, null, false)
    private val inputCall: EditText = frView.findViewById(R.id.callsign)
    private val inputPass: EditText = frView.findViewById(R.id.passcode)
    private var movedAwayFromCallsign = false

    init {
        setTitle(act.getString(if (firstrun) R.string.fr_title else R.string.p_passcode_entry))
        if (!firstrun) {
            frView.findViewById<View>(R.id.fr_text).visibility = View.GONE
            frView.findViewById<View>(R.id.fr_text2).visibility = View.GONE
        }
        setView(frView)
        setIcon(android.R.drawable.ic_dialog_info)

        inputCall.setText(prefs.getCallsign())
        inputCall.addTextChangedListener(this)
        inputCall.filters = arrayOf(InputFilter.AllCaps())
        inputCall.onFocusChangeListener = this
        inputPass.setText(prefs.getString("passcode", ""))
        inputPass.addTextChangedListener(this)

        setButton(DialogInterface.BUTTON_POSITIVE, act.getString(android.R.string.ok), this)
        setButton(DialogInterface.BUTTON_NEUTRAL, act.getString(R.string.p_passreq), this)
        setOnCancelListener(this)
    }

    private fun okButton() = getButton(DialogInterface.BUTTON_POSITIVE)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (inputCall.text.toString() == "") {
            okButton().isEnabled = false
        }
        if (!firstrun) {
            inputPass.requestFocus()
        }
    }

    // DialogInterface.OnClickListener
    override fun onClick(d: DialogInterface?, which: Int) {
        when (which) {
            DialogInterface.BUTTON_POSITIVE -> saveFirstRun(true)
            DialogInterface.BUTTON_NEUTRAL -> {
                saveFirstRun(false)
                act.startActivity(Intent(Intent.ACTION_VIEW,
                    Uri.parse(act.getString(R.string.passcode_url))))
            }
            else -> cancelled()
        }
    }

    // DialogInterface.OnCancelListener
    override fun onCancel(d: DialogInterface?) {
        cancelled()
    }

    // TextWatcher
    override fun afterTextChanged(s: Editable?) {
        verifyInput()
    }

    override fun beforeTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

    // OnFocusChangeListener
    override fun onFocusChange(v: View?, hasFocus: Boolean) {
        // only relevant for inputCall
        if (!hasFocus) {
            movedAwayFromCallsign = true
            verifyInput()
        }
    }

    private fun passOK(call: String, pass: String): Boolean {
        return if (pass != "") AprsPacket.passcodeAllowed(call, pass, true) else true
    }

    private fun verifyInput() {
        val call = inputCall.text.toString()
        val pass = inputPass.text.toString()
        val callError = if (call != "" || !movedAwayFromCallsign) null else act.getString(R.string.p_callsign_entry)
        val passError = if (passOK(call, pass)) null else act.getString(R.string.wrongpasscode)
        inputCall.error = callError
        inputPass.error = passError
        okButton().isEnabled = call != "" && callError == null && passError == null
    }

    private fun saveFirstRun(completed: Boolean) {
        val call = inputCall.text.toString()
        val passcode = inputPass.text.toString()
        val pe = prefs.prefs.edit()
        val parts = call.split("-")
        when (parts.size) {
            1 -> pe.putString("callsign", parts[0])
            2 -> {
                pe.putString("callsign", parts[0])
                pe.putString("ssid", parts[1])
            }
            else -> {
                Log.d("PasscodeDialog", "could not split callsign")
                act.finish()
                return
            }
        }
        if (passOK(call, passcode)) {
            pe.putString("passcode", passcode)
        }
        pe.putBoolean("firstrun", !completed)
        pe.commit()
    }

    private fun cancelled() {
        if (firstrun) {
            Log.d("PasscodeDialog", "closing parent activity")
            act.finish()
        }
    }
}
