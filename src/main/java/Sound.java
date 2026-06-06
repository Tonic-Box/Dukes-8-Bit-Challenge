import javax.sound.midi.MidiChannel;
import javax.sound.midi.MidiSystem;
import javax.sound.midi.Synthesizer;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Procedural MIDI audio for Duke's Descent. Both the short effect blips and the looping chiptune track
 * are scheduled note-by-note through one ScheduledExecutorService onto the JDK synthesizer's channels,
 * so no audio assets ship and a single playback path drives everything. Effects own channels 0-3 and
 * music owns channels 4-6 so they never collide. If no synthesizer is available every method is a no-op.
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

    private final MidiChannel[] channels;
    private final ScheduledExecutorService scheduler;
    private boolean muted;
    private boolean musicMuted;
    private int musicEighth;
    private long lastFootstepNanos;

    Sound() {
        MidiChannel[] openChannels;
        ScheduledExecutorService timer;
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
            timer = Executors.newSingleThreadScheduledExecutor();
        } catch (Exception unavailable) {
            openChannels = null;
            timer = null;
        }
        channels = openChannels;
        scheduler = timer;
        if (scheduler != null) {
            scheduler.scheduleAtFixedRate(this::advanceMusic, 0, EIGHTH_MS, TimeUnit.MILLISECONDS);
        }
    }

    /** A fast, bright two-note downward slash for Duke's spin attack. */
    void swordAttack() { play("0p:!-0i:*1"); }

    /** A low, blunt two-note thud when an enemy lands a hit on Duke. */
    void enemyAttack() { play("1CZ!31<Z,;"); }

    /** A soft, dampened low thud for a footstep, throttled so rapid steps stay natural. */
    void footstep() {
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
    void stairs() { play("2`F!92dF/92gF=92lLKM"); }

    /** A bright two-note chime when Duke pockets a key. */
    void keyPickup() { play("2kD!32pJ1?"); }

    /** A heavy latch clunk resolving into a rising chime as a sealed vault door swings open. */
    void doorUnlock() { play("1@R!92dD;;2kLQM"); }

    /** A sharp percussive crack when a breakable prop is smashed. */
    void sceneryBreak() { play("0d`!)1HR#10T:,-"); }

    /** A descending bass sequence as Duke tumbles into a pit. */
    void pitFall() { play("0`N!10YN510QNI10JX]51<duu"); }

    /** A harsh dissonant cluster hit when a mimic chest springs to life. */
    void mimicReveal() { play("1Oh!11Ph!12xX!-2lR/91C`5A"); }

    /** A deep, percussive boom as the boss brings down an area slam. */
    void bossSlam() { play("17d!314d*A10h4O"); }

    /** A bright descending fanfare when the boss is finally felled. */
    void bossDefeat() { play("2lL!?2gL??2dL]?2`R{a"); }

    /**
     * Plays a packed sound effect: five chars per note — channel+48, key+24, velocity-22,
     * (startMs/5)+33, (durationMs/5)+33 — biased so every byte stays printable, mirroring the music pack.
     */
    private void play(String packed) {
        if (channels == null) {
            return;
        }
        for (int i = 0; i < packed.length(); i += 5) {
            note(packed.charAt(i) - 48, packed.charAt(i + 1) - 24, packed.charAt(i + 2) + 22,
                    (packed.charAt(i + 3) - 33) * 5, (packed.charAt(i + 4) - 33) * 5, false);
        }
    }

    /** Toggles all audio: the tickers keep running but stay quiet, and any sounding notes are cut off. */
    void toggleMute() {
        if (channels == null) return;
        muted = !muted;
        if (muted) {
            for (MidiChannel channel : channels) channel.allSoundOff();
        }
    }

    /** Toggles only the music track; sound effects are unaffected. */
    void toggleMusicMute() {
        if (channels == null) return;
        musicMuted = !musicMuted;
        if (musicMuted) {
            channels[MELODY_CHANNEL].allSoundOff();
            channels[BASS_CHANNEL].allSoundOff();
            channels[ARP_CHANNEL].allSoundOff();
        }
    }

    /**
     * Schedules one note: key on at {@code startMs}, off {@code durationMs} later. The note-on is gated at
     * fire time by the mute flags ({@code music} notes also honor the music-only mute) so a toggle is instant.
     */
    private void note(int channel, int key, int velocity, long startMs, long durationMs, boolean music) {
        scheduler.schedule(() -> {
            if (!muted && !(music && musicMuted)) channels[channel].noteOn(key, velocity);
        }, startMs, TimeUnit.MILLISECONDS);
        scheduler.schedule(() -> channels[channel].noteOff(key), startMs + durationMs, TimeUnit.MILLISECONDS);
    }

    /** Sets a music voice's instrument and trims it to background volume. */
    private static void configureMusicChannel(MidiChannel channel, int program) {
        channel.programChange(program);
        channel.controlChange(VOLUME, MUSIC_VOLUME);
    }

    /** One eighth-note tick of the looping track: starts the bass, arp, and melody notes that begin here. */
    private void advanceMusic() {
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
    private void tickVoice(int channel, String notes, int velocity, int pos) {
        long gap = channel == BASS_CHANNEL ? NOTE_GAP_MS : 0;
        for (int i = 0; i < notes.length(); i += 3) {
            if (notes.charAt(i + 1) - NOTE_PACK_OFFSET == pos) {
                note(channel, notes.charAt(i) - NOTE_PACK_OFFSET, velocity, 0,
                        (notes.charAt(i + 2) - NOTE_PACK_OFFSET) * EIGHTH_MS - gap, true);
            }
        }
    }
}
