+ Tuning {
    at { arg index;
        var extendedTuning;
        if (index.isInteger) {^tuning.at(index)};
        extendedTuning = tuning ++ [this.stepsPerOctave];
        ^extendedTuning.blendAt(index.clip(0, this.size - 1e-12));
    }

    wrapAt { arg index;
        var extendedTuning;
        if (index.isInteger) {^tuning.wrapAt(index)};
        extendedTuning = tuning ++ [this.stepsPerOctave];
        ^extendedTuning.blendAt(index % this.size);
    }
}