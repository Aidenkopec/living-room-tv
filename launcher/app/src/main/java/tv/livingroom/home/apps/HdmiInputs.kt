package tv.livingroom.home.apps

import android.content.Context
import android.content.Intent
import android.media.tv.TvContract
import android.media.tv.TvInputInfo
import android.media.tv.TvInputManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import tv.livingroom.home.TAG

/**
 * A device on an HDMI port that names itself over HDMI-CEC ("PlayStation 5").
 * [portLabel] is the port's own name ("HDMI 1"); [on] is null when the TV can't tell.
 */
data class HdmiInput(val id: String, val label: String, val portLabel: String?, val on: Boolean?)

/**
 * The devices plugged into the TV's HDMI ports that name themselves over
 * HDMI-CEC (today, the PlayStation 5). Android gives each one its own input
 * under its port's; ports with nothing that names itself are left out.
 */
fun loadHdmiInputs(context: Context): List<HdmiInput> = try {
    val manager = context.getSystemService(TvInputManager::class.java)
    val all = manager.tvInputList
    val byId = all.associateBy { it.id }
    all.filter { it.type == TvInputInfo.TYPE_HDMI && it.parentId != null }.map {
        HdmiInput(
            id = it.id,
            label = it.loadLabel(context).toString(),
            portLabel = byId[it.parentId]?.loadLabel(context)?.toString(),
            on = inputOn(manager.getInputState(it.id)),
        ).also { input -> Log.i(TAG, "Input ${input.label} on ${input.portLabel}: on=${input.on}") }
    }
} catch (e: Exception) {
    Log.w(TAG, "Could not read the TV's inputs", e)
    emptyList()
}

/** Switches to [input], the way the remote's Input button does; falls back to its port if the device's own input won't open. */
fun Context.openInput(input: HdmiInput) {
    if (start(Intent(Intent.ACTION_VIEW, TvContract.buildChannelUriForPassthroughInput(input.id)))) return
    val port = try {
        getSystemService(TvInputManager::class.java).getTvInputInfo(input.id)?.parentId
    } catch (ignored: Exception) {
        null
    }
    if (port != null) start(Intent(Intent.ACTION_VIEW, TvContract.buildChannelUriForPassthroughInput(port)))
}

/**
 * Whether a device is on, from its input's state. Tested on this TV: the PS5's input reads
 * "connected" (0) while on and "standby" (1) in standby. Null otherwise.
 */
internal fun inputOn(state: Int): Boolean? = when (state) {
    TvInputManager.INPUT_STATE_CONNECTED -> true
    TvInputManager.INPUT_STATE_CONNECTED_STANDBY -> false
    else -> null
}

/** The square tile's name for a device: "PS5" for "PlayStation 5", others as they call themselves. */
fun shortInputLabel(label: String): String = PLAYSTATION.matchEntire(label.trim())?.let { "PS${it.groupValues[1]}" } ?: label

/** Whether a device is a games console, so its tile gets the controller icon. */
fun isConsole(label: String): Boolean = CONSOLES.any { label.contains(it, ignoreCase = true) }

private val PLAYSTATION = Regex("""PlayStation ?(\d)""", RegexOption.IGNORE_CASE)
private val CONSOLES = listOf("PlayStation", "Xbox", "Nintendo")

/** Calls [onChange] whenever an input appears, goes, or turns on or off, until the returned function is called. */
fun Context.watchInputs(onChange: () -> Unit): () -> Unit {
    val manager = getSystemService(TvInputManager::class.java)
    val callback = object : TvInputManager.TvInputCallback() {
        override fun onInputStateChanged(inputId: String, state: Int) = onChange()
        override fun onInputAdded(inputId: String) = onChange()
        override fun onInputRemoved(inputId: String) = onChange()
        override fun onInputUpdated(inputId: String) = onChange()
    }
    manager.registerCallback(callback, Handler(Looper.getMainLooper()))
    return { manager.unregisterCallback(callback) }
}
