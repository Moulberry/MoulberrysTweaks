package com.moulberry.moulberrystweaks.mixin.autovanish;

import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.PoseStack;
import com.moulberry.moulberrystweaks.AutoVanishPlayers;
import com.moulberry.moulberrystweaks.MoulberrysTweaks;
import com.moulberry.moulberrystweaks.ext.TranslucentAlphaExt;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntityRenderer.class)
public abstract class MixinLivingEntityRenderer extends EntityRenderer<LivingEntity, LivingEntityRenderState> {

    @Shadow
    public abstract ResourceLocation getTextureLocation(LivingEntityRenderState livingEntityRenderState);

    protected MixinLivingEntityRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Inject(method = "extractRenderState(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;F)V", at = @At("RETURN"))
    public void extractRenderState(LivingEntity livingEntity, LivingEntityRenderState livingEntityRenderState, float f, CallbackInfo ci) {
        // Note: TranslucentAlphaExt is only implemented for PlayerRenderState, so this only affects players
        if (livingEntityRenderState instanceof TranslucentAlphaExt ext) {
            if (AutoVanishPlayers.isEnabled && livingEntity != Minecraft.getInstance().cameraEntity) { // Enabled
                Camera camera = Minecraft.getInstance().gameRenderer.getMainCamera();
                double distanceSq = livingEntity.getBoundingBox().distanceToSqr(camera.getPosition());
                if (distanceSq <= 0.2*0.2) {
                    ext.moulberrystweaks$setTranslucentAlpha(0x00);
                } else if (distanceSq < 5*5) {
                    ext.moulberrystweaks$setTranslucentAlpha((int)(distanceSq/25 * (0xFF - 0x20) + 0x20));
                } else {
                    ext.moulberrystweaks$setTranslucentAlpha(0xFF);
                }
            } else {
                ext.moulberrystweaks$setTranslucentAlpha(0xFF);
            }
        }
    }

    @Inject(method = "render(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V", at = @At("HEAD"), cancellable = true)
    public void render(LivingEntityRenderState livingEntity, PoseStack poseStack, MultiBufferSource multiBufferSource, int i, CallbackInfo ci) {
        if (livingEntity instanceof TranslucentAlphaExt ext) {
            int translucentAlpha = ext.moulberrystweaks$getTranslucentAlpha();
            if (translucentAlpha <= 0) {
                ci.cancel();
            }
        }
    }

    @ModifyArg(method = "render(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/model/EntityModel;renderToBuffer(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;III)V"), index = 4)
    public int render_renderToBuffer(int argb, @Local(argsOnly = true) LivingEntityRenderState livingEntityRenderState) {
        if (livingEntityRenderState instanceof TranslucentAlphaExt ext) {
            int oldAlpha = (argb >> 24) & 0xFF;
            int newAlpha = Math.min(oldAlpha, ext.moulberrystweaks$getTranslucentAlpha());
            argb &= 0xFFFFFF;
            argb |= newAlpha << 24;
        }
        return argb;
    }

    @Inject(method = "getRenderType", at = @At("HEAD"), cancellable = true)
    public void getRenderType(LivingEntityRenderState livingEntityRenderState, boolean visible, boolean translucent, boolean glowing, CallbackInfoReturnable<RenderType> cir) {
        if (visible && livingEntityRenderState instanceof TranslucentAlphaExt ext && ext.moulberrystweaks$getTranslucentAlpha() < 0xFF) {
            cir.setReturnValue(RenderType.itemEntityTranslucentCull(this.getTextureLocation(livingEntityRenderState)));
        }
    }

}
