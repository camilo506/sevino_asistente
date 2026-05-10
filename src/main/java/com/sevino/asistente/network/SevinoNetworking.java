package com.sevino.asistente.network;

import com.sevino.asistente.SevinoAsistente;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.Optional;
import java.util.function.Supplier;

import net.minecraftforge.network.NetworkDirection;

/**
 * Envía la reproducción de voz al cliente: el TTS y {@link SevinoAudioPlayer} deben ejecutarse
 * en la máquina del jugador; si se hace en el servidor dedicado no se oye nada en el juego.
 * En el cliente se delega a {@code com.sevino.asistente.client.SevinoVoiceClient} (narrador o StreamElements).
 */
public final class SevinoNetworking {

    private static final String PROTOCOL = "1";
    private static int packetId;

    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(SevinoAsistente.MOD_ID, "voice"),
            () -> PROTOCOL,
            PROTOCOL::equals,
            PROTOCOL::equals
    );

    private SevinoNetworking() {}

    public static void register() {
        CHANNEL.registerMessage(
                packetId++,
                VoiceToClientPacket.class,
                VoiceToClientPacket::encode,
                VoiceToClientPacket::decode,
                VoiceToClientPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT)
        );
    }

    /**
     * Pide al cliente que sintetice y reproduzca el texto (mismo jugador que recibe el chat).
     */
    public static void sendVoice(ServerPlayer player, String text) {
        if (text == null || text.isBlank()) return;
        // Límite razonable para el tamaño del paquete y URLs GET del TTS.
        String clipped = text.length() > 6000 ? text.substring(0, 6000) : text;
        SevinoAsistente.LOGGER.debug("[Sevino Voz] Enviando paquete de voz al cliente ({} caracteres).", clipped.length());
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new VoiceToClientPacket(clipped));
    }

    public static final class VoiceToClientPacket {
        private final String text;

        public VoiceToClientPacket(String text) {
            this.text = text;
        }

        public static void encode(VoiceToClientPacket p, FriendlyByteBuf buf) {
            buf.writeUtf(p.text, 32767);
        }

        public static VoiceToClientPacket decode(FriendlyByteBuf buf) {
            return new VoiceToClientPacket(buf.readUtf(32767));
        }

        public static void handle(VoiceToClientPacket p, Supplier<NetworkEvent.Context> ctx) {
            NetworkEvent.Context context = ctx.get();
            if (context.getDirection().getReceptionSide().isClient()) {
                final String voiceText = p.text;
                context.enqueueWork(() -> {
                    try {
                        Class<?> voice = Class.forName("com.sevino.asistente.client.SevinoVoiceClient");
                        voice.getMethod("onVoicePacket", String.class).invoke(null, voiceText);
                    } catch (ReflectiveOperationException e) {
                        SevinoAsistente.LOGGER.error("[Sevino] No se pudo cargar la voz en el cliente", e);
                    }
                });
            }
            context.setPacketHandled(true);
        }
    }
}
