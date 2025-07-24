package com.moulberry.moulberrystweaks.mixin.addopenreportbutton;

import com.moulberry.moulberrystweaks.MoulberrysTweaks;
import com.moulberry.moulberrystweaks.Translations;
import net.minecraft.Util;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.network.DisconnectionDetails;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(DisconnectedScreen.class)
public class MixinDisconnectedScreen {

    @Shadow
    @Final
    private LinearLayout layout;

    @Shadow
    @Final
    private DisconnectionDetails details;

    @Inject(at = @At(value = "INVOKE", target = "Lnet/minecraft/network/DisconnectionDetails;bugReportLink()Ljava/util/Optional;"), method = "init")
    private void init(CallbackInfo ci) {
        if (!MoulberrysTweaks.config.debugging.addOpenReportFileButton) return;

        var button = Button.builder(Translations.OPEN_REPORT_FILE, buttonx -> {
            if (this.details.report().isPresent()) {
                Util.getPlatform().openFile(this.details.report().get().toFile());
            }
        }).width(200).build();

        if (this.details.report().isPresent()) {
            this.layout.addChild(button);
        }
    }
}
