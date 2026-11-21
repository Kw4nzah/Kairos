package com.kairos.app.logic

enum class ToneStyle { FORMAL, GENZ }

/** All user-facing copy of one tone. Templates use {name} and {title}. */
class ToneSet(
    val style: ToneStyle,
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

object Tone {
    const val FORMAL_MIN_AGE = 30

    fun styleFor(age: Int): ToneStyle = if (age >= FORMAL_MIN_AGE) ToneStyle.FORMAL else ToneStyle.GENZ

    fun forAge(age: Int): ToneSet = if (styleFor(age) == ToneStyle.FORMAL) FORMAL else GENZ

    private val FORMAL = ToneSet(
        style = ToneStyle.FORMAL,
        greetings = listOf(
            "Hoş geldiniz, {name}. Emrinize amadeyim, Efendim.",
            "İyi günler, {name}. Gündeminiz hazır, Efendim.",
            "Sizi tekrar görmek güzel, {name}. Emredin, Efendim."
        ),
        headsUpTitle = "Efendim, vakit yaklaşıyor",
        headsUpBodies = listOf(
            "Emriniz üzerine hatırlatıyorum: \"{title}\" için 10 dakika kaldı.",
            "\"{title}\" vaktine 10 dakika var, Efendim. Hazırlıklarınızı yapabilirsiniz.",
            "Bilgilerinize sunarım, {name}: \"{title}\" 10 dakika sonra."
        ),
        mainTitle = "Efendim, vakit geldi",
        mainBodies = listOf(
            "Emriniz üzerine hatırlatıyorum: \"{title}\" vakti şimdi.",
            "\"{title}\" zamanı geldi, {name}. Bilgilerinize sunarım.",
            "Efendim, \"{title}\" için beklenen an bu an."
        ),
        checkTitle = "Efendim, bir teyit rica ederim",
        checkBodies = listOf(
            "\"{title}\" işini tamamlayabildiniz mi, Efendim?",
            "\"{title}\" hakkında bilgi bekliyorum, {name}. Tamamlandı mı?"
        ),
        nagTitle = "Efendim, ısrarımı bağışlayın",
        nagBodies = listOf(
            "Üç erteleme geride kaldı, Efendim. \"{title}\" artık sonlandırılmalı.",
            "\"{title}\" hâlâ açık duruyor, {name}. Sonlandırmanızı rica ederim."
        ),
        emptyToday = "Bugün için bekleyen bir göreviniz yok, Efendim.",
        emptyUpcoming = "Yaklaşan bir göreviniz görünmüyor.",
        emptyDone = "Henüz tamamlanmış bir göreviniz bulunmuyor.",
        emptyDay = "Bu gün için kayıtlı bir göreviniz yok, Efendim.",
        snackAdded = "Emriniz üzerine kaydedildi.",
        snackUpdated = "Değişiklik uygulandı, Efendim.",
        snackDeleted = "Görev silindi, Efendim.",
        snackDone = "Tamamlandı olarak işaretlendi, Efendim.",
        snackReopened = "Görev yeniden açıldı, Efendim.",
        snackHardcoreOn = "Sert mod devrede, Efendim. Hatırlatmalar takvime de işlenecek.",
        snackHardcoreOff = "Sert mod kapatıldı. Takvim kayıtları kaldırıldı.",
        snackCalendarDenied = "Takvim izni verilmedi, Efendim. Sert mod etkinleştirilemedi.",
        snackProfileSaved = "Bilgileriniz güncellendi, Efendim.",
        permissionBanner = "Efendim, bazı izinler eksik. Hatırlatmaların aksamaması için Ayarlar'dan tamamlamanızı rica ederim."
    )

    private val GENZ = ToneSet(
        style = ToneStyle.GENZ,
        greetings = listOf(
            "Naber {name}, hoş geldin kanka.",
            "Yine mi buradasın {name}, iyi ki varsın bro.",
            "Knk {name}, bugün neler var bakalım, no cap."
        ),
        headsUpTitle = "Kanka, az kaldı",
        headsUpBodies = listOf(
            "\"{title}\" için 10 dk kaldı knk, hazırlan.",
            "Bro \"{title}\" 10 dk sonra, unutma yine.",
            "{name}, \"{title}\" yaklaşıyor, no cap 10 dk var."
        ),
        mainTitle = "Kanka, zamanı geldi",
        mainBodies = listOf(
            "\"{title}\" şimdi knk, hadi kalk.",
            "Bro \"{title}\" vakti geldi, yine mi unutuyon?",
            "{name}, \"{title}\" şimdi, no cap."
        ),
        checkTitle = "Knk, yaptın mı?",
        checkBodies = listOf(
            "\"{title}\" işini hallettin mi kanka?",
            "Yine mi unutuyon {name}? \"{title}\" yapıldı mı bro?"
        ),
        nagTitle = "Bro bu sefer ciddiyim",
        nagBodies = listOf(
            "3 kere erteledin kanka, \"{title}\" artık bitsin.",
            "\"{title}\" hâlâ yapılmadı {name}, no cap bitir şunu."
        ),
        emptyToday = "Bugün boşsun kanka, no cap.",
        emptyUpcoming = "Yaklaşan bir şey yok knk, rahatla.",
        emptyDone = "Henüz bitirdiğin bir şey yok bro.",
        emptyDay = "Bu gün boş knk, bi şey yok.",
        snackAdded = "Tamamdır kanka, kaydettim.",
        snackUpdated = "Güncelledim knk.",
        snackDeleted = "Sildim bro, gitti.",
        snackDone = "Helal knk, bitti.",
        snackReopened = "Tekrar açtım bro.",
        snackHardcoreOn = "Hardcore mod açık bro, takvime de yazıyorum no cap.",
        snackHardcoreOff = "Hardcore kapandı knk, takvim kayıtlarını sildim.",
        snackCalendarDenied = "Takvim iznini vermedin kanka, hardcore açılmadı.",
        snackProfileSaved = "Bilgilerini güncelledim kanka.",
        permissionBanner = "Kanka bazı izinler eksik, Ayarlar'dan ver yoksa yine unutuyon."
    )
}
