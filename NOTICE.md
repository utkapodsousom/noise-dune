# Attribution

The DSP in this app is written from scratch, but the algorithms behind it are
not. They come from the following sources, all of them public domain or under a
permissive licence.

## Algorithms

- **Voss-McCartney pink noise** — described and refined by Allan Herriman,
  James McCartney and Phil Burk, collected at
  [firstpr.com.au/dsp/pink-noise](http://www.firstpr.com.au/dsp/pink-noise/).
  Placed in the public domain by its authors. Used for `PinkNoise`.
- **Paul Kellett's pinking filter** — same page, also public domain. Not used in
  the shipped code (Voss-McCartney is rate-independent, which the filter is not)
  but it is documented in `PinkNoise.kt` as the alternative that was measured
  against it.
- **RBJ Audio EQ Cookbook** — Robert Bristow-Johnson's biquad coefficient
  formulas, used in `Biquad` for the band-pass and high-pass sections.
- **Klatt formant synthesis** — Dennis H. Klatt, "Software for a cascade/parallel
  formant synthesiser", *JASA* 67(3), 1980. The model behind `ShushNoise`: a
  noise source through front-cavity resonances, with no voicing oscillator.
- **IEC 61672 A-weighting** — the standard weighting curve, used in the tests to
  equalise perceived loudness between the voices.

## Reference implementations consulted

Neither was copied from, both are worth reading:

- [noise-timer](https://github.com/chmouel/noise-timer) — MIT, Kotlin/Compose,
  real-time generation with a foreground service and sleep timer.
- [whitenoise-android](https://github.com/davidalbers/whitenoise-android) — MIT,
  Compose, timer and fades.

[ChromaDoze](https://github.com/pmarks-net/chromadoze) is GPL-3.0. Its approach
(shaped noise through an inverse DCT, blocks crossfaded at random) is an
interesting one and is mentioned here only as an idea; **no code was taken from
it**, and none should be, as the licence is incompatible with this project.

## Hearing safety guidance

- СанПиН 1.2.3685-21, «Гигиенические нормативы и требования к обеспечению
  безопасности и (или) безвредности для человека факторов среды обитания»,
  утверждён постановлением Главного государственного санитарного врача РФ от
  28.01.2021 № 2. Таблица 5.35, строка «Жилые комнаты квартир … спальные
  помещения в школах-интернатах, дошкольных образовательных организациях …»:
  с 23:00 до 7:00 — 30 дБА эквивалентный уровень звука, 45 дБА максимальный;
  с 7:00 до 23:00 — 40 и 55 дБА. This is the figure the in-app notice states,
  and the only place in this repository that carries the document number.
- Hugh et al., "Infant Sleep Machines and Hazardous Sound Pressure Levels",
  *Pediatrics* 133(4), 2014 — measured fourteen infant sleep machines and found
  all of them above 50 dBA at 30 cm. Cited as a measurement, not as guidance.

## Dependencies

AndroidX (Apache-2.0) and the Kotlin standard library (Apache-2.0). There is no
third-party audio library: see `docs/decisions.md` for why.
