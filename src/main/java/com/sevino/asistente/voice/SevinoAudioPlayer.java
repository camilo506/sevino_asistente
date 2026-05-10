package com.sevino.asistente.voice;

import com.mojang.logging.LogUtils;
import javazoom.jl.decoder.JavaLayerException;
import javazoom.jl.player.Player;
import org.slf4j.Logger;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.FloatControl;
import javax.sound.sampled.SourceDataLine;
import java.io.ByteArrayInputStream;
import java.io.InputStream;

/**
 * Reproductor de audio en hilos separados. StreamElements TTS devuelve MP3; WAV/PCM se soporta por si acaso.
 */
public final class SevinoAudioPlayer {

    private static final Logger LOGGER = LogUtils.getLogger();

    private SevinoAudioPlayer() {}

    /**
     * Reproduce bytes de audio TTS (MP3 de StreamElements o WAV/PCM).
     */
    public static void playTts(byte[] audioData) {
        if (audioData == null || audioData.length == 0) return;
        if (isWav(audioData)) {
            playWavAsync(audioData);
        } else {
            playMp3Async(audioData);
        }
    }

    private static boolean isWav(byte[] d) {
        return d.length >= 12
                && d[0] == 'R' && d[1] == 'I' && d[2] == 'F' && d[3] == 'F'
                && d[8] == 'W' && d[9] == 'A' && d[10] == 'V' && d[11] == 'E';
    }

    private static void playMp3Async(byte[] mp3Data) {
        Thread t = new Thread(() -> {
            try {
                LOGGER.info("[Sevino Audio] Reproduciendo MP3 ({} bytes)...", mp3Data.length);
                try (ByteArrayInputStream in = new ByteArrayInputStream(mp3Data)) {
                    Player player = new Player(in);
                    player.play();
                }
                LOGGER.info("[Sevino Audio] Fin de reproduccion MP3.");
            } catch (JavaLayerException e) {
                LOGGER.error("[Sevino Audio] Error al decodificar/reproducir MP3", e);
            } catch (Exception e) {
                LOGGER.error("[Sevino Audio] Error al reproducir MP3", e);
            }
        }, "Sevino-VoiceMp3");
        t.setDaemon(false);
        t.start();
    }

    private static void playWavAsync(byte[] wavData) {
        new Thread(() -> {
            try (InputStream is = new ByteArrayInputStream(wavData);
                 AudioInputStream ais = AudioSystem.getAudioInputStream(is)) {

                AudioFormat format = ais.getFormat();
                DataLine.Info info = new DataLine.Info(SourceDataLine.class, format);

                if (!AudioSystem.isLineSupported(info)) {
                    LOGGER.warn("[Sevino Audio] Formato no soportado por el hardware: {}", format);
                    return;
                }

                try (SourceDataLine line = (SourceDataLine) AudioSystem.getLine(info)) {
                    line.open(format);
                    if (line.isControlSupported(FloatControl.Type.MASTER_GAIN)) {
                        FloatControl gain = (FloatControl) line.getControl(FloatControl.Type.MASTER_GAIN);
                        gain.setValue(gain.getMaximum());
                    }
                    line.start();
                    LOGGER.info("[Sevino Audio] Reproduciendo WAV ({} bytes)...", wavData.length);

                    byte[] buffer = new byte[4096];
                    int n;
                    while ((n = ais.read(buffer)) != -1) {
                        line.write(buffer, 0, n);
                    }
                    line.drain();
                    LOGGER.info("[Sevino Audio] Fin de reproduccion WAV.");
                }
            } catch (Exception e) {
                LOGGER.error("[Sevino Audio] Error al reproducir WAV", e);
            }
        }, "Sevino-VoiceWav").start();
    }
}
