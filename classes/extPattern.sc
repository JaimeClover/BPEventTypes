+ Pattern {
    // use with eventType \echo or \arpEcho?
    clumpEchoes {
        ^Pclump(Pkey(\numEchoes) + 1, this);
    }

    // SKETCHY: only use with eventType \arp?
    clumpArp {
        ^Pclump(Pkey(\subdivisions) ? Pkey(\freq).value.size, this);
    }

    // SKETCHY: only use with eventType \arpEcho?
    clumpArpEchoes{
        ^Pclump(Pkey(\numEchoes) + 1 * (Pkey(\subdivisions) ? Pkey(\freq).value.size), this);
    }
}

+ Pbind {
    *doesNotUnderstand { |selector ... args|
        Event.eventTypes.keys.includes(selector).if {
            var newArgs = [\type, selector] ++ args;
            ^this.new(*newArgs);
        };
        ^super.doesNotUnderstand(selector, args);
	}
}

