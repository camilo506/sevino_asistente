package com.sevino.asistente.client;

import com.sevino.asistente.SevinoAsistente;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.resources.ResourceLocation;

public final class ModModelLayers {
    public static final ModelLayerLocation ASSISTANT =
            new ModelLayerLocation(new ResourceLocation(SevinoAsistente.MOD_ID, "assistant"), "main");

    private ModModelLayers() {}
}
