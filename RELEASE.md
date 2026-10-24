# Releasing

How a build gets from this repository into RuStore. The listing itself — every
field, every asset, and the reasoning behind the wording — lives in
[store/](store/); this file is the mechanics.

What has to happen outside a terminal, and can only be done by whoever owns the
account, is in [store/checklist.md](store/checklist.md). Start there: the
keystore in step 1 below is the first item on it, and two of the listing fields
cannot be filled until an email address and a published policy URL exist.

## The signing key

Once, and then never again for the life of the app. Every future version has to
carry this same signature — RuStore refuses an update signed by anything else,
and so does Android itself. Lose the store or its password and `ru.noisedune`
becomes unusable: the listing has to be recreated under a new application id,
from zero installs and zero ratings. There is no recovery procedure and no
support ticket that fixes it.

```bash
keytool -genkeypair -v -keystore ~/keys/noise-dune-release.jks -storetype PKCS12 -alias noise-dune -keyalg RSA -keysize 4096 -validity 36500
```

RSA 2048 is RuStore's floor; 4096 costs nothing when the key is used once per
release. The 100-year validity is not optimism about the app — a certificate
that expires takes the ability to update with it.

Back it up in two places that are not this machine, together with the passwords,
and keep it out of the repository: `.gitignore` covers `*.jks`, `*.p12` and
`keystore.properties`, but that is the second line of defence, not the first.

Then point the build at it:

```bash
cp keystore.properties.example keystore.properties
```

Fill in the absolute path and the two passwords. Without this file the release
build still succeeds and comes out unsigned, which is the old behaviour and a
deliberate one: a fresh clone builds, and a missing key fails at the signature
rather than at the build.

## The build

```bash
./gradlew clean :app:testDebugUnitTest :app:assembleRelease
```

43 tests, then an APK at `app/build/outputs/apk/release/app-release.apk`, about
1.3 MB. R8 and resource shrinking are on for release; `proguard-rules.pro`
asserts `BuildConfig.DEBUG` is false so the tuning panel is removed as a
guarantee rather than as an optimisation that happened to fire.

**APK, not AAB.** The reasoning is in [docs/decisions.md](docs/decisions.md);
the short version is that this app has nothing an App Bundle could split, and
the AAB path would add a second key to manage forever.

## Checking the APK before anybody else sees it

Everything here is one command and answers a question a reviewer could ask.
`$BT` is the build-tools directory, e.g. `$ANDROID_HOME/build-tools/37.0.0`.

```bash
$BT/apksigner verify --print-certs --verbose app/build/outputs/apk/release/app-release.apk
```

Expect `Verified using v2 scheme: true`, one signer, RSA 4096, and this
certificate — the one 1.0 shipped with, and so the one every later version has
to carry:

```
CN=Mikhail Kopytin, O=Noise Dune, C=RU
SHA-256 90638a857da710a730258ff3c3e0b3659d271803a55f46409860bb8903626848
```

A different fingerprint means a different key, and the store will refuse the
update. Catching it here costs a rebuild; catching it at the upload costs a
moderation round. The certificate is public — it is inside every APK — so it is
safe to keep here; the key and its password are not, and are not.

```bash
$BT/aapt2 dump badging app/build/outputs/apk/release/app-release.apk | grep -E "^package|uses-permission|SdkVersion|debuggable"
```

Expect `minSdkVersion:'26'`, `targetSdkVersion:'36'`, a `versionCode` higher than
the published one, **no** `application-debuggable`, and exactly these
permissions:

```
android.permission.FOREGROUND_SERVICE
android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK
android.permission.POST_NOTIFICATIONS
ru.noisedune.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION
```

The fourth is AndroidX's own, declared at `signature` level by the merged
manifest; [store/data-safety.md](store/data-safety.md) explains it. What must
never appear is `android.permission.INTERNET` — the claim that the app cannot
send anything anywhere rests on its absence, and that claim is made in the
listing, in the privacy policy and to the moderators.

```bash
unzip -p app/build/outputs/apk/release/app-release.apk classes.dex | strings | grep -cE "Factory values|Copy Kotlin|Reset to factory"
```

Expect `0`: the developer tuning panel is not in the release build.

Then install it and go through the by-hand list in
[README.md](README.md#what-the-tests-cannot-cover) — the service, the
notification, audio focus and the Compose UI need a device, and none of the
above touches them.

## The listing

Everything to paste is in [store/listing-rustore.md](store/listing-rustore.md),
in the order the console asks for it. Check the field lengths first; the console
refuses to save an overrun after you have written the paragraph, not while:

```bash
python3 tools/check-listing.py
```

Regenerate the assets if the interface has changed since the last release:

```bash
tools/store-assets.sh
```

The icon needs nothing but Python; the screenshots need a running emulator or
phone with the app installed, and the script says so rather than failing
halfway.

Then, in the console:

1. **Applications → Add application**, name `Шум Дюн`.
2. **Upload version**, type Universal, the signed APK. The minimum Android
   version fills itself in from the manifest.
3. The text fields, the category, the age rating and the five tags, from
   `store/listing-rustore.md`.
4. The icon and at least three screenshots, from `store/assets/`.
5. **Data safety**: nothing is collected, nothing is transmitted. The answers
   and the justification for each permission are in
   [store/data-safety.md](store/data-safety.md).
6. The privacy policy URL and a contact channel. Both are marked `ЗАПОЛНИТЬ` in
   the listing until they exist.
7. Submit for moderation. The rules give up to 72 hours; two to three days is
   what it takes in practice. A first publication is the one most likely to come
   back with questions — [store/moderation-notes.md](store/moderation-notes.md)
   answers the ones this app attracts.

## After it is published

- `versionCode` has to increase on every upload. `versionName` is for people.
- The same keystore signs every future version, in RuStore and in any other
  store the app ever appears in. Signing a second store with a second key means
  the app can never be updated across the two.
- An app untouched for twelve months may be reviewed for removal if it no longer
  meets the requirements by then.
- Keep the published version in step with whatever is distributed elsewhere;
  the rules ask for that explicitly.
