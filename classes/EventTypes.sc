EventTypes {

    classvar <keyOverrides, <>defaultPitchKeys;
    classvar <>useKeyOverrides = true, <useAlternateTuning = false;
    classvar <>useControlDefaults = false;
    classvar <>defaultSymbols, <>presetSymbols;
    classvar alternateTuning, <panFunctions, <arpFunctions;
    classvar <eventTypesDict, <>defaultEventType = \note;
    classvar <>eventChainOrder;


    // normally the \note key assumes you are using ET12 tuning.
    // this method allows the \note key to access other tunings.
    *useAlternateTuning_ { arg val;
        useAlternateTuning = val.asBoolean;
        if(val) {
            Event.addParentType(\note,
                Event.parentTypes[\note] ++ alternateTuning.copy;
            )
        } {
            Event.parentTypes[\note] !? {|parentEvent|
                parentEvent.removeAt(\note);
                parentEvent.removeAt(\midinote);
            }
        }
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


        // initialize class variables used by the \preset, \echo, and \arp event types:
        this.initKeyOverrides;
        defaultSymbols = [\x];
        presetSymbols = [\preset, \p];
        defaultPitchKeys = Set[
            \mtranspose, \gtranspose, \ctranspose,
            \octave, \root, \detune, \harmonic,
            \degree, \note, \midinote, \freq
        ];

        // panFunctions are used by the \echo and \arp event types
        // to determine panning behavior of echoed and arpeggiated notes.
        // user can add custom panning functions with EventTypes.panFunctions[\custom] = {|size| ...}
        panFunctions = (
            rand: {|size| {1.0.rand2} ! size},
            gauss: {|size| {1.0.sum3rand} ! size},
            lr: {[-1, 1]},
            rl: {[1, -1]}
        );

        // arpFunctions are used by the \arp event type
        // to determine how arpeggios respond to the \mode key.
        // user can add custom arp functions with EventTypes.arpFunctions[\custom] = {|array| ...}
        arpFunctions = (
            fwd: { arg array, method = \wrapAt;
                var size = array.size;
                ~subdivisions.collect{|i|
                    array.perform(method, i * ~hop.wrapAt(i) + ~skip.wrapAt(i) + ~jump.wrapAt(i.div(size)))
                }
            },
            rev: { arg array; arpFunctions[\fwd].value(array.reverse) },
            fwdRev: { arg array; arpFunctions[\fwd].value(array, \foldAt) },
            revFwd: { arg array; arpFunctions[\fwd].value(array.reverse, \foldAt) },
            up: { arg array; arpFunctions[\fwd].value(array.sort) },
            down: { arg array; arpFunctions[\fwd].value(array.sort{ |a, b| a > b }) },
            upDown: { arg array; arpFunctions[\fwd].value(array.sort, \foldAt) },
            downUp: { arg array; arpFunctions[\fwd].value(array.sort{ |a, b| a > b }, \foldAt) },
            shuf: { arg array;
                var scrambled = array.scramble;
                ~subdivisions.collect{|i| scrambled.wrapAt(i)}
            },
            rand: { arg array; ~subdivisions.collect{|i| array.choose} },
            xrand: { arg array;
                var size = array.size;
                var index = size.rand;
                ~subdivisions.collect{|i|
                    index = (index + (size - 1).rand + 1) % size;
                    array.at(index)
                }
            },
            wrand: { arg array;
                var size = array.size;
                ~subdivisions.collect{|i| array.wchoose(~weights ? (1 ! size / size))}
            }
        );

        eventChainOrder = [\preset, \echo, \arp, \note, \grain];

        // add custom event types:
        eventTypesDict = ();

        // chain together multiple event types:
        eventTypesDict.put(\chain, { arg server;
            var nextEventType, autoSortChain, func;

            autoSortChain = ~autoSortChain ? true;
            ~chainedEventTypes = ~chainedEventTypes ? [];
            if(autoSortChain) {
                ~chainedEventTypes = ~chainedEventTypes.sort{|a, b|
                    var indices = [a, b].collect(eventChainOrder.indexOf(_));
                    indices[0] < indices[1];
                    (eventChainOrder.indexOf(a) ? inf) < (eventChainOrder.indexOf(b) ? inf)
                };
            };
            nextEventType = ~chainedEventTypes[0] ? defaultEventType;
            currentEnvironment.parent = Event.parentTypes.atFail(nextEventType, Event.default);
            ~chainedEventTypes = ~chainedEventTypes[1..];
            func = eventTypesDict[nextEventType] ?? {~eventTypes[nextEventType]} ?? {~eventTypes[defaultEventType]};
            func.value(server);
        });

        // access synth presets that are saved in a Preset object or in the synthdef's metadata.presets dictionary:
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
            currentEnvironment = currentEnvironment ++ preset;

            eventTypesDict[\chain].value(server);
        });

        // add echoes:
        eventTypesDict.put(\echo, {arg server;
            var numEchoes, numNotes, echoTime, echoCoef, timingOffset, echoPan, echoRhythm;

            numEchoes = (~numEchoes ? 0).asInteger.max(0);
            numNotes = numEchoes + 1;
            echoTime = ~echoTime ? 0.5;
            echoCoef = (~echoCoef ? 0.5).asArray;

            echoPan = panFunctions.atFail(~echoPan.asSymbol, ~echoPan);
            echoPan = echoPan.value(numEchoes) ? [0];
            echoPan = (~echoSpread ? 1) * echoPan;
            ~pan = ~pan +.x ([0] ++ echoPan.wrapExtend(numEchoes));

            timingOffset = ~timingOffset ? 0;
            echoRhythm = (~echoRhythm ? 1).asArray.wrapExtend(numNotes).normalizeSum * numNotes;
            echoRhythm = [0] ++ echoRhythm.drop(-1).integrate * echoTime.abs;
            ~timingOffset = timingOffset +.x echoRhythm.wrapExtend(numNotes);

            ~amp = ~amp.value * Array.geom(numNotes, 1, echoCoef).collect{|echo, i| echo.asArray.wrapAt(i - 1)};
            ~amp = ~amp * (~echoSieve ? 1).asArray.wrapExtend(numNotes);

            ~chainedEventTypes = ~chainedEventTypes ? [];
            ~arpeggiateEchoes = ~arpeggiateEchoes ? false;
            if(~chainedEventTypes.includes(\arp).not && ~arpeggiateEchoes.not) {
                currentEnvironment.keysValuesChange{|k, v|
                    var pitchKeys = ~arpKeys ? this.defaultPitchKeys;
                    if(v.isKindOf(Array) and: {pitchKeys.includes(k)}) {
                        v.dupEach(numNotes)
                    } { v }
                }
            };

            eventTypesDict[\chain].value(server);
        });

        // turn chords into arpeggios:
        eventTypesDict.put(\arp, {arg server;
            var maxSize, arpKeys, timingOffset, arpRhythm, arpPan;

            arpKeys = ~arpKeys ? this.defaultPitchKeys;
            maxSize = (currentEnvironment.select{|v, k| arpKeys.includes(k)}.maxValue{|item, i| item.size} ? 1).max(1);
            ~subdivisions = (~subdivisions.value ? maxSize).max(1).asInteger;

            ~hop = (~hop ? 1).asArray;
            ~skip = (~skip ? 0).asArray;
            ~jump = (~jump ? 0).asArray;
            ~jump = ~subdivisions.div(maxSize).collect{|i| ~jump.wrapAt(i)};

            currentEnvironment.keysValuesChange{|k, v|
                if(v.isKindOf(Array) and: {arpKeys.includes(k)}){
                    var mode = ~mode ? \fwd;
                    if(mode.isKindOf(SequenceableCollection)) { ~subdivisions.collect{|i|
                        v.wrapAt(mode.wrapAt(i * ~hop.wrapAt(i) + ~skip.wrapAt(i) + ~jump.wrapAt(i.div(v.size))))
                    }} { arpFunctions[mode].value(v) }
                } { v }
            };

            ~octavate = (~octavate ? 0).asArray.collect(_.asInteger);
            ~octavate = ~subdivisions.div(maxSize).collect{|i| ~octavate.wrapAt(i) * (i + 1)};
            ~octavate = [0] ++ ~octavate;
            ~maxOctaves = ~maxOctaves ? 8;
            ~octavate = ~octavate.fold2(~maxOctaves);
            ~octaves = ~octaves ? ~octavate;
            ~octave = ~octave + ~subdivisions.collect{|i| ~octaves.wrapAt(i.div(maxSize))};

            arpPan = panFunctions.atFail(~arpPan.asSymbol, ~arpPan);
            arpPan = arpPan.value(~subdivisions) ? [0];
            arpPan = (~arpSpread ? 1) * arpPan;
            ~pan = ~pan +.x arpPan.wrapExtend(~subdivisions);

            timingOffset = ~timingOffset ? 0;
            arpRhythm = (~arpRhythm ? 1).asArray.wrapExtend(~subdivisions).normalizeSum;
            arpRhythm = [0] ++ arpRhythm.drop(-1).integrate * (~arpLegato ? 1) * ~dur * ~stretch;
            ~timingOffset = timingOffset +.x arpRhythm;
            ~sustain = ~sustain / ~subdivisions;
            ~amp = ~amp.value *.x (~arpSieve ? 1).asArray.wrapExtend(~subdivisions);
            ~amp = ~amp.wrapExtend(~timingOffset.size);

            eventTypesDict[\chain].value(server);
        });


        // The following chain together multiple of the above event types:

        eventTypesDict.put(\presetEcho, {arg server;
            ~chainedEventTypes = [\preset, \echo];
            eventTypesDict[\chain].value(server);
        });

        eventTypesDict.put(\presetArp, {arg server;
            ~chainedEventTypes = [\preset, \arp];
            eventTypesDict[\chain].value(server);
        });

        eventTypesDict.put(\echoArp, {arg server;
            ~chainedEventTypes = [\echo, \arp];
            eventTypesDict[\chain].value(server);
        });

        eventTypesDict.put(\presetEchoArp, {arg server;
            ~chainedEventTypes = [\preset, \echo, \arp];
            eventTypesDict[\chain].value(server);
        });

    }

    *includeEventType { arg name, alias, overwrite = false;
        if( name.isNil ) { EventTypes.includeAllEventTypes(overwrite) } {
            if( name.isString ) { name = name.asSymbol };
            if( alias.isString ) { alias = alias.asSymbol };
            name = name.asArray;
            alias = alias.asArray;
            if( alias.size > 0 and: {alias.size != name.size} ) { "name and alias must be the same size".error; ^nil } {
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
        // This method is called by the \preset event type if useKeyOverrides is true.
        // It causes "high-level" keys in the event to override "low-level" keys in the preset.
        // e.g. If the event has the \degree key, the preset's \freq key will be ignored.
        keyOverrides.keysValuesDo {|key, overriddenKeys|
            if(overEvent.keys.includes(key)) {
                if(exclusionList.includes(overEvent[key]).not) {
                    overriddenKeys.do {|overriddenKey| underEvent.removeAt(overriddenKey)};
                }
            }
        }
    }
}
