EventTypes {

    classvar <keyOverrides, <>defaultArpKeys;
    classvar <>useKeyOverrides = true;
    classvar <>useControlDefaults = false;
    classvar <>defaultSymbols, <>presetSymbols;
    classvar <>alternateTuning;
    classvar <>eventTypesDict;


    *useAlternateTuning {
        Event.addParentType(\note,
            Event.parentTypes[\note] ++ alternateTuning.copy;
        )
    }

    *useDefaultTuning {
        Event.parentTypes[\note].removeAt(\note);
        Event.parentTypes[\note].removeAt(\midinote);
    }

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

        alternateTuning = (
            note: { degreeToNote.(~degree.value + ~mtranspose.value) },
            midinote: { noteToMidinote.(~note.value + ~gtranspose.value + ~root.value) },
        );

        // alternateTuning.postln;

        // This parent type allows the \note key to access scale tunings properly:
        /*Event.addParentType(\note,
            Event.parentTypes[\note] ++ (
                note: { degreeToNote.(~degree + ~mtranspose) },
                midinote: { noteToMidinote.(~note.value + ~gtranspose + ~root) },
            )
        );*/


        // initialize class variables used by the \preset event type:
        this.initKeyOverrides;
        defaultSymbols = [\x];
        presetSymbols = [\preset, \p];
        defaultArpKeys = Set[
            \mtranspose, \gtranspose, \ctranspose,
            \octave, \root, \detune, \harmonic,
            \degree, \note, \midinote, \freq
        ];


        // add custom event types:

        eventTypesDict = ();

        /*Event.addEventType(\tunedNote, {arg server;
            var makeScaleFrom, degreeToNote, noteToMidinote;

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

            "here".postln;

            /*if(currentEnvironment.includesKey(\note).not.postln) {
                ~note = { "a".postln; degreeToNote.(~degree.value + ~mtranspose.value) };
            };*/

            if(currentEnvironment.includesKey(\midinote).not.postln) {
                ~midinote = { "b".postln; noteToMidinote.(~note.value + ~gtranspose.value + ~root.value) };
            };

            ~tunedNote = ~tunedNote ? ~note;
            ~tunedNote = ~tunedNote ? {"a".postln; degreeToNote.(~degree.value + ~mtranspose.value) };
            if(~tunedMidinote.isNil) {
                ~tunedMidinote = { "b".postln; noteToMidinote.(~note.value + ~gtranspose.value + ~root.value) };
            };

            this.prChainEventType(server);
        });*/

        eventTypesDict.put(\preset, {arg server;
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
            currentEnvironment.proto = (currentEnvironment.proto ?? IdentityDictionary.new).proto_(preset);

            this.prChainEventType(server);
        });

        eventTypesDict.put(\echo, {arg server;
            var numNotes, echoTime, echoCoef, timingOffset, echoPan;

            numNotes = (~numEchoes ? 0).asInteger.max(0) + 1;
            echoTime = ~echoTime ? 0.5;
            echoCoef = ~echoCoef ? 0.5;

            echoPan = case
            {~echoPan.isKindOf(Array)} {~echoPan}
            {~echoPan.asSymbol == \rand} {{1.0.rand2} ! (numNotes - 1)}
            {~echoPan.asSymbol == \gauss} {{1.0.sum3rand} ! (numNotes - 1)}
            {~echoPan.asSymbol == \lr} {[-1, 1]}
            {~echoPan.asSymbol == \rl} {[1, -1]};
            echoPan = echoPan ? [0];
            echoPan = (~echoSpread ? 1) * echoPan;

            timingOffset = ~timingOffset ? 0;
            ~timingOffset = timingOffset + Array.series(numNotes, 0, echoTime.abs);
            ~amp = ~amp.value * Array.geom(numNotes, 1, echoCoef);
            ~pan = [0] ++ echoPan.wrapExtend(numNotes - 1) + ~pan;

            this.prChainEventType(server);
        });

        eventTypesDict.put(\arp, {arg server;
            var maxSize, arpKeys;

            arpKeys = ~arpKeys ? this.defaultArpKeys;
            maxSize = (currentEnvironment.select{|v, k| arpKeys.includes(k)}.maxValue{|item, i| item.size} ? 1).max(1);
            ~subdivisions = (~subdivisions.value ? maxSize).max(1);

            ~hop = (~hop ? 1).asArray;
            ~skip = (~skip ? 0).asArray;
            ~jump = (~jump ? 0).asArray;
            ~jump = ~subdivisions.div(maxSize).collect{|i| ~jump.wrapAt(i)};
            ~jump = [0] ++ ~jump;

            currentEnvironment.keysValuesChange{|k, v|
                if(v.isKindOf(Array) and: {arpKeys.includes(k)}){
                    var scrambled, result, size, mode;
                    size = v.size;
                    scrambled = v.scramble;
                    mode = ~mode ? \fwd;
                    result = case
                    {mode == \fwd} {~subdivisions.collect{|i|
                        v.wrapAt(i * ~hop.wrapAt(i) + ~skip.wrapAt(i) + ~jump.wrapAt(i.div(size)))
                    }}
                    {mode == \rev} {~subdivisions.collect{|i|
                        v.wrapAt(~skip.wrapAt(i).neg - 1 - (i * ~hop.wrapAt(i)) - ~jump.wrapAt(i.div(size)))
                    }}
                    {mode == \fwdRev} {~subdivisions.collect{|i|
                        v.foldAt(i * ~hop.wrapAt(i) + ~skip.wrapAt(i) + ~jump.wrapAt(i.div(size)))
                    }}
                    {mode == \revFwd} {~subdivisions.collect{|i|
                        v.foldAt(i * ~hop.wrapAt(i) + ~skip.wrapAt(i) + ~jump.wrapAt(i.div(size)) + size - 1)
                    }}
                    {mode == \up} {~subdivisions.collect{|i|
                        v.sort.wrapAt(i * ~hop.wrapAt(i) + ~skip.wrapAt(i) + ~jump.wrapAt(i.div(size)))
                    }}
                    {mode == \down} {~subdivisions.collect{|i|
                        v.sort.wrapAt(~skip.wrapAt(i).neg - 1 - (i * ~hop.wrapAt(i)) - ~jump.wrapAt(i.div(size)))
                    }}
                    {mode == \upDown} {~subdivisions.collect{|i|
                        v.sort.foldAt(i * ~hop.wrapAt(i) + ~skip.wrapAt(i) + ~jump.wrapAt(i.div(size)))
                    }}
                    {mode == \downUp} {~subdivisions.collect{|i|
                        v.sort.foldAt(i * ~hop.wrapAt(i) + ~skip.wrapAt(i) + ~jump.wrapAt(i.div(size)) + size - 1)
                    }}
                    {mode == \shuf} {~subdivisions.collect{|i| scrambled.wrapAt(i)}}
                    {mode == \rand} {~subdivisions.collect{|i| v.choose}}
                    {mode == \xrand} {
                        var index = size.rand;
                        ~subdivisions.collect{|i|
                            index = (index + (size - 1).rand + 1) % size;
                            v.at(index)
                        }
                    }
                    {mode == \wrand} {
                        ~subdivisions.collect{|i| v.wchoose(~weights ? (1 ! size / size))}
                    }
                    {mode.isKindOf(SequenceableCollection)} {~subdivisions.collect{|i|
                        v.wrapAt(~mode.wrapAt(i * ~hop.wrapAt(i) + ~skip.wrapAt(i) + ~jump.wrapAt(i.div(size))))
                    }};
                    result;
                } { v }
            };

            ~octavate = (~octavate ? 0).asArray.collect(_.asInteger);
            ~octavate = ~subdivisions.div(maxSize).collect{|i| ~octavate.wrapAt(i) * (i + 1)};
            ~octavate = [0] ++ ~octavate;
            ~maxOctaves = ~maxOctaves ? 8;
            ~octavate = ~octavate.fold2(~maxOctaves);
            ~octaves = ~octaves ? ~octavate;
            ~octave = ~octave + ~subdivisions.collect{|i| ~octaves.wrapAt(i.div(maxSize))};

            ~arpDurs = (~arpDurs ? 1).asArray;
            ~arpDurs = ~subdivisions.collect{|i| ~arpDurs.wrapAt(i)}.normalizeSum.integrate;
            ~arpDurs = ~arpDurs - ~arpDurs[0];
            ~timingOffset = ~timingOffset ? 0;
            ~timingOffset = ~arpDurs * ~legato + ~timingOffset;
            // ~timingOffset = ~subdivisions.collect{|i| i / ~subdivisions * ~legato} + ~timingOffset;
            ~sustain = ~sustain / ~subdivisions * (~legatoEach ? 1).max(0.03 * thisThread.clock.tempo);
            ~amp = ~amp.value * (~ampSieve ? 1);

            this.prChainEventType(server);
        });

        eventTypesDict.put(\presetEcho, {arg server;
            ~chainedEventTypes = [\preset, \echo];
            this.prChainEventType(server);
        });

        eventTypesDict.put(\presetArp, {arg server;
            ~chainedEventTypes = [\preset, \arp];
            this.prChainEventType(server);
        });

        eventTypesDict.put(\arpEcho, {arg server;
            var maxSize, arpKeys;
            var numNotes, echoTime, echoCoef, timingOffset, echoPan, legatoEach;

            numNotes = (~numEchoes ? 0).asInteger.max(0) + 1;
            echoTime = ~echoTime ? 0.5;
            echoCoef = ~echoCoef ? 0.5;

            echoPan = case
            {~echoPan.isKindOf(Array)} {~echoPan}
            {~echoPan.asSymbol == \rand} {{1.0.rand2} ! (numNotes - 1)}
            {~echoPan.asSymbol == \gauss} {{1.0.sum3rand} ! (numNotes - 1)}
            {~echoPan.asSymbol == \lr} {[-1, 1]}
            {~echoPan.asSymbol == \rl} {[1, -1]};
            echoPan = echoPan ? [0];
            echoPan = (~echoSpread ? 1) * echoPan;

            timingOffset = ~timingOffset ? 0;
            ~timingOffset = timingOffset + Array.series(numNotes, 0, echoTime.abs);
            ~amp = ~amp.value * Array.geom(numNotes, 1, echoCoef);
            ~pan = [0] ++ echoPan.wrapExtend(numNotes - 1) + ~pan;
            legatoEach = (~legatoEach ? 1).asArray.wrapExtend(numNotes);

            arpKeys = ~arpKeys ? this.defaultArpKeys;
            maxSize = (currentEnvironment.select{|v, k| arpKeys.includes(k)}.maxValue{|item, i| item.size} ? 1).max(1);
            ~subdivisions = (~subdivisions.value ? maxSize).max(1).asInteger;

            ~hop = (~hop ? 1).asArray;
            ~skip = (~skip ? 0).asArray;
            ~jump = (~jump ? 0).asArray;
            ~jump = ~subdivisions.div(maxSize).collect{|i| ~jump.wrapAt(i)};
            ~jump = [0] ++ ~jump;

            currentEnvironment.keysValuesChange{|k, v|
                if(v.isKindOf(Array) and: {arpKeys.includes(k)}){
                    var scrambled, result, size, mode;
                    size = v.size;
                    scrambled = v.scramble;
                    mode = ~mode ? \fwd;
                    result = case
                    {mode == \fwd} {~subdivisions.collect{|i|
                        v.wrapAt(i * ~hop.wrapAt(i) + ~skip.wrapAt(i) + ~jump.wrapAt(i.div(size)))
                    }}
                    {mode == \rev} {~subdivisions.collect{|i|
                        v.wrapAt(~skip.wrapAt(i).neg - 1 - (i * ~hop.wrapAt(i)) - ~jump.wrapAt(i.div(size)))
                    }}
                    {mode == \fwdRev} {~subdivisions.collect{|i|
                        v.foldAt(i * ~hop.wrapAt(i) + ~skip.wrapAt(i) + ~jump.wrapAt(i.div(size)))
                    }}
                    {mode == \revFwd} {~subdivisions.collect{|i|
                        v.foldAt(i * ~hop.wrapAt(i) + ~skip.wrapAt(i) + ~jump.wrapAt(i.div(size)) + size - 1)
                    }}
                    {mode == \up} {~subdivisions.collect{|i|
                        v.sort.wrapAt(i * ~hop.wrapAt(i) + ~skip.wrapAt(i) + ~jump.wrapAt(i.div(size)))
                    }}
                    {mode == \down} {~subdivisions.collect{|i|
                        v.sort.wrapAt(~skip.wrapAt(i).neg - 1 - (i * ~hop.wrapAt(i)) - ~jump.wrapAt(i.div(size)))
                    }}
                    {mode == \upDown} {~subdivisions.collect{|i|
                        v.sort.foldAt(i * ~hop.wrapAt(i) + ~skip.wrapAt(i) + ~jump.wrapAt(i.div(size)))
                    }}
                    {mode == \downUp} {~subdivisions.collect{|i|
                        v.sort.foldAt(i * ~hop.wrapAt(i) + ~skip.wrapAt(i) + ~jump.wrapAt(i.div(size)) + size - 1)
                    }}
                    {mode == \shuf} {~subdivisions.collect{|i| scrambled.wrapAt(i)}}
                    {mode == \rand} {~subdivisions.collect{|i| v.choose}}
                    {mode == \xrand} {
                        var index = size.rand;
                        ~subdivisions.collect{|i|
                            index = (index + (size - 1).rand + 1) % size;
                            v.at(index)
                        }
                    }
                    {mode == \wrand} {
                        ~subdivisions.collect{|i| v.wchoose(~weights ? (1 ! size / size))}
                    }
                    {mode.isKindOf(SequenceableCollection)} {~subdivisions.collect{|i|
                        v.wrapAt(~mode.wrapAt(i * ~hop.wrapAt(i) + ~skip.wrapAt(i) + ~jump.wrapAt(i.div(size))))
                    }};
                    result.dupEach(numNotes);
                } { v }
            };

            ~octavate = (~octavate ? 0).asArray.collect(_.asInteger);
            ~octavate = ~subdivisions.div(maxSize).collect{|i| ~octavate.wrapAt(i) * (i + 1)};
            ~octavate = [0] ++ ~octavate;
            ~octave = ~octave + ~subdivisions.collect{|i| ~octavate.wrapAt(i.div(maxSize))};
            ~octave = ~octave.keep(~subdivisions).dupEach(numNotes);

            ~arpDurs = (~arpDurs ? 1).asArray;
            ~arpDurs = ~subdivisions.collect{|i| ~arpDurs.wrapAt(i)}.normalizeSum.integrate;
            ~arpDurs = ~arpDurs - ~arpDurs[0];
            ~timingOffset = ~arpDurs * ~legato +.x ~timingOffset;
            // ~timingOffset.postln;
            // ~timingOffset = ~subdivisions.collect{|i| i / ~subdivisions * ~legato} +.x ~timingOffset;
            ~amp = (~subdivisions.collect{ ~amp * (~echoSieve ? 1) } * (~arpSieve ? 1)).flat * (~ampSieve ? 1);
            ~pan = ~subdivisions.collect{ ~pan }.flat;
            legatoEach = ~subdivisions.collect{ ~legatoEach }.flat;
            ~sustain = ~sustain / ~subdivisions.max(1) * ~legatoEach.max(0.1);

            this.prChainEventType(server);
        });

        eventTypesDict.put(\presetArpEcho, {arg server;
            ~chainedEventTypes = [\preset, \arpEcho];
            this.prChainEventType(server);
        });
    }

    *includeEventType { arg name, alias, overwrite = false;
        if( name.isNil ) { EventTypes.includeAllEventTypes(overwrite) } {
            if( name.isString ) { name = name.asSymbol };
            if( alias.isString ) { alias = alias.asSymbol };
            name = name.asArray;
            alias = alias.asArray;
            if( alias.size != name.size ) { "name and alias must be the same size".error; ^nil } {
                name.do {|nm, i|
                    if( overwrite.not && Event.partialEvents.playerEvent.eventTypes.keys.includes(alias[i]) ) {
                        var msg = "% event type already exists. Use 'overwrite = true' to overwrite it.".format(alias[i]);
                        msg.warn;
                    } {
                        Event.addEventType(alias[i] ? nm, eventTypesDict[nm]);
                    }
                }
            }
        }
    }

    *includeAllEventTypes { arg overwrite = false;
        eventTypesDict.keysValuesDo {|key, val|
            if( overwrite.not && Event.partialEvents.playerEvent.eventTypes.keys.includes(key) ) {
                var msg = "% event type already exists. Use 'overwrite = true' to overwrite it.".format(key);
                msg.warn;
            } {
                Event.addEventType(key, val)
            }
        }
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
