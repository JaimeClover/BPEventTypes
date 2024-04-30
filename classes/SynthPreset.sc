SynthPreset : Synth {

    var <>finalArgs;
    classvar <>useKeyOverrides = true;

    *new { arg defName, presetName, args, target, addAction=\addToHead;
        var fullArgs = this.prepareArgs(defName, presetName, args);
        ^super.new(defName, fullArgs, target, addAction).finalArgs_(fullArgs);
    }

    *newPaused { arg defName, presetName, args, target, addAction=\addToHead;
        var fullArgs = this.prepareArgs(defName, presetName, args);
        ^super.newPaused(defName, fullArgs, target, addAction).finalArgs_(fullArgs);
    }

    *replace { arg nodeToReplace, defName, presetName, args, sameID=false;
        var fullArgs = this.prepareArgs(defName, presetName, args);
        ^super.replace(nodeToReplace, defName, fullArgs, sameID).finalArgs_(fullArgs);
    }

    newMsg { arg target, presetName, args, addAction = \addToHead;
        var fullArgs = this.prepareArgs(defName, presetName, args);
        ^super.newMsg(target, fullArgs, addAction).finalArgs_(fullArgs);
    }

    *after { arg aNode, defName, presetName, args;
		^this.new(defName, presetName, args, aNode, \addAfter)
	}

	*before { arg aNode, defName, presetName, args;
		^this.new(defName, presetName, args, aNode, \addBefore)
	}

	*head { arg aGroup, defName, presetName, args;
		^this.new(defName, presetName, args, aGroup, \addToHead)
	}

	*tail { arg aGroup, defName, presetName, args;
		^this.new(defName, presetName, args, aGroup, \addToTail)
	}

	replace { arg defName, presetName, args, sameID;
		^this.class.replace(this, defName, presetName, args, sameID)
	}

    *grain { arg defName, presetName, args, target, addAction=\addToHead;
        var fullArgs = this.prepareArgs(defName, presetName, args);
        ^super.grain(defName, fullArgs, target, addAction).finalArgs_(fullArgs);
    }

    *prepareArgs {arg defName, presetName, args;
        var desc, preset, defaults, name, variant;
        #name, variant = defName.asString.split($.);
        variant !? {"SynthPreset can't access variants. Using default values instead".warn};
        desc = SynthDescLib.at(name);
        preset = Preset.build(presetName, desc, useKeyOverrides);
        defaults = desc.controlDict.collect(_.defaultValue).asEvent;

        if(preset.isKindOf(Dictionary).not) {
            preset = ();
        } {
            preset = preset.collect {|val| val.asStream.value};
        };
        args = args ? [];
        args = args.asEvent.collect {|val| val.asStream.value};

        if(useKeyOverrides == true) {
            EventTypes.resolveOverrides(args, preset, EventTypes.presetSymbols);
        };

        args = args.reject {|val| EventTypes.presetSymbols.includes(val)};
        preset.putAll(args);

        if(useKeyOverrides == true) {
            EventTypes.resolveOverrides(preset, defaults, EventTypes.defaultSymbols);
        };

        preset = preset.reject {|val| EventTypes.defaultSymbols.includes(val)};
        defaults.putAll(preset);

        defaults.parent = Event.default;
        defaults.use {
            ~freq = ~detunedFreq.value;
            ~amp = ~amp.value;
        };
        defaults = defaults.select{|val, key| desc.controlNames.includes(key)};
        ^defaults.asPairs;
    }
}
