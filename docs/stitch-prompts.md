# Промпты Google Stitch для редизайна NoiseDune

Вход для [stitch.withgoogle.com](https://stitch.withgoogle.com): как промптить
этот инструмент и готовые промпты под конкретный экран этого приложения.
Промпты — на английском, пояснения — на русском: Stitch заметно точнее следует
английским терминам UI, а копи переводится отдельным промптом
([P8](#p8--локализация)).

Документ переписан после первого прогона. Экспорт лежит в
`stitch_nightlight_hush.zip` и разбирается в
[приложении «Почему подробные промпты вышли хуже»](#приложение--почему-подробные-промпты-вышли-хуже)
— прочитай его прежде, чем писать свой промпт: там причина, по которой все
промпты ниже выглядят куда менее детальными, чем хочется.

---

## 1. Как работает Stitch и чего от него ждать

| | |
|---|---|
| Что выдаёт | HTML + Tailwind-конфиг с токенами, экспорт в Figma, и `DESIGN.md` с design system проекта |
| Чего не выдаёт | Compose, SwiftUI, React — ничего фреймворкового |
| Standard-режим | Gemini 2.5 Flash, ~350 генераций в месяц, экспорт в Figma есть |
| Experimental-режим | Gemini 2.5 Pro, ~50 генераций в месяц, принимает картинки и скетчи, **экспорта в Figma нет** |
| Ввод | текст, изображение/скетч, голос (Voice Canvas) |
| Многоэкранность | генерирует несколько экранов и связывает в кликабельный прототип |

### Анимацию он делает — если ему не мешать

Таймлайна анимации и редактора микроинтеракций в Stitch нет, и Figma-экспорт
движение не несёт. **Но HTML-экспорт несёт.** В сгенерированном
`nocturne_noise_field_substrate/code.html` работает полноценный движок на
`requestAnimationFrame`: синус гонит масштаб и прозрачность янтарного гало за
кнопкой и высоту шести полосок волновой формы внутри неё, а отдельный
`<canvas>`-движок рисует шумовое поле из 34 волн.

Это важно дважды. Во-первых, движение можно и нужно просить словами — см.
[P2b](#p2b--поле-как-подложка) и [P6](#p6--три-варианта-шумового-поля).
Во-вторых, выданный HTML — готовый референс для реализации: формулы переносятся
в Compose почти как есть ([раздел 5](#5-перенос-результата-на-compose)).

Обратная сторона: если написать в промпте «static image, one frozen frame»,
движение пропадёт целиком. Именно это и произошло в первом прогоне.

### Design system он держит — и её легко сломать

Stitch создаёт на проект файл `DESIGN.md` с токенами, парой шрифтов, правилами
глубины и спецификациями компонентов, и переносит эту систему между экранами
проекта. В нашем случае он назвал её **Nocturne Sleep Engine** и выбрал
Space Grotesk для заголовков и Plus Jakarta Sans для текста — пару, которую
никто не просил и которая оказалась лучше нашей.

Сломать систему проще, чем кажется: достаточно написать в следующем промпте свою
палитру хексами и один шрифт — и Stitch честно выбросит всё, что сам построил.
Поэтому вместо описания стиля в промптах стоит **ссылка на систему по имени**
([System lock](#блок-1--system-lock)).

### Одна известная кривизна в выданном DESIGN.md

Он противоречит сам себе. Во фронтматтере — `surface: #131313`,
`primary: #ffb86a`; в прозе ниже — «Absolute True Pitch Black `#000000`» и
«`#D98E32` Muted Warm Amber». Сгенерированный экран взял токен и получил фон
`#0e0e0e`, то есть не истинно чёрный, а тёмно-серый. На OLED это разница между
выключенными пикселями и светящейся подложкой.

Поэтому первый промпт в [P7](#p7--итерации-по-теме) — не опциональная
полировка, а обязательная коррекция.

### Чего Stitch в этом приложении не решает

- **Сохранение настроек между запусками** — уже сделано:
  [`SettingsRepository`](../app/src/main/java/ru/noisedune/data/SettingsRepository.kt)
  на DataStore пишет все одиннадцать полей
  [`NoiseSettings`](../app/src/main/java/ru/noisedune/playback/NoiseSettings.kt).
  От макета нужно ровно одно: чтобы в шторке настроек **не появилось Save /
  Cancel**. Это сказано в [P4](#p4--bottom-sheet-настроек) явно.
- **Фоновое воспроизведение, нотификация, аудиофокус** — служба, не дизайн.
- **Сам синтез шума** — DSP, покрытый тестами, к UI отношения не имеет.

---

## 2. Правила промптинга

### Добытые на этом проекте

**Не специфицируй — ставь задачу.** Если продиктовать все значения, Stitch
перестаёт проектировать и начинает транскрибировать прозу. Признак в выводе:
твои собственные фразы появляются в HTML как комментарии. Разбор —
в [приложении](#приложение--почему-подробные-промпты-вышли-хуже).

> ✗ `a circular icon-only button, 96px diameter, 1px #2A2A2E ring, fill #0A0A0A, and a solid amber #9C6B4F play triangle glyph 32px in the middle`
> ✓ `the orb is the only thing that matters on this screen — everything else should feel like it is receding from it`

**Запирай систему по имени, а не переспецифицируй.** Одна строка
«Keep the Nocturne Sleep Engine design system exactly» сохраняет пару шрифтов,
шкалу токенов и ритм. Абзац хексов их убивает.

Но держит она не всё: во втором прогоне шторка настроек сохранила раскладку и
шрифты, а цвета ушли на хардкод — тринадцать литеральных хексов и ни одного
токена, причём часть палитры притащилась из экранов **первого** прогона, уже
удалённых из экспорта. Проект Stitch помнит удалённые экраны. Поэтому к System
lock нужна отдельная фраза про токены: «use the design system tokens, not
literal hex values».

**Правь скрытый текст отдельным промптом.** Stitch меняет то, что видно.
`aria-label`, `data-*` и строковые литералы внутри JS правку копи переживают: во
втором прогоне `aria-label="Toggle womb soundscape"` и `data-time="Cry Sentry"`
выжили **в шести экранах из шести** после промпта, который заменил всю видимую
выдумку. Скринридер при этом произносит «womb soundscape».

**Правь состояния, а не только первый кадр.** Текст, который появляется после
взаимодействия, лежит в обработчиках и чистится отдельно. Выпиленный из разметки
шумомер вернулся через `statusHint.textContent = "Playing softly • 42 dB"` —
два тапа по кнопке, и число снова на экране.

**Разделяй факты и форму.** Контент — буквально и жёстко: Stitch охотно
придумывает фичи, которых нет. Геометрия, размеры, цвета — на его усмотрение, в
рамках запретов. Отсюда три блока в [разделе 3](#3-три-блока-для-вклейки):
`Facts` буквальный, `Prohibitions` в форме запретов, и никакого «вот как должно
выглядеть».

**Движение проси явно.** «This must move», «animate it continuously», «drifting
at roughly one cycle per 40 seconds». И никогда не писать «static frame».

**Ни один факт не показан дважды.** Если название шума живёт внутри кнопки, то
заголовка сверху быть не должно. Stitch сам дубли не вычистит — он их
добросовестно нарисует оба.

### Общие, из официального гайда

**Начинай просто.** Короткий вайбовый промпт на старте, затем доработка экран за
экраном. Никаких XML-тегов и JSON — обычный язык.

**Один экран на промпт.** Промпт про главный экран и шторку сразу даёт плохо
оба.

**Одно изменение за правку.** Самая частая жалоба пользователей: при нескольких
правках в одном промпте Stitch «забывает всё и начинает заново». Исключение —
замена только текста: она структуру не ломает, так что [P2a](#p2a--правда-вместо-выдумки)
меняет несколько подписей за раз сознательно.

**Называй элемент, а не намекай.**

> ✗ `fix the text at the bottom`
> ✓ `the metadata line over the noise field: keep it legible against the densest part of the field`

**`Keep everything else unchanged`** в конце правки — дешёвая страховка от
переразметки всего экрана.

**Держись в пределах ~2000 символов.** Официальный лимит эффективности —
5000, после него компоненты начинают пропадать из выдачи; но практика этого
проекта: чем короче промпт, тем больше Stitch проектирует сам.

**Скриншоть удачные шаги.** Отката к произвольной версии нет, накопительные
правки ломают вёрстку — снимок экрана единственный способ вернуться.

**Мимо — переформулируй, а не дописывай.** Уточнения к плохому промпту обычно
хуже нового промпта.

---

## 3. Три блока для вклейки

Вместо описания стиля. Первый вклеивается во все промпты, второй — где есть
контент, третий — где генерируется новый экран.

### Блок 1 — System lock

```
Keep the "Nocturne Sleep Engine" design system exactly as it is: the same
Space Grotesk / Plus Jakarta Sans pairing, the same token scale, the same
whisper hierarchy with no dividers and no enclosing cards, the same
letter-spaced uppercase micro-labels. Do not re-theme anything.
Reference the design system tokens by name — surface, outline, primary,
on-surface-variant and the rest. Do not write literal hex values, and do not
reuse any palette from an earlier version of this project.
```

Последние две строки — не педантизм. Без них шторка настроек во втором прогоне
собралась на тринадцати литеральных хексах, половина из которых пришла из
удалённых экранов первого прогона: в Figma-экспорте это безымянные разовые
цвета вместо токенов.

### Блок 2 — Facts

Единственное место, где буквальность помогает. Сверено с
[`strings.xml`](../app/src/main/res/values/strings.xml) и
`NoiseSettings.TIMER_PRESETS_MINUTES`.

```
Facts about this app. Use these exactly and invent nothing.

Four noise voices, and only these four:
  White noise — "Flat spectrum"
  Pink noise — "−3 dB/oct, softer than white"
  Brown noise — "−6 dB/oct, a deep rumble"
  Shhh — "Hiss peaking at 3 kHz"
Pink noise is the default and is the selected one.

Volume is a 0-100% setting, default 45%. There is no loudness measurement.
Sleep timer presets, and only these: Off, 15, 30, 45, 60, 90, 120 minutes, plus
a "+15 min" extend action that exists only while a timer is running.
Five on/off settings: Fade in, Fade out, Volume cap, Night mode, Keep screen on.
Playback states, and only these: Ready, Playing, Paused, Fading in, Fading out.

This app does NOT have, so never show: a decibel or SPL readout, a clock or the
time of day, cry detection or baby monitoring, rain / womb / heartbeat / nature
sounds, an alarm, sleep tracking, accounts, sharing, or any network feature
whatsoever. No wavelength, nanometre or OLED marketing claims in the interface.
```

### Блок 3 — Prohibitions

Единственная часть про стиль, и та в форме запретов — чтобы оставить Stitch
решения.

```
Hard constraints, never violate:
The canvas is pure #000000 — not #0e0e0e, not #131313, not any dark grey. On
OLED those pixels must be switched off and emit nothing.
One accent family only: muted warm amber. No blue, no teal, no cold hue
anywhere, including icons and tertiary colours.
No pure white anywhere — not in text, not in a fill, not at 6% opacity.
Font weights 300 to 500 only. Bold is forbidden — heavy strokes bloom on a
dark-adapted retina.
No surface, fill, border or line is lighter than #353535. Text is exempt and may
be lighter so it stays readable, but never pure white.
No drop shadows and no shadow utility classes. Depth comes from a very
low-opacity warm filament glow.
Anything a finger can touch, and the playback metadata line, is at least 12px.
The 10px micro-label size is for ornament only — never for a control.
Dark theme only — there is no light mode, ever.
The screen never scrolls.
```

Тёплый янтарный вместо синего — осознанно: длинноволновый свет меньше мешает
темновой адаптации, то есть взгляд на экран ночью дешевле стоит. Истинно чёрный
фон — чтобы пустые области не светили совсем.

Два пункта переформулированы после второго прогона. «Nothing lighter than
#353535» в прежнем виде запрещал читаемый текст и нарушался повсеместно и
безвредно — сама система держит `outline: #a08e7d` и `on-surface: #e2e2e2`, —
поэтому ограничение сужено до поверхностей и линий. А порог размера шрифта
раньше конфликтовал с `label-sm` = 10px из `DESIGN.md`: Stitch выбрал систему и
посадил на 10px все восемь интерактивных контролов главного экрана. Теперь
порог привязан не к «существенному тексту», а к тому, что нажимают.

---

## 4. Промпты

### P1 — Project kickoff

Сработал как есть; менять не надо. Короткий вайбовый старт — именно он породил
`DESIGN.md` и дышащее гало.

```
A white-noise app for settling a baby to sleep. One screen, one big button, and
almost nothing else. Calm, nocturnal, minimal — the opposite of a dashboard.
Dark theme only, pure black background, one muted amber accent. The parent opens
it in the dark, taps once, and puts the phone down.
```

**Что проверить:** нет онбординга, нет табов внизу, нет светлой темы. Если есть —
переформулируй, а не правь. И загляни в `DESIGN.md` проекта: дальше всё опирается
на него.

---

### Цепочка P2 — доводка понравившегося экрана

Шесть правок по одному изменению, применяются к удачному экрану из P1
**по очереди**, не одним промптом. Смысл: идея переносится, а не
воспроизводится заново по словесному описанию — ровно то, на чём сломался первый
заход.

> **Как применять — это важнее формулировок.** Во втором прогоне цепочка
> оборвалась после P2b: шаги P2c и P2d не доехали ни до одного из шести
> экранов, и требование «мета-информация поверх анимации» в экспорте
> отсутствовало целиком. Причина механическая. Каждый промпт правки порождает в
> проекте **новый** экран, а не меняет текущий на месте, и следующий промпт
> уходит в тот экран, который выбран в интерфейсе. Поэтому перед каждым
> следующим шагом нужно открыть результат предыдущего и убедиться, что правишь
> именно его. Иначе шаги начинают применяться к одному и тому же исходнику и
> затирают друг друга.

Порядок обязателен: правда в видимых текстах → правда в скрытых → подложка →
вынос настроек → мета-строка → чистка лишнего. Если сделать наоборот,
мета-строка ляжет на пустое место, а поле придётся перерисовывать.

#### P2a — правда вместо выдумки

Stitch нафантазировал «Womb Pulse», «Heavy Rain», «Cry Sentry», «42 dB», часы
и футер про нанометры. Меняется **только текст** — структуру, цвета и анимацию
не трогаем, поэтому несколько подписей за один промпт здесь безопасны.

```
[вклеить блок System lock]
[вклеить блок Facts]

Only the copy on this screen changes. Do not touch the layout, the colours, the
sizes, the breathing halo or the waveform animation.

The four sound tabs become the four real voices: White, Pink, Brown, Shhh —
Pink is the selected one.
The label inside the orb becomes "Pink noise"; the micro-label under it becomes
"CONTINUOUS".
The status line under the orb becomes "Playing softly" — drop the decibel
number, this app cannot measure loudness.
The header micro-label becomes "SLEEP NOISE" and keeps its pulsing presence dot.
Remove the clock.
Delete the bottom footer line about OLED and wavelengths entirely.
Leave the timer row alone for now — it moves in a later step.
```

**Что проверить:** четыре голоса ровно те, что в Facts; нигде не осталось dB,
времени суток и нанометров; гало по-прежнему дышит, полоски волны живы.

Отдельно: «Sentry» в ряду таймера этот промпт **не уберёт** — он оставлен
следующему шагу намеренно, потому что весь ряд уезжает в шторку в P2c.

#### P2a-bis — правда в скрытом тексте и в состояниях

Без этого шага выдумка остаётся жить там, куда промпт про видимую копию не
достаёт. Во втором прогоне после P2a в шести экранах из шести уцелели
`aria-label="Toggle womb soundscape"`, `aria-label="Screen Lock Activated"`,
`data-time="Cry Sentry"`, выдуманные `data-desc` вместо настоящих описаний и
строка с шумомером в обработчике нажатия.

```
[вклеить блок Facts]

One change, and it is invisible on the screen: the hidden text is still wrong.
Fix every accessibility label, every data attribute and every string literal
inside the interaction handlers. Do not touch the layout, the colours or the
animation.

The orb's accessibility label describes what it actually does: start and stop
the selected noise. It is not a "womb soundscape".
The four voice buttons carry their real names and the real descriptions:
  White noise — "Flat spectrum"
  Pink noise — "−3 dB/oct, softer than white"
  Brown noise — "−6 dB/oct, a deep rumble"
  Shhh — "Hiss peaking at 3 kHz"
No handler may ever set a decibel number into the status line — this app cannot
measure loudness, so "42 dB" must not exist in any state, not just in the
initial one.
Delete the line about putting the phone face down to mute the display: there is
no such gesture in this app.
```

**Что проверить** — глазами по экрану не видно, смотри в выгруженный HTML:
`womb`, `Cry Sentry`, `Screen Lock`, `42 dB`, `Face-down` — ноль вхождений;
четыре настоящих описания голосов — есть.

#### P2b — поле как подложка

Главное композиционное решение. Поле не полоса внизу, а **подложка нижней
половины**: контролы лежат поверх него. Так поле получает всю высоту, а место
под контролы не приходится отрезать.

```
[вклеить блок System lock]

One change: add the noise field, and make it the substrate of the lower half of
the screen.

It starts just below the orb and bleeds full-width to the very bottom edge and
to both side edges — no border, no card, no corner radius, and no visible top
edge: it dissolves into pure black under the orb. It owns that space. The voice
tabs and the volume bar now float on top of it rather than sitting below it.

Fill it with slow horizontal flowing lines, thirty or more, each a hairline,
each with its own phase so they never run parallel, growing denser and very
slightly brighter towards the bottom edge. No line lighter than #353535.

This must move. Animate it continuously with the same kind of sine-driven
requestAnimationFrame engine already driving the breathing halo, drifting
sideways at roughly one cycle per 40 seconds — slow enough that you never catch
it moving. Do not produce a static frame.
```

**Что проверить:** поле уходит в края и не оформлено карточкой; у него нет
видимой верхней границы; оно реально движется; контролы читаются поверх.

#### P2c — таймер и тумблеры в шторку

**Обязательный шаг.** Во втором прогоне он не был применён, и ряд таймера
остался на главном экране во всех шести экранах — включая экран со шторкой, где
в итоге два разных контрола таймера видны одновременно: «Infinity / 45 Min /
90 Min / Sentry» за скримом и «Off / 15m / … / 120m» в самой шторке.

```
One change: the sleep timer and the five on/off settings do not belong on this
screen. Remove the timer row from the main screen entirely — the whole row,
including the "Sentry" option, which is not a feature of this app. The gear
button in the header becomes the only way to reach them, and it opens a modal
bottom sheet.

What stays on the main screen: the header, the orb, the four voice tabs, the
volume bar, the noise field and nothing else. Keep everything else unchanged.
```

**Что проверить:** главный экран по-прежнему без скролла и стал заметно пустее;
ряда таймера нет; шестерёнка не потерялась; громкость и голоса остались.

#### P2d — мета-строка поверх анимации

**Обязательный шаг.** Это единственное место, где выполняется требование
«мета-информация о проигрывании поверх анимации». Во втором прогоне шаг не
доехал, и во всех шести экранах мета-строки не было вообще.

```
One change: add a single line of playback metadata on top of the noise field,
near the bottom edge, horizontally centred, in the same letter-spaced uppercase
micro-label style as the header — 12px, not smaller. It reads:

PINK · −3 dB/OCT · 45% · FADE-IN 0:07

It sits on top of the field and the field keeps running behind it. It must stay
legible against the densest part of the field without a backing plate, a chip or
a gradient behind it. Keep everything else unchanged.
```

**Что проверить:** строка поверх поля, а не под ним и не в отдельной полосе;
читается на самой плотной части поля; нет подложки под текстом; 12px, не 10.

#### P2e — выпилить лишнее

Stitch добавляет декор, который выглядит уместно и ничего не делает. Во втором
прогоне в шапке висели две круглые кнопки: `bedtime` без функции и `lock`,
которая вообще не кнопка, а `<div>` с иконкой замка и подписью для скринридера
«Screen Lock Activated». Плюс тени там, где они запрещены, и белый там, где он
запрещён.

```
One change: remove the decorative controls and the forbidden styling.

Delete the lock icon in the header entirely — it is not a control, it does
nothing, and it claims a feature this app does not have.
Delete the second header icon button as well. Night mode lives in the settings
sheet; the header needs only the presence dot, the app micro-label and the gear.
Remove every shadow utility class from the orb and the sheet — this system has
no drop shadows, only the warm filament glow.
The volume track must not use white at any opacity; use a dark surface token.

Keep everything else unchanged.
```

**Что проверить:** в шапке остались ровно три элемента; нет ни одного
`shadow-*`; `bg-white` не встречается; орб и поле не изменились.

---

### P3 — второе состояние, как диф

Понравившийся экран — это состояние **playing**. Idle описывается как отличие от
него, а не новой спецификацией.

```
Duplicate this screen and show it in its idle state — nothing is playing.
Same layout, same proportions, nothing changes position.

The orb's warm glow is gone and its edge recedes to a faint grey. The breathing
halo is not rendered at all. The waveform bars inside the orb settle into a
flat, barely visible row. The label inside the orb stays "Pink noise"; the
micro-label under it becomes "READY".
The status line under the orb becomes "Tap to soothe".
The noise field goes quiet: fewer lines, dimmer, and nearly still — it keeps
drifting but at half the amplitude.
The metadata line becomes "PINK · −3 dB/OCT · 45% · TIMER OFF".
```

**Что проверить:** каркас не поехал относительно playing-экрана — тот же центр
орба, та же высота поля. Поле не исчезло и не замерло полностью.

---

### P4 — bottom sheet настроек

```
[вклеить блок System lock]
[вклеить блок Facts]
[вклеить блок Prohibitions]

New screen: the settings sheet that the gear button opens. A modal bottom sheet
rising over the main screen, which stays visible behind it under a soft dark
scrim. Same system as everywhere else — true black base, warm amber accent, pill
geometry, no dividers, letter-spaced uppercase section labels. Its content may
scroll; the main screen behind it still may not.

It holds two things, in this order: the sleep timer, then the five on/off
settings.

Timer: the presets Off, 15, 30, 45, 60, 90 and 120 minutes, with Off selected.
Settings, each a row with a name, a quiet second line explaining it, and a
switch on the right:
  Fade in — "10 s from silence up to the set volume" — on
  Fade out — "30 s before the timer stops playback" — on
  Volume cap — "Holds the top of the slider 6 dB lower" — on
  Night mode — "Dims the screen and hides the interface after 12 s" — off
  Keep screen on — "The screen stays lit while the app is open" — off

Every control applies the instant it is touched. There is no Save button, no
Cancel button, no Apply and no confirmation step anywhere in this sheet. That is
deliberate — do not add one.
There is no close button either: the sheet is dismissed by swiping it down or by
tapping the scrim, so an X in its corner is redundant.
The sheet needs no title. If you label it at all, label it "Settings" — never
with the internal name of the design system, which is not product copy.
A switch thumb is amber when on, never white: a white dot is a bright spot in a
dark room.
```

**Что проверить:**
- **Нет Save / Cancel / Apply** — иначе макет противоречит тому, как устроено
  хранение: настройки пишутся сразу при каждом изменении.
- Тумблер во включённом состоянии янтарный, а не белый.
- Подписи совпадают с [`strings.xml`](../app/src/main/res/values/strings.xml)
  буквально — иначе придётся переписывать ресурсы.
- Громкости и выбора голоса в шторке нет: они остались на главном экране.
- Нет крестика закрытия и нет заголовка «NOCTURNE SETTINGS» — во втором прогоне
  появились оба.
- Цвета взяты из токенов, а не из литеральных хексов: в выгруженном HTML у
  шторки не должно быть россыпи `#0A0A0A`, `#1F1F1F`, `#9C6B4F` и прочих.

---

### P5 — Safety notice

Показывается один раз; флаг `safetyNoticeAcknowledged` уже есть в
`NoiseSettings`.

```
[вклеить блок System lock]

One change: the main screen gets a one-time hearing-safety note, low on the
screen, over the noise field. It is a quiet recessed surface, not an alert — no
red, no warning icon, no exclamation mark.

Title: "Protect your baby's hearing"
Body: "Keep the level at or below 50 dBA measured at the cot, and place the
phone at least 2 m away from it — the American Academy of Pediatrics guidance.
The app cannot measure the real loudness: that depends on the speaker, the
system volume and the distance."
One borderless amber text action, right-aligned: "Got it"

The screen still does not scroll, and the orb does not move. The note may cover
part of the noise field — that is fine.
```

**Что проверить:** экран не уехал в скролл, орб на месте, нет красного и нет
иконки предупреждения.

---

### P6 — три варианта шумового поля

По одному промпту на вариант, остальной экран не трогаем. Движение просим в
каждом: Stitch его сделает.

#### P6a — перетекающие линии

```
Replace only the noise field. Keep the header, the orb, the voice tabs, the
volume bar and the metadata line exactly as they are.

New field: long horizontal hairlines spanning the full width, each a slow
irregular wave with its own phase and amplitude so they never run parallel.
Spacing tightens towards the bottom edge, and the top dissolves into pure black.
Animate it: the whole set drifts sideways, about one cycle per 40 seconds, and
the waves breathe slightly in amplitude. Never fast enough to notice directly.
```

#### P6b — слоистые силуэты

```
Replace only the noise field. Keep everything above it exactly as it is.

New field: five overlapping soft hill silhouettes, like distant dunes, stacked
back to front, each a single smooth filled shape with a soft blurred top edge
and no outline. Their crests sit at different heights and do not align. The
back layer fades into pure black.
Animate it as parallax: each layer drifts sideways at its own speed, the front
layer fastest at roughly one cycle per 25 seconds, the back layer slowest at
about one per 70 seconds, in alternating directions.
```

#### P6c — геометрическое поле

```
Replace only the noise field. Keep everything above it exactly as it is.

New field: a loose irregular grid of tiny squares, dense at the bottom edge and
thinning upwards until it disappears into pure black. Brightness varies randomly
between neighbours. Slight horizontal banding in density, like a spectrum. No
connecting lines, no glow.
Animate it as a slow shimmer: individual squares fade in and out of brightness
independently, and the banding drifts upwards almost imperceptibly. No single
obvious direction of motion.
```

**Что проверить во всех трёх:** поле не стало карточкой с рамкой, нигде не
светлее `#353535`, движение есть, и мета-строка поверх по-прежнему читается.

---

### P7 — итерации по теме

Первый — **обязательный**: это коррекция расхождения внутри `DESIGN.md`
(см. [раздел 1](#одна-известная-кривизна-в-выданном-designmd)). Остальные —
по вкусу, по одному за раз.

```
The canvas is not black enough. Set the base background of every screen to pure #000000, not #0e0e0e and not #131313. Keep everything else unchanged.
```

```
Reduce the amber accent to 80% of its current intensity everywhere. Keep everything else unchanged.
```

```
Make the orb noticeably larger, and let the noise field start correspondingly closer to the bottom edge. Keep everything else unchanged.
```

```
The metadata line should be readable only when you look for it — lower its contrast one step. Keep everything else unchanged.
```

```
The gear button should recede: smaller and dimmer, so it is found by position rather than by seeing it. Keep everything else unchanged.
```

```
Slow the breathing halo down — it should feel like sleeping breath, not like a loading indicator. Keep everything else unchanged.
```

---

### P8 — локализация

```
Switch all product copy and button text to Russian. Keep the layout, the colours
and the type scale unchanged. Use these exact strings: "Белый шум",
"Розовый шум", "Коричневый шум", "Шшш", "Готов", "Играет",
"Нарастает · 0:07", "Осталось 38:12", "Громкость", "Автовыключение", "Выкл",
"Плавное нарастание", "Плавное затухание", "Ограничение громкости",
"Ночной режим", "Не гасить экран", "Понятно".
Russian switch labels run 20-40% longer than the English ones — let the two-line
rows grow in height rather than truncating or shrinking the font.
```

**Что проверить:** строки совпадают с
[`values-en/strings.xml`](../app/src/main/res/values-en/strings.xml); подписи
тумблеров не обрезаны многоточием.

---

### P9 — восстановление, когда Stitch поехал

| Симптом | Что писать |
|---|---|
| Пропала анимация после правки | `The breathing halo and the drifting noise field must keep animating continuously. Restore the motion — this screen is never a static frame.` |
| Вернулась выдуманная функциональность | `Remove the decibel readout, the clock and any sound that is not White, Pink, Brown or Shhh. This app has four voices and no loudness measurement.` |
| Сломалась пара шрифтов, стало плоско | `Restore the Nocturne Sleep Engine design system: the Space Grotesk / Plus Jakarta Sans pairing, the token scale and the letter-spaced uppercase micro-labels.` |
| Появился скролл | `The screen must fit the viewport exactly with no scrolling. Absorb the leftover height into the noise field — do not shrink the type.` |
| Поле схлопнулось в полосу | `The noise field is the substrate of the entire lower half of the screen, from just below the orb to the bottom edge. It is not a band and not a card — it has no fixed height.` |
| Орб съехал | `The orb stays optically centred in the space above the noise field. Keep its size and everything else unchanged.` |
| Подкрался светлый фон | `The base background is pure #000000. No surface or line may be lighter than #353535.` |
| Под мета-строкой появилась подложка | `The metadata line sits directly on the noise field — no chip, no backing plate, no gradient behind it.` |
| Шумомер вернулся после тапа | `No interaction handler may put a decibel number into the status line. Remove "42 dB" from every state, not just the initial one.` |
| Выдумка осталась в скрытом тексте | `The accessibility labels and data attributes still name features this app does not have. Fix every aria-label and every data attribute to match the four real voices and the real timer presets.` |
| Шторка собралась на хексах | `Rebuild the sheet using the design system tokens instead of literal hex values, and do not reuse any colour from an earlier version of this project.` |
| Два контрола таймера одновременно | `The main screen behind the sheet must not have a timer row — the timer exists only inside the sheet. Remove it from the screen underneath.` |
| Поплыла вёрстка целиком | Не правь — вернись к скриншоту удачного шага и перегенерируй с него. |

---

## 5. Перенос результата на Compose

Stitch отдаёт HTML/Tailwind — переносится не код, а раскладка и движение.

### Движение: формулы прямо из выдачи

`nocturne_noise_field_substrate/code.html` — не только макет, но и референс
анимации. Движок инкрементирует фазу орба на `0.035` за кадр, то есть при 60 fps
полный цикл ≈ 3 с — дыхание, а не индикатор загрузки:

| Элемент | Формула в выдаче | Куда в Compose |
|---|---|---|
| Гало за орбом | `scale = 1 + sin(step) · 0.05`, `alpha = 0.08 + sin(step) · 0.06` | радиальный градиент под кнопкой |
| Внешнее кольцо | `scale = 1 + sin(step + 0.5) · 0.08` | второй, более широкий градиент |
| Полоски волны | `h = 8 + sin(step + i · 0.8) · 14`, минимум 4 px, 6 полосок | `Canvas` внутри кнопки |
| Свечение активного состояния | `0 0 45px -8px rgba(217,142,50,0.22)` | очень размытый градиент, без `elevation` |

Шумовое поле — отдельный, намного более медленный цикл: ~40 с против 3 с у
дыхания. Два разных темпа важны: совпади они, экран начнёт пульсировать целиком.

### Три движка поля — готовые к переносу

Второй прогон выдал все три варианта из [P6](#p6--три-варианта-шумового-поля)
реализованными, каждый в своём экране. Это и есть `NoiseField.kt`, остаётся
выбрать вариант:

| Вариант | Экран в экспорте | Что внутри |
|---|---|---|
| Линии | `nocturne_flowing_noise_field` | 42 волны; `y = sin(x·f₁ + t)·amp + cos(x·f₂ − t)·amp·0.45`; `speed ≈ 0.00015` (≈40 с/цикл); тон `18 + progress·32`, то есть `#121212…#323232`; альфа гаснет к верху |
| Дюны | `nocturne_dune_parallax_noise_field` | 5 слоёв, периоды 70 / 55 / 42 / 32 / 25 с от заднего к переднему, направление чередуется `+1 −1 +1 −1 +1` |
| Сетка | `nocturne_spectral_grid_noise_field` | `cellSize = 6`, мерцание ячейки `0.5 + 0.5·sin(globalTime)` |

Базовый экран (`nocturne_noise_field_substrate`) несёт тот же движок линий, но
на 34 волнах, а idle-экран — на 14 и с вдвое меньшей амплитудой
(`amp: 2 + progress·4.5` против `4 + progress·9`). Это готовая пара значений для
перехода playing → idle, подбирать её заново не надо.

**Одно решение, которое выдача приняла молча.** В ней `renderWaveSubstrate()`
вызывается **вне** проверки `isPlaying` — поле продолжает рисоваться на паузе.
Для веб-макета это незаметно, для Android это кадры и батарея при остановленном
звуке. Решать осознанно вместе с остановкой при `dimScreen` (см. ниже).

### Карта файлов

**[`ui/NoiseScreen.kt`](../app/src/main/java/ru/noisedune/ui/NoiseScreen.kt)** —
убрать `verticalScroll(rememberScrollState())`, перейти на `Box` с зонами:
хедер (точка присутствия + микро-лейбл + шестерёнка), орб по центру верхней
половины, поле от основания орба до низа, а поверх поля — голоса, громкость и
мета-строка. `TransportButtons` заменяется орбом. Состояние «открыта ли шторка» —
локальный `remember { mutableStateOf(false) }`, не настройка.

**Новый `ui/components/NoiseOrb.kt`** — круглая кнопка с живой волновой формой,
названием шума и микро-лейблом состояния внутри, плюс дышащее гало за ней.
Иконки [`ic_play.xml`](../app/src/main/res/drawable/ic_play.xml),
[`ic_pause.xml`](../app/src/main/res/drawable/ic_pause.xml) и
[`ic_stop.xml`](../app/src/main/res/drawable/ic_stop.xml) остаются для
нотификации; внутри орба роль иконки играет сама волновая форма. Тактильный
отклик — существующий паттерн
`LocalHapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)`.

**Новый `ui/components/NoiseField.kt`** — `Canvas` плюс
`withInfiniteAnimationFrameNanos`; плотность и амплитуда читаются из
`PlaybackStatus` и `NoiseSettings.volume`, в idle поле почти затихает.
**При `settings.dimScreen` анимацию останавливать:** ночной режим специально
гасит интерфейс через 12 с (`NightDimOverlay`, `DIM_AFTER_MS = 12_000`) и роняет
яркость окна до `0.02f` — непрерывная перерисовка будет бороться с ним и по
свету, и по батарее, а батарея здесь метрика (README целит в 2–4 % за ночь).

**Новый `ui/components/NoiseMeta.kt`** — мета-строка 12 sp поверх поля, с
разрядкой, из `PlaybackStatus` и `type.labelRes()`. Существующая приватная
`statusLine()` из `NoiseScreen.kt` переезжает сюда.

**Новый `ui/components/SettingsSheet.kt`** — `ModalBottomSheet`, внутри
переиспользуются **существующие** composable: `ToggleRow` из
[`Controls.kt`](../app/src/main/java/ru/noisedune/ui/components/Controls.kt) и
[`TimerRow`](../app/src/main/java/ru/noisedune/ui/components/TimerRow.kt).
Новых компонентов для настроек писать не надо.

**Голоса и громкость остаются на главном экране.**
[`NoiseTypeGrid`](../app/src/main/java/ru/noisedune/ui/components/NoiseTypeGrid.kt)
придётся переделать из сетки 2×2 карточек в узкий ряд пилюль, а
[`VerticalVolumeSlider`](../app/src/main/java/ru/noisedune/ui/components/VerticalVolumeSlider.kt)
— заменить горизонтальной полосой: вертикальному слайдеру на 190 dp в новой
раскладке места нет.

**`NoiseSettings` и `SettingsRepository` — не трогать.** Персистентность
работает, требование «настройки сохраняются между запусками» закрыто.

**Шрифты.** Space Grotesk и Plus Jakarta Sans придётся либо завести в
`res/font`, либо сознательно отказаться и остаться на системном sans. Это
решение, а не недоделка: два веса двух гарнитур — это вес APK и ещё один
источник расхождения с макетом.

**`values/strings.xml` + `values-en/strings.xml`** — добавить строки мета-линии,
подписи кнопки настроек и `contentDescription` для орба (тексты уже есть:
`action_play`, `action_pause`, `action_stop`).

---

## Приложение — почему подробные промпты вышли хуже

Первая версия этого документа содержала P2 и P3 — подробные промпты на главный
экран, с хексами, пикселями и позициями каждого элемента. Результат оказался
хуже короткого вайбового P1. В экспорте видно, почему.

> Цитаты ниже — из экспорта **первого** прогона, экранов `nocturne_white_noise`,
> `nocturne_idle` и `nocturne_playing`. В текущем архиве их уже нет: Stitch
> выгружает только живые экраны проекта, а эти были заменены. Сохрани тот
> экспорт отдельно, если нужны первоисточники.

**1. Stitch транскрибировал, а не проектировал.** В `nocturne_idle/code.html`
мои фразы из промпта лежали в выводе как HTML-комментарии:

> `<!-- Top bar: only gear button in top-right corner, 48x48 tap target, 24px icon in #4A4A4E -->`

Когда продиктованы все значения, решений не остаётся — задача сводится к
переводу прозы в разметку. А транскрипция словесного описания всегда выглядит
хуже сгенерированного дизайна.

**2. Промпт стёр design system.** P1 построил `DESIGN.md` с парой
Space Grotesk + Plus Jakarta Sans, шкалой токенов, правилами глубины без теней.
Мой «Design brief» с сырыми хексами и одним шрифтом его перезаписал: в
`nocturne_idle` остался только Plus Jakarta Sans, пропала разрядка
микро-лейблов и весь ритм токенов.

**3. Промпт запретил то, что понравилось.** В P2 стояло «Static image, one
frozen frame» — и дышащее гало с живой волновой формой, то есть ровно обаяние
P1, не появилось вообще. Инструмент умеет анимировать; достаточно было не
запрещать.

**4. Шумовое поле схлопнулось.** «Filling remaining height» стояло рядом с
жёсткими размерами всего остального, и поле получило `h-[220px] max-h-[30vh]` —
короткую полосу вместо доминирующего элемента.

**Вывод, на котором построен раздел 4:** подробность нужна в контенте и в
запретах, а не в геометрии. Контент Stitch выдумывает, если его не прижать
фактами; геометрию он придумывает лучше, чем её можно описать словами.

---

## Приложение — что показал второй прогон

Переписанные промпты сработали там, где дело касалось композиции и движения, и
провалились там, где требовалась дисциплина исполнения. Шесть экранов экспорта:
`nocturne_noise_field_substrate`, `nocturne_idle_noise_field`,
`nocturne_settings_sheet` и три варианта поля.

**Сработало.** Поле стало `<canvas>` на `absolute inset-x-0 bottom-0 top-[52%]
z-0` — подложкой нижней половины, с контролами на `z-10` поверх. Токены
`background / surface / surface-container-lowest` переехали на `#000000`. P3 дал
точный диф вместо нового экрана: 34 волны → 14, амплитуда вдвое ниже. P6a/b/c
изменили **только** движок canvas — сверка id-элементов с базовым экраном даёт
ноль отличий во всех трёх. Шторка воспроизвела все семь пресетов и пять
тумблеров с текстами буквально из `strings.xml`, без Save и Cancel.

**Провалилось — и каждый раз по вине документа, а не инструмента.**

**1. Цепочка оборвалась на механике, а не на формулировках.** Шаги P2c и P2d не
были применены ни к одному экрану: ряд таймера остался на главном экране во всех
шести файлах, а мета-строки нет нигде — `grep -c "dB/OCT"` даёт ноль по всем. То
есть требование «мета-информация поверх анимации» в экспорте отсутствовало
целиком. Документ не объяснял, что каждая правка порождает новый экран и что
перед следующим шагом надо выбрать результат предыдущего. Отсюда врезка в
начале [цепочки P2](#цепочка-p2--доводка-понравившегося-экрана).

**2. Правка копи не достаёт до скрытого текста.** После P2a в шести экранах из
шести уцелели `aria-label="Toggle womb soundscape"`, `data-time="Cry Sentry"`,
`aria-label="Screen Lock Activated"`, «Face-down to mute display» и выдуманные
`data-desc` — настоящих описаний голосов не было ни одного. Отсюда
[P2a-bis](#p2a-bis--правда-в-скрытом-тексте-и-в-состояниях).

**3. Правка копи не достаёт до состояний.** Шумомер исчез из разметки и остался
в обработчике: `statusHint.textContent = "Playing softly • 42 dB"`. Два тапа по
орбу — и число снова на экране.

**4. System lock держал раскладку, но не цвета.** Шторка собралась на тринадцати
литеральных хексах без единого токена, причём часть палитры пришла из экранов
первого прогона, уже удалённых из экспорта: проект Stitch помнит удалённые
экраны. Отсюда две новые строки в
[блоке System lock](#блок-1--system-lock).

**5. Два запрета в Prohibitions были написаны неверно.** «Nothing lighter than
#353535» запрещал читаемый текст и нарушался повсеместно и безвредно — сама
система держит `outline: #a08e7d` и `on-surface: #e2e2e2`. А порог «12px для
существенного текста» конфликтовал с `label-sm` = 10px из `DESIGN.md`: Stitch
выбрал систему и посадил на 10px все восемь интерактивных контролов главного
экрана. Оба пункта переформулированы.

**Вывод:** второй прогон показал, что проблема сместилась с формулировок на
исполнение. Промпты стали правильными, но документ не описывал ни порядок их
применения в интерфейсе, ни то, что у сгенерированного экрана есть слои, до
которых правка видимого текста не доходит, — разметка, атрибуты доступности и
обработчики. Проверять результат надо не только глазами по скриншоту, но и
grep'ом по выгруженному HTML.

---

## Источники

- [Stitch Prompt Guide — Google AI Developers Forum](https://discuss.ai.google.dev/t/stitch-prompt-guide/83844)
- [Google Stitch Complete Guide — fysalyaqoob.com](https://www.fysalyaqoob.com/guides/google-stitch-complete-guide)
- [Vibe Design, Voice Canvas и режимы — nxcode.io](https://www.nxcode.io/resources/news/google-stitch-complete-guide-vibe-design-2026)
- [Google Stitch Review: реальные ограничения — moda.app](https://moda.app/blog/google-stitch-review)
- [From Prompt to UI with Google Stitch — openreplay.com](https://blog.openreplay.com/prompt-ui-google-stitch/)
- `stitch_nightlight_hush.zip` — экспорт **второго** прогона:
  `nocturne_noise_field_substrate/` (главный экран, playing, и оба движка
  анимации), `nocturne_idle_noise_field/` (состояние idle),
  `nocturne_settings_sheet/` (шторка), `nocturne_flowing_noise_field/`,
  `nocturne_dune_parallax_noise_field/`, `nocturne_spectral_grid_noise_field/`
  (три варианта поля), `nocturne_sleep_engine/DESIGN.md` (design system).
  Экраны первого прогона — `nocturne_white_noise/`, `nocturne_idle/`,
  `nocturne_playing/` — в этом архиве отсутствуют: Stitch выгружает только живые
  экраны проекта.
- У трёх вариантов поля `screen.png` — пустышки по 28 байт. Сравнить варианты
  глазами можно только открыв их `code.html` в браузере.
