# The DSP classes are plain Kotlin and carry no reflection, so the defaults are
# enough. Keep the rules file around so release builds stay reproducible if a
# dependency with reflective access is added later.

# The developer tuning panel is reachable only from inside `if (BuildConfig.DEBUG)`
# branches. R8 normally propagates that final field's value and drops those
# branches anyway; stating it here makes the elimination a guarantee rather than
# an optimisation that happens to fire. What goes with it: DevTuningPanel,
# DevTuning, DevTuningRepository and the snippet writer.
-assumevalues class ru.noisedune.BuildConfig {
    boolean DEBUG return false;
}
