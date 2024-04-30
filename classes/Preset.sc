Preset {

    classvar <>all;

	*initClass { all = () }

	*new { | key, preset |
        if (preset.notNil) {
            all.put(key, preset);
            ^preset;
        } {
            ^all.at(key);
        };
	}

    *clear { all.clear }

    *remove {|key| all.removeAt(key)}

    *build {|preset, desc, useOverrides = true|
        var res = preset.bubble.flatten.collect {|pre|
            var onePreset = (), chain;
            if(pre.isKindOf(PresetChain)) {
                chain = pre.list.reverse;
            } {
                chain = pre.bubble.flatten;
            };
            chain.do {|name|
                var thisPre = desc.metadata.presets[name] ?? {Preset(name)} ?? {name};
                if(thisPre.isKindOf(PresetChain)) {
                    thisPre = Preset.build(thisPre, desc, useOverrides);
                };
                if(thisPre.isKindOf(Dictionary).not) {thisPre = ()};
                if(useOverrides == true) {
                    EventTypes.resolveOverrides(thisPre, onePreset, EventTypes.presetSymbols);
                };
                thisPre = thisPre.reject {|val| EventTypes.presetSymbols.includes(val)};
                onePreset.putAll(thisPre);
                onePreset = onePreset.collect {|val| val.asStream.value};
            };
            onePreset;
        }.flopDict;
        ^res;
    }
}

PresetChain {
    var <>list;

    *new {arg ... listOfPresets;
        ^super.new.list_(listOfPresets);
    }
}

PC : PresetChain {}
