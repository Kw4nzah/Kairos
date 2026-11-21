package com.kairos.app.system

import android.content.Context
import android.media.MediaPlayer
import android.net.Uri
import androidx.annotation.RawRes
import com.kairos.app.R

data class Sound(val key: String, val label: String, @RawRes val res: Int)

object Sounds {
    const val DEFAULT_KEY = "bell"

    val all: List<Sound> = listOf(
        Sound("ack", "Ack", R.raw.ack),
        Sound("amogus", "Amogus", R.raw.amogus),
        Sound("applepay", "Apple Pay", R.raw.applepay),
        Sound("bell", "Zil", R.raw.bell),
        Sound("bruh", "Bruh", R.raw.bruh),
        Sound("dexter", "Dexter", R.raw.dexter),
        Sound("duck", "Ördek", R.raw.duck),
        Sound("enrique", "Enrique", R.raw.enrique),
        Sound("heehee", "Hehe", R.raw.heehee),
        Sound("meow", "Miyav", R.raw.meow),
        Sound("metal_pipe", "Metal boru", R.raw.metal_pipe),
        Sound("prowler", "Prowler", R.raw.prowler),
        Sound("rizz", "Rizz", R.raw.rizz),
        Sound("saxophone", "Saksafon", R.raw.saxophone),
        Sound("spiderman", "Spiderman", R.raw.spiderman),
        Sound("spongebob", "Sünger Bob", R.raw.spongebob)
    )

    fun byKey(key: String): Sound =
        all.firstOrNull { it.key == key } ?: all.first { it.key == DEFAULT_KEY }

    /** Channel sound is immutable, so there is one channel per sound. */
    fun channelId(key: String): String = "kairos_${byKey(key).key}"

    fun uri(context: Context, sound: Sound): Uri =
        Uri.parse("android.resource://${context.packageName}/${sound.res}")
}

/** Tiny single-instance preview player for the sound pickers. */
object SoundPreview {
    private var player: MediaPlayer? = null

    fun play(context: Context, sound: Sound) {
        stop()
        player = MediaPlayer.create(context.applicationContext, sound.res)?.also { mp ->
            mp.setOnCompletionListener { stop() }
            mp.start()
        }
    }

    fun stop() {
        player?.release()
        player = null
    }
}
