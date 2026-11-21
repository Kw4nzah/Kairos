# Kairos

**English** | [Türkçe](README.tr.md) | [Русский](README.ru.md)

*An offline reminder app that won't let you ignore it.*

Kairos is an Android reminder app written in Kotlin with Jetpack Compose (Material 3). It does not just chime once: every reminder becomes a small escalating chain of sticky notifications, all in one clear, friendly tone. The app is available in English, Russian and Turkish.

## Status

- Built: the debug APK builds (`:app:assembleDebug`).
- Unit-tested: recurrence, the snooze state machine and tone variant selection (`LogicTest`).
- Emulator-tested: the notification chain (heads-up, main alert, check, snooze actions) has been run end to end on an Android emulator.
- **Not yet tested on a real device.** Exact alarms under real battery management, full-screen intents, sounds and calendar sync still need on-device verification.

## Features

- **One friendly tone.** A greeting on the task list, notification copy, empty states and snackbars all use the same clear, friendly voice (`logic/Tone.kt`, backed by string resources).
- **Reminder chain.** Each reminder fires three notifications around its time `T`: a heads-up at **T-10 min**, the main alert at **T**, and a "did you do it?" check at **T+5 min**.
- **Non-dismissable, aggressive notifications.** They are ongoing, not auto-cancelled, have no dismiss action, use a max-importance alarm channel with vibration, bypass Do Not Disturb and show on the lock screen. If one is swiped away, a delete intent re-posts it. The T and T+5 notifications also request a full-screen intent.
- **Snooze-only final notification.** The T+5 notification offers only **Snooze 5 min** and **Snooze 10 min**.
- **After three snoozes.** Only **"Snooze 10 more min"** or **"Done, don't notify again"** remain, and the copy becomes a bit firmer.
- **Per-reminder sounds.** 16 bundled sounds. Pick one per reminder, set a default in Settings, preview before saving. Each sound gets its own notification channel.
- **Repeats.** None, daily, weekly (chosen weekdays) or monthly (clamped to short months).
- **Hardcore mode.** Optional. Mirrors every reminder into a local "Kairos" calendar on the phone (alarm 10 min before and at the time), so it also fires through the system calendar. Needs calendar permission; turning it off removes the entries.
- **Tasks and calendar views.** Today / upcoming / done lists and a month calendar with day markers. Dates, month names, weekday names and the first day of the week follow the active language.
- **Theme choice.** Follow the system, or force light or dark, in Settings.
- **Languages.** English, Russian and Turkish. By default Kairos follows the system language; Settings has a language section (System / English / Türkçe / Русский) that applies instantly, using Android's per-app language support. Notifications and calendar entries use the chosen language too.
- **Fully offline.** No `INTERNET` permission, no accounts, no analytics. Data stays in a local database.

Permissions used: notifications, exact alarms, full-screen intent, boot completed (to reschedule), vibrate, and read/write calendar (Hardcore mode only).

## Screenshots

Captured on an Android emulator.

| Launcher icon | Onboarding | Tasks | Edit reminder | Calendar | Settings | Notification |
|---|---|---|---|---|---|---|
| ![Launcher icon](docs/device/00-launcher-icon.png) | ![Onboarding](docs/device/01-onboarding.png) | ![Tasks](docs/device/02-tasks.png) | ![Edit reminder](docs/device/03-edit.png) | ![Calendar](docs/device/04-calendar.png) | ![Settings](docs/device/05-settings.png) | ![Notification](docs/device/06-notification.png) |

## Build

Requirements: JDK 17, Android SDK (compileSdk 35, minSdk 26), Gradle 8.9. The repo has no Gradle wrapper, so use a local Gradle 8.9.

```sh
# point Gradle at the SDK (local.properties is git-ignored)
echo "sdk.dir=/path/to/Android/Sdk" > local.properties

gradle :app:testDebugUnitTest     # unit tests
gradle :app:assembleDebug         # app/build/outputs/apk/debug/*.apk
gradle :app:lintDebug             # lint (missing translations, hardcoded text, ...)
```

## Localization

All user-facing text lives in Android string resources: `app/src/main/res/values/strings.xml` (English, the default), `values-tr/` (Turkish) and `values-ru/` (Russian). Tone copy uses `%1$s` (name) and `%2$s` (task title) placeholders and string arrays for random variants. To add a language, add a `values-xx/strings.xml`, list it in `res/xml/locales_config.xml` and add it to `AppLanguage`.

## CI

GitHub Actions (`.github/workflows/android.yml`) runs on every push, pull request and manual dispatch: Temurin JDK 17, Android SDK, Gradle 8.9, then unit tests and the debug APK build. The APK is uploaded as the `kairos-debug-apk` artifact.

## License

MIT, see [LICENSE](LICENSE).
