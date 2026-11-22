package com.kairos.app.system

import android.content.Context
import android.media.MediaPlayer
import android.net.Uri
import androidx.annotation.RawRes
import androidx.annotation.StringRes
import com.kairos.app.R

data class Sound(val key: String, @StringRes val label: Int, @RawRes val res: Int) {
    fun name(ctx: Context): String = ctx.getString(label)
}

object Sounds {
    const val DEFAULT_KEY = "bell"

    val all: List<Sound> = listOf(
        Sound("ack", R.string.sound_ack, R.raw.ack),
        Sound("amogus", R.string.sound_amogus, R.raw.amogus),
        Sound("applepay", R.string.sound_applepay, R.raw.applepay),
        Sound("bell", R.string.sound_bell, R.raw.bell),
        Sound("bruh", R.string.sound_bruh, R.raw.bruh),
        Sound("dexter", R.string.sound_dexter, R.raw.dexter),
        Sound("duck", R.string.sound_duck, R.raw.duck),
        Sound("enrique", R.string.sound_enrique, R.raw.enrique),
        Sound("heehee", R.string.sound_heehee, R.raw.heehee),
        Sound("meow", R.string.sound_meow, R.raw.meow),
        Sound("metal_pipe", R.string.sound_metal_pipe, R.raw.metal_pipe),
        Sound("prowler", R.string.sound_prowler, R.raw.prowler),
        Sound("rizz", R.string.sound_rizz, R.raw.rizz),
        Sound("saxophone", R.string.sound_saxophone, R.raw.saxophone),
        Sound("spiderman", R.string.sound_spiderman, R.raw.spiderman),
        Sound("spongebob", R.string.sound_spongebob, R.raw.spongebob)
    )

    fun byKey(key: String): Sound =
        all.firstOrNull { it.key == key } ?: all.first { it.key == DEFAULT_KEY }

    /** Channel sound is immutable, so there is one channel per sound. */
    fun channelId(key: String): String = "kairos2_${byKey(key).key}"

    /** Channels made up to 1.4.2. They stored numeric resource ids, which moved between builds. */
    fun legacyChannelId(key: String): String = "kairos_${byKey(key).key}"

    /** By name, never by number: a channel keeps this URI forever and resource ids change between builds. */
    fun uri(context: Context, sound: Sound): Uri =
        Uri.parse("android.resource://${context.packageName}/raw/${context.resources.getResourceEntryName(sound.res)}")
}

/** Tiny single-instance preview player for the sound pickers. */
object SoundPreview {
    private var player: MediaPlayer? = null

    fun play(context: Context, sound: Sound) {
        stop()
        player = runCatching { MediaPlayer.create(context.applicationContext, sound.res) }.getOrNull()?.also { mp ->
            mp.setOnCompletionListener { stop() }
            mp.setOnErrorListener { _, _, _ -> stop(); true }
            runCatching { mp.start() }
        }
    }

    fun stop() {
        player?.release()
        player = null
    }
}
