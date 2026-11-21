package com.kairos.app.logic

/** All user-facing copy of one tone. Templates use {name} and {title}. */
class ToneSet(
    private val greetings: List<String>,
    private val headsUpTitle: String,
    private val headsUpBodies: List<String>,
    private val mainTitle: String,
    private val mainBodies: List<String>,
    private val checkTitle: String,
    private val checkBodies: List<String>,
    private val nagTitle: String,
    private val nagBodies: List<String>,
    val emptyToday: String,
    val emptyUpcoming: String,
    val emptyDone: String,
    val emptyDay: String,
    val snackAdded: String,
    val snackUpdated: String,
    val snackDeleted: String,
    val snackDone: String,
    val snackReopened: String,
    val snackHardcoreOn: String,
    val snackHardcoreOff: String,
    val snackCalendarDenied: String,
    val snackProfileSaved: String,
    val permissionBanner: String
) {
    private fun fill(t: String, name: String, title: String) =
        t.replace("{name}", name).replace("{title}", title)

    private fun pick(list: List<String>, seed: Int) = list[Math.floorMod(seed, list.size)]

    fun allTexts(): List<String> = greetings + headsUpTitle + headsUpBodies + mainTitle + mainBodies +
        checkTitle + checkBodies + nagTitle + nagBodies + listOf(
        emptyToday, emptyUpcoming, emptyDone, emptyDay, snackAdded, snackUpdated, snackDeleted,
        snackDone, snackReopened, snackHardcoreOn, snackHardcoreOff, snackCalendarDenied,
        snackProfileSaved, permissionBanner
    )

    fun greeting(name: String, seed: Int = 0) = fill(pick(greetings, seed), name, "")
    fun headsUpTitle(name: String) = fill(headsUpTitle, name, "")
    fun headsUpBody(title: String, name: String, seed: Int) = fill(pick(headsUpBodies, seed), name, title)
    fun mainTitle(name: String) = fill(mainTitle, name, "")
    fun mainBody(title: String, name: String, seed: Int) = fill(pick(mainBodies, seed), name, title)
    fun checkTitle(name: String) = fill(checkTitle, name, "")
    fun checkBody(title: String, name: String, seed: Int) = fill(pick(checkBodies, seed), name, title)
    fun nagTitle(name: String) = fill(nagTitle, name, "")
    fun nagBody(title: String, name: String, seed: Int) = fill(pick(nagBodies, seed), name, title)
}

/** The single copy set: clear, friendly, no slang. */
object Tone {
    val set = ToneSet(
        greetings = listOf(
            "Merhaba {name}, bugün planın hazır.",
            "Tekrar hoş geldin {name}.",
            "{name}, bugün neler var bakalım."
        ),
        headsUpTitle = "Birazdan başlıyor",
        headsUpBodies = listOf(
            "\"{title}\" için 10 dakika kaldı.",
            "{name}, \"{title}\" 10 dakika sonra.",
            "Hazırlanma vakti: \"{title}\" 10 dakika sonra."
        ),
        mainTitle = "Şimdi zamanı",
        mainBodies = listOf(
            "\"{title}\" için vakit geldi.",
            "{name}, \"{title}\" şimdi.",
            "\"{title}\" zamanı."
        ),
        checkTitle = "Yaptın mı?",
        checkBodies = listOf(
            "\"{title}\" işini tamamladın mı?",
            "{name}, \"{title}\" tamamlandı mı?"
        ),
        nagTitle = "Bunu bitirelim",
        nagBodies = listOf(
            "\"{title}\" üç kez ertelendi. Şimdi bitirmeye ne dersin?",
            "{name}, \"{title}\" hâlâ açık. Tamamlayıp kapatalım."
        ),
        emptyToday = "Bugün için bekleyen görev yok.",
        emptyUpcoming = "Yaklaşan görev yok.",
        emptyDone = "Henüz tamamlanan görev yok.",
        emptyDay = "Bu gün için görev yok.",
        snackAdded = "Görev kaydedildi.",
        snackUpdated = "Görev güncellendi.",
        snackDeleted = "Görev silindi.",
        snackDone = "Tamamlandı olarak işaretlendi.",
        snackReopened = "Görev yeniden açıldı.",
        snackHardcoreOn = "Hardcore modu açık. Hatırlatmalar takvime de işlenecek.",
        snackHardcoreOff = "Hardcore modu kapatıldı. Takvim kayıtları kaldırıldı.",
        snackCalendarDenied = "Takvim izni verilmedi, Hardcore modu açılamadı.",
        snackProfileSaved = "Adın güncellendi.",
        permissionBanner = "Bazı izinler eksik. Hatırlatmaların aksamaması için Ayarlar'dan tamamla."
    )
}
