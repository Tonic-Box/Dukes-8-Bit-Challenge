package com.tonicbox.dukes8bit;

import javax.sound.midi.InvalidMidiDataException;
import javax.sound.midi.MidiChannel;
import javax.sound.midi.MidiEvent;
import javax.sound.midi.MidiSystem;
import javax.sound.midi.Sequence;
import javax.sound.midi.Sequencer;
import javax.sound.midi.ShortMessage;
import javax.sound.midi.Synthesizer;
import javax.sound.midi.Track;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Procedural MIDI audio for Duke's Descent. Short effect blips are synthesized
 * live through the JDK's software synthesizer and a looping chiptune track is
 * driven by a sequencer routed into the same synthesizer, so no audio assets
 * ship. Effects own channels 0-3 and music owns channels 4-6 so they never
 * collide. If no synthesizer is available every method is a silent no-op and the
 * game runs unaffected.
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

    private static final long FOOTSTEP_THROTTLE_NANOS = 170_000_000L;

    private static final int TICKS_PER_EIGHTH = 2;
    private static final float MUSIC_TEMPO_BPM = 96f;
    private static final int MELODY_VELOCITY = 74;
    private static final int BASS_VELOCITY = 68;
    private static final int ARP_VELOCITY = 44;

    /**
     * An 8-bar Am-F-C-G call-and-response loop. Voices are packed as {key, startEighth,
     * lengthEighths} triples, one character per value biased by NOTE_PACK_OFFSET so every byte
     * stays printable; {@link #addVoice} unpacks them (each char = value + 48). Bass and arp repeat
     * over both phrases, while the melody answers its open "call" ending (two half-notes, D5-B4) with
     * a descending arpeggio cadence (D5-B4-G4-E4) that resolves low and leads back up to the tonic.
     */
    private static final int NOTE_PACK_OFFSET = 48;
    private static final int PHRASE_EIGHTHS = 32;
    private static final String MELODY = "u04p44x84u<4w@4sD4zH4wL4";
    private static final String MELODY_RESPONSE = "u04p44x84u<4w@4sD4zH2wJ2sL2pN2";
    private static final String BASS = "]04d44Y84`<4`@4[D4[H4bL4";
    private static final String ARP = "i02l22p42u62e82i:2l<2q>2l@2pB2sD2xF2gH2kJ2nL2sN2";

    private final MidiChannel[] channels;
    private final ScheduledExecutorService scheduler;
    private long lastFootstepNanos;

    Sound() {
        MidiChannel[] openChannels;
        ScheduledExecutorService timer;
        Sequencer music;
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
            music = MidiSystem.getSequencer(false);
            music.open();
            music.getTransmitter().setReceiver(synthesizer.getReceiver());
            music.setSequence(buildMusic());
            music.setLoopCount(Sequencer.LOOP_CONTINUOUSLY);
            music.setTempoInBPM(MUSIC_TEMPO_BPM);
            music.start();
        } catch (Exception unavailable) {
            openChannels = null;
            timer = null;
        }
        channels = openChannels;
        scheduler = timer;
    }

    /** A fast, bright two-note downward slash for Duke's spin attack. */
    void swordAttack() {
        if (channels == null) {
            return;
        }
        note(ATTACK_CHANNEL, 88, 80, 0, 60);
        note(ATTACK_CHANNEL, 81, 80, 45, 80);
    }

    /** A low, blunt two-note thud when an enemy lands a hit on Duke. */
    void enemyAttack() {
        if (channels == null) {
            return;
        }
        note(ENEMY_CHANNEL, 43, 112, 0, 90);
        note(ENEMY_CHANNEL, 36, 112, 55, 130);
    }

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
        note(FOOTSTEP_CHANNEL, 38, 70, 0, 70);
    }

    /** A short rising glockenspiel arpeggio when Duke takes the stairs. */
    void stairs() {
        if (channels == null) {
            return;
        }
        note(CHIME_CHANNEL, 72, 92, 0, 120);
        note(CHIME_CHANNEL, 76, 92, 70, 120);
        note(CHIME_CHANNEL, 79, 92, 140, 120);
        note(CHIME_CHANNEL, 84, 98, 210, 220);
    }

    /** Schedules one note: key on at {@code startMs}, off {@code durationMs} later. */
    private void note(int channel, int key, int velocity, long startMs, long durationMs) {
        scheduler.schedule(() -> channels[channel].noteOn(key, velocity), startMs, TimeUnit.MILLISECONDS);
        scheduler.schedule(() -> channels[channel].noteOff(key), startMs + durationMs, TimeUnit.MILLISECONDS);
    }

    /** Sets a music voice's instrument and trims it to background volume. */
    private static void configureMusicChannel(MidiChannel channel, int program) {
        channel.programChange(program);
        channel.controlChange(VOLUME, MUSIC_VOLUME);
    }

    /** Builds the 8-bar loop: a "call" phrase followed by a resolving "response" phrase. */
    private static Sequence buildMusic() throws InvalidMidiDataException {
        Sequence sequence = new Sequence(Sequence.PPQ, TICKS_PER_EIGHTH * 2);
        Track track = sequence.createTrack();
        addPhrase(track, MELODY, 0);
        addPhrase(track, MELODY_RESPONSE, PHRASE_EIGHTHS);
        return sequence;
    }

    /** Lays the bass, arp, and the given melody for one phrase starting at {@code offsetEighths}. */
    private static void addPhrase(Track track, String melody, int offsetEighths) throws InvalidMidiDataException {
        addVoice(track, BASS_CHANNEL, BASS, BASS_VELOCITY, offsetEighths);
        addVoice(track, MELODY_CHANNEL, melody, MELODY_VELOCITY, offsetEighths);
        addVoice(track, ARP_CHANNEL, ARP, ARP_VELOCITY, offsetEighths);
    }

    /** Unpacks one voice's {key, start, length} char-triples and adds its notes at the phrase offset. */
    private static void addVoice(Track track, int channel, String notes, int velocity, int offsetEighths) throws InvalidMidiDataException {
        for (int i = 0; i < notes.length(); i += 3) {
            int key = notes.charAt(i) - NOTE_PACK_OFFSET;
            long on = (long) (notes.charAt(i + 1) - NOTE_PACK_OFFSET + offsetEighths) * TICKS_PER_EIGHTH;
            long off = on + (long) (notes.charAt(i + 2) - NOTE_PACK_OFFSET) * TICKS_PER_EIGHTH;
            track.add(new MidiEvent(new ShortMessage(ShortMessage.NOTE_ON, channel, key, velocity), on));
            track.add(new MidiEvent(new ShortMessage(ShortMessage.NOTE_OFF, channel, key, 0), off));
        }
    }
}
