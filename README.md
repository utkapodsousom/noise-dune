# NoiseDune

Published in Russian as **Шум Дюн**, with the store title carrying a subtitle
for search. See [the name](docs/decisions.md#the-name).

An Android noise generator for settling infants. White, pink, brown and a
voiceless "shhh", synthesised in real time — no audio files, no network, no
accounts, no analytics. The app has no `INTERNET` permission at all.

## Features

- **Four voices**, each generated live: white (flat), pink (−3 dB/octave, via
  Voss-McCartney), brown (−6 dB/octave, via a leaky integrator) and a shush
  modelled on the fricative [ʃ] — a noise source through front-cavity resonances,
  no voicing oscillator.
- **Volume**, spaced evenly in decibels so the control feels linear to the ear,
  with an optional cap on by default.
- **10 second fade-in**, exponential so it sounds like a fade rather than
  switching on at the end. Toggleable, as is the fade-out before the timer stops.
- **Sleep timer** — 15 to 120 minutes, with a gentle fade-out and a "+15 min"
  button in the app and in the notification.
- **One screen, no scrolling.** A single circular control in the middle carries
  the voice name and what playback is doing; the four voices and the volume sit
  along the bottom; everything else lives in a bottom sheet.
- **Reachable with one thumb.** The sheet opens by tapping the line at the
  bottom. There is no gear in a corner: on a 914 dp screen a top corner is some
  855 dp above the bottom edge, well outside the roughly 300 dp arc a thumb
  sweeps, and whichever corner it took would be the far one for somebody. The
  other hand is holding a baby.
- **A drifting noise field** under the control — five dune silhouettes on a
  canvas, parallaxing against each other at between 25 and 70 seconds a cycle,
  slow enough that you notice it has moved rather than that it is moving.
- **Two small lines of text, and both earn their place.** The clock in the
  header answers how far into the night it is without leaving the app. The line
  over the field answers how loud it is and when it stops — a level, because
  the volume control is a bare track and knowing that 45% worked last night is
  how you get back to it, and a time of day rather than a countdown, because
  "still on when I come back?" is a different question from "how long is left",
  which the line under the control already answers. Both follow the system's
  12- or 24-hour setting.
- **Dark, dim theme**: true black, muted warm amber, no light mode at any time.
- **The app never dims the screen.** It does not override the window
  brightness, and it does not fade the interface out after a delay. How bright
  the screen is, and when it switches off, is left entirely to the system
  — the one place the user already controls it. There used to be a night mode
  that did both; it is gone, deliberately and for good.
- **Background playback** through a foreground service, so it survives a locked
  screen. Ducks under a navigation prompt, pauses for a phone call, and pauses
  when headphones are unplugged rather than blasting the room through the
  speaker.
- **Russian, and only Russian.** The strings live in `values/` with no second
  locale, and `locales_config.xml` declares that one language, so the system
  offers no per-app picker. Every phone gets Russian whatever it is set to. The
  app ships on Russian stores to people who read Russian; an English build would
  have been a second copy of every string to keep in step for nobody.

## Building

Requires JDK 17 or newer and an Android SDK with **platform 37** and
**build-tools 37.0.0**. Gradle 9.6 is fetched by the wrapper.

```bash
./gradlew :app:assembleDebug
```

If Gradle cannot find the SDK, point it at one:

```bash
echo "sdk.dir=$HOME/Android/Sdk" > local.properties
```

### Toolchain

| | Version | Note |
|---|---|---|
| AGP | 9.4.1 | Current AndroidX releases require 9.1 or newer |
| Gradle | 9.6.0 | AGP 9.4 refuses anything older |
| Kotlin | 2.3.21 | Pinned by the Compose plugin version; without it AGP 9.4.1 would use its bundled 2.2.10 |
| compileSdk | 37 | Required by `core-ktx` 1.19 and `compose-ui` 1.12 |
| targetSdk | 36 | What Play requires; 37's runtime behaviour changes are untested here |
| minSdk | 26 | `AudioTrack.Builder`, `AudioFocusRequest` and `PERFORMANCE_MODE_POWER_SAVING` all need 26 |

AGP 9 has Kotlin support built in, so there is deliberately no
`org.jetbrains.kotlin.android` plugin — adding it back makes the build fail.

## Verification

The DSP is where the quality of this app lives, and it is pure Kotlin with no
Android dependencies, so it is covered by ordinary JVM tests that measure the
real thing — spectral slope, A-weighted loudness, peak headroom, fade geometry:

```bash
./gradlew :app:testDebugUnitTest
```

41 tests, about half a second. They assert on numbers that were measured before
the code was written (see [docs/decisions.md](docs/decisions.md)) and they have
teeth — verified by mutation: cutting the Voss-McCartney row count from 16 to 3,
retuning the shush formant from 3 kHz to 6 kHz, and corrupting brown's
calibration constant each fail a different test with a legible message:

```
pink slope over 20.0-200.0 Hz expected:<-3.0> but was:<0.17070922173646183>
shush peak at 4695.060701207916 Hz should be within 2000-4500 Hz
PINK should sit on the calibration target expected:<-29.5> but was:<-18.65>
```

### What the tests cover

| Property | Expected | Tolerance |
|---|---|---|
| White slope, 100 Hz – 10 kHz | 0 dB/octave | ±0.5 |
| Pink slope, in each of three bands | −3 dB/octave | ±0.6 |
| Brown slope, 100 Hz – 10 kHz | −6 dB/octave | ±0.6 |
| Shush spectral peak | 2–4.5 kHz | — |
| Shush energy below 500 Hz | ≥20 dB down | — |
| A-weighted level, every voice | −29.5 dBFS | ±0.6 |
| Peak after calibration | below the 0.9 soft-clip knee | — |
| DC offset, every voice | 0 | ±1e−3 |
| 10 s fade-in duration | exactly 480 000 frames | exact |
| Fade step size | inaudible | <0.001 dB/sample |
| Fade-out endpoint | exactly zero | exact |
| Tuning defaults vs the shipped constants | identical | exact |
| Retuned filters vs freshly built ones | same spectrum | ±1 dB |

### What the tests cannot cover

The service, the notification, audio focus and the Compose UI need a device.
After installing, check by hand:

- the fade-in is audibly gradual over its whole 10 seconds, with no late jump;
- switching voice does not click, and does not change apparent loudness;
- minimising the app and locking the screen does not interrupt playback;
- the notification's pause, "+15 min" and stop actions work;
- a 1-minute timer fades out smoothly and stops completely;
- unplugging headphones pauses rather than switching to the speaker;
- an incoming call pauses, and playback resumes afterwards;
- the screen does not scroll at any density or font scale, and the bottom row
  survives a short screen — the control shrinks before anything is pushed off;
- **in landscape**, where the screen is less than half as tall and the control
  shrinks to around 110 dp, the voice name still fits inside it. Check the long
  Russian one, "Коричневый шум": the type scales with the circle, but this is
  where it runs out of room first;
- **at a 2x font scale**, all four voice labels are still whole — "КОРИЧН." in
  particular, which is the one that runs out of width. The pills auto-size their
  type down to fit rather than clipping it, so the failure to look for is a word
  cut short, not a row that has visibly changed size:

  ```bash
  adb shell settings put system font_scale 2.0
  ```

  Put it back to `1.0` afterwards;
- **at the same scale, with the safety notice showing** — `adb shell pm clear`
  brings it back — the orb is still there and still readable, the notice has
  stopped growing at about four tenths of the screen, and its text scrolls inside
  it while "Понятно" stays put. The failure this replaced was the notice taking
  the whole column and leaving the orb nothing, so what to look for is the orb,
  not the notice;
- the metadata line stays readable over the brightest part of the field;
- the stop time equals the clock plus whatever the countdown under the control
  says, and both it and the header clock follow
  `adb shell settings put system time_12_24 12`.

One of those is measurable rather than eyeballed. The app must never touch the
window brightness:

```bash
adb shell dumpsys power | grep -oE "mScreenBrightnessOverrideFromWindowManager=[-0-9.]+"
```

**`-1` always** — at launch, while playing, on pause. Anything else means a
brightness override has come back. It used to read about `5` while playing, the
0.02 the app asked for scaled into the 0–255 the service keeps it in; that is
the behaviour this check now exists to catch, not to confirm.

Ask `dumpsys power`, not `dumpsys window`. The window dump prints the request as
`sbrt=` only on recent Android; on 10 no window prints it at all, so grepping
for `sbrt` there reports a failure on every older phone whether or not anything
is wrong.

Then leave it running overnight. This is the real risk for an app like this, and
it is not something a unit test can reach:

```bash
adb shell dumpsys media.audio_flinger | grep -iE "underrun"
```

```bash
adb shell dumpsys batterystats --charged ru.noisedune | head -40
```

Expect zero underruns across eight hours and roughly 2–4 % battery. If underruns
appear, raise `BUFFER_SECONDS` in `NoiseEngine`; only if that fails is a
`PARTIAL_WAKE_LOCK` justified.

Neither command is guaranteed to answer. Not every vendor publishes the underrun
counters — EMUI's `media.audio_flinger` dump has no `underrun` line anywhere in
it, so on those phones this check simply cannot be run. And `batterystats` is
meaningless while the phone is charging, which it is whenever adb is on the
cable; measuring overnight means `adb tcpip 5555`, reconnecting over Wi-Fi, and
unplugging.

**This figure is the open question, and removing night mode is why.** The noise
field lights roughly 43 % of the screen and now drifts for as long as the screen
is on, where the veil used to freeze it — along with the orb and the presence
dot — after 12 seconds. That was the app's whole answer to an OLED running all
night, and it is gone.

In practice the system's own display timeout now does that job: once the screen
switches off, nothing is drawn at all, which is a better outcome than a veil
over a lit panel. The cost falls on whoever runs with the screen deliberately
kept on — the "keep screen on" setting, or a charger on a bedside table — and
has not been measured since the change. If an overnight run shows the drain, the
fix is to stop the animations on a timer *without* darkening anything: the
screen staying at its own brightness was the point, a static field is not a dim
one, and `NoiseField`, `NoiseOrb` and `PresenceDot` are each a single flag away
from being able to hold still.

## Tuning the voices by ear

Every DSP constant in this app was chosen by measurement, and the tests above
defend those measurements. But a periodogram does not answer the question the
app is actually for: whether a voice is pleasant to fall asleep to, out of a
phone speaker, across a dark room. The only instrument for that is an ear, and
an ear needs the parameter to move while it is listening.

So debug builds carry a tuning panel. It is **not in the release APK** — every
reference to it sits inside `if (BuildConfig.DEBUG)`, and `proguard-rules.pro`
states that assumption with `-assumevalues` so R8's removal is a guarantee
rather than an optimisation that happens to fire. The debug build also takes
`applicationIdSuffix = ".dev"`, so it installs alongside the real app, under its
own name, with its own DataStore — a night of experiments cannot reach the
settings of the app in use.

```bash
./gradlew :app:installDebug
```

Open it with **seven taps on the header clock inside three seconds**. A haptic
tick confirms it. That element because nothing up there is a control, so nothing
else listens to it, and because it sits some 850 dp above the bottom edge, well
outside the arc a thumb sweeps — it cannot be hit while reaching for anything
real.

Inside: every parameter of every voice on a slider, a per-voice trim in
decibels, and a **Factory values** switch that A/Bs the shipped constants
against the current edits instantly. Use the switch constantly; the ear is far
better at catching a difference than at remembering one. Frequencies and Q
values are on logarithmic sliders, because pitch is logarithmic and Q is a
ratio. Edits persist across restarts, so a setting can be slept on.

Moving a parameter does not rebuild anything. The filters re-point their
coefficients and keep their state, which is why a slider sweeps rather than
gates — the same reason a synthesiser's cutoff knob sounds the way it does. The
one exception is pink's row count, which sizes the generator's bank and so has
to be rebuilt behind a 20 ms crossfade.

### Getting the numbers back into the source

**Copy Kotlin** puts a paste-ready block on the clipboard: the `Params` default
arguments in the shape the source already has, each changed line annotated with
what it was, and the `NoiseType` constants with each trim multiplied into the
measured calibration. **Log** writes the same thing to
`adb logcat -s NoiseTuning`.

Paste it over the defaults and run the tests. **Expect them to fail** if a slope
or a level moved — that is the point:

```bash
./gradlew :app:testDebugUnitTest
```

Read the new measured values out of the failure messages, then update the
expectations, the KDoc on each number, and the tables in
[docs/decisions.md](docs/decisions.md). The tests are a ratchet: do not widen a
tolerance to make a by-ear number pass. If a value sounds right and measures
wrong, that disagreement is the interesting result and belongs in the decision
log, not under a looser bound.

One caveat on trusting the dev build: it runs unminified and debuggable, so its
render thread is slower than the real thing. Numbers settled by ear there are
worth re-hearing once on a release build before they are believed.

## Layout

```
app/src/main/java/ru/noisedune/
  dsp/        generators, filters, gain ramps, the mixer — pure Kotlin, JVM-testable
  audio/      AudioTrack ownership, the render thread, audio focus
  service/    foreground service, notification, sleep timer
  playback/   settings and the state bridge between UI and service
  data/       DataStore persistence
  ui/         the one screen, the dark theme
  ui/components/
              the orb, the dune field, the metadata line, the settings sheet
  ui/dev/     the tuning panel — debug builds only, stripped from release
app/src/test/java/ru/noisedune/dsp/
              spectrum measurement harness and the DSP tests
```

The interface was drawn in [Google Stitch](https://stitch.withgoogle.com) and
then rewritten in Compose; the prompts, the design system it generated and the
constants behind the dune field are in
[docs/stitch-prompts.md](docs/stitch-prompts.md).

## Hearing safety

The reference figure is the Russian sanitary limit for a room slept in at night:
**30 dBA equivalent, 45 dBA maximum**, from a table whose row covers apartment
living rooms and the sleeping quarters of preschools and boarding schools alike.
It is attributed by document number in [NOTICE.md](NOTICE.md). That is well below
the 50 dBA this app used to quote from American paediatric guidance, so the
advice got stricter, not looser, when it was localised.

A 2014 *Pediatrics* study measured fourteen infant sleep machines and found every
one of them above 50 dBA at 30 cm, so a sound machine being too loud is the
default, not the exception. That is a measurement rather than anybody's
recommendation, and it is why the cap exists.

The app shows this once on first run and keeps a volume cap on by default, but it
cannot measure the actual sound pressure — that depends on the speaker, the
system volume and the distance. Treat the cap as a guard rail, not a guarantee.
The in-app wording carries the number and a household anchor for it — a whisper
rather than a conversation — and no regulation number; see
[docs/decisions.md](docs/decisions.md) for why, and for what the norm does and
does not actually govern.

## Licence and attribution

The DSP algorithms are borrowed from public-domain sources; see
[NOTICE.md](NOTICE.md). Design rationale and the measurements behind every
constant are in [docs/decisions.md](docs/decisions.md).
