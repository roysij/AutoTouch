# AutoTouch – Android typing & touch automation

Runs a simple macro script (tap, swipe, long-press, type, click-by-text…) inside any app,
controlled from a floating panel. Built on Android's Accessibility Service, which is the
only supported way for an app to touch and type into other apps without root.

## Build & install
1. Open the `AutoTouch` folder in Android Studio (Koala or newer). Let Gradle sync –
   it downloads Gradle 8.7, AGP 8.5 and Kotlin 1.9 automatically.
2. Plug in your phone (USB debugging on) and press ▶ Run. Min Android version: 8.0.

## First-time setup on the phone
1. Open AutoTouch → **Open Accessibility settings** → Installed apps / Downloaded apps →
   **AutoTouch automation** → turn ON.
   - Android 13+ shows "Restricted setting" for sideloaded apps. Fix: Settings ▸ Apps ▸
     AutoTouch ▸ ⋮ menu (top right) ▸ **Allow restricted settings**, then try again.
2. Back in AutoTouch, write your script → **Save & show floating panel**.
3. Open the target app, press **▶ Run** on the panel. Drag the panel by its title.

## Panel buttons
- **▶ Run / ■ Stop** – run the saved script (loops and step delay come from the main screen)
- **📍 Pick** – tap anywhere; adds `tap X Y` to the end of your script
- **✕** – hide the panel (also stops a running macro)

## Commands
See the help text at the bottom of the main screen, or `MacroParser.kt`.

## Notes
- Prefer `click TEXT` / `focus TEXT` over coordinates – they survive layout changes.
  `click` waits up to 3 s for the element to appear.
- `type` sets the field's text directly (instant, works with any keyboard). Use
  `typeslow` for apps that check each keystroke (search-as-you-type boxes).
- Keep the panel away from spots your script taps – a tap on the panel hits the panel.
- Games and some banking apps block accessibility input or draw without views;
  coordinates still work in most games, text-based commands won't.

## Automatic APK builds (GitHub Actions)
Every push to `main` builds the app on GitHub's servers. Get the APK from
**Releases ▸ AutoTouch (latest build) ▸ AutoTouch.apk** – you can open that link
straight from your phone. You can also start a build by hand from **Actions ▸ Build APK ▸ Run workflow**.
