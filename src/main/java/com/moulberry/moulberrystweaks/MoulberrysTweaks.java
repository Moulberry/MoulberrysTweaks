package com.moulberry.moulberrystweaks;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.serialization.JsonOps;
import com.moulberry.lattice.Lattice;
import com.moulberry.lattice.element.LatticeElements;
import com.moulberry.moulberrystweaks.config.MoulberrysTweaksConfig;
import com.moulberry.moulberrystweaks.debugrender.DebugRenderManager;
import com.moulberry.moulberrystweaks.packet.AutoVanishPlayersSetPacket;
import com.moulberry.moulberrystweaks.packet.DebugMovementDataPacket;
import com.moulberry.moulberrystweaks.packet.DebugRenderAddPacket;
import com.moulberry.moulberrystweaks.packet.DebugRenderClearNamespacePacket;
import com.moulberry.moulberrystweaks.packet.DebugRenderClearPacket;
import com.moulberry.moulberrystweaks.packet.DebugRenderRemovePacket;
import com.moulberry.moulberrystweaks.widget.ActiveWidgets;
import net.fabricmc.api.ModInitializer;

import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientWorldEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.C2SPlayChannelEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudLayerRegistrationCallback;
import net.fabricmc.fabric.api.client.rendering.v1.IdentifiedLayer;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.font.FontManager;
import net.minecraft.client.gui.font.FontSet;
import net.minecraft.client.gui.screens.LoadingOverlay;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

public class MoulberrysTweaks implements ModInitializer {
	public static final String MOD_ID = "moulberrystweaks";

	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    public static Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static KeyMapping viewComponentsKeyBind = null;
    public static KeyMapping viewPacketsKeyBind = null;

    public static MoulberrysTweaksConfig config = new MoulberrysTweaksConfig();
    public static LatticeElements configElements = null;

    public static boolean autoVanishPlayersRegistered = false;
    public static boolean dumpHeldJsonRegistered = false;
    public static boolean generateFontWidthTableRegistered = false;
    public static boolean dumpPlayerAttributesRegistered = false;
    public static boolean debugRenderRegistered = false;
    public static boolean setLatencyRegistered = false;

    public static boolean supportsDebugMovementDataPacket = false;

    public static long additionalLatencyMs = 0;

	@Override
	public void onInitialize() {
		LOGGER.info("Initializing Moulberry's Tweaks");

        config = MoulberrysTweaksConfig.loadFromDefaultFolder();
        viewComponentsKeyBind = KeyBindingHelper.registerKeyBinding(new KeyMapping("moulberrystweaks.keybind.view_components",
            InputConstants.Type.KEYSYM, InputConstants.UNKNOWN.getValue(), "moulberrystweaks.keybind"));
        viewPacketsKeyBind = KeyBindingHelper.registerKeyBinding(new KeyMapping("moulberrystweaks.keybind.view_packets",
            InputConstants.Type.KEYSYM, InputConstants.UNKNOWN.getValue(), "moulberrystweaks.keybind"));
        config.debugging.inventory.itemComponentWidgetKeybind = viewComponentsKeyBind;
        config.debugging.inventory.packetDebugWidgetKeybind = viewPacketsKeyBind;
        configElements = LatticeElements.fromAnnotations(Component.literal("Moulberry's Tweaks"), config);

        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            DebugRenderManager.clear();
            AutoVanishPlayers.setServerState(ServerState.CLIENT_OR_DEFAULT);
            additionalLatencyMs = 0;
        });

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            DebugRenderManager.clear();
            AutoVanishPlayers.setServerState(ServerState.CLIENT_OR_DEFAULT);
            additionalLatencyMs = 0;
        });

        ClientWorldEvents.AFTER_CLIENT_WORLD_CHANGE.register((handler, client) -> {
            DebugRenderManager.clear();
        });

        C2SPlayChannelEvents.REGISTER.register((handler, sender, client, channels) -> {
            if (channels.contains(DebugMovementDataPacket.PACKET_ID)) {
                supportsDebugMovementDataPacket = true;
            }
        });

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            supportsDebugMovementDataPacket = false;
        });

        if (FabricLoader.getInstance().isDevelopmentEnvironment()) {
            Minecraft.getInstance().schedule(() -> Lattice.performTest(configElements));
        }

        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            var command = ClientCommandManager.literal("moulberrystweaks")
                .executes(commandContext -> {
                    Minecraft.getInstance().schedule(() -> Minecraft.getInstance().setScreen(
                        Lattice.createConfigScreen(configElements, config::saveToDefaultFolder, Minecraft.getInstance().screen)
                    ));
                    return 0;
                });
            dispatcher.register(command);

            autoVanishPlayersRegistered = config.commands.autoVanishPlayers;
            if (config.commands.autoVanishPlayers) {
                command = ClientCommandManager.literal("autovanishplayers");
                command.then(ClientCommandManager.literal("on").executes(cmd -> {
                    AutoVanishPlayers.setClientState(true);
                    switch (AutoVanishPlayers.serverState()) {
                        case CLIENT_OR_DEFAULT, ON, OFF -> cmd.getSource().sendFeedback(Component.literal("AutoVanishPlayers is now ON").withStyle(ChatFormatting.YELLOW));
                        case FORCE_ON -> cmd.getSource().sendFeedback(Component.literal("AutoVanishPlayers was already forced ON by server").withStyle(ChatFormatting.YELLOW));
                        case FORCE_OFF -> cmd.getSource().sendFeedback(Component.literal("Can't enable, AutoVanishPlayers is forced OFF by server").withStyle(ChatFormatting.RED));
                    }
                    return 0;
                }));
                command.then(ClientCommandManager.literal("off").executes(cmd -> {
                    AutoVanishPlayers.setClientState(false);
                    switch (AutoVanishPlayers.serverState()) {
                        case CLIENT_OR_DEFAULT, ON, OFF -> cmd.getSource().sendFeedback(Component.literal("AutoVanishPlayers is now OFF").withStyle(ChatFormatting.YELLOW));
                        case FORCE_ON -> cmd.getSource().sendFeedback(Component.literal("Can't disable, AutoVanishPlayers is forced ON by server").withStyle(ChatFormatting.YELLOW));
                        case FORCE_OFF -> cmd.getSource().sendFeedback(Component.literal("AutoVanishPlayers was already forced OFF by server").withStyle(ChatFormatting.RED));
                    }
                    return 0;
                }));
                command.executes(cmd -> {
                    String text = AutoVanishPlayers.isEnabled ? "AutoVanishPlayers is ON" : "AutoVanishPlayers is OFF";
                    switch (AutoVanishPlayers.serverState()) {
                        case FORCE_ON, FORCE_OFF -> text += " (forced by server)";
                        case ON, OFF -> text += " (set by server)";
                        case CLIENT_OR_DEFAULT -> {}
                    }
                    cmd.getSource().sendFeedback(Component.literal(text).withStyle(ChatFormatting.YELLOW));
                    return 0;
                });
                dispatcher.register(command);
            }

            dumpHeldJsonRegistered = config.commands.dumpHeldJson;
            if (config.commands.dumpHeldJson) {
                command = ClientCommandManager.literal("dumpheldjson")
                          .executes(commandContext -> {
                              var player = commandContext.getSource().getPlayer();
                              ItemStack itemStack = player.getMainHandItem();
                              var output = ItemStack.CODEC.encodeStart(player.registryAccess().createSerializationContext(JsonOps.INSTANCE), itemStack).getOrThrow();
                              System.out.println(output);
                              return 0;
                          });
                dispatcher.register(command);
            }

            generateFontWidthTableRegistered = config.commands.generateFontWidthTable;
            if (config.commands.generateFontWidthTable) {
                command = ClientCommandManager.literal("generatefontwidthtable")
                          .then(ClientCommandManager.argument("font", ResourceLocationArgument.id())
                                                    .executes(MoulberrysTweaks::writeFontWidths));
                dispatcher.register(command);
            }

            dumpPlayerAttributesRegistered = config.commands.dumpPlayerAttributes;
            if (config.commands.dumpPlayerAttributes) {
                command = ClientCommandManager.literal("dumpplayerattributes")
                      .executes(commandContext -> {
                          var player = commandContext.getSource().getPlayer();
                          Set<AttributeInstance> modified = player.getAttributes().getAttributesToSync();
                          if (!modified.isEmpty()) {
                              player.displayClientMessage(Component.literal("Modified Attributes").withStyle(ChatFormatting.BOLD), false);
                              for (AttributeInstance attribute : modified) {
                                  player.displayClientMessage(Component.literal(attribute.getAttribute().unwrapKey().get().location().toString()).withStyle(ChatFormatting.UNDERLINE), false);
                                  player.displayClientMessage(Component.literal("Base Value: " + attribute.getBaseValue()), false);
                                  player.displayClientMessage(Component.literal("Value: " + attribute.getValue()), false);

                                  if (!attribute.getModifiers().isEmpty()) {
                                      player.displayClientMessage(Component.literal("Modifiers:"), false);

                                      List<AttributeModifier> addValue = new ArrayList<>();
                                      List<AttributeModifier> addMultipliedBase = new ArrayList<>();
                                      List<AttributeModifier> addMultipliedTotal = new ArrayList<>();
                                      List<AttributeModifier> unknown = new ArrayList<>();
                                      for (AttributeModifier modifier : attribute.getModifiers()) {
                                          switch (modifier.operation()) {
                                              case ADD_VALUE -> addValue.add(modifier);
                                              case ADD_MULTIPLIED_BASE -> addMultipliedBase.add(modifier);
                                              case ADD_MULTIPLIED_TOTAL -> addMultipliedTotal.add(modifier);
                                              default -> unknown.add(modifier);
                                          }
                                      }
                                      addValue.sort(Comparator.comparing(modifier -> modifier.id().toString()));
                                      addMultipliedBase.sort(Comparator.comparing(modifier -> modifier.id().toString()));
                                      addMultipliedTotal.sort(Comparator.comparing(modifier -> modifier.id().toString()));
                                      unknown.sort(Comparator.comparing(modifier -> modifier.id().toString()));

                                      double value = attribute.getBaseValue();
                                      for (AttributeModifier modifier : addValue) {
                                          value += modifier.amount();
                                          player.displayClientMessage(Component.literal("  " + modifier.id() + ": +" + modifier.amount() + " => " + value), false);

                                      }
                                      double newValue = value;
                                      for (AttributeModifier modifier : addMultipliedBase) {
                                          newValue += value * modifier.amount();
                                          player.displayClientMessage(Component.literal("  " + modifier.id() + ": +sum*" + modifier.amount() + " => " + newValue), false);
                                      }
                                      for (AttributeModifier modifier : addMultipliedTotal) {
                                          newValue *= 1.0 + modifier.amount();
                                          player.displayClientMessage(Component.literal("  " + modifier.id() + ": *(1.0+" + modifier.amount()+") => " + newValue), false);
                                      }

                                      if (!unknown.isEmpty()) {
                                          player.displayClientMessage(Component.literal("Unknown modifier type, above calculation maybe be incorrect..."), false);
                                      }
                                      for (AttributeModifier modifier : unknown) {
                                          player.displayClientMessage(Component.literal("  " + modifier.id() + " (" + modifier.operation() + ") => " + modifier.amount()), false);
                                      }


                                  }
                              }
                          }
                          return 0;
                      });
                dispatcher.register(command);
            }

            debugRenderRegistered = config.commands.debugRender;
            if (config.commands.debugRender) {
                var debugRenderClear = ClientCommandManager.literal("clear").executes(context -> {
                    DebugRenderManager.clear();
                    return 0;
                });
                var debugRenderHide = ClientCommandManager.literal("hide").executes(context -> {
                    boolean success = DebugRenderManager.hideAll();
                    if (success) {
                        context.getSource().sendFeedback(Component.literal("Hiding all debug renders").withStyle(ChatFormatting.YELLOW));
                    } else {
                        context.getSource().sendFeedback(Component.literal("All debug renders are already hidden").withStyle(ChatFormatting.RED));
                    }
                    return 0;
                });
                debugRenderHide.then(ClientCommandManager.argument("namespace", StringArgumentType.word()).suggests((commandContext, suggestionsBuilder) -> {
                    for (String namespace : DebugRenderManager.availableNamespaces) {
                        if (!DebugRenderManager.hiddenNamespaces.contains(namespace)) {
                            suggestionsBuilder.suggest(namespace);
                        }
                    }
                    return suggestionsBuilder.buildFuture();
                }).executes(context -> {
                    String namespace = context.getArgument("namespace", String.class);
                    boolean success = DebugRenderManager.hideNamespace(namespace);
                    if (success) {
                        context.getSource().sendFeedback(Component.literal("Namespace '" + namespace + "' is now hidden").withStyle(ChatFormatting.YELLOW));
                    } else {
                        context.getSource().sendFeedback(Component.literal("Namespace '" + namespace + "' was already hidden").withStyle(ChatFormatting.RED));
                    }
                    return 0;
                }));
                var debugRenderShow = ClientCommandManager.literal("show").executes(context -> {
                    boolean success = DebugRenderManager.showAll();
                    if (success) {
                        context.getSource().sendFeedback(Component.literal("Showing all debug renders").withStyle(ChatFormatting.YELLOW));
                    } else {
                        context.getSource().sendFeedback(Component.literal("All debug renders are already shown").withStyle(ChatFormatting.RED));
                    }
                    return 0;
                });
                debugRenderShow.then(ClientCommandManager.argument("namespace", StringArgumentType.word()).suggests((commandContext, suggestionsBuilder) -> {
                    for (String namespace : DebugRenderManager.isAllHidden() ? DebugRenderManager.availableNamespaces : DebugRenderManager.hiddenNamespaces) {
                        suggestionsBuilder.suggest(namespace);
                    }
                    return suggestionsBuilder.buildFuture();
                }).executes(context -> {
                    String namespace = context.getArgument("namespace", String.class);
                    boolean success = DebugRenderManager.showNamespace(namespace);
                    if (success) {
                        context.getSource().sendFeedback(Component.literal("Namespace '" + namespace + "' is now shown").withStyle(ChatFormatting.YELLOW));
                    } else {
                        context.getSource().sendFeedback(Component.literal("Namespace '" + namespace + "' was already shown").withStyle(ChatFormatting.RED));
                    }
                    return 0;
                }));
                command = ClientCommandManager.literal("debugrender").then(debugRenderClear).then(debugRenderHide).then(debugRenderShow);
                dispatcher.register(command);
            }

            setLatencyRegistered = config.commands.setLatency;
            if (config.commands.setLatency) {
                command = ClientCommandManager.literal("setlatency")
                                              .requires(source -> source.getPlayer() != null && source.hasPermission(2))
                                              .then(ClientCommandManager.argument("latency", IntegerArgumentType.integer(0, 5000))
                                              .executes(commandContext -> {
                                                  additionalLatencyMs = IntegerArgumentType.getInteger(commandContext, "latency");
                                                  commandContext.getSource().sendFeedback(Component.literal("Set additional latency to: " + additionalLatencyMs + "ms"));
                                                  return 0;
                                              }));
                dispatcher.register(command);
            }
        });

        PayloadTypeRegistry.playC2S().register(DebugMovementDataPacket.TYPE, DebugMovementDataPacket.STREAM_CODEC);
        PayloadTypeRegistry.playS2C().register(DebugRenderAddPacket.TYPE, DebugRenderAddPacket.STREAM_CODEC);
        PayloadTypeRegistry.playS2C().register(DebugRenderRemovePacket.TYPE, DebugRenderRemovePacket.STREAM_CODEC);
        PayloadTypeRegistry.playS2C().register(DebugRenderClearPacket.TYPE, DebugRenderClearPacket.STREAM_CODEC);
        PayloadTypeRegistry.playS2C().register(DebugRenderClearNamespacePacket.TYPE, DebugRenderClearNamespacePacket.STREAM_CODEC);
        PayloadTypeRegistry.playS2C().register(AutoVanishPlayersSetPacket.TYPE, AutoVanishPlayersSetPacket.STREAM_CODEC);

        ClientPlayNetworking.registerGlobalReceiver(DebugRenderAddPacket.TYPE, DebugRenderAddPacket::handle);
        ClientPlayNetworking.registerGlobalReceiver(DebugRenderRemovePacket.TYPE, DebugRenderRemovePacket::handle);
        ClientPlayNetworking.registerGlobalReceiver(DebugRenderClearPacket.TYPE, DebugRenderClearPacket::handle);
        ClientPlayNetworking.registerGlobalReceiver(DebugRenderClearNamespacePacket.TYPE, DebugRenderClearNamespacePacket::handle);
        ClientPlayNetworking.registerGlobalReceiver(AutoVanishPlayersSetPacket.TYPE, AutoVanishPlayersSetPacket::handle);

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (!(Minecraft.getInstance().getOverlay() instanceof LoadingOverlay)) {
                PackFolderWatcher.tick();
            }
            ActiveWidgets.activeWidgets.removeIf(widget -> !widget.isOpen());
            DebugRenderManager.tick();
        });

        HudLayerRegistrationCallback.EVENT.register(layeredDrawer -> layeredDrawer.attachLayerAfter(IdentifiedLayer.DEBUG, ResourceLocation.fromNamespaceAndPath("moulberrystweaks", "after_debug"), (guiGraphics, tickCounter) -> {
            DebugRenderManager.renderGui(guiGraphics);
        }));
	}

    private static int writeFontWidths(CommandContext<FabricClientCommandSource> cmd) {
        ResourceLocation fontName = cmd.getArgument("font", ResourceLocation.class);

        Font font = Minecraft.getInstance().font;
        FontSet fontSet = font.fonts.apply(fontName);
        if (fontSet.name().equals(FontManager.MISSING_FONT)) {
            cmd.getSource().sendFeedback(Component.literal("Font does not exist"));
            return 0;
        }

        JsonArray array = new JsonArray();

        int lastWidth = -1;
        int runLength = 0;
        for (int c = Character.MIN_CODE_POINT; c <= Character.MAX_CODE_POINT; c++) {
            int width = Mth.ceil(fontSet.getGlyphInfo(c, false).getAdvance());

            if (lastWidth == -1) {
                lastWidth = width;
            } else if (lastWidth != width) {
                array.add(runLength);
                array.add(lastWidth);

                lastWidth = width;
                runLength = 1;
            } else {
                runLength += 1;
            }
        }

        array.add(runLength);
        array.add(lastWidth);

        Path path = FabricLoader.getInstance().getGameDir().resolve("widths.json");
        try {
            Files.writeString(path, new Gson().toJson(array));
            cmd.getSource().sendFeedback(Component.literal("Wrote widths.json in .minecraft folder"));
        } catch (IOException ignored) {
            cmd.getSource().sendFeedback(Component.literal("Failed to write widths.json"));
        }

        return 0;
    }
}
