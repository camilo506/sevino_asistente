package com.sevino.asistente.client;

import com.sevino.asistente.entity.AssistantEntity;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;

/**
 * Modelo del asistente. Reusa la geometria humanoide estandar de Minecraft
 * (mismo layout de UVs que un skin 64x64 de jugador) para que sea facil
 * crear una textura nueva en cualquier editor de skins.
 */
public class AssistantModel extends HumanoidModel<AssistantEntity> {

    public AssistantModel(ModelPart root) {
        super(root);
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = HumanoidModel.createMesh(CubeDeformation.NONE, 0.0F);
        // Reusamos el mesh humanoide base (Steve-like). Devolvemos LayerDefinition.
        return LayerDefinition.create(mesh, 64, 64);
    }

    @Override
    public void setupAnim(AssistantEntity entity, float limbSwing, float limbSwingAmount,
                          float ageInTicks, float netHeadYaw, float headPitch) {
        super.setupAnim(entity, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch);
        // Ligero balanceo "amistoso" en los brazos cuando esta quieto, para que se vea vivo.
        if (limbSwingAmount < 0.05F) {
            this.rightArm.zRot = (float) Math.cos(ageInTicks * 0.06F) * 0.05F + 0.05F;
            this.leftArm.zRot = -this.rightArm.zRot;
        }
    }
}
