package com.moulberry.moulberrystweaks.config;

import com.moulberry.lattice.LatticeDynamicFrequency;
import com.moulberry.lattice.annotation.LatticeCategory;
import com.moulberry.lattice.annotation.LatticeOption;
import com.moulberry.lattice.annotation.constraint.LatticeHideIf;
import com.moulberry.lattice.annotation.widget.LatticeWidgetButton;
import com.moulberry.lattice.annotation.widget.LatticeWidgetKeybind;
import com.moulberry.lattice.annotation.widget.LatticeWidgetMessage;
import com.moulberry.moulberrystweaks.MoulberrysTweaks;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;

public class MoulberrysTweaksConfig {

    @LatticeCategory(name = "moulberrystweaks.config.gameplay")
    public Gameplay gameplay = new Gameplay();

    public static class Gameplay {
        @LatticeOption(title = "moulberrystweaks.config.gameplay.confirm_disconnect", description = "!!.description")
        @LatticeWidgetButton
        public boolean confirmDisconnect = false;

        @LatticeOption(title = "moulberrystweaks.config.gameplay.correct_attack_indicator", description = "!!.description")
        @LatticeWidgetButton
        public boolean correctAttackIndicator = false;

        @LatticeOption(title = "moulberrystweaks.config.gameplay.prevent_server_closing_pause_screen", description = "!!.description")
        @LatticeWidgetButton
        public boolean preventServerClosingPauseScreen = false;
    }

    @LatticeCategory(name = "moulberrystweaks.config.loading_overlay")
    public LoadingOverlay loadingOverlay = new LoadingOverlay();

    public static class LoadingOverlay {
        @LatticeOption(title = "moulberrystweaks.config.loading_overlay.fast", description = "!!.description")
        @LatticeWidgetButton
        public boolean fast = false;

        @LatticeOption(title = "moulberrystweaks.config.loading_overlay.transparent", description = "!!.description")
        @LatticeWidgetButton
        public boolean transparent = false;
    }

    @LatticeCategory(name = "moulberrystweaks.config.resource_pack")
    public ResourcePack resourcePack = new ResourcePack();

    public static class ResourcePack {
        @LatticeOption(title = "moulberrystweaks.config.resource_pack.automatic_pack_reload", description = "!!.description")
        @LatticeWidgetButton
        public boolean automaticPackReload = false;

        @LatticeOption(title = "moulberrystweaks.config.resource_pack.disable_warnings", description = "!!.description")
        @LatticeWidgetButton
        public boolean disableWarnings = false;
    }

    @LatticeCategory(name = "moulberrystweaks.config.debugging")
    public Debugging debugging = new Debugging();

    public static class Debugging {
        @LatticeOption(title = "moulberrystweaks.config.debugging.log_packet_exceptions", description = "!!.description")
        @LatticeWidgetButton
        public boolean logPacketExceptions = true;

        @LatticeOption(title = "moulberrystweaks.config.debugging.ignore_narrator_error", description = "!!.description")
        @LatticeWidgetButton
        public boolean ignoreNarratorError = true;

        @LatticeCategory(name = "moulberrystweaks.config.debugging.inventory")
        public Inventory inventory = new Inventory();

        @LatticeOption(title = "moulberrystweaks.config.debugging.debug_movement", description = "!!.description")
        @LatticeWidgetButton
        public boolean debugMovement = false;

        @LatticeOption(title = "moulberrystweaks.config.debugging.add_open_report_file", description = "moulberrystweaks.config.debugging.add_open_report_file.description")
        @LatticeWidgetButton
        public boolean addOpenReportFileButton = true;

        public static class Inventory {
            @LatticeOption(title = "moulberrystweaks.config.debugging.inventory.item_component_widget", description = "!!.description")
            @LatticeWidgetKeybind
            public transient KeyMapping itemComponentWidgetKeybind = null;

            @LatticeOption(title = "moulberrystweaks.config.debugging.inventory.packet_debug_widget", description = "!!.description")
            @LatticeWidgetKeybind
            public transient KeyMapping packetDebugWidgetKeybind = null;
        }
    }

    @LatticeCategory(name = "moulberrystweaks.config.commands")
    public Commands commands = new Commands();

    public static class Commands {
        @LatticeOption(title = "moulberrystweaks.config.commands.auto_vanish_players", description = "!!.description")
        @LatticeWidgetButton
        public boolean autoVanishPlayers = true;

        @LatticeOption(title = "moulberrystweaks.config.commands.dump_held_json", description = "!!.description")
        @LatticeWidgetButton
        public boolean dumpHeldJson = false;

        @LatticeOption(title = "moulberrystweaks.config.commands.generate_font_width_table", description = "!!.description")
        @LatticeWidgetButton
        public boolean generateFontWidthTable = false;

        @LatticeOption(title = "moulberrystweaks.config.commands.dump_player_attributes", description = "!!.description")
        @LatticeWidgetButton
        public boolean dumpPlayerAttributes = false;

        @LatticeOption(title = "moulberrystweaks.config.commands.debug_render", description = "!!.description")
        @LatticeWidgetButton
        public boolean debugRender = false;

        @LatticeOption(title = "moulberrystweaks.config.commands.set_latency", description = "!!.description")
        @LatticeWidgetButton
        public boolean setLatency = false;

        @LatticeWidgetMessage
        @LatticeHideIf(function = "hideRequiresRelogMessage", frequency = LatticeDynamicFrequency.EVERY_TICK)
        public transient Component requiresRelogMessage = Component.literal("Relog is required in order to reload commands").withStyle(ChatFormatting.RED);

        private boolean hideRequiresRelogMessage() {
            return Minecraft.getInstance().player == null ||
                (this.autoVanishPlayers == MoulberrysTweaks.autoVanishPlayersRegistered &&
                this.dumpHeldJson == MoulberrysTweaks.dumpHeldJsonRegistered &&
                this.generateFontWidthTable == MoulberrysTweaks.generateFontWidthTableRegistered &&
                this.dumpPlayerAttributes == MoulberrysTweaks.dumpPlayerAttributesRegistered &&
                this.debugRender == MoulberrysTweaks.debugRenderRegistered &&
                this.setLatency == MoulberrysTweaks.setLatencyRegistered);
        }
    }

    public static MoulberrysTweaksConfig loadFromDefaultFolder() {
        Path configFolder = FabricLoader.getInstance().getConfigDir().resolve("moulberrystweaks");
        return tryLoadFromFolder(configFolder);
    }

    public static MoulberrysTweaksConfig tryLoadFromFolder(Path configFolder) {
        if (Files.exists(configFolder)) {
            Path primary = configFolder.resolve("moulberrystweaks.json");
            Path backup = configFolder.resolve(".moulberrystweaks.json.backup");

            if (Files.exists(primary)) {
                try {
                    return load(primary);
                } catch (Exception e) {
                    MoulberrysTweaks.LOGGER.error("Failed to load config from {}", primary, e);
                }
            }

            if (Files.exists(backup)) {
                try {
                    return load(backup);
                } catch (Exception e) {
                    MoulberrysTweaks.LOGGER.error("Failed to load config from {}", backup, e);
                }
            }
        }


        return new MoulberrysTweaksConfig();
    }

    public void saveToDefaultFolder() {
        Path configFolder = FabricLoader.getInstance().getConfigDir().resolve("moulberrystweaks");
        if (!Files.exists(configFolder)) {
            try {
                Files.createDirectories(configFolder);
            } catch (IOException ignored) {
            }
        }
        this.saveToFolder(configFolder);
    }

    public synchronized void saveToFolder(Path configFolder) {
        Path primary = configFolder.resolve("moulberrystweaks.json");
        Path backup = configFolder.resolve(".moulberrystweaks.json.backup");

        if (Files.exists(primary)) {
            try {
                // Ensure primary can be loaded before backing it up
                // Don't want to back up a bad config now, do we?
                load(primary);

                // Try to back up the config
                try {
                    Files.move(primary, backup, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                } catch (IOException e) {
                    MoulberrysTweaks.LOGGER.error("Failed to backup config", e);
                }
            } catch (Exception ignored) {
            }
        }

        this.save(primary);
    }

    private static MoulberrysTweaksConfig load(Path path) throws IOException {
        String serialized = Files.readString(path);
        return MoulberrysTweaks.GSON.fromJson(serialized, MoulberrysTweaksConfig.class);
    }

    private void save(Path path) {
        String serialized = MoulberrysTweaks.GSON.toJson(this, MoulberrysTweaksConfig.class);

        try {
            Files.writeString(path, serialized, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.CREATE, StandardOpenOption.DSYNC);
        } catch (IOException e) {
            MoulberrysTweaks.LOGGER.error("Failed to save config", e);
        }
    }

}
