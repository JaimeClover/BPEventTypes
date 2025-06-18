EventTypes {

    classvar <keyOverrides, <>defaultArpKeys;
    classvar <>useKeyOverrides = true;
    classvar <>useControlDefaults = false;
    classvar <>defaultSymbols, <>presetSymbols;
    classvar <>alternateTuning;
    classvar <>eventTypesDict, <>parentEventsDict;


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
        parentEventsDict = ();

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

            echoPan = case
            {~echoPan.isKindOf(Array)} {~echoPan}
            {~echoPan.asSymbol == \rand} {{1.0.rand2} ! (numEchoes)}
            {~echoPan.asSymbol == \gauss} {{1.0.sum3rand} ! (numEchoes)}
            {~echoPan.asSymbol == \lr} {[-1, 1]}
            {~echoPan.asSymbol == \rl} {[1, -1]};
            echoPan = echoPan ? [0];
            echoPan = (~echoSpread ? 1) * echoPan;

            timingOffset = ~timingOffset ? 0;
            echoRhythm = (~echoRhythm ? 1).asArray.wrapExtend(numNotes).normalizeSum * numNotes;
            echoRhythm = [0] ++ echoRhythm.drop(-1).integrate * echoTime.abs;
            ~timingOffset = timingOffset +.x echoRhythm;

            ~amp = ~amp.value * Array.geom(numNotes, 1, echoCoef);
            ~pan = [0] ++ echoPan.wrapExtend(numNotes - 1) + ~pan;

            this.prChainEventType(server);
        });

        eventTypesDict.put(\arp, {arg server;
            var maxSize, arpKeys, timingOffset, arpRhythm;

            arpKeys = ~arpKeys ? this.defaultArpKeys;
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

            timingOffset = ~timingOffset ? 0;
            arpRhythm = (~arpRhythm ? 1).asArray.wrapExtend(~subdivisions).normalizeSum;
            arpRhythm = [0] ++ arpRhythm.drop(-1).integrate * ~legato;
            ~timingOffset = timingOffset +.x arpRhythm;
            ~sustain = ~sustain / ~subdivisions * (~legatoEach ? 1).max(0.03 * thisThread.clock.tempo);
            ~amp = ~amp.value *.x (~arpSieve ? 1).asArray.wrapExtend(~subdivisions);
            ~amp = ~amp.wrapExtend(~timingOffset.size);

            this.prChainEventType(server);
        });

        parentEventsDict.put(\arp, (legato: 1));

        eventTypesDict.put(\presetEcho, {arg server;
            ~chainedEventTypes = [\preset, \echo];
            this.prChainEventType(server);
        });

        eventTypesDict.put(\presetArp, {arg server;
            ~chainedEventTypes = [\preset, \arp];
            this.prChainEventType(server);
        });

        eventTypesDict.put(\echoArp, {arg server;
            ~chainedEventTypes = [\echo, \arp];
            this.prChainEventType(server);
        });

        eventTypesDict.put(\presetEchoArp, {arg server;
            ~chainedEventTypes = [\preset, \echo, \arp];
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
                        Event.addEventType(alias[i] ? nm, eventTypesDict[nm], parentEventsDict[nm]);
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
                Event.addEventType(key, val, parentEventsDict[key])
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
