import javax.sound.midi.MidiChannel;
import javax.sound.midi.MidiSystem;
import javax.sound.midi.Synthesizer;

/**
 * Procedural MIDI audio: short effect blips and a looping chiptune track played note-by-note on the JDK
 * synthesizer (effects own channels 0-3, music 4-6) with no audio assets shipped. Notes are queued by
 * absolute fire time and played by {@link #pump}, called once per frame by the game loop, so audio needs
 * no background thread. Every method is a no-op if no synthesizer is available.
 */
final class Sound {

    private static final int ATTACK_CHANNEL = 0;
    private static final int ENEMY_CHANNEL = 1;
    private static final int CHIME_CHANNEL = 2;
    private static final int FOOTSTEP_CHANNEL = 3;
    private static final int MELODY_CHANNEL = 4;
    private static final int BASS_CHANNEL = 5;
    private static final int ARP_CHANNEL = 6;

    private static final int SQUARE_LEAD = 80;
    private static final int SYNTH_BASS = 38;
    private static final int GLOCKENSPIEL = 9;
    private static final int ACOUSTIC_BASS = 32;

    private static final int BRIGHTNESS = 74;
    private static final int VOLUME = 7;
    private static final int REVERB_SEND = 91;
    private static final int FOOTSTEP_CUTOFF = 20;
    private static final int MUSIC_VOLUME = 78;

    private static final long FOOTSTEP_THROTTLE_NANOS = 230_000_000L;

    // One eighth note at ~96 BPM; the music ticker fires once per eighth and note lengths are whole eighths.
    private static final long EIGHTH_MS = 313;
    // A short release gap so each music note ends just before the next note on the same voice begins; without
    // it a repeated key can have its fresh note-on cancelled by the previous note's same-timestamp note-off.
    private static final long NOTE_GAP_MS = 30;
    private static final int MELODY_VELOCITY = 74;
    private static final int BASS_VELOCITY = 68;
    private static final int ARP_VELOCITY = 44;

    /**
     * An 8-bar Am-F-C-G call-and-response loop. Voices are packed as {key, startEighth,
     * lengthEighths} triples, one character per value biased by NOTE_PACK_OFFSET so every byte
     * stays printable; {@link #tickVoice} unpacks them (each char = value + 48). Bass and arp repeat
     * over both phrases, while the melody answers its open "call" ending (two half-notes, D5-B4) with
     * a descending arpeggio cadence (D5-B4-G4-E4) that resolves low and leads back up to the tonic.
     */
    private static final int NOTE_PACK_OFFSET = 48;
    private static final int PHRASE_EIGHTHS = 32;
    private static final int LOOP_EIGHTHS = PHRASE_EIGHTHS * 2;
    private static final String MELODY = "u04p44x84u<4w@4sD4zH4wL4";
    private static final String MELODY_RESPONSE = "u04p44x84u<4w@4sD4zH2wJ2sL2pN2";
    private static final String BASS = "]04d44Y84`<4`@4[D4[H4bL4";
    private static final String ARP = "i02l22p42u62e82i:2l<2q>2l@2pB2sD2xF2gH2kJ2nL2sN2";

    // Pending notes, fired by absolute time in update(). evData packs one note: music<<24 | off<<23 |
    // channel<<16 | key<<8 | velocity. The cap is a saturation guard; overlapping notes never approach it.
    private static final int MAX_EVENTS = 256;
    private static final long[] eventFireMs = new long[MAX_EVENTS];
    private static final int[] eventData = new int[MAX_EVENTS];
    private static int eventCount;
    private static long nextTickMs;

    private static final MidiChannel[] channels;
    private static boolean muted;
    private static boolean musicMuted;
    private static int musicEighth;
    private static long lastFootstepNanos;

    static {
        MidiChannel[] openChannels;
        try {
            Synthesizer synthesizer = MidiSystem.getSynthesizer();
            synthesizer.open();
            openChannels = synthesizer.getChannels();
            openChannels[ATTACK_CHANNEL].programChange(SQUARE_LEAD);
            openChannels[ENEMY_CHANNEL].programChange(SYNTH_BASS);
            openChannels[CHIME_CHANNEL].programChange(GLOCKENSPIEL);
            openChannels[FOOTSTEP_CHANNEL].programChange(ACOUSTIC_BASS);
            openChannels[FOOTSTEP_CHANNEL].controlChange(BRIGHTNESS, FOOTSTEP_CUTOFF);
            openChannels[FOOTSTEP_CHANNEL].controlChange(REVERB_SEND, 0);
            configureMusicChannel(openChannels[MELODY_CHANNEL], SQUARE_LEAD);
            configureMusicChannel(openChannels[BASS_CHANNEL], SYNTH_BASS);
            configureMusicChannel(openChannels[ARP_CHANNEL], SQUARE_LEAD);
        } catch (Exception unavailable) {
            openChannels = null;
        }
        channels = openChannels;
    }

    private Sound() {
    }

    /** No-op whose only job is to run the static initializer eagerly (open the synth). */
    static void init() {
    }

    /** A fast, bright two-note downward slash for Duke's spin attack. */
    static void swordAttack() { play("0p:!-0i:*1"); }

    /** A low, blunt two-note thud when an enemy lands a hit on Duke. */
    static void enemyAttack() { play("1CZ!31<Z,;"); }

    /** A soft, dampened low thud for a footstep, throttled so rapid steps stay natural. */
    static void footstep() {
        if (channels == null) {
            return;
        }
        long now = System.nanoTime();
        if (now - lastFootstepNanos < FOOTSTEP_THROTTLE_NANOS) {
            return;
        }
        lastFootstepNanos = now;
        note(FOOTSTEP_CHANNEL, 38, 70, 0, 70, false);
    }

    /** A short rising glockenspiel arpeggio when Duke takes the stairs. */
    static void stairs() { play("2`F!92dF/92gF=92lLKM"); }

    /** A bright two-note chime when Duke pockets a key. */
    static void keyPickup() { play("2kD!32pJ1?"); }

    /** A heavy latch clunk resolving into a rising chime as a sealed vault door swings open. */
    static void doorUnlock() { play("1@R!92dD;;2kLQM"); }

    /** A sharp percussive crack when a breakable prop is smashed. */
    static void sceneryBreak() { play("0d`!)1HR#10T:,-"); }

    /** A descending bass sequence as Duke tumbles into a pit. */
    static void pitFall() { play("0`N!10YN510QNI10JX]51<duu"); }

    /** A harsh dissonant cluster hit when a mimic chest springs to life. */
    static void mimicReveal() { play("1Oh!11Ph!12xX!-2lR/91C`5A"); }

    /** A deep, percussive boom as the boss brings down an area slam. */
    static void bossSlam() { play("17d!314d*A10h4O"); }

    /** A bright descending fanfare when the boss is finally felled. */
    static void bossDefeat() { play("2lL!?2gL??2dL]?2`R{a"); }

    /**
     * Plays a packed sound effect: five chars per note — channel+48, key+24, velocity-22,
     * (startMs/5)+33, (durationMs/5)+33 — biased so every byte stays printable, mirroring the music pack.
     */
    private static void play(String packed) {
        if (channels == null) {
            return;
        }
        for (int i = 0; i < packed.length(); i += 5) {
            note(packed.charAt(i) - 48, packed.charAt(i + 1) - 24, packed.charAt(i + 2) + 22,
                    (packed.charAt(i + 3) - 33) * 5, (packed.charAt(i + 4) - 33) * 5, false);
        }
    }

    /** Toggles all audio: the tickers keep running but stay quiet, and any sounding notes are cut off. */
    static void toggleMute() {
        if (channels == null) return;
        muted = !muted;
        if (muted) {
            for (MidiChannel channel : channels) channel.allSoundOff();
        }
    }

    /** Toggles only the music track; sound effects are unaffected. */
    static void toggleMusicMute() {
        if (channels == null) return;
        musicMuted = !musicMuted;
        if (musicMuted) {
            channels[MELODY_CHANNEL].allSoundOff();
            channels[BASS_CHANNEL].allSoundOff();
            channels[ARP_CHANNEL].allSoundOff();
        }
    }

    /**
     * Queues one note: key on at {@code startMs}, off {@code durationMs} later (both relative to now). The
     * note-on is gated at fire time by the mute flags ({@code music} notes also honor the music-only mute)
     * so a toggle is instant; the note-off always fires so a key never sticks.
     */
    private static void note(int channel, int key, int velocity, long startMs, long durationMs, boolean music) {
        long now = System.currentTimeMillis();
        int tag = (music ? 1 << 24 : 0) | (channel << 16) | (key << 8);
        enqueue(now + startMs, tag | velocity);
        enqueue(now + startMs + durationMs, tag | 1 << 23);
    }

    /** Appends a packed note event, dropping it if the (generously sized) queue is saturated. */
    private static void enqueue(long fireMs, int data) {
        if (eventCount < MAX_EVENTS) {
            eventFireMs[eventCount] = fireMs;
            eventData[eventCount] = data;
            eventCount++;
        }
    }

    /**
     * Pumped once per frame by the game loop: advances the eighth-note music ticker for any boundaries crossed
     * since the last call, then fires every queued note whose time has come (removing it by swapping in the
     * last entry). After a long stall the ticker resyncs to now rather than firing a burst of catch-up eighths.
     */
    static void pump(long nowMs) {
        if (channels == null) {
            return;
        }
        if (nextTickMs == 0) {
            nextTickMs = nowMs;
        }
        while (nowMs >= nextTickMs) {
            advanceMusic();
            nextTickMs += EIGHTH_MS;
            if (nowMs - nextTickMs > EIGHTH_MS * 8) {
                nextTickMs = nowMs;
            }
        }
        int i = 0;
        while (i < eventCount) {
            if (eventFireMs[i] <= nowMs) {
                int data = eventData[i];
                int channel = (data >> 16) & 0x7f;
                int key = (data >> 8) & 0x7f;
                if ((data & 1 << 23) != 0) {
                    channels[channel].noteOff(key);
                } else if (!muted && !((data & 1 << 24) != 0 && musicMuted)) {
                    channels[channel].noteOn(key, data & 0x7f);
                }
                eventCount--;
                eventFireMs[i] = eventFireMs[eventCount];
                eventData[i] = eventData[eventCount];
            } else {
                i++;
            }
        }
    }

    /** Sets a music voice's instrument and trims it to background volume. */
    private static void configureMusicChannel(MidiChannel channel, int program) {
        channel.programChange(program);
        channel.controlChange(VOLUME, MUSIC_VOLUME);
    }

    /** One eighth-note tick of the looping track: starts the bass, arp, and melody notes that begin here. */
    private static void advanceMusic() {
        int eighth = musicEighth;
        musicEighth = (musicEighth + 1) % LOOP_EIGHTHS;
        if (muted || musicMuted) {
            return;
        }
        int pos = eighth % PHRASE_EIGHTHS;
        tickVoice(BASS_CHANNEL, BASS, BASS_VELOCITY, pos);
        tickVoice(ARP_CHANNEL, ARP, ARP_VELOCITY, pos);
        tickVoice(MELODY_CHANNEL, eighth < PHRASE_EIGHTHS ? MELODY : MELODY_RESPONSE, MELODY_VELOCITY, pos);
    }

    /**
     * Fires every note in one packed voice whose start eighth equals {@code pos}, for its packed length.
     * Only the bass repeats a pitch on back-to-back notes, so only it gets the release gap; the melody and
     * arp ring their full length to keep the sustained, tailed sound of the original sequencer playback.
     */
    private static void tickVoice(int channel, String notes, int velocity, int pos) {
        long gap = channel == BASS_CHANNEL ? NOTE_GAP_MS : 0;
        for (int i = 0; i < notes.length(); i += 3) {
            if (notes.charAt(i + 1) - NOTE_PACK_OFFSET == pos) {
                note(channel, notes.charAt(i) - NOTE_PACK_OFFSET, velocity, 0,
                        (notes.charAt(i + 2) - NOTE_PACK_OFFSET) * EIGHTH_MS - gap, true);
            }
        }
    }
}
