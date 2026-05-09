package com.sevino.asistente.client;

import com.sevino.asistente.SevinoAsistente;

import javax.sound.sampled.*;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * Captura audio del micrófono y lo guarda en memoria para su procesamiento.
 */
public class AudioRecorder {
    private static final AudioFormat FORMAT = new AudioFormat(16000, 16, 1, true, false);
    private TargetDataLine line;
    private ByteArrayOutputStream out;
    private boolean recording = false;

    public void start() {
        if (recording) return;
        try {
            DataLine.Info info = new DataLine.Info(TargetDataLine.class, FORMAT);
            if (!AudioSystem.isLineSupported(info)) {
                return;
            }
            line = (TargetDataLine) AudioSystem.getLine(info);
            line.open(FORMAT);
            line.start();

            out = new ByteArrayOutputStream();
            recording = true;

            Thread t = new Thread(() -> {
                byte[] buffer = new byte[4096];
                while (recording) {
                    int count = line.read(buffer, 0, buffer.length);
                    if (count > 0) out.write(buffer, 0, count);
                }
            });
            t.setDaemon(true);
            t.start();
        } catch (Exception e) {
            // Error silencioso para evitar crash
        }
    }

    public byte[] stop() {
        try {
            if (!recording) return null;
            recording = false;
            if (line != null) {
                line.stop();
                line.close();
            }

            byte[] pcmData = out.toByteArray();
            return convertToWav(pcmData);
        } catch (Exception e) {
            return null;
        }
    }

    public boolean isRecording() {
        return recording;
    }

    private byte[] convertToWav(byte[] pcmData) {
        ByteArrayOutputStream wavOut = new ByteArrayOutputStream();
        try {
            AudioInputStream ais = new AudioInputStream(
                    new ByteArrayInputStream(pcmData), FORMAT, pcmData.length / FORMAT.getFrameSize());
            AudioSystem.write(ais, AudioFileFormat.Type.WAVE, wavOut);
            ais.close();
        } catch (Exception e) {
            // Error silencioso
        }
        return wavOut.toByteArray();
    }
}
