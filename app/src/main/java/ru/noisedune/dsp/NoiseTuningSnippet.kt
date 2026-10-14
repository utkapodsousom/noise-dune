package ru.noisedune.dsp

/**
 * Renders a [NoiseTuning] as Kotlin that can be pasted over the `Params`
 * defaults it came from.
 *
 * This is what makes the tuning panel a tool rather than a toy: a number
 * settled by ear at 3 a.m. is worth nothing if it dies with the process. The
 * output is deliberately a diff against the shipped constants — every changed
 * line carries `// was ...`, and the header says where the numbers came from —
 * because a constant without its provenance would be out of place in this
 * codebase, where every one of them currently cites a measurement.
 *
 * Pure Kotlin with no Android imports, so it is unit-testable; the panel only
 * puts the string somewhere.
 */
fun NoiseTuning.toKotlinSnippet(sampleRate: Int, device: String): String {
    val factory = NoiseTuning.FACTORY
    val out = StringBuilder()

    out.appendLine("// Tuned by ear on $device at $sampleRate Hz.")
    out.appendLine("// Paste over the Params defaults, then re-run ./gradlew test:")
    out.appendLine("// NoiseSpectrumTest and NoiseCalibrationTest report the new measured")
    out.appendLine("// slopes and levels. Update the expectations, the KDoc on each number")
    out.appendLine("// and the tables in docs/decisions.md from what they print — do not")
    out.appendLine("// widen a tolerance to make a by-ear number pass.")
    if (this == factory) {
        out.appendLine()
        out.appendLine("// Nothing is off factory: this is the shipped sound verbatim.")
        return out.toString()
    }

    out.appendLine()
    out.appendLine("// --- PinkNoise.Params")
    out.appendLine(intLine("rows", pink.rows, factory.pink.rows))
    out.appendLine(doubleLine("dcBlockHz", pink.dcBlockHz, factory.pink.dcBlockHz))

    out.appendLine()
    out.appendLine("// --- BrownNoise.Params")
    out.appendLine(doubleLine("integratorHz", brown.integratorHz, factory.brown.integratorHz))
    out.appendLine(doubleLine("highPassHz", brown.highPassHz, factory.brown.highPassHz))
    out.appendLine(doubleLine("highPassQ", brown.highPassQ, factory.brown.highPassQ))

    out.appendLine()
    out.appendLine("// --- ShushNoise.Params")
    out.appendLine(doubleLine("hpHz", shush.hpHz, factory.shush.hpHz))
    out.appendLine(doubleLine("f1Hz", shush.f1Hz, factory.shush.f1Hz))
    out.appendLine(doubleLine("f1Q", shush.f1Q, factory.shush.f1Q))
    out.appendLine(doubleLine("f2Hz", shush.f2Hz, factory.shush.f2Hz))
    out.appendLine(doubleLine("f2Q", shush.f2Q, factory.shush.f2Q))
    out.appendLine(
        floatLine(
            "upperFormantGain",
            shush.upperFormantGain,
            factory.shush.upperFormantGain,
        ),
    )
    out.appendLine(doubleLine("lpHz", shush.lpHz, factory.shush.lpHz))
    out.appendLine(doubleLine("amRateHz", shush.amRateHz, factory.shush.amRateHz))
    out.appendLine(floatLine("amDepth", shush.amDepth, factory.shush.amDepth))

    out.appendLine()
    out.appendLine("// --- NoiseType, with each trim multiplied into the calibration.")
    out.appendLine("// The trim is a by-ear offset; the enum holds one absolute gain, so it")
    out.appendLine("// belongs folded in rather than carried separately.")
    for (type in NoiseType.entries) {
        out.appendLine(calibrationLine(type))
    }

    return out.toString()
}

/**
 * One enum constant, with the trim folded into the measured calibration and the
 * arithmetic shown so the next reader can see where the number came from.
 */
private fun NoiseTuning.calibrationLine(type: NoiseType): String {
    val trimDb = trimDb(type)
    val tuned = type.calibration * trimAmplitude(type)
    val code = "${type.name}(${format(tuned.toDouble())}f),"
    if (trimDb == 0f) return "$ENUM_INDENT$code"
    val sign = if (trimDb > 0f) "+" else ""
    // The trim at full precision, not rounded for display: the whole value of
    // showing the arithmetic is that it reproduces the number beside it.
    val arithmetic = "${format(type.calibration.toDouble())} " +
        "x $sign${format(trimDb.toDouble())} dB"
    return "$ENUM_INDENT${code.padEnd(COMMENT_COLUMN)} // $arithmetic"
}

private fun intLine(name: String, value: Int, factory: Int): String =
    line("val $name: Int = $value,", value != factory, factory.toString())

private fun doubleLine(name: String, value: Double, factory: Double): String =
    line(
        "val $name: Double = ${format(value)},",
        value != factory,
        format(factory),
    )

private fun floatLine(name: String, value: Float, factory: Float): String =
    line(
        "val $name: Float = ${format(value.toDouble())}f,",
        value != factory,
        "${format(factory.toDouble())}f",
    )

private fun line(code: String, changed: Boolean, was: String): String =
    if (changed) {
        "$PARAM_INDENT${code.padEnd(COMMENT_COLUMN)} // was $was"
    } else {
        "$PARAM_INDENT$code"
    }

/**
 * Enough digits to reproduce the sound, not enough to suggest a precision the
 * ear supplied. Trailing zeros are kept off, but a `.0` is kept on so the
 * literal stays a `Double`.
 */
private fun format(value: Double): String {
    val rounded = String.format(java.util.Locale.ROOT, "%.6f", value)
        .trimEnd('0')
    return if (rounded.endsWith('.')) rounded + "0" else rounded
}

private const val COMMENT_COLUMN = 34

/** Constructor parameters sit two levels in; enum constants sit one. */
private const val PARAM_INDENT = "        "
private const val ENUM_INDENT = "    "
