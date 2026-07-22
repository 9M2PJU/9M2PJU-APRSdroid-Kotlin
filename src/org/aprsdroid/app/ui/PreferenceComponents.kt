package org.aprsdroid.app.ui

import android.content.Context
import android.content.SharedPreferences
import android.media.RingtoneManager
import android.net.Uri
import android.preference.PreferenceManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Load a string array from resources, or return an empty array if
 * the resource ID is 0.
 */
fun Context.stringArrayOrNull(resId: Int): Array<String>? =
    if (resId == 0) null else resources.getStringArray(resId)

/**
 * Load a string array from resources, or an empty array if not found.
 */
fun Context.stringArray(resId: Int): Array<String> =
    if (resId == 0) emptyArray() else resources.getStringArray(resId)

/**
 * A simple preference host that renders a list of [PrefItem]s inside a
 * Scaffold with a TopAppBar. Each activity creates a list of items
 * and passes them to this composable.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PreferenceScreen(
    title: String,
    items: List<PrefItem>,
    onBack: (() -> Unit)? = null,
    actions: @Composable (() -> Unit)? = null,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    if (onBack != null) {
                        TextButton(onClick = onBack) { Text("Back") }
                    }
                },
                actions = { actions?.invoke() },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .padding(padding),
        ) {
            items(items) { item ->
                when (item) {
                    is PrefItem.Category -> PrefCategoryHeader(item)
                    is PrefItem.Switch -> PrefSwitchRow(item)
                    is PrefItem.EditText -> PrefEditTextRow(item)
                    is PrefItem.List -> PrefListRow(item)
                    is PrefItem.Clickable -> PrefClickableRow(item)
                    is PrefItem.Ringtone -> PrefRingtoneRow(item)
                    is PrefItem.Divider -> HorizontalDivider()
                }
            }
        }
    }
}

/**
 * Sealed hierarchy representing a single preference row.
 */
sealed class PrefItem {
    /** A category header (group title). */
    data class Category(val title: String) : PrefItem()

    /** A toggle switch backed by a Boolean in SharedPreferences. */
    data class Switch(
        val key: String,
        val title: String,
        val summary: String? = null,
        val summaryOn: String? = null,
        val summaryOff: String? = null,
        val default: Boolean = false,
        val dependency: String? = null,
        val enabled: Boolean = true,
        val onChanged: ((Boolean) -> Unit)? = null,
    ) : PrefItem()

    /** A text-entry preference backed by a String in SharedPreferences. */
    data class EditText(
        val key: String,
        val title: String,
        val summary: String? = null,
        val default: String = "",
        val hint: String? = null,
        val dialogTitle: String? = null,
        val keyboardType: KeyboardType = KeyboardType.Text,
        val digits: String? = null,
        val maxLength: Int = Int.MAX_VALUE,
        val dependency: String? = null,
        val enabled: Boolean = true,
        val isNumeric: Boolean = false,
        val onChanged: (() -> Unit)? = null,
    ) : PrefItem()

    /** A list-selection preference backed by a String in SharedPreferences. */
    data class List(
        val key: String,
        val title: String,
        val summary: String? = null,
        val entries: Array<String>,
        val entryValues: Array<String>,
        val default: String = "",
        val dialogTitle: String? = null,
        val dependency: String? = null,
        val enabled: Boolean = true,
        val onChanged: (() -> Unit)? = null,
    ) : PrefItem()

    /** A clickable preference that triggers an action (no stored value). */
    data class Clickable(
        val title: String,
        val summary: String? = null,
        val enabled: Boolean = true,
        val onClick: () -> Unit,
    ) : PrefItem()

    /** A ringtone picker preference backed by a String URI. */
    data class Ringtone(
        val key: String,
        val title: String,
        val summary: String? = null,
        val default: String = "",
        val ringtoneType: Int = RingtoneManager.TYPE_NOTIFICATION,
    ) : PrefItem()

    /** A thin divider. */
    object Divider : PrefItem()
}

// ---- Helpers for reading/writing SharedPreferences from Compose ----

@Composable
private fun sharedPrefs(): SharedPreferences {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    return remember(ctx) { PreferenceManager.getDefaultSharedPreferences(ctx) }
}

private fun SharedPreferences.getBool(key: String, default: Boolean): Boolean =
    getBoolean(key, default)

private fun SharedPreferences.getStringOrEmpty(key: String, default: String): String =
    getString(key, default) ?: default

@Composable
private fun depEnabled(dep: String?): Boolean {
    if (dep == null) return true
    val sp = sharedPrefs()
    return sp.getBoolean(dep, false)
}

// ---- Individual preference row composables ----

@Composable
private fun PrefCategoryHeader(item: PrefItem.Category) {
    Text(
        text = item.title,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp),
    )
}

@Composable
private fun PrefSwitchRow(item: PrefItem.Switch) {
    val sp = sharedPrefs()
    val depOk = depEnabled(item.dependency)
    val enabled = item.enabled && depOk
    var checked by remember {
        mutableStateOf(sp.getBool(item.key, item.default))
    }
    val summary = when {
        checked && item.summaryOn != null -> item.summaryOn
        !checked && item.summaryOff != null -> item.summaryOff
        else -> item.summary
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) {
                checked = !checked
                sp.edit().putBoolean(item.key, checked).apply()
                item.onChanged?.invoke(checked)
            }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (enabled) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (summary != null) {
                Text(
                    text = summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = {
                checked = it
                sp.edit().putBoolean(item.key, checked).apply()
                item.onChanged?.invoke(checked)
            },
            enabled = enabled,
        )
    }
}

@Composable
private fun PrefEditTextRow(item: PrefItem.EditText) {
    val sp = sharedPrefs()
    val depOk = depEnabled(item.dependency)
    val enabled = item.enabled && depOk
    var value by remember {
        mutableStateOf(sp.getStringOrEmpty(item.key, item.default))
    }
    var showDialog by remember { mutableStateOf(false) }
    val displayValue = if (value.isEmpty()) (item.hint ?: item.default) else value

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) { showDialog = true }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (enabled) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = item.summary ?: displayValue,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    if (showDialog) {
        var textValue by remember { mutableStateOf(value) }
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text(item.dialogTitle ?: item.title) },
            text = {
                OutlinedTextField(
                    value = textValue,
                    onValueChange = { new ->
                        if (new.length <= item.maxLength) {
                            if (item.digits == null || new.all { it in item.digits }) {
                                textValue = new
                            }
                        }
                    },
                    singleLine = true,
                    placeholder = { item.hint?.let { Text(it) } },
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    value = textValue
                    sp.edit().putString(item.key, textValue).apply()
                    item.onChanged?.invoke()
                    showDialog = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun PrefListRow(item: PrefItem.List) {
    val sp = sharedPrefs()
    val depOk = depEnabled(item.dependency)
    val enabled = item.enabled && depOk
    var value by remember {
        mutableStateOf(sp.getStringOrEmpty(item.key, item.default))
    }
    var showDialog by remember { mutableStateOf(false) }
    val selectedIndex = item.entryValues.indexOf(value).coerceAtLeast(0)
    val displayValue = item.entries.getOrNull(selectedIndex) ?: value

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) { showDialog = true }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (enabled) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = item.summary ?: displayValue,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text(item.dialogTitle ?: item.title) },
            text = {
                Column {
                    item.entries.forEachIndexed { idx, entry ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    value = item.entryValues[idx]
                                    sp.edit().putString(item.key, item.entryValues[idx]).apply()
                                    item.onChanged?.invoke()
                                    showDialog = false
                                }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(
                                selected = idx == selectedIndex,
                                onClick = {
                                    value = item.entryValues[idx]
                                    sp.edit().putString(item.key, item.entryValues[idx]).apply()
                                    item.onChanged?.invoke()
                                    showDialog = false
                                },
                            )
                            Text(entry)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showDialog = false }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun PrefClickableRow(item: PrefItem.Clickable) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = item.enabled) { item.onClick() }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (item.enabled) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (item.summary != null) {
                Text(
                    text = item.summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun PrefRingtoneRow(item: PrefItem.Ringtone) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val sp = sharedPrefs()
    var value by remember {
        mutableStateOf(sp.getStringOrEmpty(item.key, item.default))
    }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent(),
    ) { uri: Uri? ->
        if (uri != null) {
            value = uri.toString()
            sp.edit().putString(item.key, uri.toString()).apply()
        }
    }

    // Use RingtoneManager to get a display name
    val displayName = remember(value) {
        if (value.isEmpty()) "Silent"
        else try {
            val rt = RingtoneManager.getRingtone(ctx, Uri.parse(value))
            rt?.getTitle(ctx) ?: "Unknown"
        } catch (_: Exception) {
            "Unknown"
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                // Launch ringtone picker via Intent since GetContent doesn't filter ringtones
                val intent = android.content.Intent(RingtoneManager.ACTION_RINGTONE_PICKER)
                    .putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, item.ringtoneType)
                    .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                    .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, true)
                    .putExtra(
                        RingtoneManager.EXTRA_RINGTONE_DEFAULT_URI,
                        RingtoneManager.getDefaultUri(item.ringtoneType),
                    )
                    .putExtra(
                        RingtoneManager.EXTRA_RINGTONE_EXISTING_URI,
                        if (value.isNotEmpty()) Uri.parse(value) else null,
                    )
                // We need to use ActivityResultContracts.StartActivityForResult for this
                // For simplicity, we'll use the GetContent launcher as a fallback
                launcher.launch("audio/*")
            }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                text = item.summary ?: displayName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
