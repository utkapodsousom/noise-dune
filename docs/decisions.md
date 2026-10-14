# Design decisions

Notes on the choices that are not obvious from the code, and the measurements
behind them. Everything quoted here was measured with a Welch periodogram
(16384-point blocks, Hann window) at 48 kHz; the figures are reproduced by
`app/src/test/java/ru/noisedune/dsp/`.

## Why there is no audio library

Three candidates were evaluated:

| Option | Licence | Verdict |
|---|---|---|
| [JSyn](https://github.com/philburk/jsyn) | Apache-2.0 | Has exactly the right unit generators — `WhiteNoise`, `PinkNoise`, `BrownNoise`, `FilterBandPass`, `LinearRamp`. But its Android backend is a separate source file marked beta since 2017, and the jar carries `javax.sound` classes that R8 has to be told to ignore. |
| [Soundpipe](https://github.com/PaulBatchelor/Soundpipe) | MIT | `pinknoise`, `brown` and Butterworth filters in C, but needs NDK and JNI. |
| [Oboe](https://developer.android.com/games/sdk/oboe) | Apache-2.0 | Transport only; the DSP would still be ours. Its reason to exist is low latency, which an all-night noise generator does not need. |

Four generators come to about 250 lines. Written directly against `AudioTrack`,
they carry no Android dependency at all, which is what makes the spectral tests
plain JVM tests that run in half a second. The algorithms are still borrowed —
see `NOTICE.md` — just not the packaging.

## The toolchain is not freely chosen

Worth recording because the obvious instinct is wrong. Picking AGP 8.13.2 —
the last 8.x, on the grounds that its DSL is well understood — does not build:
every current AndroidX release (`core-ktx` 1.19, `compose-ui` 1.12) now demands
AGP 9.1 or newer and `compileSdk` 37. So the chain is forced: AGP 9.4.1 →
Gradle 9.6 → `compileSdk` 37.

Two consequences that are easy to trip over:

- AGP 9 has Kotlin support built in and **rejects** the
  `org.jetbrains.kotlin.android` plugin outright. Only the Compose plugin
  remains, and its version is what pins the Kotlin toolchain — drop it and the
  build silently uses the 2.2.10 that AGP 9.4.1 bundles instead of 2.3.21.
- `resourceConfigurations` is deprecated in AGP 8 and gone in AGP 9. It was only
  trimming unused locales, so it was removed rather than migrated.

## Pink: Voss-McCartney, not a pinking filter

Both were implemented and measured. Both hit -3 dB/octave (Voss-McCartney
-3.09, Kellett's seven one-poles -3.04 over 100 Hz - 10 kHz).

Voss-McCartney won because its structure is defined in *samples* — one row
updated per power-of-two counter tick — so the slope is identical at 44.1 and
48 kHz. Kellett's coefficients are fitted for 44.1 kHz and drift elsewhere.

Measured sub-20 Hz content was 32 % of total power, appearing as a DC offset of
-5.7e-3 over 83 seconds. Inaudible, but it costs peak headroom and moves the
speaker cone for nothing. A DC blocker at 8 Hz drops the offset to -5.7e-6 and
leaves the slope untouched in every octave band. At 16 Hz the bottom octave
starts to bend (-3.01), at 30 Hz it is clearly off (-2.85), so 8 Hz it is.

## Brown sets the maximum loudness of the whole app

Brown is the limiting voice, and not for the reason you would guess. Its energy
is overwhelmingly subsonic, so its *peaks* are set by frequencies nobody can hear
and no phone speaker can reproduce — yet those peaks decide how much gain the
chain can take before clipping.

Measured, as the loudest clip-free A-weighted level the voice can reach:

| Integrator | High pass | Max A-weighted level | Power below 30 Hz |
|---|---|---|---|
| 18 Hz | 8 Hz, 1st order | -33.4 dBFS | 50 % |
| 18 Hz | 30 Hz, 1st order | -30.5 dBFS | 26 % |
| 30 Hz | 30 Hz, 2nd order | **-29.4 dBFS** | 11 % |
| 30 Hz | 60 Hz, 2nd order | -28.8 dBFS | 2 % |

Moving to a second-order high pass at 30 Hz buys 4 dB of real loudness from
inaudible frequencies. Going further to 60 Hz buys only 0.6 dB more and starts
thinning the rumble that is the point of brown noise, so the trade stops there.

This is what fixes the overall target at **-29.5 dBFS(A)**: the loudest level
every voice can reach while the worst of them keeps 3 dB of peak headroom.

## Loudness is matched A-weighted, not by RMS

At equal RMS the four voices differ by 10-20 dB in perceived loudness, because
white and shush put their energy where the ear is most sensitive and brown does
not. Equalising RMS would mean a jolt every time the user changed type at 2 a.m.

So each voice carries a gain that equalises its A-weighted level. The constants
in `NoiseType` were solved from the measurement, and verified against the
implementation over five seeds with a 96-block periodogram — every voice lands
within 0.1 dB of target:

| Voice | Raw A-weighted | Calibration | Peak after calibration |
|---|---|---|---|
| White | -10.50 dBFS | 0.112214 | 0.112 |
| Pink | -25.57 dBFS | 0.635854 | 0.354 |
| Brown | -47.83 dBFS | 8.243407 | 0.760 |
| Shush | -13.78 dBFS | 0.165371 | 0.222 |

Measured at 48 kHz. At 44.1 kHz every constant shifts by under 0.4 dB and all in
the same direction, so the balance between voices survives and one set of numbers
serves both rates.

Raw generators are *not* bounded to ±1: shush peaks near 1.17 before
calibration. Bounding is the mixer's job, which is why `NoiseSource` does not
promise a range.

## Shush is tuned to [ʃ], not to [s]

The spectral peak of a voiceless postalveolar fricative sits at 2.5-3.5 kHz,
set by the resonance of the cavity in front of the constriction. Alveolar [s] is
higher, 4-7 kHz. Tune too high and a shush becomes a hiss.

Four designs were measured. High-Q filters (Q 3-4) gave a narrow peak, -3 dB
across only 2216-4183 Hz, which reads as a whistle. The shipped design uses
Q 1.1 and 2.0 in parallel: peak at 3.0 kHz, -3 dB from 1974 to 5270 Hz, 42 dB
down below 500 Hz, 12.7 dB down above 10 kHz — a broad hump, which is what a
real shush is.

Note that "spectral slope" is a meaningless statistic for this voice: fitting a
line across 100 Hz - 10 kHz returns +9.8 dB/octave, which is just the rising
skirt of a band-pass, not a defect. The tests assert on peak position and band
balance instead.

## Fades are exponential, i.e. linear in decibels

A 10 second fade-in that is linear in amplitude spends seven seconds inaudible
and then appears to switch on, because half amplitude is only 6 dB down. Stepping
by a constant factor per sample gives a constant number of decibels per second —
one multiply, and it sounds like a fade. Measured step size over a 10 s fade:
0.000125 dB per sample.

True zero has no decibel value, so fades run between -45 dB and the target and
snap the final step, which is inaudible.

## Playback choices

- **Mono**, not stereo. Two decorrelated channels of noise are
  indistinguishable on a phone speaker, and mono halves the generator work.
- **200 ms buffer** with `PERFORMANCE_MODE_POWER_SAVING`. The opposite of what a
  synth wants, exactly right here: latency is irrelevant, and one underrun at
  3 a.m. is an audible click in a silent room.
- **Native sample rate** from `PROPERTY_OUTPUT_SAMPLE_RATE`, so the framework
  never resamples all night for nothing.
- **A plain notification, not a MediaSession.** Costs the lock-screen transport
  row and headset buttons; saves implementing a `Player` for a generator with no
  track list. Reconsider if headset control is wanted.
- **A process-wide `NoiseController` instead of a bound service.** The activity
  and the service share a process, so a `StateFlow` carries settings down and
  status up with none of the binder lifecycle. Dragging the volume slider must
  not post an Intent per frame or wait on a DataStore write.
- **No wake lock.** An active `AudioTrack` already holds one at the audio HAL
  level. Add one only if an overnight run shows underruns — see the verification
  notes in `README.md`.

## Volume cap

On by default, holding the top of the slider 6 dB lower. It is a guard rail, not
a measurement: the app cannot see the speaker, the system volume or the distance
to the cot, and the UI says so rather than implying a dBA figure it does not
know. The 2014 *Pediatrics* study found all fourteen machines it measured were
above 50 dBA at 30 cm, so a sound machine's default behaviour is to be too loud —
and the target it was too loud for, 50 dBA, is itself 20 dB above the Russian
night-time limit the notice now quotes. The cap is the only lever the app has
over any of that, and 6 dB of it.

## The hearing-safety notice, and where its number comes from

The notice used to quote American paediatric guidance: 50 dBA at the cot, device
two metres away. For an app shipping on Russian stores that is a reference to a
body the reader cannot check against anything that applies where they live, so
it was replaced with the domestic norm — which turned out to be both more
specific and considerably stricter.

**СанПиН 1.2.3685-21**, approved by order of the Chief State Sanitary Doctor of
the Russian Federation No. 2 of 28 January 2021, table 5.35. Its fifth row reads
«Жилые комнаты квартир … спальные помещения в школах-интернатах, дошкольных
образовательных организациях …» — apartment living rooms and the sleeping
quarters of preschools in the same breath, which is as close as Russian
regulation comes to naming a room where a small child sleeps. The limits there
are 40 dBA equivalent and 55 dBA maximum by day, **30 and 45 by night** (23:00
to 07:00). The full citation lives in `NOTICE.md`.

Three things are worth recording about that choice, because none of them is
visible from the number alone.

**There is no Ministry of Health guidance to use instead.** Минздрав has clinical
guidelines on noise-induced hearing loss — occupational, adult — and on
sensorineural hearing loss in children, and neither gives a bedroom figure or
mentions sound machines at all. The norm above is Роспотребнадзор's, not
Минздрав's, and saying otherwise would be wrong. Russian consumer sources that
look like an alternative (baby-sleep.ru, AmaroBaby, Фрутоняня) all quote 50–65 dB
at 1–1.5 m, which is the American figure relabelled; citing them would have
laundered the source rather than replaced it.

**The norm governs penetrating noise, not deliberate sound.** Table 5.35 is about
проникающий шум, and note 106 excludes noise from residents' own activity
outright. Legally it does not bind a parent who switches a noise generator on.
What it is good for is the question actually being asked — how quiet a room ought
to be at night in this country — so the copy says the room «должно быть не
громче», framing it as the benchmark it is rather than a rule about the app.

**Only the 30 appears in the interface, and no document number.** The 45 dBA
maximum is omitted because this signal is continuous broadband noise, for which
the equivalent and maximum levels coincide; a maximum governs transients and
there are none here, so printing it would have added a figure that cannot differ
from the other one. The regulation number is omitted because a card read at three
in the morning cannot be audited, only obeyed or ignored — what replaces it is
«это шёпот, а не разговор», which a parent can check by ear with no meter. The
number is in `NOTICE.md` for anyone who does want to audit it.

## The tuning panel, and what it does to the constants

Every number above was chosen by measurement. The panel in `ui/dev/` exists
because measurement does not settle the question the app is for: whether a voice
is pleasant to fall asleep to, out of a phone speaker, across a dark room. That
needs an ear, and an ear needs the parameter to move while it is listening. See
`README.md` for how to use it; this is why it is built the way it is.

**The constants did not move — they became default arguments.** Each voice's
`private companion object` is now a nested `data class Params` whose default
values are the same numbers, with the same KDoc explaining the same
measurements. So `NoiseTuning.FACTORY` is the shipped sound exactly, and every
spectral test keeps passing untouched. `NoiseTuningTest` compares those defaults
against literals, rather than reading them from the classes under test, so a
typo in a default cannot change what ships while the tests that read the same
default go on agreeing with it.

**Parameters are applied in place, not by rebuilding.** `OnePole`, `DcBlocker`
and `Biquad` re-point their coefficients and keep `x1/x2/y1/y2`. This was the
load-bearing decision: the obvious design — rebuild the generator, crossfade
0.15 s — collapses under a dragged slider, because at sixty events a second each
crossfade restarts before the last one lands and the voice sits near silence for
the whole gesture. Keeping filter state makes a parameter change phase-
continuous and free: no allocation, no crossfade, nothing to hear. The cost is a
non-final field read per sample, invisible beside the multiplies around it.

The limit is honest rather than hidden: a large coefficient jump leaves state
that no longer matches the filter, and a high-Q biquad can ring. At the Q values
these voices use and with the panel's throttled, quantised steps it is
inaudible.

**Pink's row count is the one exception.** It sizes the generator's bank,
derives the gain that goes with it and seeds the running sum, so it has to be
rebuilt — behind a 20 ms crossfade rather than the 150 ms a voice change gets,
because there is no timbre to hand over, only a fresh filter's state
discontinuity to hide. 20 ms sits below the 50 ms that already governs the level
ramp and above the ~10 ms at which the dip stops hiding anything and becomes a
tick itself.

**Generators are built on the calling thread.** `NoiseMixer.setVoice` decides
between rebuild and retune on the main thread and ships a finished `NoiseSource`
inside the queued command; the audio thread only assigns a reference. The audio
thread must not allocate — a GC pause on a `THREAD_PRIORITY_URGENT_AUDIO` thread
is an audible gap, which is the whole thing this codebase is arranged to avoid.
This adds one invariant worth stating: `prepare`, `setType` and `setVoice` must
be called from a single thread. `setLevel`, `setDucked` and the fades are
unaffected, which matters because `setDucked` arrives from the audio focus
listener.

**`NoiseType.calibration` was left alone.** It is a measurement with a test that
owns it, so the panel adds a per-voice decibel trim on top rather than editing
it — effective gain is `calibration × dbToAmplitude(trim)`. The trim rides a
fourth `GainRamp`, because a step change in a multiplier is a click; it jumps
for free at the bottom of a crossfade and ramps when it is the only thing
moving. The export folds the trim back into one absolute float, because the enum
should hold a single gain and not carry a by-ear offset around forever.

**Debug only, twice over.** `BuildConfig.DEBUG` gates every reference, so R8
removes the panel, `DevTuning` and `DevTuningRepository` from release;
`-assumevalues` in `proguard-rules.pro` makes that a guarantee rather than an
optimisation that happens to fire. Verified against R8's own usage report and by
grepping every dex in the release APK — note *every* dex: the debug APK puts the
panel in `classes7.dex`, so checking `classes.dex` alone would have reported a
false pass. On top of that the panel needs a secret gesture, so it is not
underfoot during ordinary dev work.

The debug build is unminified and debuggable, so its render thread is slower
than the real thing. Numbers settled by ear there should be re-heard once on a
release build before they are trusted.

## Night mode was removed

The app used to dim itself twice over while playing: the window brightness was
overridden to 2 %, and after 12 seconds of no touching a veil at 88 % black
faded the whole interface out, freezing the field, the orb, the presence dot and
the countdown with it. It was on by default and had a switch in the settings.

All of it is gone — the veil, the brightness override, the `dimScreen` setting,
its DataStore key and its strings. The app now never touches `screenBrightness`
and never covers its own interface. How bright the screen is and when it
switches off belong to the system, which is where the user already sets them and
where their choice applies to everything else they own.

Two things made it worth deleting rather than defaulting off. An app that
reaches for the backlight is making a decision it does not have the information
for — it cannot see the room, and "enough to read in the dark" is a guess about
someone else's eyes in someone else's bedroom. And a switch that is on by
default means the first experience of the app is it going dark on you, which
reads as a fault long before it reads as a feature.

The honest cost: the field, the orb and the presence dot now animate for as long
as the screen is on, where the veil used to stop them dead. The system's display
timeout covers the normal case better than a veil over a lit panel ever did —
nothing is drawn at all once the screen is off. What is no longer covered is a
screen deliberately kept on, and that has not been measured since. The fix, if
it is ever needed, is to stop the animations on a timer **without** darkening
anything: a static field is not a dim screen, and each of those three
composables is one flag away from being able to hold still. Reaching for the
backlight again is not the fix.

## The name

The app is called **Шум Дюн** — "the noise of dunes". It is the launcher label
and the whole of the brand: at seven characters it fits under an icon without
eliding, so unlike a one-word brand with a descriptive tail, there is no shorter
form to keep in step with it. `app_name` therefore carries the real name, not an
abbreviation of it.

It replaced a first choice of «Дюна», for two reasons that are the same reason.
A RuStore search for «Дюна» returns the Dune real-time-strategy remakes — ten of
them across the first page, «Дюна 2», «Open Dune 2», «Пазлы Дюна», all under
Стратегии — so the bare word puts a baby-sleep app in a queue behind a 1992
Westwood game. And the word a parent actually types is «шум». Folding it into the
name instead of leaving it to the subtitle fixes both at once: the brand is no
longer a homonym of a franchise, and it contains its own keyword.

What survives from that first choice is why a dune at all: the field under the
orb is five dune silhouettes parallaxing against each other. The name was picked
after the field, not before it, which is the right order — it describes what the
app looks like rather than asking the art to catch up. The plural is what makes
it a description of that picture rather than a reference to the novel.

The store title adds a subtitle, because the name alone does not spell out what
the app is for: **Шум Дюн — белый шум для сна малыша**. «шум» appearing twice
across the title is a cost accepted on purpose — «белый шум» is the exact phrase
parents search, and store titles are indexed by token, so paying it buys the
query. That title belongs to the store listing and appears nowhere in the build.

There is no second name, because there is no second locale. `values-en/` held a
full English translation of all forty-eight strings and has been deleted; the
strings live in `values/` alone and `locales_config.xml` declares Russian only,
so an English phone now gets Russian like every other phone. The app ships on
Russian stores to people who read Russian, and the translation was a second copy
of every string to keep in step on behalf of nobody.

**NoiseDune** survives, but as the project's own name rather than a name the app
answers to: it is the README title, `rootProject.name`, the `ru.noisedune`
package and the `NoiseDuneApp` / `NoiseDuneTheme` identifiers. It was coined
because "Dune" alone in English is Herbert and Villeneuve and nothing else, so
the compound had to supply the missing noun; against «Шум Дюн» it is the same two
words in the same order, which is why it was worth keeping in the code when it
stopped being shown to anyone.

### What the stores say, searched 14 October 2026

No app of this name in RuStore, NashStore or AppGallery, and nothing in the
infant-sleep category carrying «Дюн» at all. The reasoning above also holds up
as a measurement rather than a hope: searching RuStore for «Шум Дюн» returns the
noise field — «Белый шум/Успокаивающие звуки», Whispy, «Белый шум для сна» —
while «Дюн» on its own still returns «Дюна 2», «Open Dune 2», «Пазлы Дюна». One
word moves the query out of the games cluster.

What the same search makes plain is that the field itself is crowded, which the
NashStore result had disguised. RuStore carries «Белый шум: ночник малыша» at
5,0, «Белый шум/Успокаивающие звуки» and «Белый шум: звуки для сна и
концентрации» at 4,8, «Белый шум для сна» at 4,5, and Whispy under Родителям at
3,6. NashStore, by contrast, shows ten results of which the best-installed noise
app has 108 installs. The name is free; the category is not.

### The trademark exposure is DUNE HD, not the novel

Searched over the indexed Rospatent mirror, not the register itself. No mark on
«ШУМ ДЮН». What there is belongs to a line nobody would guess from the word:
**DUNE**, certificate № 380119, held by ООО «ДЮНА ЭЛЕКТРОНИКС» — filed
05.10.2006, registered 26.05.2009, exclusive right expired **5 October 2026**,
which is recent enough that the six-month renewal window is still open. The same
owner holds **DUNEHD / DUNE HD to 28.05.2032**, alive, plus THESTREAMER to
11.11.2031. That is Dune HD, the media player and set-top box brand, fifteen
years on the Russian market and stocked by DNS and Ozon; the company's ОКВЭД is
wholesale of domestic electrical goods and manufacture of television receivers
and monitors.

The exposure was asymmetric, and it fell on the English name: «Шум Дюн» is a
different word from DUNE — plural genitive, prefixed, Cyrillic — while
**NoiseDune** contains it outright, and software would be filed in the same
class 9 that DUNE HD occupies. Deleting the English locale is what closes that
gap, and it closes it almost entirely rather than by argument. A trademark is
infringed by use in trade for the relevant goods; NoiseDune is no longer offered
to anyone under any locale, in any store, on any icon. What is left of it is a
package identifier and a repository title, which nothing is sold under.

The name to clear, then, is **«Шум Дюн»**, and against DUNE it is the easy half
of the pair.

Still not established: the mirror does not publish Nice classes, so class 9 for
DUNE HD is inferred from the owner's line of business rather than read off the
record, and an authoritative search still needs an account on Rospatent's search
platform or an attorney's report. The AppGallery storefront reachable from here
is the European one, so that result wants re-checking from Russia.
