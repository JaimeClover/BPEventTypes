EventTypes {

    classvar <keyOverrides, <>defaultPitchKeys;
    classvar <>useKeyOverrides = true, <useAlternateTuning = false;
    classvar <>useControlDefaults = false;
    classvar <>defaultSymbols, <>presetSymbols;
    classvar <>alternateTuning, <>panFunctions;
    classvar <>eventTypesDict, <>parentEventsDict;


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
        panFunctions = (
            rand: {|size| {1.0.rand2} ! size},
            gauss: {|size| {1.0.sum3rand} ! size},
            lr: {[-1, 1]},
            rl: {[1, -1]}
        );


        // add custom event types:
        eventTypesDict = ();

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
            var numEchoes, numNotes, echoTime, echoCoef, timingOffset, echoPan, echoRhythm;

            numEchoes = (~numEchoes ? 0).asInteger.max(0);
            numNotes = numEchoes + 1;
            echoTime = ~echoTime ? 0.5;
            echoCoef = ~echoCoef ? 0.5;

            echoPan = panFunctions.atFail(~echoPan.asSymbol, ~echoPan);
            echoPan = echoPan.value(numEchoes) ? [0];
            echoPan = (~echoSpread ? 1) * echoPan;
            ~pan = ~pan +.x ([0] ++ echoPan.wrapExtend(numEchoes));

            timingOffset = ~timingOffset ? 0;
            echoRhythm = (~echoRhythm ? 1).asArray.wrapExtend(numNotes).normalizeSum * numNotes;
            echoRhythm = [0] ++ echoRhythm.drop(-1).integrate * echoTime.abs;
            ~timingOffset = timingOffset +.x echoRhythm;

            ~amp = ~amp.value * Array.geom(numNotes, 1, echoCoef);
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

            this.prChainEventType(server);
        });

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
                        v.wrapAt(mode.wrapAt(i * ~hop.wrapAt(i) + ~skip.wrapAt(i) + ~jump.wrapAt(i.div(size))))
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

            arpPan = panFunctions.atFail(~arpPan.asSymbol, ~arpPan);
            arpPan = arpPan.value(~subdivisions) ? [0];
            arpPan = (~arpSpread ? 1) * arpPan;
            ~pan = ~pan +.x arpPan.wrapExtend(~subdivisions);

            timingOffset = ~timingOffset ? 0;
            arpRhythm = (~arpRhythm ? 1).asArray.wrapExtend(~subdivisions).normalizeSum;
            arpRhythm = [0] ++ arpRhythm.drop(-1).integrate * (~arpLegato ? 1);
            ~timingOffset = timingOffset +.x arpRhythm;
            ~sustain = ~sustain / ~subdivisions;
            ~amp = ~amp.value *.x (~arpSieve ? 1).asArray.wrapExtend(~subdivisions);
            ~amp = ~amp.wrapExtend(~timingOffset.size);

            this.prChainEventType(server);
        });

        // The following are composite event types, which chain together multiple of the above event types.
        // ~chainedEventTypes should be in reverse order of execution.
        eventTypesDict.put(\presetEcho, {arg server;
            ~chainedEventTypes = [\echo, \preset];
            this.prChainEventType(server);
        });

        eventTypesDict.put(\presetArp, {arg server;
            ~chainedEventTypes = [\arp, \preset];
            this.prChainEventType(server);
        });

        eventTypesDict.put(\echoArp, {arg server;
            ~chainedEventTypes = [\arp, \echo];
            this.prChainEventType(server);
        });

        eventTypesDict.put(\presetEchoArp, {arg server;
            ~chainedEventTypes = [\arp, \echo, \preset];
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

    *prChainEventType {arg server;
        // chain together multiple event types, ending with the \note type.
        var nextEventType = ~chainedEventTypes.pop ? \note;
        currentEnvironment.parent = Event.parentTypes.atFail(nextEventType, Event.default);
        ~eventTypes[nextEventType].value(server);
    }
}
