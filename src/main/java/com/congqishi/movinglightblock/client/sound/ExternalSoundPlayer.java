package com.congqishi.movinglightblock.client.sound;

import com.mojang.blaze3d.audio.SoundBuffer;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.sounds.JOrbisAudioStream;
import net.minecraft.sounds.SoundSource;
import org.lwjgl.openal.AL10;

import javax.sound.sampled.AudioFormat;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Plays the player's own .ogg files from the dedicated sound folder.
 *
 * <p>Decoding happens off-thread through Minecraft's Ogg reader; OpenAL calls are marshalled
 * back onto the main thread. One-shot sounds own a temporary source/buffer pair, while the
 * moving phase keeps a looping source until the entity stops.</p>
 */
public final class ExternalSoundPlayer {
    private static final Map<Integer, Integer> LIVE = new HashMap<>();
    private static final Set<Integer> LOOPING = new HashSet<>();
    private static final Map<UUID, ActiveLoop> LOOPS = new HashMap<>();
    private static int nextToken;

    private ExternalSoundPlayer() {
    }

    public static void playOnce(SoundSettings.Entry entry) {
        start(entry, false, null, 0);
    }

    public static void startLoop(UUID entityId, SoundSettings.Entry entry) {
        stopLoop(entityId);
        int token = ++nextToken;
        LOOPS.put(entityId, new ActiveLoop(token, -1));
        start(entry, true, entityId, token);
    }

    public static void stopLoop(UUID entityId) {
        ActiveLoop loop = LOOPS.remove(entityId);
        if (loop != null) {
            nextToken++;
            kill(loop.source);
        }
    }

    public static void stopAll() {
        for (UUID id : new ArrayList<>(LOOPS.keySet())) {
            stopLoop(id);
        }
        for (int source : new ArrayList<>(LIVE.keySet())) {
            kill(source);
        }
    }

    /** Removes finished one-shot sources; looping sources are managed by phase transitions. */
    public static void tick() {
        List<Integer> finished = new ArrayList<>();
        for (int source : LIVE.keySet()) {
            if (!LOOPING.contains(source)
                    && AL10.alGetSourcei(source, AL10.AL_SOURCE_STATE) == AL10.AL_STOPPED) {
                finished.add(source);
            }
        }
        finished.forEach(ExternalSoundPlayer::kill);
    }

    private static void start(SoundSettings.Entry entry, boolean loop, UUID entityId, int token) {
        Path path = SoundSettings.file(entry.file);
        if (path == null || entry.volume <= 0.0F || !Files.isRegularFile(path)) {
            return;
        }
        float volume = entry.volume
                * Minecraft.getInstance().options.getSoundSourceVolume(SoundSource.MASTER);
        if (volume <= 0.0F) {
            return;
        }
        float pitch = Math.max(SoundSettings.MIN_SPEED,
                Math.min(SoundSettings.MAX_SPEED, entry.speed));

        CompletableFuture.supplyAsync(() -> decode(path), Util.nonCriticalIoPool())
                .thenAccept(decoded -> Minecraft.getInstance().execute(() -> {
                    if (entityId != null) {
                        ActiveLoop active = LOOPS.get(entityId);
                        if (active == null || active.token != token) {
                            return;
                        }
                    }
                    int source = createSource(decoded, volume, pitch, loop);
                    if (source < 0) {
                        return;
                    }
                    if (entityId != null) {
                        ActiveLoop active = LOOPS.get(entityId);
                        if (active == null || active.token != token) {
                            kill(source);
                            return;
                        }
                        active.source = source;
                    }
                }))
                .exceptionally(error -> null);
    }

    private static Decoded decode(Path path) {
        try (InputStream in = Files.newInputStream(path);
             JOrbisAudioStream stream = new JOrbisAudioStream(in)) {
            return new Decoded(stream.readAll(), stream.getFormat());
        } catch (IOException | RuntimeException error) {
            throw new IllegalStateException("Cannot read sound file: " + path, error);
        }
    }

    private static int createSource(Decoded decoded, float volume, float pitch, boolean loop) {
        OptionalInt alBuffer = new SoundBuffer(decoded.data(), decoded.format()).releaseAlBuffer();
        if (alBuffer.isEmpty()) {
            return -1;
        }
        int buffer = alBuffer.getAsInt();

        int[] sources = new int[1];
        AL10.alGenSources(sources);
        if (AL10.alGetError() != AL10.AL_NO_ERROR) {
            AL10.alDeleteBuffers(new int[] {buffer});
            return -1;
        }
        int source = sources[0];

        AL10.alSourcef(source, AL10.AL_GAIN, volume);
        AL10.alSourcef(source, AL10.AL_PITCH, pitch);
        AL10.alSourcei(source, AL10.AL_LOOPING, loop ? AL10.AL_TRUE : AL10.AL_FALSE);
        // These sounds are tied to the player's own cube, so they play at a fixed volume
        // instead of fading with camera distance.
        AL10.alSourcei(source, AL10.AL_SOURCE_RELATIVE, AL10.AL_TRUE);
        AL10.alSource3f(source, AL10.AL_POSITION, 0.0F, 0.0F, 0.0F);
        AL10.alSourcei(source, AL10.AL_BUFFER, buffer);
        AL10.alSourcePlay(source);
        if (AL10.alGetError() != AL10.AL_NO_ERROR) {
            AL10.alDeleteSources(new int[] {source});
            AL10.alDeleteBuffers(new int[] {buffer});
            return -1;
        }

        LIVE.put(source, buffer);
        if (loop) {
            LOOPING.add(source);
        }
        return source;
    }

    private static void kill(int source) {
        if (source < 0) {
            return;
        }
        Integer buffer = LIVE.remove(source);
        LOOPING.remove(source);
        AL10.alSourceStop(source);
        AL10.alDeleteSources(new int[] {source});
        if (buffer != null) {
            AL10.alDeleteBuffers(new int[] {buffer});
        }
        // Swallow stale errors from a device that was reinitialised between ticks.
        AL10.alGetError();
    }

    private record Decoded(ByteBuffer data, AudioFormat format) {
    }

    private static final class ActiveLoop {
        private final int token;
        private int source;

        private ActiveLoop(int token, int source) {
            this.token = token;
            this.source = source;
        }
    }
}
