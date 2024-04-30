BPEventTypes is a SuperCollider quark that adds the following event types:
- `\preset`: an alternative to SynthDef variants that provides a way for users to use presets in Events and Patterns.
- `\echo`: allows users to add language-side delay effects in Events and Patterns.
- `\presetEcho`: combines the functionality of the above two event types.

More event types may be added in the future, and contributions are welcome!

I've also added a few classes to help with presets:
- `Preset`: a class for building and storing global presets.
- `PresetChain`: a way to chain together presets.
- `PC`: an alias for `PresetChain`.
- `SynthPreset`: a way to use presets in Synths.

To install, run `Quarks.install("https://github.com/JaimeClover/BPEventTypes")`
