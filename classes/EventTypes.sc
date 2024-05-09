EventTypes {

    classvar <keyOverrides;
    classvar <>useKeyOverrides = true;
    classvar <>useControlDefaults = false;
    classvar <>defaultSymbols, <>presetSymbols;

    *initClass {
        var makeScaleFrom, degreeToNote, noteToMidinote;

        Class.initClassTree(Event);

        // helper functions for degree -> note -> midinote conversions:
        makeScaleFrom = { arg scale;
            scale.isKindOf(Scale).if(
                {scale},
                {Scale(scale)}
            );
        };

        degreeToNote = { arg scaleDegree;
            var scale, degree, accidental, baseNote;
            scale = makeScaleFrom.(~scale);
            degree = scaleDegree.round.asInteger;
            accidental = (scaleDegree - degree) * 10.0;
            baseNote = (scale.pitchesPerOctave * (degree div: scale.size)) + scale.degrees.wrapAt(degree);
            baseNote + accidental;
        };

        noteToMidinote = {arg note;
            var scale, tunedNote, midiOctave;
            scale = makeScaleFrom.(~scale);
            tunedNote = scale.stepsPerOctave * (note div: scale.pitchesPerOctave) + scale.tuning.wrapAt(note);
            midiOctave = tunedNote / scale.stepsPerOctave + ~octave - 5.0;
            midiOctave * scale.stepsPerOctave + 60.0;
        };


        // This parent type allows the \note key to access scale tunings properly:
        Event.addParentType(\note,
            Event.parentTypes[\note] ++ (
                note: { degreeToNote.(~degree + ~mtranspose) },
                midinote: { noteToMidinote.(~note.value + ~gtranspose + ~root) },
            )
        );


        // initialize class variables used by the \preset event type:
        this.initKeyOverrides;
        defaultSymbols = [\x];
        presetSymbols = [\preset, \p];


        // add some custom event types:
        Event.addEventType(\preset, {arg server;
            var synthLib, desc, defaults, localPreset, globalPreset, preset;
            var useDefaults, useOverrides;

            synthLib = ~synthLib ?? {SynthDescLib.global};
            desc = synthLib.at(~instrument.asDefName);
            useDefaults = ~useControlDefaults ? useControlDefaults;
            useOverrides = ~useKeyOverrides ? useKeyOverrides;

            if(useDefaults == true) {
                defaults = desc.controlDict.collect(_.defaultValue);
            } {
                defaults = ();
            };

            preset = Preset.build(~preset, desc, useOverrides);
            ~preset = \changedToSomethingRandomToAvoidMultiChannelExpansionIssues;

            if(useOverrides == true) {
                this.resolveOverrides(preset, defaults, defaultSymbols);
            };

            preset = preset.reject{|val| defaultSymbols.includes(val)};
            preset = defaults.putAll(preset);

            if(useOverrides == true) {
                this.resolveOverrides(currentEnvironment, preset, presetSymbols);
            };

            currentEnvironment = currentEnvironment.reject{|val| presetSymbols.includes(val)};
            currentEnvironment.proto = preset;

            this.prChainEventType(server);
        });

        Event.addEventType(\echo, {arg server;
            var numNotes, echoTime, echoCoef, lag, echoPan;

            numNotes = (~numEchoes ? 0).asInteger.max(0) + 1;
            echoTime = ~echoTime ?? {thisThread.clock.beatDur / 2};
            echoCoef = ~echoCoef ? 0.5;

            echoPan = case
            {~echoPan.isKindOf(Array)} {~echoPan}
            {~echoPan.asSymbol == \rand} {{1.0.rand2} ! (numNotes - 1)}
            {~echoPan.asSymbol == \gauss} {{1.0.sum3rand} ! (numNotes - 1)}
            {~echoPan.asSymbol == \lr} {[-1, 1]}
            {~echoPan.asSymbol == \rl} {[1, -1]};
            echoPan = echoPan ? [0];
            echoPan = (~echoSpread ? 1) * echoPan;

            lag = ~lag ? 0;
            ~lag = lag.abs + Array.series(numNotes, 0, echoTime.abs);
            ~amp = ~amp.value * Array.geom(numNotes, 1, echoCoef);
            ~pan = [0] ++ echoPan.wrapExtend(numNotes - 1) + ~pan;

            this.prChainEventType(server);
        });

        Event.addEventType(\presetEcho, {arg server;
            ~chainedEventTypes = [\preset, \echo];
            this.prChainEventType(server);
        });
    }

    *initKeyOverrides {
        keyOverrides = (
            freq: [\detunedFreq],
            mtranspose: [\freq, \detunedFreq, \midinote, \note],
            gtranspose: [\freq, \detunedFreq, \midinote],
            ctranspose: [\freq, \detunedFreq],
            octave: [\freq, \detunedFreq, \midinote],
            root: [\freq, \detunedFreq, \midinote],
            degree: [\freq, \detunedFreq, \midinote, \note],
            scale: [\freq, \detunedFreq, \midinote, \note],
            stepsPerOctave: [\freq, \detunedFreq, \midinote, \note],
            detune: [\detunedFreq],
            harmonic: [\freq, \detunedFreq],
            octaveRatio: [\freq, \detunedFreq, \midinote],
            note: [\freq, \detunedFreq, \midinote],
            midinote: [\freq, \detunedFreq],
            dur: [\sustain],
            legato: [\sustain],
            stretch: [\sustain],
            db: [\amp]
        );
    }

    *resolveOverrides {|overEvent, underEvent, exclusionList|
        keyOverrides.keysValuesDo {|key, overriddenKeys|
            if(overEvent.keys.includes(key)) {
                if(exclusionList.includes(overEvent[key]).not) {
                    overriddenKeys.do {|overriddenKey| underEvent.removeAt(overriddenKey)};
                }
            }
        }
    }

    *prChainEventType {arg server;
        var nextEventType, chainedEventTypes;
        chainedEventTypes = ~chainedEventTypes.copy ? [];
        nextEventType = chainedEventTypes.obtain(0, \note);
        ~chainedEventTypes = chainedEventTypes[1..];
        currentEnvironment.parent = Event.parentTypes.atFail(nextEventType, Event.default);
        ~eventTypes[nextEventType].value(server);
    }
}
