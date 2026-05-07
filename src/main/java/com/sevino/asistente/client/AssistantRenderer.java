package com.sevino.asistente.client;

import com.sevino.asistente.SevinoAsistente;
import com.sevino.asistente.entity.AssistantEntity;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.resources.ResourceLocation;

public class AssistantRenderer extends HumanoidMobRenderer<AssistantEntity, AssistantModel> {

    private static final ResourceLocation TEXTURE =
            new ResourceLocation(SevinoAsistente.MOD_ID, "textures/entity/assistant.png");

    public AssistantRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new AssistantModel(ctx.bakeLayer(ModModelLayers.ASSISTANT)), 0.5F);

        // Permitir armadura encima (visualmente). Le damos modelos vacios para evitar errores.
        this.addLayer(new HumanoidArmorLayer<>(
                this,
                new HumanoidModel<>(ctx.bakeLayer(ModelLayers.PLAYER_INNER_ARMOR)),
                new HumanoidModel<>(ctx.bakeLayer(ModelLayers.PLAYER_OUTER_ARMOR)),
                ctx.getModelManager()
        ));
    }

    @Override
    public ResourceLocation getTextureLocation(AssistantEntity entity) {
        return TEXTURE;
    }
}
