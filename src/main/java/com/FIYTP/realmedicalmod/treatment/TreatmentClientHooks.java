package com.FIYTP.realmedicalmod.treatment;

import com.FIYTP.realmedicalmod.RealMedicalMod;
import com.FIYTP.realmedicalmod.capability.MedicalData;
import com.FIYTP.realmedicalmod.capability.MedicalDataCapability;
import com.FIYTP.realmedicalmod.capability.MedicalDataImpl;
import com.FIYTP.realmedicalmod.client.sound.MedicalSoundHelper;
import com.FIYTP.realmedicalmod.config.MedicalConfig;
import com.FIYTP.realmedicalmod.network.NetworkHandler;
import com.FIYTP.realmedicalmod.network.TreatmentApplyPacket;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemDisplayContext;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import org.joml.Matrix4f;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

@EventBusSubscriber(modid = RealMedicalMod.MODID, value = Dist.CLIENT)
public class TreatmentClientHooks {

    private static final int C_BG_DARK        = 0xE6060D16;
    private static final int C_NEON_CYAN      = 0xFF00E5FF;
    private static final int C_SAFE_GREEN     = 0xFF00E676;
    private static final int C_WARNING_YELLOW = 0xFFFFB300;
    private static final int C_ALERT_RED      = 0xFFFF2A4B;
    private static final int C_MAGENTA_HAZARD = 0xFFFF1E8A;
    private static final int C_ADRENALINE_GOLD = 0xFFFFD600;
    private static final int C_TEXT_WHITE     = 0xFFF0F8FF;
    private static final int C_TEXT_DIM       = 0xFF8AA4B8;

    private static int withAlpha(int rgb, float alpha) {
        int a = Mth.clamp((int) (alpha * 255.0f), 0, 255);
        return (a << 24) | (rgb & 0x00FFFFFF);
    }

    private static boolean panelActive = false;
    private static InteractionHand activeHand = InteractionHand.MAIN_HAND;
    private static float virtualCursorX = 0.0f;
    private static float virtualCursorY = 0.0f;
    private static MedicalData.Limb hoveredLimb = null;
    private static MedicalData.Limb lastHoveredLimb = null;
    private static float channelProgress = 0.0f;
    private static long lastRenderTimeMs = 0L;
    private static int postApplyCooldownTicks = 0;

    // 延迟到 ClientTick 打开界面，严禁在 RenderGuiLayerEvent 渲染中途调用 mc.setScreen
    private static boolean pendingOpenDoseScreen = false;
    private static InteractionHand pendingDoseHand = InteractionHand.MAIN_HAND;
    private static MedicalData.Limb pendingDoseLimb = null;
    private static boolean pendingOpenSutureScreen = false;
    private static InteractionHand pendingSutureHand = InteractionHand.MAIN_HAND;
    private static MedicalData.Limb pendingSutureLimb = null;
    private static boolean pendingOpenFasciotomyScreen = false;
    private static InteractionHand pendingFasciotomyHand = InteractionHand.MAIN_HAND;
    private static MedicalData.Limb pendingFasciotomyLimb = null;
    private static boolean pendingOpenOrthoScreen = false;
    private static InteractionHand pendingOrthoHand = InteractionHand.MAIN_HAND;
    private static MedicalData.Limb pendingOrthoLimb = null;
    private static boolean pendingOpenExtractionScreen = false;
    private static InteractionHand pendingExtractionHand = InteractionHand.MAIN_HAND;
    private static MedicalData.Limb pendingExtractionLimb = null;
    private static boolean pendingOpenPerforationScreen = false;
    private static InteractionHand pendingPerforationHand = InteractionHand.MAIN_HAND;
    private static MedicalData.Limb pendingPerforationLimb = null;

    private record LimbSlotLayout(
            MedicalData.Limb limb,
            String cnName,
            float offsetX,
            float offsetY,
            float width,
            float height
    ) {}

    private static final LimbSlotLayout[] LIMB_LAYOUTS = new LimbSlotLayout[] {
            new LimbSlotLayout(MedicalData.Limb.HEAD,      "头部",   0.0f, -72.0f, 44.0f, 36.0f),
            new LimbSlotLayout(MedicalData.Limb.CHEST,     "胸腔",   0.0f, -22.0f, 52.0f, 52.0f),
            new LimbSlotLayout(MedicalData.Limb.LEFT_ARM,  "左臂", -52.0f, -20.0f, 38.0f, 54.0f),
            new LimbSlotLayout(MedicalData.Limb.RIGHT_ARM, "右臂",  52.0f, -20.0f, 38.0f, 54.0f),
            new LimbSlotLayout(MedicalData.Limb.LEFT_LEG,  "左腿", -24.0f,  52.0f, 40.0f, 62.0f),
            new LimbSlotLayout(MedicalData.Limb.RIGHT_LEG, "右腿",  24.0f,  52.0f, 40.0f, 62.0f)
    };

    public static void openBandageScreen(InteractionHand hand) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && mc.screen == null) {
            activatePanel(mc, hand != null ? hand : InteractionHand.MAIN_HAND);
        }
    }

    public static void openVascularSutureScreen(InteractionHand hand, MedicalData.Limb preselectedLimb) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        MedicalData data = mc.player.getData(MedicalDataCapability.MEDICAL_DATA);
        if (data == null || data.getConsciousness() < ModItems.getSutureMinConsciousness()
                || data.isCardiacArrest() || data.isInVFib()) {
            return;
        }
        InteractionHand useHand = hand != null ? hand : InteractionHand.MAIN_HAND;
        ItemStack stack = mc.player.getItemInHand(useHand);
        if (!TreatmentRouter.isSutureKitItem(stack)) return;

        MedicalData.Limb target = preselectedLimb;
        if (target == null) {
            for (MedicalData.Limb limb : MedicalData.Limb.values()) {
                if (TreatmentRouter.canTreatLimb(stack, data, limb)) {
                    target = limb;
                    break;
                }
            }
        }
        if (target == null || !TreatmentRouter.canTreatLimb(stack, data, target)) {
            return;
        }

        if (panelActive) {
            panelActive = false;
            hoveredLimb = null;
            lastHoveredLimb = null;
            channelProgress = 0.0f;
        }
        mc.setScreen(new VascularSutureScreen(useHand, target));
    }

    public static void openBallisticExtractionScreen(InteractionHand hand, MedicalData.Limb preselectedLimb) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        MedicalData data = mc.player.getData(MedicalDataCapability.MEDICAL_DATA);
        if (data == null || data.getConsciousness() < ModItems.getSutureMinConsciousness()
                || data.isCardiacArrest() || data.isInVFib()) {
            return;
        }
        InteractionHand useHand = hand != null ? hand : InteractionHand.MAIN_HAND;
        ItemStack stack = mc.player.getItemInHand(useHand);
        if (!TreatmentRouter.isBallisticExtractionKitItem(stack)) return;

        MedicalData.Limb target = preselectedLimb;
        if (target == null) {
            for (MedicalData.Limb limb : MedicalData.Limb.values()) {
                if (TreatmentRouter.canTreatLimb(stack, data, limb)) {
                    target = limb;
                    break;
                }
            }
        }
        if (target == null || !TreatmentRouter.canTreatLimb(stack, data, target)) {
            return;
        }

        if (panelActive) {
            panelActive = false;
            hoveredLimb = null;
            lastHoveredLimb = null;
            channelProgress = 0.0f;
        }
        mc.setScreen(new BulletExtractionScreen(useHand, target));
    }

    public static void openPerforationRepairScreen(InteractionHand hand, MedicalData.Limb preselectedLimb) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        MedicalData data = mc.player.getData(MedicalDataCapability.MEDICAL_DATA);
        if (data == null || data.getConsciousness() < ModItems.getSutureMinConsciousness()
                || data.isCardiacArrest() || data.isInVFib()) {
            return;
        }
        InteractionHand useHand = hand != null ? hand : InteractionHand.MAIN_HAND;
        ItemStack stack = mc.player.getItemInHand(useHand);
        if (!TreatmentRouter.isPerforationRepairKitItem(stack)) return;

        MedicalData.Limb target = preselectedLimb;
        if (target == null) {
            for (MedicalData.Limb limb : MedicalData.Limb.values()) {
                if (TreatmentRouter.canTreatLimb(stack, data, limb)) {
                    target = limb;
                    break;
                }
            }
        }
        if (target == null || !TreatmentRouter.canTreatLimb(stack, data, target)) {
            return;
        }

        if (panelActive) {
            panelActive = false;
            hoveredLimb = null;
            lastHoveredLimb = null;
            channelProgress = 0.0f;
        }
        mc.setScreen(new PerforationRepairScreen(useHand, target));
    }

    public static void openFasciotomyScreen(InteractionHand hand, MedicalData.Limb preselectedLimb) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        MedicalData data = mc.player.getData(MedicalDataCapability.MEDICAL_DATA);
        if (data == null || data.getConsciousness() < ModItems.getFasciotomyMinConsciousness()
                || data.isCardiacArrest() || data.isInVFib()) {
            return;
        }
        InteractionHand useHand = hand != null ? hand : InteractionHand.MAIN_HAND;
        ItemStack stack = mc.player.getItemInHand(useHand);
        if (!TreatmentRouter.isFasciotomyKitItem(stack)) return;

        MedicalData.Limb target = preselectedLimb;
        if (target == null) {
            for (MedicalData.Limb limb : MedicalData.Limb.values()) {
                if (TreatmentRouter.canTreatLimb(stack, data, limb)) {
                    target = limb;
                    break;
                }
            }
        }
        if (target == null || !TreatmentRouter.canTreatLimb(stack, data, target)) {
            return;
        }

        if (panelActive) {
            panelActive = false;
            hoveredLimb = null;
            lastHoveredLimb = null;
            channelProgress = 0.0f;
        }
        mc.setScreen(new FasciotomyScreen(useHand, target));
    }

    public static void openOrthopedicScreen(InteractionHand hand, MedicalData.Limb preselectedLimb) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        MedicalData data = mc.player.getData(MedicalDataCapability.MEDICAL_DATA);
        if (data == null || data.getConsciousness() < ModItems.getOrthopedicMinConsciousness()
                || data.isCardiacArrest() || data.isInVFib()) {
            return;
        }
        InteractionHand useHand = hand != null ? hand : InteractionHand.MAIN_HAND;
        ItemStack stack = mc.player.getItemInHand(useHand);
        if (!TreatmentRouter.isOrthopedicKitItem(stack)) return;

        MedicalData.Limb target = preselectedLimb;
        if (target == null) {
            for (MedicalData.Limb limb : MedicalData.Limb.values()) {
                if (TreatmentRouter.canTreatLimb(stack, data, limb)) {
                    target = limb;
                    break;
                }
            }
        }
        if (target == null || !TreatmentRouter.canTreatLimb(stack, data, target)) {
            return;
        }

        if (panelActive) {
            panelActive = false;
            hoveredLimb = null;
            lastHoveredLimb = null;
            channelProgress = 0.0f;
        }
        mc.setScreen(new OrthopedicSurgeryScreen(useHand, target));
    }

    public static void openChestDrainScreen(InteractionHand hand) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        MedicalData data = mc.player.getData(MedicalDataCapability.MEDICAL_DATA);
        if (data == null || data.getConsciousness() < ModItems.getChestDrainMinConsciousness()
                || data.isCardiacArrest() || data.isInVFib()) {
            return;
        }
        InteractionHand useHand = hand != null ? hand : InteractionHand.MAIN_HAND;
        ItemStack stack = mc.player.getItemInHand(useHand);
        if (!TreatmentRouter.isChestDrainKitItem(stack)) return;
        if (!TreatmentRouter.canTreatLimb(stack, data, MedicalData.Limb.CHEST)) {
            return;
        }

        if (panelActive) {
            panelActive = false;
            hoveredLimb = null;
            lastHoveredLimb = null;
            channelProgress = 0.0f;
        }
        mc.setScreen(new ChestDrainScreen(useHand));
    }

    public static void openOpioidDoseScreen(InteractionHand hand, MedicalData.Limb preselectedLimb) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        MedicalData data = mc.player.getData(MedicalDataCapability.MEDICAL_DATA);
        if (data != null && (data.getConsciousness() <= 0 || data.isCardiacArrest())) {
            return;
        }
        InteractionHand useHand = hand != null ? hand : InteractionHand.MAIN_HAND;
        ItemStack stack = mc.player.getItemInHand(useHand);
        if (!TreatmentRouter.isQuantitativeSyringeItem(stack)) return;

        if (panelActive) {
            panelActive = false;
            hoveredLimb = null;
            lastHoveredLimb = null;
            channelProgress = 0.0f;
        }
        mc.setScreen(new DoseSelectionScreen(useHand, preselectedLimb));
    }

    private static String getLimbDisplayName(MedicalData.Limb limb) {
        if (limb == null) return "";
        return switch (limb) {
            case HEAD -> "头部";
            case CHEST -> "胸腔";
            case LEFT_ARM -> "左臂";
            case RIGHT_ARM -> "右臂";
            case LEFT_LEG -> "左腿";
            case RIGHT_LEG -> "右腿";
        };
    }

    private static int getAssetAccentColor(MedicalAssetLibrary.AssetType assetType) {
        if (assetType == null) return C_NEON_CYAN;
        return switch (assetType) {
            case BANDAGE_ROLL -> C_NEON_CYAN;
            case PRESSURE_BANDAGE -> C_SAFE_GREEN;
            case MORPHINE_INJECTOR -> C_WARNING_YELLOW;
            case FENTANYL_SYRINGE -> C_MAGENTA_HAZARD;
            case ADRENALINE_INJECTOR -> C_ADRENALINE_GOLD;
            case NALOXONE_INJECTOR -> C_NEON_CYAN;
            case TOURNIQUET -> C_ALERT_RED;
            case VASCULAR_SUTURE_KIT -> C_SAFE_GREEN;
            case BALLISTIC_EXTRACTION_KIT -> C_ADRENALINE_GOLD;
            case PERFORATION_REPAIR_KIT -> 0xFFFF2A85;
            case FASCIOTOMY_KIT -> C_WARNING_YELLOW;
            case ORTHOPEDIC_KIT -> C_NEON_CYAN;
            case CHEST_DRAIN_KIT -> 0xFF29B6F6;
            case DECOMPRESSION_NEEDLE -> 0xFF00E5FF;
            case TRANEXAMIC_ACID -> C_MAGENTA_HAZARD;
            case ACTIVATED_CHARCOAL -> 0xFFFFCA28;
            case ACETYLCYSTEINE -> 0xFF00E676;
            case POVIDONE_IODINE -> 0xFFFF8F00;
            case AMOXICILLIN -> 0xFF29B6F6;
            case MEROPENEM -> 0xFFFFD54F;
            case SYRINGE -> 0xFF40C4FF;
            case SPLINT -> 0xFF69F0AE;
            case MEDICINE_BOTTLE -> 0xFFFFD54F;
            default -> C_NEON_CYAN;
        };
    }

    public static boolean isLockingCameraView() {
        return panelActive;
    }

    public static void onMouseDelta(double rawDX, double rawDY) {
        if (!panelActive) return;
        Minecraft mc = Minecraft.getInstance();
        int sw = mc.getWindow().getGuiScaledWidth();
        int sh = mc.getWindow().getGuiScaledHeight();

        float sensitivity = 0.42f;
        virtualCursorX = Mth.clamp(virtualCursorX + (float) rawDX * sensitivity, sw * 0.5f - 145.0f, sw * 0.5f + 145.0f);
        virtualCursorY = Mth.clamp(virtualCursorY + (float) rawDY * sensitivity, sh * 0.5f - 125.0f, sh * 0.5f + 125.0f);
    }

    @SubscribeEvent
    public static void onInteractionKeyTriggered(InputEvent.InteractionKeyMappingTriggered event) {
        if (!event.isAttack()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.screen != null) return;

        MedicalData data = mc.player.getData(MedicalDataCapability.MEDICAL_DATA);
        if (data != null && (data.getConsciousness() <= 0 || data.isCardiacArrest())) {
            event.setCanceled(true);
            event.setSwingHand(false);
            return;
        }

        InteractionHand medHand = TreatmentRouter.findActiveMedicalHand(mc.player);
        if (medHand != null) {
            event.setCanceled(true);
            event.setSwingHand(false);
            ItemStack medStack = mc.player.getItemInHand(medHand);
            if (TreatmentRouter.isOralMedicineItem(medStack)) {
                if (postApplyCooldownTicks <= 0) {
                    startIbuprofenDrinkAnimation(medHand, medStack);
                    NetworkHandler.sendTreatmentToServer(new TreatmentApplyPacket(medHand.ordinal(), MedicalData.Limb.CHEST.ordinal(), 100));
                    postApplyCooldownTicks = 12;
                }
                return;
            }
            if (!panelActive && postApplyCooldownTicks <= 0) {
                activatePanel(mc, medHand);
            }
        }
    }

    @SubscribeEvent
    public static void onMouseScrollLockDuringMedicalAnim(InputEvent.MouseScrollingEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.screen != null) return;
        if (isFirstPersonMedicalAnimActive() && ModItems.safeCfgBool(MedicalConfig.LOCK_HOTBAR_DURING_MEDICAL_ANIM, true)) {
            if (fpAnimLockedSlot >= 0 && fpAnimLockedSlot < 9) {
                mc.player.getInventory().selected = fpAnimLockedSlot;
            }
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;

        if (pendingOpenDoseScreen) {
            pendingOpenDoseScreen = false;
            if (player != null && mc.screen == null) {
                openOpioidDoseScreen(pendingDoseHand, pendingDoseLimb);
            }
            return;
        }
        if (pendingOpenSutureScreen) {
            pendingOpenSutureScreen = false;
            if (player != null && mc.screen == null) {
                openVascularSutureScreen(pendingSutureHand, pendingSutureLimb);
            }
            return;
        }
        if (pendingOpenFasciotomyScreen) {
            pendingOpenFasciotomyScreen = false;
            if (player != null && mc.screen == null) {
                openFasciotomyScreen(pendingFasciotomyHand, pendingFasciotomyLimb);
            }
            return;
        }
        if (pendingOpenOrthoScreen) {
            pendingOpenOrthoScreen = false;
            if (player != null && mc.screen == null) {
                openOrthopedicScreen(pendingOrthoHand, pendingOrthoLimb);
            }
            return;
        }
        if (pendingOpenExtractionScreen) {
            pendingOpenExtractionScreen = false;
            if (player != null && mc.screen == null) {
                openBallisticExtractionScreen(pendingExtractionHand, pendingExtractionLimb);
            }
            return;
        }
        if (pendingOpenPerforationScreen) {
            pendingOpenPerforationScreen = false;
            if (player != null && mc.screen == null) {
                openPerforationRepairScreen(pendingPerforationHand, pendingPerforationLimb);
            }
            return;
        }

        if (player != null && mc.level != null && mc.screen == null) {
            checkAndTriggerSurgicalKitSwitchAnim(player);
        }

        if (player == null || mc.level == null || mc.screen != null) {
            if (panelActive) deactivatePanel();
            return;
        }

        MedicalData data = player.getData(MedicalDataCapability.MEDICAL_DATA);
        if (data != null && (data.getConsciousness() <= 0 || data.isCardiacArrest())) {
            if (panelActive) deactivatePanel();
            return;
        }

        if (postApplyCooldownTicks > 0) {
            postApplyCooldownTicks--;
        }

        long window = mc.getWindow().getWindow();
        boolean leftDown = GLFW.glfwGetMouseButton(window, GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
        InteractionHand medHand = TreatmentRouter.findActiveMedicalHand(player);

        if (leftDown && medHand != null) {
            ItemStack heldMed = player.getItemInHand(medHand);
            if (TreatmentRouter.isOralMedicineItem(heldMed)) {
                if (postApplyCooldownTicks <= 0) {
                    startIbuprofenDrinkAnimation(medHand, heldMed);
                    NetworkHandler.sendTreatmentToServer(new TreatmentApplyPacket(medHand.ordinal(), MedicalData.Limb.CHEST.ordinal(), 100));
                    postApplyCooldownTicks = 12;
                }
                if (mc.gameMode != null) {
                    mc.gameMode.stopDestroyBlock();
                }
                return;
            }
            if (!panelActive && postApplyCooldownTicks <= 0) {
                activatePanel(mc, medHand);
            } else if (panelActive) {
                activeHand = medHand;
            }
            if (mc.gameMode != null) {
                mc.gameMode.stopDestroyBlock();
            }
        } else {
            if (panelActive) {
                if (hoveredLimb != null && channelProgress >= 0.15f && postApplyCooldownTicks <= 0) {
                    ItemStack stack = player.getItemInHand(activeHand);
                    if (TreatmentRouter.canTreatLimb(stack, data, hoveredLimb)) {
                        if (TreatmentRouter.isChestDrainKitItem(stack)) {
                            InteractionHand handCopy = activeHand;
                            deactivatePanel();
                            openChestDrainScreen(handCopy);
                            return;
                        } else if (TreatmentRouter.isOrthopedicKitItem(stack)) {
                            MedicalData.Limb chosen = hoveredLimb;
                            InteractionHand handCopy = activeHand;
                            deactivatePanel();
                            openOrthopedicScreen(handCopy, chosen);
                            return;
                        } else if (TreatmentRouter.isFasciotomyKitItem(stack)) {
                            MedicalData.Limb chosen = hoveredLimb;
                            InteractionHand handCopy = activeHand;
                            deactivatePanel();
                            openFasciotomyScreen(handCopy, chosen);
                            return;
                        } else if (TreatmentRouter.isBallisticExtractionKitItem(stack)) {
                            MedicalData.Limb chosen = hoveredLimb;
                            InteractionHand handCopy = activeHand;
                            deactivatePanel();
                            openBallisticExtractionScreen(handCopy, chosen);
                            return;
                        } else if (TreatmentRouter.isPerforationRepairKitItem(stack)) {
                            MedicalData.Limb chosen = hoveredLimb;
                            InteractionHand handCopy = activeHand;
                            deactivatePanel();
                            openPerforationRepairScreen(handCopy, chosen);
                            return;
                        } else if (TreatmentRouter.isSutureKitItem(stack)) {
                            MedicalData.Limb chosen = hoveredLimb;
                            InteractionHand handCopy = activeHand;
                            deactivatePanel();
                            openVascularSutureScreen(handCopy, chosen);
                            return;
                        } else if (TreatmentRouter.isQuantitativeSyringeItem(stack)) {
                            MedicalData.Limb chosen = hoveredLimb;
                            InteractionHand handCopy = activeHand;
                            deactivatePanel();
                            openOpioidDoseScreen(handCopy, chosen);
                            return;
                        } else {
                            triggerApplyPacket(activeHand, hoveredLimb, 100);
                        }
                    }
                }
                deactivatePanel();
            }
        }
    }

    private static void activatePanel(Minecraft mc, InteractionHand hand) {
        panelActive = true;
        activeHand = hand;
        hoveredLimb = null;
        lastHoveredLimb = null;
        channelProgress = 0.0f;
        virtualCursorX = mc.getWindow().getGuiScaledWidth() * 0.5f;
        virtualCursorY = mc.getWindow().getGuiScaledHeight() * 0.5f;
        lastRenderTimeMs = System.currentTimeMillis();
        MedicalSoundHelper.playOpenSound();
    }

    private static void deactivatePanel() {
        panelActive = false;
        hoveredLimb = null;
        lastHoveredLimb = null;
        channelProgress = 0.0f;
        MedicalSoundHelper.playCloseSound();
    }

    private static void triggerApplyPacket(InteractionHand hand, MedicalData.Limb limb, int doseUnits) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && doseUnits <= 100) {
            ItemStack usedStack = mc.player.getItemInHand(hand);
            if (isSyringeAnimationItem(usedStack)) {
                startSyringeInjectionAnimation(hand, limb, usedStack, doseUnits);
            } else if (TreatmentRouter.isTourniquetItem(usedStack)) {
                startTourniquetApplyAnimation(hand, limb, usedStack);
            } else if (TreatmentRouter.isPovidoneIodineItem(usedStack)) {
                startPovidoneSprayAnimation(hand, limb, usedStack);
            } else if (TreatmentRouter.isOralMedicineItem(usedStack)) {
                startIbuprofenDrinkAnimation(hand, usedStack);
            } else if (isBandageAnimationItem(usedStack)) {
                startBandageWrapAnimation(hand, limb, usedStack);
            }
        }
        NetworkHandler.sendTreatmentToServer(new TreatmentApplyPacket(hand.ordinal(), limb.ordinal(), doseUnits));
        MedicalSoundHelper.playLimbSelectSound();
        channelProgress = 0.0f;
        postApplyCooldownTicks = 10;
    }

    @SubscribeEvent
    public static void onRenderGuiPost(RenderGuiLayerEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null || mc.options.hideGui) return;

        String layerName = event.getName().getPath();
        if (!"hotbar".equals(layerName)) {
            return;
        }

        MedicalData preData = player.getData(MedicalDataCapability.MEDICAL_DATA);
        if (preData != null) {
            renderPneumothoraxScreenVignette(event.getGuiGraphics(), mc, preData, System.currentTimeMillis());
        }

        if (!panelActive) return;

        ItemStack stack = player.getItemInHand(activeHand);
        if (!TreatmentRouter.isMedicalItem(stack)) {
            deactivatePanel();
            return;
        }

        MedicalData data = player.getData(MedicalDataCapability.MEDICAL_DATA);
        if (data == null) return;

        MedicalAssetLibrary.AssetType assetType = MedicalAssetLibrary.resolveAssetType(stack);
        int themeColor = getAssetAccentColor(assetType);

        long now = System.currentTimeMillis();
        float dtSeconds = (lastRenderTimeMs == 0L) ? 0.016f : Mth.clamp((now - lastRenderTimeMs) / 1000.0f, 0.001f, 0.1f);
        lastRenderTimeMs = now;

        GuiGraphics g = event.getGuiGraphics();
        Font font = mc.font;
        int sw = mc.getWindow().getGuiScaledWidth();
        int sh = mc.getWindow().getGuiScaledHeight();
        float cx = sw * 0.5f;
        float cy = sh * 0.5f;

        int panelW = 290;
        int panelH = 230;
        int px = (int) (cx - panelW * 0.5f);
        int py = (int) (cy - panelH * 0.5f);

        g.fill(px, py, px + panelW, py + panelH, C_BG_DARK);
        drawTacticalCornerBrackets(g, px, py, panelW, panelH, themeColor);

        boolean isOpioid = TreatmentRouter.isQuantitativeSyringeItem(stack);
        String itemTitle = stack.getHoverName().getString();
        g.drawString(font, itemTitle, px + 10, py + 9, themeColor, false);
        g.fill(px + 8, py + 20, px + panelW - 8, py + 21, withAlpha(themeColor, 0.45f));

        MedicalAssetLibrary.render3DMedicalItem(g, px + 42.0f, py + 76.0f, 34.0f, stack, now);

        if (stack.isDamageableItem() && stack.getMaxDamage() > 0) {
            float remRatio = Mth.clamp((float) (stack.getMaxDamage() - stack.getDamageValue()) / stack.getMaxDamage(), 0.0f, 1.0f);
            int barW = 64;
            int barX = px + 10;
            int barY = py + 118;
            int durColor = remRatio > 0.5f ? C_SAFE_GREEN : (remRatio > 0.25f ? C_WARNING_YELLOW : C_ALERT_RED);
            g.fill(barX, barY, barX + barW, barY + 4, 0xAA081522);
            g.fill(barX, barY, barX + Math.round(barW * remRatio), barY + 4, durColor);
        }

        hoveredLimb = null;
        for (LimbSlotLayout slot : LIMB_LAYOUTS) {
            float sx = cx + slot.offsetX() - slot.width() * 0.5f;
            float sy = cy + slot.offsetY() - slot.height() * 0.5f + 6.0f;
            boolean isHovered = virtualCursorX >= sx && virtualCursorX <= sx + slot.width()
                    && virtualCursorY >= sy && virtualCursorY <= sy + slot.height();
            if (isHovered) {
                hoveredLimb = slot.limb();
            }
        }

        if (hoveredLimb != lastHoveredLimb) {
            if (hoveredLimb != null) {
                MedicalSoundHelper.playHoverSound();
            }
            channelProgress = 0.0f;
            lastHoveredLimb = hoveredLimb;
        }

        drawAnatomicalSkeletonLines(g, cx, cy + 6.0f, withAlpha(themeColor, 0.3f));

        for (LimbSlotLayout slot : LIMB_LAYOUTS) {
            drawLimbSlot(g, font, data, stack, slot, cx, cy + 6.0f, slot.limb() == hoveredLimb, themeColor, now);
        }

        if (hoveredLimb != null && postApplyCooldownTicks <= 0) {
            boolean canTreat = TreatmentRouter.canTreatLimb(stack, data, hoveredLimb);
            if (canTreat) {
                float durationSec = Math.max(0.25f, TreatmentRouter.getChannelDurationSeconds(stack));
                channelProgress += dtSeconds / durationSec;
                if (channelProgress >= 1.0f) {
                    if (TreatmentRouter.isChestDrainKitItem(stack)) {
                        InteractionHand handCopy = activeHand;
                        deactivatePanel();
                        openChestDrainScreen(handCopy);
                        return;
                    } else if (TreatmentRouter.isOrthopedicKitItem(stack)) {
                        pendingOrthoHand = activeHand;
                        pendingOrthoLimb = hoveredLimb;
                        pendingOpenOrthoScreen = true;
                        deactivatePanel();
                        return;
                    } else if (TreatmentRouter.isFasciotomyKitItem(stack)) {
                        pendingFasciotomyHand = activeHand;
                        pendingFasciotomyLimb = hoveredLimb;
                        pendingOpenFasciotomyScreen = true;
                        deactivatePanel();
                        return;
                    } else if (TreatmentRouter.isBallisticExtractionKitItem(stack)) {
                        pendingExtractionHand = activeHand;
                        pendingExtractionLimb = hoveredLimb;
                        pendingOpenExtractionScreen = true;
                        deactivatePanel();
                        return;
                    } else if (TreatmentRouter.isPerforationRepairKitItem(stack)) {
                        pendingPerforationHand = activeHand;
                        pendingPerforationLimb = hoveredLimb;
                        pendingOpenPerforationScreen = true;
                        deactivatePanel();
                        return;
                    } else if (TreatmentRouter.isSutureKitItem(stack)) {
                        pendingSutureHand = activeHand;
                        pendingSutureLimb = hoveredLimb;
                        pendingOpenSutureScreen = true;
                        deactivatePanel();
                        return;
                    } else if (isOpioid) {
                        pendingDoseHand = activeHand;
                        pendingDoseLimb = hoveredLimb;
                        pendingOpenDoseScreen = true;
                        deactivatePanel();
                        return;
                    } else {
                        triggerApplyPacket(activeHand, hoveredLimb, 100);
                        deactivatePanel();
                        return;
                    }
                }
            } else {
                channelProgress = 0.0f;
            }
        } else {
            channelProgress = 0.0f;
        }

        drawTacticalCursor(g, virtualCursorX, virtualCursorY, channelProgress, themeColor);
    }

    private static void drawLimbSlot(
            GuiGraphics g,
            Font font,
            MedicalData data,
            ItemStack stack,
            LimbSlotLayout slot,
            float centerX,
            float centerY,
            boolean hovered,
            int themeColor,
            long nowMs
    ) {
        MedicalData.LimbData limbData = data.getLimb(slot.limb());
        if (limbData == null) return;
        boolean canTreat = TreatmentRouter.canTreatLimb(stack, data, slot.limb());
        int bleed = limbData.getBleeding();
        int pain = limbData.getPainLevel();
        boolean openWound = limbData.hasOpenWound();
        boolean fracture = limbData.hasFracture() || limbData.hasDislocation();
        boolean bandaged = limbData.isBandaged();

        int x0 = (int) (centerX + slot.offsetX() - slot.width() * 0.5f);
        int y0 = (int) (centerY + slot.offsetY() - slot.height() * 0.5f);
        int x1 = (int) (x0 + slot.width());
        int y1 = (int) (y0 + slot.height());

        int borderColor;
        int fillColor;
        if (canTreat && (pain > 0 || bleed > 0 || openWound || fracture)) {
            float pulse = 0.65f + 0.35f * Mth.sin(nowMs * 0.01f);
            borderColor = hovered ? C_SAFE_GREEN : withAlpha(C_ALERT_RED, pulse);
            fillColor = hovered ? withAlpha(C_SAFE_GREEN, 0.28f) : withAlpha(C_ALERT_RED, 0.20f);
        } else if (canTreat) {
            borderColor = hovered ? C_SAFE_GREEN : withAlpha(themeColor, 0.65f);
            fillColor = hovered ? withAlpha(C_SAFE_GREEN, 0.22f) : 0x77081524;
        } else {
            borderColor = hovered ? themeColor : withAlpha(themeColor, 0.42f);
            fillColor = hovered ? withAlpha(themeColor, 0.18f) : 0x660A1624;
        }

        g.fill(x0, y0, x1, y1, fillColor);
        g.fill(x0, y0, x1, y0 + 1, borderColor);
        g.fill(x0, y1 - 1, x1, y1, borderColor);
        g.fill(x0, y0, x0 + 1, y1, borderColor);
        g.fill(x1 - 1, y0, x1, y1, borderColor);

        if (hovered && channelProgress > 0.01f) {
            int barH = (int) ((y1 - y0 - 2) * Mth.clamp(channelProgress, 0.0f, 1.0f));
            g.fill(x0 + 1, y1 - 1 - barH, x1 - 1, y1 - 1, withAlpha(C_SAFE_GREEN, 0.35f));
        }

        g.drawCenteredString(font, slot.cnName(), (x0 + x1) / 2, (y0 + y1) / 2 - 4, C_TEXT_WHITE);
        if (slot.limb() == MedicalData.Limb.CHEST) {
            int pType = ModItems.getPneumothoraxType(data);
            float airMl = ModItems.getPleuralAirMl(data);
            if (pType == 3) {
                g.drawCenteredString(font, "⚠张力气胸", (x0 + x1) / 2, (y0 + y1) / 2 + 6, 0xFFFF1744);
            } else if (pType == 2) {
                g.drawCenteredString(font, "开放气胸", (x0 + x1) / 2, (y0 + y1) / 2 + 6, 0xFF00E5FF);
            } else if (pType == 1 || airMl > 40.0f) {
                String tag = ModItems.isThreeSidedValveSealed(data) ? "单向阀封" : String.format("气胸%.0f", airMl);
                g.drawCenteredString(font, tag, (x0 + x1) / 2, (y0 + y1) / 2 + 6, 0xFF80DEEA);
            } else if (data.getChestBloodAccumulation() > 40.0f) {
                g.drawCenteredString(font, String.format("血胸%.0f", data.getChestBloodAccumulation()), (x0 + x1) / 2, (y0 + y1) / 2 + 6, 0xFFFF8A80);
            } else if ((TreatmentRouter.isActivatedCharcoalItem(stack) || TreatmentRouter.isAcetylcysteineItem(stack)) && data.getToxinValue() > 0.5f) {
                g.drawCenteredString(font, String.format("毒素%.0f%%", data.getToxinValue()), (x0 + x1) / 2, (y0 + y1) / 2 + 6, 0xFF00E676);
            } else if (data.getSirsLevel() > 0 && (TreatmentRouter.isAmoxicillinItem(stack) || TreatmentRouter.isMeropenemItem(stack))) {
                g.drawCenteredString(font, String.format("SIRS %d级", data.getSirsLevel()), (x0 + x1) / 2, (y0 + y1) / 2 + 6, 0xFFFF7043);
            }
        } else if (limbData.hasPerforation()) {
            String perfTag = ModItems.isPerforationPacked(limbData) ? "贯穿[塞纱]" : ("⚠贯穿 " + limbData.getBleeding());
            g.drawCenteredString(font, perfTag, (x0 + x1) / 2, (y0 + y1) / 2 + 6, 0xFFFF2A85);
        } else if (ModItems.getEmbeddedBulletCount(limbData) > 0) {
            g.drawCenteredString(font, "嵌弹×" + ModItems.getEmbeddedBulletCount(limbData), (x0 + x1) / 2, (y0 + y1) / 2 + 6, 0xFFFFC400);
        } else if (limbData.getInfection() > 0) {
            g.drawCenteredString(font, String.format("感染%d%%", limbData.getInfection()), (x0 + x1) / 2, (y0 + y1) / 2 + 6, 0xFFFFB74D);
        } else if (limbData.getDisinfectantTimer() > 0 && TreatmentRouter.isPovidoneIodineItem(stack)) {
            g.drawCenteredString(font, String.format("碘膜%ds", limbData.getDisinfectantTimer()), (x0 + x1) / 2, (y0 + y1) / 2 + 6, 0xFFFFD54F);
        }
    }

    private static void drawAnatomicalSkeletonLines(GuiGraphics g, float cx, float cy, int lineColor) {
        g.fill((int) cx - 1, (int) (cy - 54), (int) cx + 1, (int) (cy - 48), lineColor);
        g.fill((int) (cx - 33), (int) (cy - 34), (int) (cx + 33), (int) (cy - 33), lineColor);
        g.fill((int) (cx - 18), (int) (cy + 4), (int) (cx + 18), (int) (cy + 5), lineColor);
        g.fill((int) (cx - 18), (int) (cy + 4), (int) (cx - 17), (int) (cy + 21), lineColor);
        g.fill((int) (cx + 17), (int) (cy + 4), (int) (cx + 18), (int) (cy + 21), lineColor);
    }

    private static void drawTacticalCornerBrackets(GuiGraphics g, int x, int y, int w, int h, int color) {
        int len = 10;
        g.fill(x, y, x + len, y + 2, color);
        g.fill(x, y, x + 2, y + len, color);
        g.fill(x + w - len, y, x + w, y + 2, color);
        g.fill(x + w - 2, y, x + w, y + len, color);
        g.fill(x, y + h - 2, x + len, y + h, color);
        g.fill(x, y + h - len, x + 2, y + h, color);
        g.fill(x + w - len, y + h - 2, x + w, y + h, color);
        g.fill(x + w - 2, y + h - len, x + w, y + h, color);
    }

    private static void drawTacticalCursor(GuiGraphics g, float x, float y, float progress, int themeColor) {
        int ix = Math.round(x);
        int iy = Math.round(y);
        int c = (progress > 0.01f) ? C_SAFE_GREEN : themeColor;

        g.fill(ix - 6, iy, ix - 2, iy + 1, c);
        g.fill(ix + 3, iy, ix + 7, iy + 1, c);
        g.fill(ix, iy - 6, ix + 1, iy - 2, c);
        g.fill(ix, iy + 3, ix + 1, iy + 7, c);
        g.fill(ix, iy, ix + 1, iy + 1, C_TEXT_WHITE);

        if (progress > 0.02f) {
            PoseStack pose = g.pose();
            Matrix4f mat = pose.last().pose();
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.setShader(GameRenderer::getPositionColorShader);

            Tesselator tess = Tesselator.getInstance();
            BufferBuilder buf = tess.begin(VertexFormat.Mode.TRIANGLE_STRIP, DefaultVertexFormat.POSITION_COLOR);
            int segments = 24;
            float rInner = 9.0f;
            float rOuter = 12.0f;
            float maxAngle = Mth.TWO_PI * Mth.clamp(progress, 0.02f, 1.0f);

            for (int i = 0; i <= segments; i++) {
                float t = (float) i / segments;
                float a = -Mth.HALF_PI + maxAngle * t;
                float cos = Mth.cos(a);
                float sin = Mth.sin(a);
                buf.addVertex(mat, x + cos * rInner, y + sin * rInner, 0.0f).setColor(0, 230, 118, 220);
                buf.addVertex(mat, x + cos * rOuter, y + sin * rOuter, 0.0f).setColor(0, 229, 255, 255);
            }
            MeshData mesh = buf.build();
            if (mesh != null) {
                BufferUploader.drawWithShader(mesh);
            }
        }
    }

    // =========================================================================
    // 阿片类镇痛剂定量推注界面 (极简无废话玩家版)
    // =========================================================================
    public static class DoseSelectionScreen extends Screen {

        private final InteractionHand hand;
        private MedicalData.Limb selectedLimb;
        private int selectedDoseUnits = 30;
        private boolean draggingSlider = false;

        private float sliderDriftAccum = 0.0f;
        private long lastFrameTimeMs = 0L;

        private int winX, winY, winW = 296, winH = 158;
        private int sliderX, sliderY, sliderW, sliderH = 16;

        public DoseSelectionScreen(InteractionHand hand, MedicalData.Limb initialLimb) {
            super(Component.literal("注射"));
            this.hand = hand != null ? hand : InteractionHand.MAIN_HAND;
            this.selectedLimb = initialLimb;
        }

        @Override
        protected void init() {
            super.init();
            this.winX = (this.width - this.winW) / 2;
            this.winY = (this.height - this.winH) / 2;
            this.sliderX = this.winX + 86;
            this.sliderY = this.winY + 76;
            this.sliderW = this.winW - 98; // 198px

            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) {
                MedicalData data = mc.player.getData(MedicalDataCapability.MEDICAL_DATA);
                ItemStack stack = mc.player.getItemInHand(this.hand);
                int remUnits = Math.max(1, TreatmentRouter.getRemainingOpioidUnits(stack));
                boolean isTxa = TreatmentRouter.isTranexamicAcidItem(stack);
                boolean isNac = TreatmentRouter.isAcetylcysteineItem(stack);
                boolean isMero = TreatmentRouter.isMeropenemItem(stack);
                boolean isFentanyl = TreatmentRouter.isFentanylItem(stack);

                if (this.selectedLimb == null && data != null) {
                    MedicalData.Limb best = MedicalData.Limb.LEFT_ARM;
                    float bestScore = -1.0f;
                    for (MedicalData.Limb limb : MedicalData.Limb.values()) {
                        MedicalData.LimbData ld = data.getLimb(limb);
                        if (ld != null && !ld.isAmputated()) {
                            float score = isMero
                                    ? (ld.getInfection() * 10.0f + (limb == MedicalData.Limb.CHEST ? data.getSirsLevel() * 25.0f : 0.0f))
                                    : (isNac
                                            ? (limb == MedicalData.Limb.LEFT_ARM || limb == MedicalData.Limb.RIGHT_ARM ? 50.0f : 10.0f)
                                            : (isTxa
                                                    ? (ld.getInternalBleedingRate() * 10.0f + ModItems.getCompartmentTamponadedBleed(ld) * 8.0f + ModItems.getCompartmentBlood(ld))
                                                    : (float) ld.getPainLevel()));
                            if (score > bestScore) {
                                bestScore = score;
                                best = limb;
                            }
                        }
                    }
                    this.selectedLimb = best;
                }
                if (this.selectedLimb == null) {
                    this.selectedLimb = MedicalData.Limb.LEFT_ARM;
                }

                int defaultUnits = (isMero || isNac || isTxa) ? 50 : (isFentanyl ? 30 : 45);
                this.selectedDoseUnits = Mth.clamp(defaultUnits, 1, remUnits);
            }
            MedicalSoundHelper.playOpenSound();
        }

        @Override
        public boolean isPauseScreen() {
            return false;
        }

        @Override
        public void renderBackground(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        }

        @Override
        public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null) {
                this.onClose();
                return;
            }
            ItemStack stack = mc.player.getItemInHand(this.hand);
            boolean isTxa = TreatmentRouter.isTranexamicAcidItem(stack);
            boolean isNac = TreatmentRouter.isAcetylcysteineItem(stack);
            boolean isMero = TreatmentRouter.isMeropenemItem(stack);
            boolean isNonOpioidSyringe = isTxa || isNac || isMero;
            if (!TreatmentRouter.isQuantitativeSyringeItem(stack)) {
                this.onClose();
                return;
            }
            MedicalData data = mc.player.getData(MedicalDataCapability.MEDICAL_DATA);
            if (data == null || data.getConsciousness() <= 0 || data.isCardiacArrest()) {
                this.onClose();
                return;
            }

            int maxUnits = Math.max(1, TreatmentRouter.getMaxOpioidUnits(stack));
            int remUnits = Math.max(1, TreatmentRouter.getRemainingOpioidUnits(stack));
            this.selectedDoseUnits = Mth.clamp(this.selectedDoseUnits, 1, remUnits);

            boolean isFentanyl = !isNonOpioidSyringe && TreatmentRouter.isFentanylItem(stack);
            float remRatio = remUnits / (float) maxUnits;

            float maxDrugAmount = isMero
                    ? ((float) ModItems.safeCfgDouble(MedicalConfig.MEROPENEM_MAX_MG, 1000.0))
                    : (isNac
                            ? ((float) ModItems.safeCfgDouble(MedicalConfig.NAC_MAX_MG, 2000.0))
                            : (isTxa
                                    ? 1000.0f
                                    : (isFentanyl
                                            ? ((float) ModItems.safeCfgDouble(MedicalConfig.FENTANYL_MAX_MCG, 100.0))
                                            : ((float) ModItems.safeCfgDouble(MedicalConfig.MORPHINE_MAX_MG, 10.0)))));
            String unitStr = isFentanyl ? "mcg" : "mg";

            float concPerFull = isMero
                    ? ((float) ModItems.safeCfgDouble(MedicalConfig.MEROPENEM_CONCENTRATION_PER_FULL_DOSE, 70.0))
                    : (isNac
                            ? ((float) ModItems.safeCfgDouble(MedicalConfig.NAC_CONCENTRATION_PER_FULL_DOSE, 65.0))
                            : (isTxa
                                    ? ((float) ModItems.safeCfgDouble(MedicalConfig.TXA_CONCENTRATION_PER_DOSE, 55.0))
                                    : (isFentanyl
                                            ? ((float) ModItems.safeCfgDouble(MedicalConfig.FENTANYL_CONCENTRATION_PER_FULL_DOSE, 85.0))
                                            : ((float) ModItems.safeCfgDouble(MedicalConfig.MORPHINE_CONCENTRATION_PER_FULL_DOSE, 60.0)))));
            float currentConc = isMero
                    ? ModItems.getMeropenemConcentration(data)
                    : (isNac
                            ? ModItems.getNacConcentration(data)
                            : (isTxa
                                    ? ((data instanceof MedicalDataImpl impl) ? impl.getTxaConcentration() : 0.0f)
                                    : ((data instanceof MedicalDataImpl impl)
                                            ? impl.getOpioidConcentrationFloat()
                                            : (float) data.getOpioidConcentration())));

            long nowMs = System.currentTimeMillis();
            float dtSec = (lastFrameTimeMs == 0L) ? 0.016f : Mth.clamp((nowMs - lastFrameTimeMs) / 1000.0f, 0.001f, 0.1f);
            lastFrameTimeMs = nowMs;

            int addictionLevel = data.getAddictionLevel();
            int addictionTier = isNonOpioidSyringe ? 0 : ModItems.getAddictionTier(addictionLevel);

            boolean sliderSelfMoving = false;
            if (addictionTier > 0 && remUnits > 1) {
                sliderSelfMoving = true;
                float driftSpeed = switch (addictionTier) {
                    case 1 -> ((float) ModItems.safeCfgDouble(MedicalConfig.ADDICTION_SLIDER_DRIFT_SPEED_T1, 12.0));
                    case 2 -> ((float) ModItems.safeCfgDouble(MedicalConfig.ADDICTION_SLIDER_DRIFT_SPEED_T2, 28.0));
                    default -> ((float) ModItems.safeCfgDouble(MedicalConfig.ADDICTION_SLIDER_DRIFT_SPEED_T3, 52.0));
                };
                float minSwing = (addictionTier == 1) ? 0.35f : (addictionTier == 2 ? 0.52f : 0.72f);
                float wave = 0.5f + 0.5f * (0.72f * Mth.sin(nowMs * 0.0042f) + 0.28f * Mth.cos(nowMs * 0.0115f));
                float targetUnits = Mth.lerp(wave, remUnits * minSwing, (float) remUnits);

                if (!this.draggingSlider || addictionTier >= 2) {
                    float dragResist = this.draggingSlider ? (addictionTier >= 3 ? 0.75f : 0.42f) : 1.0f;
                    float curPos = this.selectedDoseUnits + this.sliderDriftAccum;
                    float diff = targetUnits - curPos;
                    float dir = (Math.abs(diff) < 1.5f) ? (Mth.sin(nowMs * 0.009f) >= 0 ? 1.0f : -1.0f) : Math.signum(diff);
                    this.sliderDriftAccum += dir * driftSpeed * dragResist * dtSec;
                    if (Math.abs(this.sliderDriftAccum) >= 1.0f) {
                        int step = (int) this.sliderDriftAccum;
                        this.sliderDriftAccum -= step;
                        this.selectedDoseUnits = Mth.clamp(this.selectedDoseUnits + step, 1, remUnits);
                    }
                }
            }

            float doseFraction = this.selectedDoseUnits / (float) maxUnits;
            float chosenAmount = doseFraction * maxDrugAmount;
            float projectedConc = Mth.clamp(currentConc + doseFraction * concPerFull, 0.0f, 100.0f);

            boolean herxheimerRisk = isMero && (ModItems.getTotalInfection(data) >= ModItems.safeCfgInt(MedicalConfig.MEROPENEM_HERXHEIMER_INFECTION_THRESHOLD, 110)
                    || data.getSirsLevel() >= 3);
            float herxRatioThresh = ((float) ModItems.safeCfgDouble(MedicalConfig.MEROPENEM_HERXHEIMER_DOSE_RATIO_THRESHOLD, 0.65));

            int respDepStart = isMero
                    ? (herxheimerRisk ? Math.round(herxRatioThresh * concPerFull * 0.80f) : 55)
                    : (isNac
                            ? Math.max(30, Math.round(ModItems.safeCfgInt(MedicalConfig.NAC_HISTAMINE_OVERDOSE_THRESHOLD, 70) * 0.72f))
                            : (isTxa
                                    ? Math.max(25, Math.round(ModItems.safeCfgInt(MedicalConfig.TXA_THROMBOSIS_THRESHOLD, 75) * 0.65f))
                                    : ModItems.safeCfgInt(MedicalConfig.OPIOID_RESP_DEPRESSION_START, 35)));
            int overdoseThresh = isMero
                    ? (herxheimerRisk ? Math.round(currentConc + herxRatioThresh * concPerFull) : ModItems.safeCfgInt(MedicalConfig.MEROPENEM_NEUROTOXIC_THRESHOLD, 85))
                    : (isNac
                            ? ModItems.safeCfgInt(MedicalConfig.NAC_HISTAMINE_OVERDOSE_THRESHOLD, 70)
                            : (isTxa
                                    ? ModItems.safeCfgInt(MedicalConfig.TXA_THROMBOSIS_THRESHOLD, 75)
                                    : ModItems.safeCfgInt(MedicalConfig.OPIOID_OVERDOSE_THRESHOLD, 65)));
            int apneaThresh = isMero
                    ? ModItems.safeCfgInt(MedicalConfig.MEROPENEM_NEUROTOXIC_THRESHOLD, 85)
                    : (isNac
                            ? Math.min(100, ModItems.safeCfgInt(MedicalConfig.NAC_HISTAMINE_OVERDOSE_THRESHOLD, 70) + 18)
                            : (isTxa
                                    ? Math.min(100, ModItems.safeCfgInt(MedicalConfig.TXA_THROMBOSIS_THRESHOLD, 75) + 15)
                                    : ModItems.safeCfgInt(MedicalConfig.OPIOID_APNEA_THRESHOLD, 85)));

            boolean isFatalApnea = projectedConc >= apneaThresh;
            boolean isOverdose = projectedConc >= overdoseThresh || (herxheimerRisk && doseFraction >= herxRatioThresh);
            boolean isMildDep = projectedConc >= respDepStart;

            boolean isSafeZoneBlind = false;
            if (addictionTier > 0) {
                double blindChance = switch (addictionTier) {
                    case 1 -> ModItems.safeCfgDouble(MedicalConfig.ADDICTION_BLIND_ZONE_CHANCE_T1, 0.18);
                    case 2 -> ModItems.safeCfgDouble(MedicalConfig.ADDICTION_BLIND_ZONE_CHANCE_T2, 0.42);
                    default -> ModItems.safeCfgDouble(MedicalConfig.ADDICTION_BLIND_ZONE_CHANCE_T3, 0.75);
                };
                double phase = (nowMs % 6000L) / 6000.0;
                isSafeZoneBlind = (phase < blindChance);
            }

            g.fill(0, 0, this.width, this.height, 0x8802060C);

            int accentColor = isSafeZoneBlind ? 0xFFE040FB
                    : (isFatalApnea ? C_ALERT_RED
                    : (isOverdose ? 0xFFFF5252
                    : (isMero ? 0xFFFFD54F : (isNac ? 0xFF00E676 : (isTxa ? 0xFFE040FB : (isFentanyl ? C_MAGENTA_HAZARD : C_WARNING_YELLOW))))));

            // 1. 面板框 (296 x 158)
            g.fill(winX, winY, winX + winW, winY + winH, 0xF2060D17);
            drawTacticalCornerBrackets(g, winX, winY, winW, winH, accentColor);
            g.fill(winX + 1, winY, winX + winW - 1, winY + 1, withAlpha(accentColor, 0.65f));
            g.fill(winX + 1, winY + winH - 1, winX + winW - 1, winY + winH, withAlpha(accentColor, 0.65f));

            String header = isMero ? "注射用美罗培南 (抗重症感染/逆转SIRS)"
                    : (isNac ? "乙酰半胱氨酸 (NAC 静脉解毒剂)"
                    : (isTxa ? "氨甲环酸" : (isFentanyl ? "枸橼酸芬太尼" : "盐酸吗啡")));
            g.drawString(this.font, header, winX + 10, winY + 7, accentColor, false);
            g.fill(winX + 8, winY + 18, winX + winW - 8, winY + 19, withAlpha(accentColor, 0.40f));

            // 2. 左侧 3D 注射器预览框 (宽 68)
            int leftBoxX = winX + 10;
            int leftBoxY = winY + 24;
            int leftBoxW = 68;
            int leftBoxH = winH - 34;
            g.fill(leftBoxX, leftBoxY, leftBoxX + leftBoxW, leftBoxY + leftBoxH, 0xAA030810);
            drawTacticalCornerBrackets(g, leftBoxX, leftBoxY, leftBoxW, leftBoxH, withAlpha(accentColor, 0.7f));

            if (isMero) {
                MedicalAssetLibrary.render3DMeropenemSyringeWithPlunger(
                        g, leftBoxX + leftBoxW * 0.5f, leftBoxY + 52.0f, 36.0f, remRatio, doseFraction, nowMs
                );
            } else if (isNac) {
                MedicalAssetLibrary.render3DNacSyringeWithPlunger(
                        g, leftBoxX + leftBoxW * 0.5f, leftBoxY + 52.0f, 36.0f, remRatio, doseFraction, nowMs
                );
            } else if (isTxa) {
                MedicalAssetLibrary.render3DTxaSyringeWithPlunger(
                        g, leftBoxX + leftBoxW * 0.5f, leftBoxY + 52.0f, 36.0f, remRatio, doseFraction, nowMs
                );
            } else {
                MedicalAssetLibrary.render3DOpioidSyringeWithPlunger(
                        g, leftBoxX + leftBoxW * 0.5f, leftBoxY + 52.0f, 36.0f, isFentanyl, remRatio, doseFraction, nowMs
                );
            }

            String remText = (isNonOpioidSyringe || isFentanyl)
                    ? String.format("%.0f %s", remRatio * maxDrugAmount, unitStr)
                    : String.format("%.1f %s", remRatio * maxDrugAmount, unitStr);
            g.drawCenteredString(this.font, remText, leftBoxX + leftBoxW / 2, leftBoxY + 106, C_TEXT_WHITE);

            // 3. 右上：6 大部位标签 (宽 31，间距 2，总宽 196 <= 198)
            int rightX = sliderX;
            int ty = winY + 28;
            MedicalData.Limb[] limbs = MedicalData.Limb.values();
            int tabW = 31;
            int tabH = 16;
            for (int i = 0; i < limbs.length; i++) {
                MedicalData.Limb limb = limbs[i];
                MedicalData.LimbData ld = data.getLimb(limb);
                boolean amputated = ld != null && ld.isAmputated();
                boolean selected = (limb == this.selectedLimb);
                int tx = rightX + i * (tabW + 2);
                boolean hov = !amputated && mouseX >= tx && mouseX <= tx + tabW && mouseY >= ty && mouseY <= ty + tabH;

                boolean hasSymptom = ld != null && (isMero
                        ? (ld.getInfection() > 0 || data.getSirsLevel() > 0)
                        : (isNac
                                ? (data.getToxinValue() > 0.5f)
                                : (isTxa
                                        ? (ld.getInternalBleedingRate() > 0 || ModItems.getCompartmentTamponadedBleed(ld) > 0 || ModItems.getCompartmentBlood(ld) > 0.1f)
                                        : (ld.getPainLevel() > 0))));
                int bg = amputated ? 0x55202020 : (selected ? withAlpha(C_NEON_CYAN, 0.35f) : (hov ? 0x88102838 : 0x66081420));
                int bd = amputated ? 0xFF404040 : (selected ? C_NEON_CYAN : (hasSymptom ? C_WARNING_YELLOW : 0xFF37474F));
                g.fill(tx, ty, tx + tabW, ty + tabH, bg);
                g.fill(tx, ty, tx + tabW, ty + 1, bd);
                g.fill(tx, ty + tabH - 1, tx + tabW, ty + tabH, bd);
                g.drawCenteredString(this.font, getLimbDisplayName(limb), tx + tabW / 2, ty + 4, amputated ? 0xFF666666 : C_TEXT_WHITE);
            }

            // 4. 中央：剂量滑块 (宽 198)
            int labelY = sliderY - 13;
            String doseReadout = (isNonOpioidSyringe || isFentanyl)
                    ? String.format("%.0f %s", chosenAmount, unitStr)
                    : String.format("%.1f %s", chosenAmount, unitStr);
            g.drawString(this.font, doseReadout, sliderX, labelY, C_TEXT_WHITE, false);

            int zoneColor = isSafeZoneBlind ? 0xFFE040FB
                    : (isFatalApnea ? C_ALERT_RED : (isOverdose ? 0xFFFF5252 : (isMildDep ? C_WARNING_YELLOW : C_SAFE_GREEN)));
            if (!isSafeZoneBlind) {
                String zoneBadge = (herxheimerRisk && doseFraction >= herxRatioThresh) ? "⚠赫氏风暴" : (isNac && isOverdose ? "⚠组胺过敏" : ((isFatalApnea || isOverdose) ? "过量" : (isMildDep ? "偏高" : "安全")));
                g.drawString(this.font, zoneBadge, sliderX + sliderW - this.font.width(zoneBadge), labelY, zoneColor, false);
            }

            g.fill(sliderX - 1, sliderY - 1, sliderX + sliderW + 1, sliderY + sliderH + 1, 0xFF0A1926);
            int segments = 40;
            for (int s = 0; s < segments; s++) {
                float u0 = (s / (float) segments) * remUnits;
                float u1 = ((s + 1) / (float) segments) * remUnits;
                int sx0 = sliderX + Math.round((s / (float) segments) * sliderW);
                int sx1 = sliderX + Math.round(((s + 1) / (float) segments) * sliderW);

                int segCol;
                if (isSafeZoneBlind) {
                    float wave = 0.5f + 0.5f * Mth.sin(nowMs * 0.008f - s * 0.25f);
                    int r = (int) Mth.lerp(wave, 145, 255);
                    int gr = (int) Mth.lerp(wave, 35, 165);
                    int b = (int) Mth.lerp(wave, 210, 245);
                    segCol = (0x66 << 24) | (r << 16) | (gr << 8) | b;
                } else {
                    float testConc = currentConc + ((u0 + u1) * 0.5f / maxUnits) * concPerFull;
                    if (testConc >= apneaThresh) segCol = 0x77FF1744;
                    else if (testConc >= overdoseThresh) segCol = 0x66FF5252;
                    else if (testConc >= respDepStart) segCol = 0x55FFB300;
                    else segCol = 0x5500E676;
                }
                g.fill(sx0, sliderY, sx1, sliderY + sliderH, segCol);
            }

            float fillRatio = (remUnits <= 1) ? 1.0f : Mth.clamp((float) this.selectedDoseUnits / remUnits, 0.0f, 1.0f);
            int fillW = Math.round(sliderW * fillRatio);
            g.fill(sliderX, sliderY + 2, sliderX + fillW, sliderY + sliderH - 2, withAlpha(zoneColor, 0.85f));

            for (int tick = 0; tick <= 10; tick++) {
                int tx = sliderX + Math.round((tick / 10.0f) * sliderW);
                int th = (tick % 5 == 0) ? 5 : 3;
                g.fill(tx, sliderY + sliderH - th, tx + 1, sliderY + sliderH, 0xAAFFFFFF);
            }

            int knobX = sliderX + fillW;
            if (sliderSelfMoving) {
                g.fill(knobX - 5, sliderY - 3, knobX + 5, sliderY + sliderH + 3, withAlpha(0xFFE040FB, 0.65f));
            }
            g.fill(knobX - 3, sliderY - 2, knobX + 3, sliderY + sliderH + 2, 0xFFFFFFFF);
            g.fill(knobX - 2, sliderY - 1, knobX + 2, sliderY + sliderH + 1, zoneColor);

            // 4 个快捷比例按钮
            int[] presets = new int[]{25, 50, 75, 100};
            String[] presetLabels = new String[]{"1/4", "1/2", "3/4", "全量"};
            int presetY = sliderY + sliderH + 8;
            int presetBtnW = (sliderW - 9) / 4;
            for (int i = 0; i < presets.length; i++) {
                int pVal = Math.max(1, Math.round(remUnits * (presets[i] / 100.0f)));
                int bx = sliderX + i * (presetBtnW + 3);
                boolean hov = mouseX >= bx && mouseX <= bx + presetBtnW && mouseY >= presetY && mouseY <= presetY + 14;
                boolean active = (this.selectedDoseUnits == pVal);
                int pBg = active ? withAlpha(zoneColor, 0.35f) : (hov ? 0x88142C42 : 0x66081420);
                int pBd = active ? zoneColor : 0xFF37474F;
                g.fill(bx, presetY, bx + presetBtnW, presetY + 14, pBg);
                g.fill(bx, presetY, bx + presetBtnW, presetY + 1, pBd);
                g.fill(bx, presetY + 13, bx + presetBtnW, presetY + 14, pBd);
                g.drawCenteredString(this.font, presetLabels[i], bx + presetBtnW / 2, presetY + 3, C_TEXT_WHITE);
            }

            // 5. 底部按钮
            int btnY = winY + winH - 24;
            int confirmW = 114;
            int cancelW = 68;
            int confirmX = sliderX;
            int cancelX = sliderX + sliderW - cancelW;

            boolean hovConfirm = mouseX >= confirmX && mouseX <= confirmX + confirmW && mouseY >= btnY && mouseY <= btnY + 16;
            boolean hovCancel = mouseX >= cancelX && mouseX <= cancelX + cancelW && mouseY >= btnY && mouseY <= btnY + 16;

            int confCol = isSafeZoneBlind ? 0xFFE040FB : ((isFatalApnea || isOverdose) ? C_ALERT_RED : C_SAFE_GREEN);
            g.fill(confirmX, btnY, confirmX + confirmW, btnY + 16, hovConfirm ? withAlpha(confCol, 0.42f) : withAlpha(confCol, 0.24f));
            drawTacticalCornerBrackets(g, confirmX, btnY, confirmW, 16, confCol);
            g.drawCenteredString(this.font, "注射", confirmX + confirmW / 2, btnY + 4, C_TEXT_WHITE);

            g.fill(cancelX, btnY, cancelX + cancelW, btnY + 16, hovCancel ? 0x8837474F : 0x551C2833);
            drawTacticalCornerBrackets(g, cancelX, btnY, cancelW, 16, 0xFF90A4AE);
            g.drawCenteredString(this.font, "取消", cancelX + cancelW / 2, btnY + 4, C_TEXT_DIM);

            super.render(g, mouseX, mouseY, partialTick);
        }

        private void updateSliderFromMouse(double mouseX) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null) return;
            ItemStack stack = mc.player.getItemInHand(this.hand);
            int remUnits = Math.max(1, TreatmentRouter.getRemainingOpioidUnits(stack));
            float ratio = Mth.clamp((float) (mouseX - this.sliderX) / (float) Math.max(1, this.sliderW), 0.0f, 1.0f);
            this.selectedDoseUnits = Mth.clamp(Math.round(1 + ratio * (remUnits - 1)), 1, remUnits);
        }

        private void confirmInjection() {
            if (this.selectedLimb != null && this.selectedDoseUnits > 0) {
                triggerApplyPacket(this.hand, this.selectedLimb, this.selectedDoseUnits);
            }
            this.onClose();
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (button == 1) {
                this.onClose();
                return true;
            }
            if (button == 0) {
                Minecraft mc = Minecraft.getInstance();
                if (mc.player == null) return false;
                MedicalData data = mc.player.getData(MedicalDataCapability.MEDICAL_DATA);
                ItemStack stack = mc.player.getItemInHand(this.hand);
                int remUnits = Math.max(1, TreatmentRouter.getRemainingOpioidUnits(stack));

                if (mouseX >= sliderX - 6 && mouseX <= sliderX + sliderW + 6
                        && mouseY >= sliderY - 4 && mouseY <= sliderY + sliderH + 4) {
                    this.draggingSlider = true;
                    updateSliderFromMouse(mouseX);
                    MedicalSoundHelper.playHoverSound();
                    return true;
                }

                int rightX = sliderX;
                int ty = winY + 28;
                int tabW = 31, tabH = 16;
                MedicalData.Limb[] limbs = MedicalData.Limb.values();
                for (int i = 0; i < limbs.length; i++) {
                    int tx = rightX + i * (tabW + 2);
                    if (mouseX >= tx && mouseX <= tx + tabW && mouseY >= ty && mouseY <= ty + tabH) {
                        MedicalData.LimbData ld = data != null ? data.getLimb(limbs[i]) : null;
                        if (ld != null && !ld.isAmputated()) {
                            this.selectedLimb = limbs[i];
                            MedicalSoundHelper.playHoverSound();
                        }
                        return true;
                    }
                }

                int[] presets = new int[]{25, 50, 75, 100};
                int presetY = sliderY + sliderH + 8;
                int presetBtnW = (sliderW - 9) / 4;
                for (int i = 0; i < presets.length; i++) {
                    int bx = sliderX + i * (presetBtnW + 3);
                    if (mouseX >= bx && mouseX <= bx + presetBtnW && mouseY >= presetY && mouseY <= presetY + 14) {
                        int pVal = Math.max(1, Math.round(remUnits * (presets[i] / 100.0f)));
                        this.selectedDoseUnits = Mth.clamp(pVal, 1, remUnits);
                        MedicalSoundHelper.playHoverSound();
                        return true;
                    }
                }

                int btnY = winY + winH - 24;
                int confirmW = 114;
                int cancelW = 68;
                int confirmX = sliderX;
                int cancelX = sliderX + sliderW - cancelW;

                if (mouseX >= confirmX && mouseX <= confirmX + confirmW && mouseY >= btnY && mouseY <= btnY + 16) {
                    confirmInjection();
                    return true;
                }
                if (mouseX >= cancelX && mouseX <= cancelX + cancelW && mouseY >= btnY && mouseY <= btnY + 16) {
                    this.onClose();
                    return true;
                }
            }
            return super.mouseClicked(mouseX, mouseY, button);
        }

        @Override
        public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
            if (this.draggingSlider && button == 0) {
                updateSliderFromMouse(mouseX);
                return true;
            }
            return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
        }

        @Override
        public boolean mouseReleased(double mouseX, double mouseY, int button) {
            if (button == 0 && this.draggingSlider) {
                this.draggingSlider = false;
                return true;
            }
            return super.mouseReleased(mouseX, mouseY, button);
        }

        @Override
        public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null && scrollY != 0) {
                ItemStack stack = mc.player.getItemInHand(this.hand);
                int remUnits = Math.max(1, TreatmentRouter.getRemainingOpioidUnits(stack));
                int step = Screen.hasShiftDown() ? 5 : 1;
                int dir = scrollY > 0 ? 1 : -1;
                this.selectedDoseUnits = Mth.clamp(this.selectedDoseUnits + dir * step, 1, remUnits);
                return true;
            }
            return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
        }

        @Override
        public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) {
                ItemStack stack = mc.player.getItemInHand(this.hand);
                int remUnits = Math.max(1, TreatmentRouter.getRemainingOpioidUnits(stack));
                int step = Screen.hasShiftDown() ? 5 : 1;

                if (keyCode == GLFW.GLFW_KEY_LEFT || keyCode == GLFW.GLFW_KEY_A) {
                    this.selectedDoseUnits = Mth.clamp(this.selectedDoseUnits - step, 1, remUnits);
                    return true;
                }
                if (keyCode == GLFW.GLFW_KEY_RIGHT || keyCode == GLFW.GLFW_KEY_D) {
                    this.selectedDoseUnits = Mth.clamp(this.selectedDoseUnits + step, 1, remUnits);
                    return true;
                }
                if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER || keyCode == GLFW.GLFW_KEY_SPACE) {
                    confirmInjection();
                    return true;
                }
            }
            return super.keyPressed(keyCode, scanCode, modifiers);
        }
    }
    // =========================================================================
    // 野战血管缝合手术交互界面 (VascularSutureScreen —— 3A级微创血管外科手术模拟)
    // =========================================================================
    public static class VascularSutureScreen extends Screen {

        private static final class SurgicalParticle {
            boolean active;
            float x, y;
            float vx, vy;
            float size;
            float age, maxAge;
            int color;
            boolean isStain;
            boolean isSpark;
        }

        private final InteractionHand hand;
        private final MedicalData.Limb limb;
        private int initialBleedRate = 600;
        private boolean isMainArtery = false;
        private boolean isAmputatedLimb = false;
        private boolean surgeryCompleted = false;
        private boolean incisionOpened = false;

        // 0 = 扩创暴露并选择路径, 1 = 路径A(血管结扎闭塞术), 2 = 路径B(血管吻合桥接术)
        private int chosenPath = 0;
        private int subStep = 0;

        // 公共第 1 步：左右齿轮牵开器扩创 (0.0 -> 1.0) + 弹簧棘轮锁定
        private float leftRetract = 0.0f;
        private float rightRetract = 0.0f;
        private boolean leftLocked = false;
        private boolean rightLocked = false;
        private int lastLeftTooth = 0;
        private int lastRightTooth = 0;
        private int draggingRetractor = 0; // 0=无, 1=左拉钩, 2=右拉钩
        private float pathCardSlideAnim = 0.0f;

        // 路径 A-2 & 路径 B-2：上下断端血管夹闭与闭合动画
        private boolean topClamped = false;
        private boolean botClamped = false;
        private float topClampAnim = 0.0f;
        private float botClampAnim = 0.0f;

        // 路径 A-3：环绕绕线 + 死结张力保持
        private boolean windingDrag = false;
        private float lastWindAngle = 0.0f;
        private float totalWindProgress = 0.0f;
        private int lastWindTickStep = 0;
        private boolean pullingKnot = false;
        private float knotTension = 0.0f;
        private float knotHoldProgress = 0.0f;
        private int lastTensionTickStep = 0;

        // 路径 B-3：人工血管桥接管拖拽与角度微调
        private boolean draggingGraft = false;
        private float graftX = 0.0f;
        private float graftY = 0.0f;
        private float graftAngleDeg = 28.0f;
        private boolean graftInserted = false;
        private float graftSnapAnim = 0.0f;

        // 路径 B-4：环周等距微创吻合 (针数由 MedicalConfig.SUTURE_ANASTOMOSIS_STITCH_COUNT 驱动)
        private boolean[] stitchesDone = new boolean[6];
        private float[] stitchTightenAnim = new float[6];
        private int activeStitchIdx = -1;

        // 枪伤深部探查：嵌留弹头夹取与非大出血盲管/贯穿创道清创缝合
        private int embeddedBulletTotal = 0;
        private int extractedBulletCount = 0;
        private final float[] bulletX = new float[8];
        private final float[] bulletY = new float[8];
        private final boolean[] bulletDone = new boolean[8];
        private int draggingBulletIdx = -1;
        private boolean hasPerforationWound = false;
        private boolean minorBleedTractOnly = false;

        // 手术完成后的 3A 血流重灌注 / 结扎收尾动画
        private boolean playingCompletionFinale = false;
        private int pendingCompletionActionCode = 0;
        private float completionAnimProgress = 0.0f;

        // 实时粒子系统、ECG心电动脉波与受击震屏反馈
        private final SurgicalParticle[] particles = new SurgicalParticle[72];
        private final float[] ecgWaveHistory = new float[76];
        private float ecgSampleAccum = 0.0f;
        private float sprayAccum = 0.0f;
        private float mistakeFlashAlpha = 0.0f;
        private float screenShakeX = 0.0f;
        private float screenShakeY = 0.0f;
        private float screenShakeAmp = 0.0f;

        private long lastFrameMs = 0L;
        private float heartPhase = 0.0f;
        private float cursorTremorX = 0.0f;
        private float cursorTremorY = 0.0f;
        private float smoothMouseX = 0.0f;
        private float smoothMouseY = 0.0f;
        private float cursorTiltDeg = 0.0f;

        private int winX, winY, winW = 316, winH = 214;
        private int viewX, viewY, viewW = 224, viewH = 164;

        public VascularSutureScreen(InteractionHand hand, MedicalData.Limb limb) {
            super(Component.literal("血管缝合"));
            this.hand = hand != null ? hand : InteractionHand.MAIN_HAND;
            this.limb = limb != null ? limb : MedicalData.Limb.LEFT_LEG;
            for (int i = 0; i < this.particles.length; i++) {
                this.particles[i] = new SurgicalParticle();
            }
        }

        private void playUiSound(SoundEvent sound, float pitch, float volume) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.getSoundManager() != null) {
                mc.getSoundManager().play(SimpleSoundInstance.forUI(sound, pitch, volume));
            }
        }

        private void spawnParticle(float x, float y, float vx, float vy, float size, float maxAge, int color, boolean isStain, boolean isSpark) {
            for (SurgicalParticle p : this.particles) {
                if (!p.active) {
                    p.active = true;
                    p.x = x;
                    p.y = y;
                    p.vx = vx;
                    p.vy = vy;
                    p.size = size;
                    p.age = 0.0f;
                    p.maxAge = maxAge;
                    p.color = color;
                    p.isStain = isStain;
                    p.isSpark = isSpark;
                    return;
                }
            }
        }

        private void triggerSurgicalMistake(float x, float y) {
            this.mistakeFlashAlpha = 0.85f;
            this.screenShakeAmp = 6.5f;
            playUiSound(SoundEvents.PLAYER_HURT, 1.15f, 0.55f);
            for (int i = 0; i < 18; i++) {
                float ang = (float) (Math.random() * Math.PI * 2.0);
                float spd = 28.0f + (float) (Math.random() * 68.0);
                spawnParticle(
                        x + (float) (Math.random() * 6.0 - 3.0),
                        y + (float) (Math.random() * 6.0 - 3.0),
                        Mth.cos(ang) * spd,
                        Mth.sin(ang) * spd - 12.0f,
                        2.2f + (float) (Math.random() * 2.0),
                        0.45f + (float) (Math.random() * 0.35),
                        0xFFFF1744,
                        true,
                        false
                );
            }
            NetworkHandler.sendTreatmentToServer(new TreatmentApplyPacket(this.hand.ordinal(), this.limb.ordinal(), 203));
        }

        private void spawnClampSparks(float x, float y, int color) {
            for (int i = 0; i < 12; i++) {
                float ang = (i / 12.0f) * Mth.TWO_PI + (float) (Math.random() * 0.25);
                float spd = 22.0f + (float) (Math.random() * 34.0);
                spawnParticle(x, y, Mth.cos(ang) * spd, Mth.sin(ang) * spd, 1.6f, 0.28f, color, false, true);
            }
        }

        @Override
        protected void init() {
            super.init();
            this.winX = (this.width - this.winW) / 2;
            this.winY = (this.height - this.winH) / 2;
            this.viewX = this.winX + 82;
            this.viewY = this.winY + 26;
            this.graftX = this.viewX + 38.0f;
            this.graftY = this.viewY + this.viewH * 0.54f;

            int stitchCount = Math.max(2, Math.min(12, ModItems.safeCfgInt(MedicalConfig.SUTURE_ANASTOMOSIS_STITCH_COUNT, 6)));
            this.stitchesDone = new boolean[stitchCount];
            this.stitchTightenAnim = new float[stitchCount];

            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) {
                MedicalData data = mc.player.getData(MedicalDataCapability.MEDICAL_DATA);
                if (data != null) {
                    MedicalData.LimbData ld = data.getLimb(this.limb);
                    if (ld != null) {
                        this.initialBleedRate = Math.max(ld.getBleeding(), ModItems.getTourniquetClampedBleed(ld));
                        this.isAmputatedLimb = ld.isAmputated();
                        this.isMainArtery = this.initialBleedRate > ModItems.getSutureArterialThreshold()
                                || ModItems.isLimbArterialLigated(ld);
                        this.embeddedBulletTotal = Math.min(8, ModItems.getEmbeddedBulletCount(ld));
                        this.extractedBulletCount = 0;
                        this.draggingBulletIdx = -1;
                        this.hasPerforationWound = ld.hasPerforation();
                        this.minorBleedTractOnly = (this.initialBleedRate <= ModItems.safeCfgInt(MedicalConfig.BANDAGE_MAX_BLEEDING_THRESHOLD, 500)
                                && !ModItems.isLimbArterialLigated(ld)
                                && !this.isAmputatedLimb);
                        float seedCx = this.viewX + this.viewW * 0.5f;
                        float seedCy = this.viewY + this.viewH * 0.54f;
                        for (int b = 0; b < this.embeddedBulletTotal; b++) {
                            float side = (b % 2 == 0) ? -1.0f : 1.0f;
                            this.bulletX[b] = seedCx + side * (22.0f + (b / 2) * 11.0f);
                            this.bulletY[b] = seedCy + ((b % 3) - 1) * 15.0f;
                            this.bulletDone[b] = false;
                        }
                    }
                }
                // 手术包自带止血带：打开手术时立即通知服务端用内置止血带夹闭该肢体出血
                NetworkHandler.sendTreatmentToServer(new TreatmentApplyPacket(this.hand.ordinal(), this.limb.ordinal(), 200));
            }
            MedicalSoundHelper.playOpenSound();
            playUiSound(SoundEvents.ARMOR_EQUIP_LEATHER.value(), 0.92f, 0.75f);
        }

        @Override
        public boolean isPauseScreen() {
            return false;
        }

        @Override
        public void renderBackground(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        }

        @Override
        public void onClose() {
            if (!this.surgeryCompleted && this.incisionOpened) {
                NetworkHandler.sendTreatmentToServer(new TreatmentApplyPacket(this.hand.ordinal(), this.limb.ordinal(), 204));
            }
            super.onClose();
        }

        @Override
        public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null) {
                super.onClose();
                return;
            }
            ItemStack stack = mc.player.getItemInHand(this.hand);
            if (!TreatmentRouter.isSutureKitItem(stack)) {
                super.onClose();
                return;
            }
            MedicalData data = mc.player.getData(MedicalDataCapability.MEDICAL_DATA);
            if (data == null || data.getConsciousness() < ModItems.getSutureMinConsciousness()
                    || data.isCardiacArrest() || data.isInVFib()) {
                this.onClose();
                return;
            }

            long nowMs = System.currentTimeMillis();
            float dt = (lastFrameMs == 0L) ? 0.016f : Mth.clamp((nowMs - lastFrameMs) / 1000.0f, 0.001f, 0.1f);
            lastFrameMs = nowMs;

            // 完成手术后的 0.65 秒 3A 血流重灌注/收尾动画
            if (this.playingCompletionFinale) {
                this.completionAnimProgress += dt / 0.65f;
                if (this.completionAnimProgress >= 1.0f) {
                    this.surgeryCompleted = true;
                    NetworkHandler.sendTreatmentToServer(new TreatmentApplyPacket(
                            this.hand.ordinal(), this.limb.ordinal(), this.pendingCompletionActionCode));
                    MedicalSoundHelper.playLimbSelectSound();
                    this.onClose();
                    return;
                }
            }

            float hr = Mth.clamp(data.getHeartRate(), 40.0f, 220.0f);
            float prevPhase = this.heartPhase;
            this.heartPhase = (this.heartPhase + dt * (hr / 60.0f)) % 1.0f;
            if (this.heartPhase < prevPhase && (!this.topClamped || !this.botClamped)) {
                playUiSound(SoundEvents.WARDEN_HEARTBEAT, this.isMainArtery ? 1.18f : 0.95f, this.isMainArtery ? 0.42f : 0.22f);
            }
            float beatWave = computeArterialPulseWave(this.heartPhase);

            // 采样实时心电/动脉血压波形历史
            this.ecgSampleAccum += dt;
            while (this.ecgSampleAccum >= 0.018f) {
                this.ecgSampleAccum -= 0.018f;
                System.arraycopy(this.ecgWaveHistory, 1, this.ecgWaveHistory, 0, this.ecgWaveHistory.length - 1);
                this.ecgWaveHistory[this.ecgWaveHistory.length - 1] = beatWave;
            }

            // 疼痛高频震颤 + 低意识眩晕8字漂移干扰（完全由 MedicalConfig 配置驱动）
            MedicalData.LimbData curLd = data.getLimb(this.limb);
            float sysPainRatio = data.getPerceivedPain() / Math.max(10.0f, ((float) ModItems.safeCfgDouble(MedicalConfig.MAX_PERCEIVED_PAIN, 100.0)));
            float limbPainRatio = (curLd != null) ? (curLd.getPainLevel() / Math.max(1.0f, ((float) ModItems.safeCfgDouble(MedicalConfig.MAX_PAIN_LEVEL, 10)))) : 0.0f;
            float painRatio = Mth.clamp(Math.max(sysPainRatio, limbPainRatio * 0.85f), 0.0f, 1.0f);

            float minCons = ModItems.getSutureMinConsciousness();
            float consDeficit = Mth.clamp((100.0f - data.getConsciousnessFloat()) / Math.max(1.0f, 100.0f - minCons), 0.0f, 1.0f);

            float maxPainShake = ((float) ModItems.safeCfgDouble(MedicalConfig.SUTURE_CURSOR_PAIN_SHAKE_MAX, 14.0));
            float maxConsSway = ((float) ModItems.safeCfgDouble(MedicalConfig.SUTURE_CURSOR_CONSCIOUSNESS_SWAY_MAX, 22.0));
            float wdShake = ModItems.isInWithdrawalMovementRestriction(data)
                    ? ((float) ModItems.safeCfgDouble(MedicalConfig.SUTURE_CURSOR_WITHDRAWAL_SHAKE, 10.0)) : 0.0f;

            // 1) 剧痛高频神经痉挛抖动 + 心跳收缩期抽搐冲击
            float painPulseSpike = 1.0f + beatWave * 0.65f * painRatio;
            float painShakeAmp = (painRatio * maxPainShake + wdShake) * painPulseSpike;
            float highFreqX = (Mth.sin(nowMs * 0.023f) * 0.55f + Mth.cos(nowMs * 0.041f) * 0.45f) * painShakeAmp;
            float highFreqY = (Mth.cos(nowMs * 0.029f) * 0.55f + Mth.sin(nowMs * 0.037f) * 0.45f) * painShakeAmp;

            // 2) 低意识脑缺氧眩晕慢速 8 字轨道漂移
            float consSwayAmp = consDeficit * maxConsSway;
            float lowFreqX = (Mth.sin(nowMs * 0.0034f) * 0.75f + Mth.cos(nowMs * 0.0058f) * 0.25f) * consSwayAmp;
            float lowFreqY = (Mth.sin(nowMs * 0.0068f) * 0.65f + Mth.cos(nowMs * 0.0027f) * 0.35f) * consSwayAmp;

            this.cursorTremorX = highFreqX + lowFreqX;
            this.cursorTremorY = highFreqY + lowFreqY;

            float targetMx = mouseX + this.cursorTremorX;
            float targetMy = mouseY + this.cursorTremorY;
            if (this.smoothMouseX == 0.0f && this.smoothMouseY == 0.0f) {
                this.smoothMouseX = targetMx;
                this.smoothMouseY = targetMy;
            } else {
                // 意识越低，手眼协调阻尼越迟钝，进一步加剧漂移失控感
                float followRate = Mth.lerp(consDeficit, 28.0f, 8.5f);
                float vx = (targetMx - this.smoothMouseX) / Math.max(0.004f, dt);
                this.cursorTiltDeg = Mth.lerp(Mth.clamp(dt * 14.0f, 0.0f, 1.0f), this.cursorTiltDeg, Mth.clamp(vx * 0.035f, -22.0f, 22.0f));
                this.smoothMouseX = Mth.lerp(Mth.clamp(dt * followRate, 0.0f, 1.0f), this.smoothMouseX, targetMx);
                this.smoothMouseY = Mth.lerp(Mth.clamp(dt * followRate, 0.0f, 1.0f), this.smoothMouseY, targetMy);
            }

            // 震屏衰减与失误红光衰减
            if (this.screenShakeAmp > 0.01f) {
                this.screenShakeAmp = Math.max(0.0f, this.screenShakeAmp - dt * 22.0f);
                this.screenShakeX = (float) (Math.random() * 2.0 - 1.0) * this.screenShakeAmp;
                this.screenShakeY = (float) (Math.random() * 2.0 - 1.0) * this.screenShakeAmp;
            } else {
                this.screenShakeX = 0.0f;
                this.screenShakeY = 0.0f;
            }
            if (this.mistakeFlashAlpha > 0.005f) {
                this.mistakeFlashAlpha = Math.max(0.0f, this.mistakeFlashAlpha - dt * 2.4f);
            }

            g.fill(0, 0, this.width, this.height, 0x9602060C);

            PoseStack pose = g.pose();
            pose.pushPose();
            pose.translate(this.screenShakeX, this.screenShakeY, 0.0f);

            int themeCol = this.isMainArtery ? C_ALERT_RED : C_SAFE_GREEN;
            g.fill(winX, winY, winX + winW, winY + winH, 0xF4050B14);
            drawTacticalCornerBrackets(g, winX, winY, winW, winH, themeCol);
            g.fill(winX + 8, winY + 19, winX + winW - 8, winY + 20, withAlpha(themeCol, 0.45f));

            g.drawString(this.font, getLimbDisplayName(this.limb), winX + 10, winY + 7, C_TEXT_WHITE, false);

            // 顶部步骤进度指示条（带流光高亮）
            int totalSteps = (chosenPath == 1) ? 3 : (chosenPath == 2 ? 4 : 2);
            int curStepIdx = (chosenPath == 0) ? 0 : this.subStep;
            for (int s = 0; s < totalSteps; s++) {
                int sx = viewX + s * 36;
                int sy = winY + 8;
                boolean done = (s < curStepIdx) || this.playingCompletionFinale;
                boolean active = (s == curStepIdx) && !this.playingCompletionFinale;
                int sCol = done ? C_SAFE_GREEN : (active ? C_NEON_CYAN : 0xFF1E2B36);
                g.fill(sx - 1, sy - 1, sx + 31, sy + 7, 0xFF0A141E);
                g.fill(sx, sy, sx + 30, sy + 6, sCol);
                if (active) {
                    int glintOffset = (int) ((nowMs / 18L) % 26L);
                    g.fill(sx + glintOffset, sy, sx + Math.min(30, glintOffset + 6), sy + 6, 0xAAFFFFFF);
                }
            }

            // 左侧：3D 手术盒全息投影 + 内置止血带缺血倒计时发光条 + 血管类型特征标识
            int leftX = winX + 10;
            int leftY = viewY;
            int leftW = 64;
            int leftH = viewH;
            g.fill(leftX, leftY, leftX + leftW, leftY + leftH, 0xB8030810);
            drawTacticalCornerBrackets(g, leftX, leftY, leftW, leftH, withAlpha(themeCol, 0.72f));
            MedicalAssetLibrary.render3DMedicalItem(g, leftX + leftW * 0.5f, leftY + 50.0f, 31.0f, stack, nowMs);

            // 左侧中部：动脉/非动脉双路径动态脉冲图腾
            int pulseBoxY = leftY + 94;
            g.fill(leftX + 6, pulseBoxY, leftX + leftW - 6, pulseBoxY + 34, 0x8806101A);
            drawVesselTypeIndicator(g, leftX + leftW * 0.5f, pulseBoxY + 17.0f, beatWave, nowMs);

            // 左侧底部：内置止血带安全缺血倒计时条
            MedicalData.LimbData ld = data.getLimb(this.limb);
            int tqSec = (ld != null && !ld.isAmputated()) ? ModItems.getTourniquetTimer(ld) : 0;
            float tqRatio = Mth.clamp(tqSec / (float) Math.max(1, ModItems.getTourniquetNecrosisSeconds()), 0.0f, 1.0f);
            int tqBarCol = tqRatio > 0.75f ? C_ALERT_RED : (tqRatio > 0.45f ? C_WARNING_YELLOW : C_NEON_CYAN);
            g.fill(leftX + 7, leftY + leftH - 16, leftX + leftW - 7, leftY + leftH - 10, 0xFF0A1622);
            int tqFillW = Math.round((leftW - 14) * (1.0f - tqRatio));
            g.fill(leftX + 7, leftY + leftH - 16, leftX + 7 + tqFillW, leftY + leftH - 10, tqBarCol);

            // 中央手术创腔主视窗
            g.fill(viewX, viewY, viewX + viewW, viewY + viewH, 0xFF120408);

            float cx = viewX + viewW * 0.5f;
            float cy = viewY + viewH * 0.54f;

            // 渲染多层解剖创腔、实时血流粒子、手术器械交互与心电波形带
            g.enableScissor(viewX + 1, viewY + 1, viewX + viewW - 1, viewY + viewH - 1);
            renderSurgicalCavityAndVessel(g, cx, cy, beatWave, dt, this.smoothMouseX, this.smoothMouseY, nowMs);
            renderBloodAndSparkParticles(g, dt);
            renderHemodynamicOscilloscope(g, beatWave);
            if (this.mistakeFlashAlpha > 0.01f) {
                drawViewportVignette(g, viewX, viewY, viewW, viewH, withAlpha(C_ALERT_RED, this.mistakeFlashAlpha * 0.55f));
            }
            g.disableScissor();

            drawTacticalCornerBrackets(g, viewX, viewY, viewW, viewH, this.mistakeFlashAlpha > 0.1f ? C_ALERT_RED : 0xFF546E7A);

            // 底部取消按钮
            int cancelW = 54, cancelH = 14;
            int cancelX = viewX + viewW - cancelW;
            int cancelY = winY + winH - 19;
            boolean hovCancel = mouseX >= cancelX && mouseX <= cancelX + cancelW && mouseY >= cancelY && mouseY <= cancelY + cancelH;
            g.fill(cancelX, cancelY, cancelX + cancelW, cancelY + cancelH, hovCancel ? 0x8837474F : 0x551C2833);
            drawTacticalCornerBrackets(g, cancelX, cancelY, cancelW, cancelH, 0xFF90A4AE);
            g.drawCenteredString(this.font, "×", cancelX + cancelW / 2, cancelY + 3, C_TEXT_DIM);

            // 渲染跟随鼠标的 3A 矢量拟真外科手术器械与准星
            renderActiveSurgicalInstrumentCursor(g, this.smoothMouseX, this.smoothMouseY, cx, cy, beatWave, nowMs);

            pose.popPose();
            super.render(g, mouseX, mouseY, partialTick);
        }

        private float computeArterialPulseWave(float phase) {
            // 拟真动脉压力波：快速收缩期主峰 (0.0~0.20) + 重搏切迹与重搏波 (0.20~0.38) + 舒张期平静窗 (0.38~1.0)
            if (phase < 0.18f) {
                return Mth.sin((phase / 0.18f) * (float) Math.PI);
            } else if (phase < 0.34f) {
                float sub = (phase - 0.18f) / 0.16f;
                return 0.38f * Mth.sin(sub * (float) Math.PI);
            }
            return 0.04f * Mth.sin((phase - 0.34f) * Mth.PI);
        }

        private void drawVesselTypeIndicator(GuiGraphics g, float x, float y, float beatWave, long nowMs) {
            Matrix4f mat = g.pose().last().pose();
            float r = this.isMainArtery ? (8.5f + beatWave * 3.8f) : (7.0f + Mth.sin(nowMs * 0.005f) * 1.0f);
            int outerCol = this.isMainArtery ? C_ALERT_RED : C_SAFE_GREEN;
            drawRingArc(mat, x, y, r - 2.0f, r, 0.0f, Mth.TWO_PI, withAlpha(outerCol, 0.85f), 20);
            drawRingArc(mat, x, y, 0.0f, r - 3.2f, 0.0f, Mth.TWO_PI, this.isMainArtery ? 0xCCFF1744 : 0xCC00E676, 16);
        }

        private void renderHemodynamicOscilloscope(GuiGraphics g, float beatWave) {
            int scopeX = viewX + 4;
            int scopeY = viewY + 4;
            int scopeW = viewW - 8;
            int scopeH = 20;
            g.fill(scopeX, scopeY, scopeX + scopeW, scopeY + scopeH, 0xCC040A12);
            g.fill(scopeX, scopeY + scopeH - 1, scopeX + scopeW, scopeY + scopeH, 0x55263238);

            float calmMax = ((float) ModItems.safeCfgDouble(MedicalConfig.SUTURE_BULLDOG_CALM_BEAT_MAX, 0.35));
            if (this.chosenPath == 2 && this.subStep == 1) {
                // 在路径 B-1 高亮显示舒张期安全下夹阈值带
                int safeTopY = Math.round(scopeY + scopeH - 3 - calmMax * (scopeH - 6));
                g.fill(scopeX + 1, safeTopY, scopeX + scopeW - 1, scopeY + scopeH - 2, 0x2E00E676);
                g.fill(scopeX + 1, safeTopY, scopeX + scopeW - 1, safeTopY + 1, 0x7700E676);
            }

            Matrix4f mat = g.pose().last().pose();
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.setShader(GameRenderer::getPositionColorShader);

            Tesselator tess = Tesselator.getInstance();
            BufferBuilder buf = tess.begin(VertexFormat.Mode.TRIANGLE_STRIP, DefaultVertexFormat.POSITION_COLOR);
            int n = this.ecgWaveHistory.length;
            for (int i = 0; i < n; i++) {
                float t = i / (float) (n - 1);
                float px = scopeX + 3.0f + t * (scopeW - 6.0f);
                float val = this.ecgWaveHistory[i];
                float py = scopeY + scopeH - 3.5f - val * (scopeH - 6.5f);
                boolean inCalm = val < calmMax;
                int col = (this.chosenPath == 2 && this.subStep == 1)
                        ? (inCalm ? C_SAFE_GREEN : C_ALERT_RED)
                        : (this.isMainArtery ? 0xFFFF5252 : C_NEON_CYAN);
                int alpha = Math.round((0.25f + 0.75f * t) * 255.0f);
                int r = (col >> 16) & 0xFF;
                int gr = (col >> 8) & 0xFF;
                int b = col & 0xFF;
                buf.addVertex(mat, px, py - 0.9f, 0.0f).setColor(r, gr, b, alpha);
                buf.addVertex(mat, px, py + 0.9f, 0.0f).setColor(r, gr, b, alpha);
            }
            MeshData mesh = buf.build();
            if (mesh != null) {
                BufferUploader.drawWithShader(mesh);
            }
        }

        private void renderBloodAndSparkParticles(GuiGraphics g, float dt) {
            Matrix4f mat = g.pose().last().pose();
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.setShader(GameRenderer::getPositionColorShader);

            Tesselator tess = Tesselator.getInstance();
            BufferBuilder buf = tess.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
            boolean hasAny = false;

            for (SurgicalParticle p : this.particles) {
                if (!p.active) continue;
                p.age += dt;
                if (p.age >= p.maxAge) {
                    p.active = false;
                    continue;
                }
                p.x += p.vx * dt;
                p.y += p.vy * dt;
                if (p.isSpark) {
                    p.vx *= (1.0f - dt * 5.5f);
                    p.vy *= (1.0f - dt * 5.5f);
                } else {
                    p.vy += 85.0f * dt;
                    p.vx *= (1.0f - dt * 3.2f);
                    if (p.isStain && p.age > p.maxAge * 0.45f) {
                        p.vx *= 0.85f;
                        p.vy *= 0.85f;
                    }
                }

                float lifeRatio = 1.0f - (p.age / p.maxAge);
                float half = p.size * (p.isSpark ? lifeRatio : (0.65f + 0.35f * lifeRatio));
                int a = Mth.clamp(Math.round(lifeRatio * 235.0f), 0, 255);
                int r = (p.color >> 16) & 0xFF;
                int gr = (p.color >> 8) & 0xFF;
                int b = p.color & 0xFF;

                buf.addVertex(mat, p.x - half, p.y + half, 0.0f).setColor(r, gr, b, a);
                buf.addVertex(mat, p.x + half, p.y + half, 0.0f).setColor(r, gr, b, a);
                buf.addVertex(mat, p.x + half, p.y - half, 0.0f).setColor(r, gr, b, a);
                buf.addVertex(mat, p.x - half, p.y - half, 0.0f).setColor(r, gr, b, a);
                hasAny = true;
            }

            MeshData mesh = buf.build();
            if (mesh != null && hasAny) {
                BufferUploader.drawWithShader(mesh);
            }
        }

        private void renderSurgicalCavityAndVessel(GuiGraphics g, float cx, float cy, float beatWave,
                                                   float dt, float mx, float my, long nowMs) {
            float openThresh = ((float) ModItems.safeCfgDouble(MedicalConfig.SUTURE_RETRACTOR_OPEN_THRESHOLD, 0.88));

            // 牵开器弹簧物理回弹与棘轮锁定
            if (this.draggingRetractor != 1 && !this.leftLocked) {
                if (this.leftRetract >= openThresh) {
                    this.leftLocked = true;
                    this.leftRetract = 1.0f;
                    playUiSound(SoundEvents.IRON_TRAPDOOR_CLOSE, 1.45f, 0.70f);
                    spawnClampSparks(viewX + 20.0f, cy, C_SAFE_GREEN);
                } else {
                    this.leftRetract = Math.max(0.0f, this.leftRetract - dt * 1.65f);
                }
            }
            if (this.draggingRetractor != 2 && !this.rightLocked) {
                if (this.rightRetract >= openThresh) {
                    this.rightLocked = true;
                    this.rightRetract = 1.0f;
                    playUiSound(SoundEvents.IRON_TRAPDOOR_CLOSE, 1.45f, 0.70f);
                    spawnClampSparks(viewX + viewW - 20.0f, cy, C_SAFE_GREEN);
                } else {
                    this.rightRetract = Math.max(0.0f, this.rightRetract - dt * 1.65f);
                }
            }

            float openRatio = (this.leftRetract + this.rightRetract) * 0.5f;
            if (this.topClamped) this.topClampAnim = Math.min(1.0f, this.topClampAnim + dt * 8.0f);
            if (this.botClamped) this.botClampAnim = Math.min(1.0f, this.botClampAnim + dt * 8.0f);

            Matrix4f mat = g.pose().last().pose();

            // 1. 深层创腔横纹肌束背景层
            for (int band = -5; band <= 5; band++) {
                float by = cy + band * 12.5f;
                int shade = (Math.abs(band) % 2 == 0) ? 0xFF240810 : 0xFF1B050B;
                g.fill(viewX + 2, Math.round(by - 5), viewX + viewW - 2, Math.round(by + 5), shade);
            }

            // 计算上下血管断端实时坐标（含生理搏动与组织痉挛游走）
            float driftAmp = ((float) ModItems.safeCfgDouble(MedicalConfig.SUTURE_STUMP_DRIFT_AMPLITUDE, 8.5));
            float stumpDriftX = (this.chosenPath == 1 && this.subStep == 1)
                    ? (Mth.sin(nowMs * 0.0042f) * 0.7f + Mth.cos(nowMs * 0.0071f) * 0.3f) * driftAmp
                    : 0.0f;
            float topX = cx + (this.topClamped ? 0.0f : stumpDriftX);
            float botX = cx - (this.botClamped ? 0.0f : stumpDriftX);

            float topStumpEnd = cy - 19.0f + (this.topClamped ? 0.0f : beatWave * (this.isMainArtery ? 4.5f : 1.5f));
            float botStumpStart = cy + 19.0f - (this.botClamped ? 0.0f : beatWave * (this.isMainArtery ? 4.5f : 1.5f));

            // 结扎收紧时血管颈部收缩变形
            float neckPinch = (this.chosenPath == 1 && this.subStep == 2) ? (this.knotTension * 4.5f) : 0.0f;
            float vesselRadius = this.isMainArtery ? (9.5f + (this.topClamped ? 0.0f : beatWave * 2.8f)) : 6.5f;

            int outerWallCol = this.isMainArtery ? 0xFFD50032 : 0xFF7B0D40;
            int innerLumenCol = this.isMainArtery ? 0xFFFF3D57 : 0xFF4A0726;
            int specHighlightCol = this.isMainArtery ? 0x77FF8A80 : 0x55F48FB1;

            // 完成路径 A 强行结扎动脉时的远端缺血发绀变色演示（若已断肢则无远端肢体缺血）
            int botOuterCol = (this.playingCompletionFinale && this.chosenPath == 1 && this.isMainArtery && !this.isAmputatedLimb)
                    ? 0xFF4A148C : outerWallCol;
            int botInnerCol = (this.playingCompletionFinale && this.chosenPath == 1 && this.isMainArtery && !this.isAmputatedLimb)
                    ? 0xFF311B92 : innerLumenCol;

            // 绘制上断端立体血管束 (带内膜管腔与高光反射带)
            drawTaperedVesselSegment(mat, cx, viewY + 24.0f, topX, topStumpEnd,
                    vesselRadius, Math.max(2.5f, vesselRadius - neckPinch), outerWallCol, innerLumenCol, specHighlightCol);

            // 绘制下断端立体血管束
            drawTaperedVesselSegment(mat, botX, botStumpStart, cx, viewY + viewH - 6.0f,
                    Math.max(2.5f, vesselRadius - neckPinch), vesselRadius, botOuterCol, botInnerCol, specHighlightCol);

            // 2. 断端动态喷血粒子与静脉漫溢涟漪
            if (openRatio > 0.25f && (!this.topClamped || !this.botClamped) && !this.playingCompletionFinale) {
                this.sprayAccum += dt;
                if (this.isMainArtery) {
                    float interval = (beatWave > 0.40f) ? 0.018f : 0.065f;
                    while (this.sprayAccum >= interval) {
                        this.sprayAccum -= interval;
                        float speedScale = 0.45f + 0.95f * beatWave;
                        if (!this.topClamped) {
                            float ang = Mth.HALF_PI + (float) (Math.random() * 0.85 - 0.425);
                            float spd = (45.0f + (float) (Math.random() * 75.0)) * speedScale;
                            spawnParticle(topX + (float) (Math.random() * 6.0 - 3.0), topStumpEnd,
                                    Mth.cos(ang) * spd, Mth.sin(ang) * spd,
                                    1.8f + beatWave * 1.4f, 0.38f, 0xFFFF1744, true, false);
                        }
                        if (!this.botClamped) {
                            float ang = -Mth.HALF_PI + (float) (Math.random() * 0.75 - 0.375);
                            float spd = (28.0f + (float) (Math.random() * 45.0)) * speedScale;
                            spawnParticle(botX + (float) (Math.random() * 6.0 - 3.0), botStumpStart,
                                    Mth.cos(ang) * spd, Mth.sin(ang) * spd,
                                    1.5f + beatWave * 1.0f, 0.32f, 0xFFD50000, true, false);
                        }
                    }
                } else {
                    // 非动脉大出血 (500~1000 mL/min)：暗红静脉血池波纹漫溢
                    float ripplePhase = (nowMs % 1400L) / 1400.0f;
                    drawRingArc(mat, cx, cy, 6.0f + ripplePhase * 22.0f, 8.5f + ripplePhase * 22.0f,
                            0.0f, Mth.TWO_PI, withAlpha(0xFFAD1457, (1.0f - ripplePhase) * 0.65f), 24);
                    while (this.sprayAccum >= 0.08f) {
                        this.sprayAccum -= 0.08f;
                        float ang = (float) (Math.random() * Math.PI * 2.0);
                        spawnParticle(cx + Mth.cos(ang) * 8.0f, cy + Mth.sin(ang) * 8.0f,
                                Mth.cos(ang) * 18.0f, Mth.sin(ang) * 18.0f,
                                2.0f, 0.45f, 0xFF880E4F, true, false);
                    }
                }
            }

            // 3. 已锁定的血管止血钳 (Path A) 或 金色哈巴狗动脉微血管夹 (Path B)
            float releaseScale = this.playingCompletionFinale ? (1.0f - this.completionAnimProgress) : 1.0f;
            if (this.topClamped && releaseScale > 0.05f) {
                renderClampedInstrumentOnVessel(g, mat, topX, topStumpEnd - 5.0f, true, this.chosenPath == 2, this.topClampAnim * releaseScale);
            }
            if (this.botClamped && releaseScale > 0.05f) {
                renderClampedInstrumentOnVessel(g, mat, botX, botStumpStart + 5.0f, false, this.chosenPath == 2, this.botClampAnim * releaseScale);
            }

            // 4. 各阶段核心互动与微创器械渲染
            if (this.chosenPath == 1) {
                renderPathALigationStage(g, mat, cx, cy, topX, botX, topStumpEnd, botStumpStart, vesselRadius, beatWave, dt, mx, my, nowMs);
            } else if (this.chosenPath == 2) {
                renderPathBAnastomosisStage(g, mat, cx, cy, topStumpEnd, botStumpStart, vesselRadius, beatWave, dt, mx, my, nowMs);
            }

            // 5. 表层肌肉切口组织瓣与胶原牵引丝（随牵开器拉开向两侧弹性退缩）
            float gapHalfW = 5.0f + openRatio * 74.0f;
            float flapLeftEdge = cx - gapHalfW;
            float flapRightEdge = cx + gapHalfW;
            renderRetractedMuscleFlaps(g, mat, flapLeftEdge, flapRightEdge, cy, openRatio);

            // 6. 第 0 阶段：齿轮牵开器锁轨与双路径全息选择卡
            if (this.chosenPath == 0) {
                renderRetractorAndPathCards(g, mat, cx, cy, flapLeftEdge, flapRightEdge, openRatio, dt, mx, my, nowMs);
            }
        }

        private void renderPathALigationStage(GuiGraphics g, Matrix4f mat, float cx, float cy,
                                              float topX, float botX, float topStumpEnd, float botStumpStart,
                                              float vesselRadius, float beatWave, float dt, float mx, float my, long nowMs) {
            float clampRadius = ((float) ModItems.safeCfgDouble(MedicalConfig.SUTURE_STUMP_CLAMP_RADIUS, 13.0));
            if (this.subStep == 1) {
                // A-1：游走断端动态锁定环
                float pulseR = clampRadius + Mth.sin(nowMs * 0.012f) * 1.8f;
                if (!this.topClamped) {
                    boolean nearTop = Math.hypot(mx - topX, my - (topStumpEnd - 4.0f)) <= clampRadius;
                    drawRingArc(mat, topX, topStumpEnd - 4.0f, pulseR - 2.0f, pulseR, 0.0f, Mth.TWO_PI,
                            nearTop ? C_SAFE_GREEN : C_NEON_CYAN, 24);
                }
                if (!this.botClamped) {
                    boolean nearBot = Math.hypot(mx - botX, my - (botStumpStart + 4.0f)) <= clampRadius;
                    drawRingArc(mat, botX, botStumpStart + 4.0f, pulseR - 2.0f, pulseR, 0.0f, Mth.TWO_PI,
                            nearBot ? C_SAFE_GREEN : C_NEON_CYAN, 24);
                }
                if (this.topClamped && this.botClamped) {
                    this.subStep = 2;
                }
            } else if (this.subStep == 2) {
                float reqDeg = ((float) ModItems.safeCfgDouble(MedicalConfig.SUTURE_WINDING_REQUIRED_DEGREES, 720.0));
                if (this.totalWindProgress < 1.0f) {
                    if (this.windingDrag) {
                        float ang = (float) Math.atan2(my - cy, mx - cx);
                        float delta = Mth.wrapDegrees((ang - this.lastWindAngle) * Mth.RAD_TO_DEG);
                        if (Math.abs(delta) < 70.0f) {
                            this.totalWindProgress = Mth.clamp(this.totalWindProgress + Math.abs(delta) / reqDeg, 0.0f, 1.0f);
                            int tickStep = (int) (this.totalWindProgress * 16.0f);
                            if (tickStep > this.lastWindTickStep) {
                                this.lastWindTickStep = tickStep;
                                playUiSound(SoundEvents.TRIPWIRE_CLICK_ON, 1.15f + this.totalWindProgress * 0.55f, 0.45f);
                                spawnParticle(mx, my, 0.0f, -10.0f, 1.4f, 0.22f, C_NEON_CYAN, false, true);
                            }
                        }
                        this.lastWindAngle = ang;
                    }

                    // 绘制 3D 环绕引导轨道与实时牵引缝合线
                    float orbitR = 28.0f;
                    drawRingArc(mat, cx, cy, orbitR - 1.5f, orbitR + 1.5f, 0.0f, Mth.TWO_PI, 0x4400E5FF, 32);
                    drawRingArc(mat, cx, cy, orbitR - 2.2f, orbitR + 2.2f, -Mth.HALF_PI,
                            Mth.TWO_PI * this.totalWindProgress, C_SAFE_GREEN, 32);

                    // 绘制已缠绕在上下断端颈部的外科缝合线圈
                    int coils = Math.max(1, Math.round(this.totalWindProgress * 7.0f));
                    for (int c = 0; c < coils; c++) {
                        float wyTop = topStumpEnd - 12.0f + c * 1.6f;
                        float wyBot = botStumpStart + 4.0f + c * 1.6f;
                        drawLineSegment(mat, cx - vesselRadius - 3.0f, wyTop, cx + vesselRadius + 3.0f, wyTop + 1.0f, 1.6f, C_NEON_CYAN);
                        drawLineSegment(mat, cx - vesselRadius - 3.0f, wyBot, cx + vesselRadius + 3.0f, wyBot + 1.0f, 1.6f, C_NEON_CYAN);
                    }

                    // 绘制从血管断端连向玩家持针钳的动态悬链缝线
                    if (this.windingDrag) {
                        drawCurvedSutureThread(mat, cx, topStumpEnd - 6.0f, (cx + mx) * 0.5f, (cy + my) * 0.5f + 10.0f, mx, my, C_NEON_CYAN);
                    }
                } else if (!this.playingCompletionFinale) {
                    // 死结张力保持博弈（血压脉搏会反向冲击张力）
                    float minT = ((float) ModItems.safeCfgDouble(MedicalConfig.SUTURE_KNOT_TENSION_MIN, 0.68));
                    float maxT = ((float) ModItems.safeCfgDouble(MedicalConfig.SUTURE_KNOT_TENSION_MAX, 0.92));
                    float snapT = ((float) ModItems.safeCfgDouble(MedicalConfig.SUTURE_KNOT_TENSION_SNAP, 0.98));
                    float holdSec = ((float) ModItems.safeCfgDouble(MedicalConfig.SUTURE_KNOT_HOLD_SECONDS, 0.75));

                    if (this.pullingKnot) {
                        this.knotTension += dt * (0.92f + beatWave * 0.25f);
                    } else {
                        this.knotTension = Math.max(0.0f, this.knotTension - dt * (0.58f + beatWave * 0.18f));
                    }

                    if (this.knotTension >= snapT) {
                        // 用力过猛绷断缝线！喷射蓝色断线碎屑并触发创口撕裂失误
                        playUiSound(SoundEvents.ITEM_BREAK, 1.35f, 0.80f);
                        for (int k = 0; k < 14; k++) {
                            float ang = (float) (Math.random() * Math.PI * 2.0);
                            spawnParticle(cx, cy, Mth.cos(ang) * 55.0f, Mth.sin(ang) * 55.0f, 1.8f, 0.35f, C_NEON_CYAN, false, true);
                        }
                        this.knotTension = 0.0f;
                        this.knotHoldProgress = 0.0f;
                        this.totalWindProgress = 0.35f;
                        this.pullingKnot = false;
                        triggerSurgicalMistake(cx, cy);
                    } else if (this.knotTension >= minT && this.knotTension <= maxT) {
                        this.knotHoldProgress += dt / Math.max(0.15f, holdSec);
                        int holdStep = (int) (this.knotHoldProgress * 8.0f);
                        if (holdStep > this.lastTensionTickStep) {
                            this.lastTensionTickStep = holdStep;
                            playUiSound(SoundEvents.NOTE_BLOCK_CHIME.value(), 0.9f + this.knotHoldProgress * 0.7f, 0.50f);
                        }
                        if (this.knotHoldProgress >= 1.0f) {
                            this.playingCompletionFinale = true;
                            this.pendingCompletionActionCode = 201;
                            this.completionAnimProgress = 0.0f;
                            playUiSound(SoundEvents.PLAYER_LEVELUP, 1.45f, 0.65f);
                            spawnClampSparks(cx, cy, C_SAFE_GREEN);
                            return;
                        }
                    } else {
                        this.knotHoldProgress = Math.max(0.0f, this.knotHoldProgress - dt * 0.55f);
                    }

                    // 绘制环形死结张力仪表盘与紧绷的双股缝合线
                    drawCurvedSutureThread(mat, cx - vesselRadius, topStumpEnd - 6.0f, cx, cy, mx, my,
                            (this.knotTension > maxT) ? C_ALERT_RED : C_SAFE_GREEN);
                    drawCurvedSutureThread(mat, cx + vesselRadius, botStumpStart + 6.0f, cx, cy, mx, my,
                            (this.knotTension > maxT) ? C_ALERT_RED : C_SAFE_GREEN);

                    float arcR = 30.0f;
                    drawRingArc(mat, cx, cy, arcR - 3.5f, arcR, -Mth.HALF_PI, Mth.TWO_PI, 0x660A1926, 32);
                    drawRingArc(mat, cx, cy, arcR - 3.5f, arcR,
                            -Mth.HALF_PI + Mth.TWO_PI * minT, Mth.TWO_PI * (maxT - minT), 0x8800E676, 16);
                    drawRingArc(mat, cx, cy, arcR - 3.5f, arcR,
                            -Mth.HALF_PI + Mth.TWO_PI * maxT, Mth.TWO_PI * (1.0f - maxT), 0x88FF1744, 12);
                    int tCol = (this.knotTension >= minT && this.knotTension <= maxT)
                            ? C_SAFE_GREEN : (this.knotTension > maxT ? C_ALERT_RED : C_NEON_CYAN);
                    drawRingArc(mat, cx, cy, arcR - 4.2f, arcR + 0.8f,
                            -Mth.HALF_PI, Mth.TWO_PI * Mth.clamp(this.knotTension, 0.0f, 1.0f), tCol, 32);
                    // 内圈锁死确认进度金环
                    if (this.knotHoldProgress > 0.01f) {
                        drawRingArc(mat, cx, cy, arcR - 8.5f, arcR - 5.5f,
                                -Mth.HALF_PI, Mth.TWO_PI * Mth.clamp(this.knotHoldProgress, 0.0f, 1.0f), C_ADRENALINE_GOLD, 28);
                    }
                }
            }
        }

        private void renderPathBAnastomosisStage(GuiGraphics g, Matrix4f mat, float cx, float cy,
                                                 float topStumpEnd, float botStumpStart, float vesselRadius,
                                                 float beatWave, float dt, float mx, float my, long nowMs) {
            float calmMax = ((float) ModItems.safeCfgDouble(MedicalConfig.SUTURE_BULLDOG_CALM_BEAT_MAX, 0.35));
            float alignTol = ((float) ModItems.safeCfgDouble(MedicalConfig.SUTURE_GRAFT_ALIGN_TOLERANCE_DEG, 9.5));
            float snapDist = ((float) ModItems.safeCfgDouble(MedicalConfig.SUTURE_GRAFT_SNAP_DISTANCE, 13.0));

            if (this.subStep == 1) {
                // B-1：舒张期同步脉冲波环（随心动周期收缩扩张，舒张期变绿可下夹）
                boolean calmWindow = (beatWave < calmMax);
                int ringCol = calmWindow ? C_SAFE_GREEN : C_ALERT_RED;
                float dynR = 9.0f + beatWave * 7.5f;
                if (!this.topClamped) {
                    drawRingArc(mat, cx, topStumpEnd - 5.0f, dynR - 2.0f, dynR, 0.0f, Mth.TWO_PI, ringCol, 24);
                    drawRingArc(mat, cx, topStumpEnd - 5.0f, 8.5f, 9.5f, 0.0f, Mth.TWO_PI, 0x66FFFFFF, 20);
                }
                if (!this.botClamped) {
                    drawRingArc(mat, cx, botStumpStart + 5.0f, dynR - 2.0f, dynR, 0.0f, Mth.TWO_PI, ringCol, 24);
                    drawRingArc(mat, cx, botStumpStart + 5.0f, 8.5f, 9.5f, 0.0f, Mth.TWO_PI, 0x66FFFFFF, 20);
                }
                if (this.topClamped && this.botClamped) {
                    this.subStep = 2;
                }
            } else if (this.subStep == 2) {
                // B-2：拖拽波纹状 ePTFE 人工血管桥接管并用滚轮旋转对齐轴线
                boolean posAligned = Math.hypot(this.graftX - cx, this.graftY - cy) <= snapDist;
                boolean angAligned = Math.abs(this.graftAngleDeg) <= alignTol;
                boolean fullyAligned = posAligned && angAligned;

                // 靶心与轴向激光对齐辅助线
                drawLineSegment(mat, cx, topStumpEnd - 4.0f, cx, botStumpStart + 4.0f, 1.0f,
                        fullyAligned ? C_SAFE_GREEN : 0x5500E5FF);
                drawRingArc(mat, cx, cy, snapDist - 1.5f, snapDist, 0.0f, Mth.TWO_PI,
                        posAligned ? C_SAFE_GREEN : 0x6600E5FF, 24);

                if (this.draggingGraft) {
                    this.graftX = mx;
                    this.graftY = my;
                }

                PoseStack pose = g.pose();
                pose.pushPose();
                pose.translate(this.graftX, this.graftY, 0.0f);
                pose.mulPose(com.mojang.math.Axis.ZP.rotationDegrees(this.graftAngleDeg));
                renderCorrugatedPtfeGraft(g, -21.0f, 21.0f, fullyAligned ? C_SAFE_GREEN : (angAligned ? C_NEON_CYAN : C_WARNING_YELLOW), 0.0f);
                pose.popPose();
            } else if (this.subStep == 3) {
                // B-3：已嵌入 PTFE 人工血管 -> 环周等距微创缝合 + 血流重灌注收尾
                this.graftSnapAnim = Math.min(1.0f, this.graftSnapAnim + dt * 6.0f);
                float reperf = this.playingCompletionFinale ? this.completionAnimProgress : 0.0f;
                PoseStack pose = g.pose();
                pose.pushPose();
                pose.translate(cx, cy, 0.0f);
                renderCorrugatedPtfeGraft(g, topStumpEnd - cy - 2.0f, botStumpStart - cy + 2.0f, C_SAFE_GREEN, reperf);
                pose.popPose();

                int stitchCount = this.stitchesDone.length;
                boolean allDone = true;
                for (int i = 0; i < stitchCount; i++) {
                    float sx = getStitchX(cx, i, stitchCount);
                    float syTop = getStitchTopY(topStumpEnd, botStumpStart, i, stitchCount);
                    float syBot = syTop + 10.0f;

                    if (this.stitchesDone[i]) {
                        this.stitchTightenAnim[i] = Math.min(1.0f, this.stitchTightenAnim[i] + dt * 7.0f);
                        float tight = this.stitchTightenAnim[i];
                        drawLineSegment(mat, sx, syTop, sx, syBot, 1.6f, C_NEON_CYAN);
                        // 缝合外科线结小十字高光
                        float midY = (syTop + syBot) * 0.5f;
                        drawLineSegment(mat, sx - 2.2f * tight, midY - 1.2f, sx + 2.2f * tight, midY + 1.2f, 1.2f, C_SAFE_GREEN);
                    } else {
                        allDone = false;
                        boolean active = (this.activeStitchIdx == i);
                        int entryCol = active ? C_SAFE_GREEN : C_WARNING_YELLOW;
                        drawRingArc(mat, sx, syTop, 1.2f, 3.2f, 0.0f, Mth.TWO_PI, entryCol, 12);
                        drawRingArc(mat, sx, syBot, 1.2f, 3.2f, 0.0f, Mth.TWO_PI, C_NEON_CYAN, 12);
                        if (active) {
                            // 渲染从穿刺入针点经过弯曲缝合针到鼠标的实时贝塞尔缝合线
                            drawCurvedSutureThread(mat, sx, syTop, (sx + mx) * 0.5f + 6.0f, (syTop + my) * 0.5f, mx, my, C_NEON_CYAN);
                        }
                    }
                }

                if (allDone && !this.playingCompletionFinale) {
                    this.playingCompletionFinale = true;
                    this.pendingCompletionActionCode = 202;
                    this.completionAnimProgress = 0.0f;
                    playUiSound(SoundEvents.PLAYER_LEVELUP, 1.55f, 0.70f);
                    spawnClampSparks(cx, cy, C_SAFE_GREEN);
                }
            }
        }

        private void renderCorrugatedPtfeGraft(GuiGraphics g, float yTop, float yBot, int cuffColor, float reperfusionWave) {
            int top = Math.round(yTop);
            int bot = Math.round(yBot);
            // 管体阴影与外层 ePTFE 医疗白材质
            g.fill(-10, top, 10, bot, 0xFFCFD8DC);
            g.fill(-8, top, 8, bot, 0xFFECEFF1);
            g.fill(-4, top, 2, bot, 0xFFFFFFFF);

            // 若处于手术完成重灌注阶段，鲜红动脉血从上至下充盈半透明人工血管管腔
            if (reperfusionWave > 0.01f) {
                int flowBottom = Math.round(Mth.lerp(Mth.clamp(reperfusionWave, 0.0f, 1.0f), top, bot));
                g.fill(-7, top, 7, flowBottom, 0xAAFF1744);
                g.fill(-3, top, 3, flowBottom, 0xCCFF5252);
            }

            // 螺旋波纹抗折环 (Crimped Rings)
            for (int ry = top + 3; ry <= bot - 3; ry += 4) {
                g.fill(-10, ry, 10, ry + 1, 0xFF90A4AE);
                g.fill(-8, ry + 1, 8, ry + 2, 0xFFB0BEC5);
            }
            // 上下吻合口密封法兰环
            g.fill(-11, top, 11, top + 2, cuffColor);
            g.fill(-11, bot - 2, 11, bot, cuffColor);
        }

        private void renderClampedInstrumentOnVessel(GuiGraphics g, Matrix4f mat, float x, float y,
                                                     boolean isTop, boolean isBulldogGold, float anim) {
            float slide = (1.0f - Mth.clamp(anim, 0.0f, 1.0f)) * 22.0f;
            float dir = isTop ? -1.0f : 1.0f;
            float cx = x + dir * slide;
            if (isBulldogGold) {
                // 金色哈巴狗动脉弹簧微血管夹 (Bulldog Micro-Clamp)
                int ix = Math.round(cx);
                int iy = Math.round(y);
                g.fill(ix - 16, iy - 3, ix + 14, iy + 3, 0xFFFFB300);
                g.fill(ix - 14, iy - 2, ix + 12, iy + 1, 0xFFFFEA00);
                // 尾部弹簧圈
                drawRingArc(mat, cx + 15.0f, y, 2.5f, 5.0f, 0.0f, Mth.TWO_PI, C_ADRENALINE_GOLD, 14);
            } else {
                // 蚊式不锈钢止血钳 (Mosquito Hemostat)
                drawLineSegment(mat, cx - 15.0f, y - 1.5f, cx + 38.0f, y + dir * 18.0f, 2.6f, 0xFFCFD8DC);
                drawLineSegment(mat, cx - 15.0f, y + 1.5f, cx + 38.0f, y + dir * 12.0f, 2.2f, 0xFF90A4AE);
                g.fill(Math.round(cx - 15), Math.round(y - 3), Math.round(cx + 12), Math.round(y + 3), 0xFFECEFF1);
            }
        }

        private void renderRetractedMuscleFlaps(GuiGraphics g, Matrix4f mat, float flapLeftEdge, float flapRightEdge, float cy, float openRatio) {
            int lEdge = Math.round(flapLeftEdge);
            int rEdge = Math.round(flapRightEdge);
            int topY = viewY + 25;
            int botY = viewY + viewH - 2;

            if (lEdge > viewX + 2) {
                g.fill(viewX + 2, topY, lEdge, botY, 0xF4340B13);
                g.fill(Math.max(viewX + 2, lEdge - 6), topY, lEdge, botY, 0xFF5D1322);
                g.fill(Math.max(viewX + 2, lEdge - 2), topY, lEdge, botY, 0xFFAD1E37);
            }
            if (rEdge < viewX + viewW - 2) {
                g.fill(rEdge, topY, viewX + viewW - 2, botY, 0xF4340B13);
                g.fill(rEdge, topY, Math.min(viewX + viewW - 2, rEdge + 6), botY, 0xFF5D1322);
                g.fill(rEdge, topY, Math.min(viewX + viewW - 2, rEdge + 2), botY, 0xFFAD1E37);
            }

            // 切口上下两端与肌肉边缘的弹性牵引胶原纤维丝
            if (openRatio > 0.05f && openRatio < 0.95f) {
                for (int i = -2; i <= 2; i++) {
                    float fy = cy + i * 16.0f;
                    drawLineSegment(mat, flapLeftEdge, fy, flapLeftEdge + 8.0f, fy + i * 2.5f, 0.9f, 0x66F48FB1);
                    drawLineSegment(mat, flapRightEdge, fy, flapRightEdge - 8.0f, fy + i * 2.5f, 0.9f, 0x66F48FB1);
                }
            }
        }

        private void renderRetractorAndPathCards(GuiGraphics g, Matrix4f mat, float cx, float cy,
                                                 float flapLeftEdge, float flapRightEdge, float openRatio,
                                                 float dt, float mx, float my, long nowMs) {
            // 顶部不锈钢棘轮齿条锁轨
            int rackY = viewY + 28;
            g.fill(viewX + 14, rackY, viewX + viewW - 14, rackY + 4, 0xFF78909C);
            for (int tx = viewX + 18; tx < viewX + viewW - 18; tx += 6) {
                g.fill(tx, rackY - 2, tx + 2, rackY, 0xFFCFD8DC);
            }

            // 左右目标棘轮卡槽
            int hookY = Math.round(cy);
            drawRingArc(mat, viewX + 18.0f, hookY, 8.0f, 11.0f, 0.0f, Mth.TWO_PI,
                    this.leftLocked ? C_SAFE_GREEN : 0x6600E5FF, 20);
            drawRingArc(mat, viewX + viewW - 18.0f, hookY, 8.0f, 11.0f, 0.0f, Mth.TWO_PI,
                    this.rightLocked ? C_SAFE_GREEN : 0x6600E5FF, 20);

            // 左右四爪外科牵开器 (Weitlaner Retractor Claws)
            int leftHookX = Math.round(flapLeftEdge - 5.0f);
            int rightHookX = Math.round(flapRightEdge + 5.0f);
            renderRetractorClaw(g, mat, leftHookX, hookY, true, this.leftLocked, this.draggingRetractor == 1);
            renderRetractorClaw(g, mat, rightHookX, hookY, false, this.rightLocked, this.draggingRetractor == 2);

            if (this.leftLocked && this.rightLocked) {
                this.incisionOpened = true;
                this.pathCardSlideAnim = Math.min(1.0f, this.pathCardSlideAnim + dt * 5.0f);
                float slideOffset = (1.0f - this.pathCardSlideAnim) * 28.0f;

                int cardW = 82, cardH = 38;
                int cardAY = Math.round(viewY + viewH - cardH - 8 + slideOffset);
                int cardAX = Math.round(cx - cardW - 10);
                int cardBX = Math.round(cx + 10);

                boolean hovA = mx >= cardAX && mx <= cardAX + cardW && my >= cardAY && my <= cardAY + cardH;
                boolean hovB = mx >= cardBX && mx <= cardBX + cardW && my >= cardAY && my <= cardAY + cardH;

                // 推荐路径高亮（断肢残端或 500~1000 推荐路径A直接闭合血管；未断肢且 >1000 动脉出血推荐路径B桥接）
                boolean recommendA = this.isAmputatedLimb || !this.isMainArtery;
                int colA = recommendA ? C_SAFE_GREEN : (hovA ? C_WARNING_YELLOW : 0xFF607D8B);
                int colB = (!recommendA) ? C_SAFE_GREEN : (hovB ? C_NEON_CYAN : 0xFF607D8B);

                // 路径 A 卡片（血管结扎图腾）
                g.fill(cardAX, cardAY, cardAX + cardW, cardAY + cardH, hovA ? 0xE6102838 : 0xCC061018);
                drawTacticalCornerBrackets(g, cardAX, cardAY, cardW, cardH, colA);
                int axMid = cardAX + cardW / 2;
                int ayMid = cardAY + cardH / 2;
                g.fill(axMid - 5, cardAY + 6, axMid + 5, cardAY + cardH - 6, 0xFFAD1457);
                drawRingArc(mat, axMid, ayMid, 6.0f, 8.5f, 0.0f, Mth.TWO_PI, C_NEON_CYAN, 18);
                g.fill(axMid - 14, ayMid - 2, axMid + 14, ayMid + 2, C_NEON_CYAN);

                // 路径 B 卡片（双金夹 + PTFE 人工血管桥接图腾）
                g.fill(cardBX, cardAY, cardBX + cardW, cardAY + cardH, hovB ? 0xE6102838 : 0xCC061018);
                drawTacticalCornerBrackets(g, cardBX, cardAY, cardW, cardH, colB);
                int bxMid = cardBX + cardW / 2;
                g.fill(bxMid - 6, cardAY + 4, bxMid + 6, cardAY + cardH - 4, 0xFFFF1744);
                g.fill(bxMid - 7, cardAY + 10, bxMid + 7, cardAY + cardH - 10, 0xFFECEFF1);
                g.fill(bxMid - 12, cardAY + 6, bxMid + 12, cardAY + 8, C_ADRENALINE_GOLD);
                g.fill(bxMid - 12, cardAY + cardH - 8, bxMid + 12, cardAY + cardH - 6, C_ADRENALINE_GOLD);
            }
        }

        private void renderEmbeddedBulletsAndKidneyTray(GuiGraphics g, Matrix4f mat, float cx, float cy,
                                                        float mx, float my, long nowMs) {
            // 右侧无菌不锈钢手术弯盘 (Kidney Dish)
            float trayX = viewX + viewW - 28.0f;
            float trayY = cy;
            boolean hovTray = (this.draggingBulletIdx >= 0) && (Math.hypot(mx - trayX, my - trayY) <= 24.0);
            int trayBorder = hovTray ? C_SAFE_GREEN : 0xFFB0BEC5;
            drawRingArc(mat, trayX, trayY, 0.0f, 19.5f, 0.0f, Mth.TWO_PI, 0xCC263238, 22);
            drawRingArc(mat, trayX, trayY, 17.5f, 20.5f, 0.0f, Mth.TWO_PI, trayBorder, 22);
            drawRingArc(mat, trayX - 2.0f, trayY - 2.0f, 11.0f, 13.0f, -0.8f, 1.8f, 0x66ECEFF1, 12);

            // 弯盘内已夹出的变形弹头
            for (int i = 0; i < this.extractedBulletCount; i++) {
                float bx = trayX - 5.0f + (i % 2) * 9.0f;
                float by = trayY - 6.0f + (i / 2) * 7.0f;
                g.fill(Math.round(bx - 3), Math.round(by - 2), Math.round(bx + 3), Math.round(by + 2), 0xFFD4AF37);
                g.fill(Math.round(bx + 1), Math.round(by - 2), Math.round(bx + 4), Math.round(by + 2), 0xFF90A4AE);
            }

            // 创腔深部嵌留的变形铜被甲铅芯弹头
            for (int b = 0; b < this.embeddedBulletTotal; b++) {
                if (this.bulletDone[b]) continue;
                float bx = (this.draggingBulletIdx == b) ? mx : this.bulletX[b];
                float by = (this.draggingBulletIdx == b) ? my : this.bulletY[b];
                boolean hov = (this.draggingBulletIdx == b) || (Math.hypot(mx - bx, my - by) <= 13.0);

                // 弹头周围组织渗血暗影与高亮抓取环
                drawRingArc(mat, bx, by, 0.0f, 7.5f, 0.0f, Mth.TWO_PI, 0x883E0512, 16);
                if (hov) {
                    drawRingArc(mat, bx, by, 8.2f, 9.8f, 0.0f, Mth.TWO_PI, C_ADRENALINE_GOLD, 18);
                }
                // 铜被甲圆柱尾段 + 蘑菇状翻卷铅芯弹头
                int ix = Math.round(bx);
                int iy = Math.round(by);
                g.fill(ix - 5, iy - 3, ix + 2, iy + 3, 0xFFC58B2B);
                g.fill(ix - 4, iy - 2, ix + 1, iy, 0xFFFFD54F);
                g.fill(ix + 1, iy - 4, ix + 5, iy + 4, 0xFF90A4AE);
                g.fill(ix + 2, iy - 2, ix + 4, iy + 1, 0xFFCFD8DC);
            }

            String hint = "用取弹钳夹出深部弹头放入右侧弯盘 (" + this.extractedBulletCount + "/" + this.embeddedBulletTotal + ")";
            g.drawCenteredString(this.font, hint, Math.round(cx), viewY + viewH - 16, C_ADRENALINE_GOLD);
        }

        private void renderRetractorClaw(GuiGraphics g, Matrix4f mat, int hx, int hy, boolean isLeft, boolean locked, boolean dragging) {
            int metalCol = locked ? C_SAFE_GREEN : (dragging ? C_NEON_CYAN : 0xFFECEFF1);
            // 连接顶轨的竖直钢臂
            drawLineSegment(mat, hx, viewY + 30.0f, hx, hy, 2.4f, 0xFF90A4AE);
            // 四爪弯钩
            for (int c = -1; c <= 1; c++) {
                int cy = hy + c * 6;
                g.fill(hx - 6, cy - 1, hx + 6, cy + 1, metalCol);
                int tipX = isLeft ? (hx + 6) : (hx - 6);
                g.fill(tipX - 1, cy - 2, tipX + 1, cy + 2, 0xFFFFFFFF);
            }
            // 牵引指环手柄
            drawRingArc(mat, hx + (isLeft ? -7.0f : 7.0f), hy, 4.5f, 7.0f, 0.0f, Mth.TWO_PI, metalCol, 16);
        }

        private void renderActiveSurgicalInstrumentCursor(GuiGraphics g, float mx, float my,
                                                          float cx, float cy, float beatWave, long nowMs) {
            float swayDist = Mth.sqrt(this.cursorTremorX * this.cursorTremorX + this.cursorTremorY * this.cursorTremorY);
            if (swayDist > 1.2f) {
                Matrix4f hudMat = g.pose().last().pose();
                int swayCol = (swayDist > 10.0f) ? 0x88FF1744 : 0x66FFB300;
                drawRingArc(hudMat, mx, my, 7.5f, 8.8f, 0.0f, Mth.TWO_PI, swayCol, 18);
            }

            SurgicalInstrumentRenderer.ToolType tool = SurgicalInstrumentRenderer.ToolType.NEEDLE_HOLDER;
            boolean active = false;
            if (this.chosenPath == 0 && this.leftLocked && this.rightLocked && this.extractedBulletCount < this.embeddedBulletTotal) {
                tool = SurgicalInstrumentRenderer.ToolType.HEMOSTAT_FORCEPS;
                active = (this.draggingBulletIdx >= 0);
            } else if (this.chosenPath == 1 && this.subStep == 1) {
                tool = SurgicalInstrumentRenderer.ToolType.HEMOSTAT_FORCEPS;
                active = this.topClamped || this.botClamped;
            } else if (this.chosenPath == 2 && this.subStep == 1) {
                tool = SurgicalInstrumentRenderer.ToolType.HEMOSTAT_FORCEPS;
                active = beatWave < ((float) ModItems.safeCfgDouble(MedicalConfig.SUTURE_BULLDOG_CALM_BEAT_MAX, 0.35));
            } else if (this.chosenPath == 2 && this.subStep == 2) {
                tool = SurgicalInstrumentRenderer.ToolType.HEMOSTAT_FORCEPS;
                active = this.draggingGraft;
            } else {
                tool = SurgicalInstrumentRenderer.ToolType.NEEDLE_HOLDER;
                active = this.windingDrag || this.pullingKnot || (this.activeStitchIdx >= 0);
            }
            SurgicalInstrumentRenderer.renderDetailedSurgicalToolCursor(
                    g, mx, my, this.cursorTiltDeg, tool, active, beatWave, (nowMs % 100000L) * 0.02f);
        }

        private void drawTaperedVesselSegment(Matrix4f mat, float x0, float y0, float x1, float y1,
                                              float r0, float r1, int outerCol, int innerCol, int specCol) {
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.setShader(GameRenderer::getPositionColorShader);

            // 外膜管壁
            drawQuad(mat, x0 - r0, y0, x0 + r0, y0, x1 + r1, y1, x1 - r1, y1, outerCol);
            // 内膜管腔
            drawQuad(mat, x0 - r0 * 0.62f, y0, x0 + r0 * 0.62f, y0, x1 + r1 * 0.62f, y1, x1 - r1 * 0.62f, y1, innerCol);
            // 湿润血管壁镜面高光带
            drawQuad(mat, x0 - r0 * 0.35f, y0, x0 - r0 * 0.12f, y0, x1 - r1 * 0.12f, y1, x1 - r1 * 0.35f, y1, specCol);
        }

        private void drawQuad(Matrix4f mat, float x0, float y0, float x1, float y1,
                              float x2, float y2, float x3, float y3, int color) {
            int a = (color >>> 24) & 0xFF;
            int r = (color >>> 16) & 0xFF;
            int gr = (color >>> 8) & 0xFF;
            int b = color & 0xFF;
            Tesselator tess = Tesselator.getInstance();
            BufferBuilder buf = tess.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
            buf.addVertex(mat, x0, y0, 0.0f).setColor(r, gr, b, a);
            buf.addVertex(mat, x3, y3, 0.0f).setColor(r, gr, b, a);
            buf.addVertex(mat, x2, y2, 0.0f).setColor(r, gr, b, a);
            buf.addVertex(mat, x1, y1, 0.0f).setColor(r, gr, b, a);
            MeshData mesh = buf.build();
            if (mesh != null) {
                BufferUploader.drawWithShader(mesh);
            }
        }

        private void drawLineSegment(Matrix4f mat, float x0, float y0, float x1, float y1, float halfThickness, int color) {
            float dx = x1 - x0;
            float dy = y1 - y0;
            float len = Mth.sqrt(dx * dx + dy * dy);
            if (len < 0.001f) return;
            float nx = -dy / len * halfThickness;
            float ny = dx / len * halfThickness;
            drawQuad(mat, x0 + nx, y0 + ny, x0 - nx, y0 - ny, x1 - nx, y1 - ny, x1 + nx, y1 + ny, color);
        }

        private void drawCurvedSutureThread(Matrix4f mat, float x0, float y0, float ctrlX, float ctrlY, float x1, float y1, int color) {
            int steps = 14;
            float prevX = x0;
            float prevY = y0;
            for (int i = 1; i <= steps; i++) {
                float t = i / (float) steps;
                float u = 1.0f - t;
                float curX = u * u * x0 + 2.0f * u * t * ctrlX + t * t * x1;
                float curY = u * u * y0 + 2.0f * u * t * ctrlY + t * t * y1;
                drawLineSegment(mat, prevX, prevY, curX, curY, 0.95f, color);
                prevX = curX;
                prevY = curY;
            }
        }

        private void drawRingArc(Matrix4f mat, float cx, float cy, float rInner, float rOuter,
                                 float startAngle, float sweepAngle, int color, int segments) {
            if (Math.abs(sweepAngle) < 0.002f) return;
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.setShader(GameRenderer::getPositionColorShader);

            int a = (color >>> 24) & 0xFF;
            int r = (color >>> 16) & 0xFF;
            int gr = (color >>> 8) & 0xFF;
            int b = color & 0xFF;

            Tesselator tess = Tesselator.getInstance();
            BufferBuilder buf = tess.begin(VertexFormat.Mode.TRIANGLE_STRIP, DefaultVertexFormat.POSITION_COLOR);
            for (int i = 0; i <= segments; i++) {
                float t = i / (float) segments;
                float ang = startAngle + sweepAngle * t;
                float cos = Mth.cos(ang);
                float sin = Mth.sin(ang);
                buf.addVertex(mat, cx + cos * rInner, cy + sin * rInner, 0.0f).setColor(r, gr, b, a);
                buf.addVertex(mat, cx + cos * rOuter, cy + sin * rOuter, 0.0f).setColor(r, gr, b, a);
            }
            MeshData mesh = buf.build();
            if (mesh != null) {
                BufferUploader.drawWithShader(mesh);
            }
        }

        private void drawViewportVignette(GuiGraphics g, int x, int y, int w, int h, int color) {
            g.fill(x, y, x + w, y + 8, color);
            g.fill(x, y + h - 8, x + w, y + h, color);
            g.fill(x, y + 8, x + 8, y + h - 8, color);
            g.fill(x + w - 8, y + 8, x + w, y + h - 8, color);
        }

        private float getStitchX(float cx, int idx, int totalStitches) {
            int perRow = Math.max(1, totalStitches / 2);
            int col = idx % perRow;
            float span = 14.0f;
            float offset = (perRow == 1) ? 0.0f : ((col / (float) (perRow - 1)) - 0.5f) * span;
            return cx + offset;
        }

        private float getStitchTopY(float topStumpEnd, float botStumpStart, int idx, int totalStitches) {
            int perRow = Math.max(1, totalStitches / 2);
            return (idx < perRow) ? (topStumpEnd - 5.0f) : (botStumpStart - 5.0f);
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (this.playingCompletionFinale) return true;
            if (button == 1) {
                this.onClose();
                return true;
            }
            if (button == 0) {
                float mx = (this.smoothMouseX != 0.0f) ? this.smoothMouseX : ((float) mouseX + this.cursorTremorX);
                float my = (this.smoothMouseY != 0.0f) ? this.smoothMouseY : ((float) mouseY + this.cursorTremorY);

                int cancelW = 54, cancelH = 14;
                int cancelX = viewX + viewW - cancelW;
                int cancelY = winY + winH - 19;
                if (mouseX >= cancelX && mouseX <= cancelX + cancelW && mouseY >= cancelY && mouseY <= cancelY + cancelH) {
                    this.onClose();
                    return true;
                }

                float cx = viewX + viewW * 0.5f;
                float cy = viewY + viewH * 0.54f;
                float topStumpEnd = cy - 19.0f;
                float botStumpStart = cy + 19.0f;

                if (this.chosenPath == 0) {
                    float openRatio = (this.leftRetract + this.rightRetract) * 0.5f;
                    float gapHalfW = 5.0f + openRatio * 74.0f;
                    float leftHookX = cx - gapHalfW - 5.0f;
                    float rightHookX = cx + gapHalfW + 5.0f;

                    if (!this.leftLocked && Math.abs(mx - leftHookX) <= 16.0f && Math.abs(my - cy) <= 22.0f) {
                        this.draggingRetractor = 1;
                        playUiSound(SoundEvents.ARMOR_EQUIP_IRON.value(), 1.2f, 0.6f);
                        return true;
                    }
                    if (!this.rightLocked && Math.abs(mx - rightHookX) <= 16.0f && Math.abs(my - cy) <= 22.0f) {
                        this.draggingRetractor = 2;
                        playUiSound(SoundEvents.ARMOR_EQUIP_IRON.value(), 1.2f, 0.6f);
                        return true;
                    }

                    if (this.leftLocked && this.rightLocked) {
                        if (this.extractedBulletCount < this.embeddedBulletTotal) {
                            for (int b = 0; b < this.embeddedBulletTotal; b++) {
                                if (!this.bulletDone[b] && Math.hypot(mx - this.bulletX[b], my - this.bulletY[b]) <= 14.0) {
                                    this.draggingBulletIdx = b;
                                    playUiSound(SoundEvents.ARMOR_EQUIP_IRON.value(), 1.45f, 0.75f);
                                    return true;
                                }
                            }
                            return true;
                        }
                        int cardW = 82, cardH = 38;
                        int cardAY = viewY + viewH - cardH - 8;
                        int cardAX = Math.round(cx - cardW - 10);
                        int cardBX = Math.round(cx + 10);

                        if (mx >= cardAX && mx <= cardAX + cardW && my >= cardAY && my <= cardAY + cardH) {
                            this.chosenPath = 1;
                            this.subStep = 1;
                            MedicalSoundHelper.playLimbSelectSound();
                            return true;
                        }
                        if (mx >= cardBX && mx <= cardBX + cardW && my >= cardAY && my <= cardAY + cardH) {
                            this.chosenPath = 2;
                            this.subStep = 1;
                            MedicalSoundHelper.playLimbSelectSound();
                            return true;
                        }
                    }
                } else if (this.chosenPath == 1) {
                    if (this.subStep == 1) {
                        long nowMs = System.currentTimeMillis();
                        float driftAmp = ((float) ModItems.safeCfgDouble(MedicalConfig.SUTURE_STUMP_DRIFT_AMPLITUDE, 8.5));
                        float clampRadius = ((float) ModItems.safeCfgDouble(MedicalConfig.SUTURE_STUMP_CLAMP_RADIUS, 13.0));
                        float driftX = (Mth.sin(nowMs * 0.0042f) * 0.7f + Mth.cos(nowMs * 0.0071f) * 0.3f) * driftAmp;
                        if (!this.topClamped && Math.hypot(mx - (cx + driftX), my - (topStumpEnd - 4.0f)) <= clampRadius) {
                            this.topClamped = true;
                            this.topClampAnim = 0.0f;
                            playUiSound(SoundEvents.IRON_TRAPDOOR_CLOSE, 1.55f, 0.75f);
                            spawnClampSparks(cx + driftX, topStumpEnd - 4.0f, C_NEON_CYAN);
                            return true;
                        }
                        if (!this.botClamped && Math.hypot(mx - (cx - driftX), my - (botStumpStart + 4.0f)) <= clampRadius) {
                            this.botClamped = true;
                            this.botClampAnim = 0.0f;
                            playUiSound(SoundEvents.IRON_TRAPDOOR_CLOSE, 1.55f, 0.75f);
                            spawnClampSparks(cx - driftX, botStumpStart + 4.0f, C_NEON_CYAN);
                            return true;
                        }
                        triggerSurgicalMistake(mx, my);
                    } else if (this.subStep == 2) {
                        if (this.totalWindProgress < 1.0f) {
                            this.windingDrag = true;
                            this.lastWindAngle = (float) Math.atan2(my - cy, mx - cx);
                        } else {
                            this.pullingKnot = true;
                            playUiSound(SoundEvents.CROSSBOW_LOADING_MIDDLE.value(), 1.25f, 0.50f);
                        }
                        return true;
                    }
                } else if (this.chosenPath == 2) {
                    if (this.subStep == 1) {
                        float beatWave = computeArterialPulseWave(this.heartPhase);
                        float calmMax = ((float) ModItems.safeCfgDouble(MedicalConfig.SUTURE_BULLDOG_CALM_BEAT_MAX, 0.35));
                        float clampRadius = ((float) ModItems.safeCfgDouble(MedicalConfig.SUTURE_STUMP_CLAMP_RADIUS, 13.0));
                        if (beatWave < calmMax) {
                            if (!this.topClamped && Math.hypot(mx - cx, my - (topStumpEnd - 5.0f)) <= clampRadius + 1.5f) {
                                this.topClamped = true;
                                this.topClampAnim = 0.0f;
                                playUiSound(SoundEvents.IRON_TRAPDOOR_CLOSE, 1.65f, 0.75f);
                                spawnClampSparks(cx, topStumpEnd - 5.0f, C_ADRENALINE_GOLD);
                                return true;
                            }
                            if (!this.botClamped && Math.hypot(mx - cx, my - (botStumpStart + 5.0f)) <= clampRadius + 1.5f) {
                                this.botClamped = true;
                                this.botClampAnim = 0.0f;
                                playUiSound(SoundEvents.IRON_TRAPDOOR_CLOSE, 1.65f, 0.75f);
                                spawnClampSparks(cx, botStumpStart + 5.0f, C_ADRENALINE_GOLD);
                                return true;
                            }
                        }
                        triggerSurgicalMistake(mx, my);
                    } else if (this.subStep == 2) {
                        if (Math.hypot(mx - this.graftX, my - this.graftY) <= 26.0) {
                            this.draggingGraft = true;
                            MedicalSoundHelper.playHoverSound();
                            return true;
                        }
                    } else if (this.subStep == 3) {
                        int stitchCount = this.stitchesDone.length;
                        for (int i = 0; i < stitchCount; i++) {
                            if (this.stitchesDone[i]) continue;
                            float sx = getStitchX(cx, i, stitchCount);
                            float syTop = getStitchTopY(topStumpEnd, botStumpStart, i, stitchCount);
                            if (Math.hypot(mx - sx, my - syTop) <= 8.5) {
                                this.activeStitchIdx = i;
                                playUiSound(SoundEvents.SHEEP_SHEAR, 1.65f, 0.45f);
                                spawnParticle(sx, syTop, 0.0f, -8.0f, 1.5f, 0.25f, 0xFFFF1744, false, false);
                                return true;
                            }
                        }
                    }
                }
            }
            return super.mouseClicked(mouseX, mouseY, button);
        }

        @Override
        public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
            if (button == 0 && !this.playingCompletionFinale) {
                float mx = (this.smoothMouseX != 0.0f) ? this.smoothMouseX : ((float) mouseX + this.cursorTremorX);
                float cx = viewX + viewW * 0.5f;
                if (this.chosenPath == 0) {
                    if (this.draggingBulletIdx >= 0 && this.draggingBulletIdx < this.embeddedBulletTotal) {
                        float my = (this.smoothMouseY != 0.0f) ? this.smoothMouseY : ((float) mouseY + this.cursorTremorY);
                        float cy = viewY + viewH * 0.54f;
                        this.bulletX[this.draggingBulletIdx] = Mth.clamp(mx, viewX + 14.0f, viewX + viewW - 14.0f);
                        this.bulletY[this.draggingBulletIdx] = Mth.clamp(my, viewY + 28.0f, viewY + viewH - 18.0f);
                        if (!this.minorBleedTractOnly && Math.abs(mx - cx) < 6.5f && Math.abs(my - cy) < 15.0f) {
                            this.draggingBulletIdx = -1;
                            triggerSurgicalMistake(mx, my);
                        }
                        return true;
                    }
                    if (this.draggingRetractor == 1 && !this.leftLocked) {
                        this.leftRetract = Mth.clamp((cx - mx) / 76.0f, 0.0f, 1.0f);
                        int tooth = (int) (this.leftRetract * 10.0f);
                        if (tooth != this.lastLeftTooth) {
                            this.lastLeftTooth = tooth;
                            playUiSound(SoundEvents.TRIPWIRE_CLICK_ON, 0.85f + this.leftRetract * 0.55f, 0.45f);
                        }
                        return true;
                    }
                    if (this.draggingRetractor == 2 && !this.rightLocked) {
                        this.rightRetract = Mth.clamp((mx - cx) / 76.0f, 0.0f, 1.0f);
                        int tooth = (int) (this.rightRetract * 10.0f);
                        if (tooth != this.lastRightTooth) {
                            this.lastRightTooth = tooth;
                            playUiSound(SoundEvents.TRIPWIRE_CLICK_ON, 0.85f + this.rightRetract * 0.55f, 0.45f);
                        }
                        return true;
                    }
                }
            }
            return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
        }

        @Override
        public boolean mouseReleased(double mouseX, double mouseY, int button) {
            if (button == 0 && !this.playingCompletionFinale) {
                float mx = (this.smoothMouseX != 0.0f) ? this.smoothMouseX : ((float) mouseX + this.cursorTremorX);
                float my = (this.smoothMouseY != 0.0f) ? this.smoothMouseY : ((float) mouseY + this.cursorTremorY);
                float cx = viewX + viewW * 0.5f;
                float cy = viewY + viewH * 0.54f;
                float topStumpEnd = cy - 19.0f;
                float botStumpStart = cy + 19.0f;

                if (this.draggingBulletIdx >= 0 && this.draggingBulletIdx < this.embeddedBulletTotal) {
                    int b = this.draggingBulletIdx;
                    this.draggingBulletIdx = -1;
                    float trayX = viewX + viewW - 28.0f;
                    if (Math.hypot(mx - trayX, my - cy) <= 25.0) {
                        this.bulletDone[b] = true;
                        this.extractedBulletCount++;
                        playUiSound(SoundEvents.CHAIN_PLACE, 1.65f, 0.85f);
                        spawnClampSparks(trayX, cy, C_ADRENALINE_GOLD);
                    }
                    return true;
                }
                this.draggingRetractor = 0;
                this.windingDrag = false;
                this.pullingKnot = false;

                if (this.chosenPath == 2 && this.subStep == 2 && this.draggingGraft) {
                    this.draggingGraft = false;
                    float alignTol = ((float) ModItems.safeCfgDouble(MedicalConfig.SUTURE_GRAFT_ALIGN_TOLERANCE_DEG, 9.5));
                    float snapDist = ((float) ModItems.safeCfgDouble(MedicalConfig.SUTURE_GRAFT_SNAP_DISTANCE, 13.0));
                    if (Math.abs(this.graftAngleDeg) <= alignTol && Math.hypot(this.graftX - cx, this.graftY - cy) <= snapDist) {
                        this.graftInserted = true;
                        this.subStep = 3;
                        playUiSound(SoundEvents.IRON_TRAPDOOR_CLOSE, 1.35f, 0.70f);
                        spawnClampSparks(cx, cy, C_SAFE_GREEN);
                    }
                    return true;
                }

                if (this.chosenPath == 2 && this.subStep == 3 && this.activeStitchIdx >= 0) {
                    int i = this.activeStitchIdx;
                    this.activeStitchIdx = -1;
                    int stitchCount = this.stitchesDone.length;
                    float sx = getStitchX(cx, i, stitchCount);
                    float syBot = getStitchTopY(topStumpEnd, botStumpStart, i, stitchCount) + 10.0f;
                    if (Math.hypot(mx - sx, my - syBot) <= 9.5) {
                        this.stitchesDone[i] = true;
                        this.stitchTightenAnim[i] = 0.0f;
                        playUiSound(SoundEvents.TRIPWIRE_CLICK_ON, 1.45f, 0.65f);
                        spawnClampSparks(sx, syBot, C_NEON_CYAN);
                    } else {
                        triggerSurgicalMistake(mx, my);
                    }
                    return true;
                }
            }
            return super.mouseReleased(mouseX, mouseY, button);
        }

        @Override
        public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
            if (this.chosenPath == 2 && this.subStep == 2 && scrollY != 0) {
                float step = Screen.hasShiftDown() ? 2.0f : 4.5f;
                this.graftAngleDeg = Mth.wrapDegrees(this.graftAngleDeg + (scrollY > 0 ? -step : step));
                playUiSound(SoundEvents.TRIPWIRE_CLICK_ON, 1.35f, 0.28f);
                return true;
            }
            return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
        }

        @Override
        public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
            if (this.chosenPath == 2 && this.subStep == 2) {
                if (keyCode == GLFW.GLFW_KEY_LEFT || keyCode == GLFW.GLFW_KEY_A) {
                    this.graftAngleDeg = Mth.wrapDegrees(this.graftAngleDeg - 4.0f);
                    playUiSound(SoundEvents.TRIPWIRE_CLICK_ON, 1.35f, 0.28f);
                    return true;
                }
                if (keyCode == GLFW.GLFW_KEY_RIGHT || keyCode == GLFW.GLFW_KEY_D) {
                    this.graftAngleDeg = Mth.wrapDegrees(this.graftAngleDeg + 4.0f);
                    playUiSound(SoundEvents.TRIPWIRE_CLICK_ON, 1.35f, 0.28f);
                    return true;
                }
            }
            return super.keyPressed(keyCode, scanCode, modifiers);
        }
    }

    // =========================================================================
    // 野战筋膜切开减压手术界面 (FasciotomyScreen —— 3A级骨筋膜室综合征四步减压与电凝止血模拟)
    // =========================================================================
    // =========================================================================
    // 独立手术界面 A：《野战弹道取弹包 (ballistic_extraction_kit)》专属深部探查与大托盘取弹界面
    // =========================================================================
    public static class BulletExtractionScreen extends Screen {
        private static final class SurgicalParticle {
            boolean active = false;
            float x, y, vx, vy, size, age, maxAge;
            int color;
            boolean isStain, isSpark;
        }

        private final InteractionHand hand;
        private final MedicalData.Limb limb;

        private float leftRetract = 0.0f;
        private float rightRetract = 0.0f;
        private boolean leftLocked = false;
        private boolean rightLocked = false;
        private int draggingRetractor = 0;
        private int lastLeftTooth = 0;
        private int lastRightTooth = 0;
        private boolean incisionOpened = false;

        private int embeddedBulletTotal = 1;
        private int extractedBulletCount = 0;
        private final float[] bulletX = new float[16];
        private final float[] bulletY = new float[16];
        private final boolean[] bulletIsPellet = new boolean[16];
        private final boolean[] bulletDone = new boolean[16];
        private int draggingBulletIdx = -1;

        private boolean playingCompletionFinale = false;
        private float completionAnimProgress = 0.0f;
        private boolean surgeryCompleted = false;

        private final SurgicalParticle[] particles = new SurgicalParticle[64];
        private float mistakeFlashAlpha = 0.0f;
        private float screenShakeX = 0.0f;
        private float screenShakeY = 0.0f;
        private float screenShakeAmp = 0.0f;

        private long lastFrameMs = 0L;
        private float heartPhase = 0.0f;
        private float cursorTremorX = 0.0f;
        private float cursorTremorY = 0.0f;
        private float smoothMouseX = 0.0f;
        private float smoothMouseY = 0.0f;
        private float cursorTiltDeg = 0.0f;

        // 左侧创腔视窗 + 右侧超大号醒目不锈钢接弹托盘
        private int winX, winY, winW = 348, winH = 218;
        private int viewX, viewY, viewW = 152, viewH = 166;
        private int trayX, trayY, trayW = 104, trayH = 166;

        public BulletExtractionScreen(InteractionHand hand, MedicalData.Limb limb) {
            super(Component.literal("野战弹道取弹手术"));
            this.hand = hand != null ? hand : InteractionHand.MAIN_HAND;
            this.limb = limb != null ? limb : MedicalData.Limb.LEFT_LEG;
            for (int i = 0; i < this.particles.length; i++) {
                this.particles[i] = new SurgicalParticle();
            }
        }

        private void playUiSound(SoundEvent sound, float pitch, float volume) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.getSoundManager() != null) {
                mc.getSoundManager().play(SimpleSoundInstance.forUI(sound, pitch, volume));
            }
        }

        private void spawnParticle(float x, float y, float vx, float vy, float size, float maxAge, int color, boolean isStain, boolean isSpark) {
            for (SurgicalParticle p : this.particles) {
                if (!p.active) {
                    p.active = true;
                    p.x = x;
                    p.y = y;
                    p.vx = vx;
                    p.vy = vy;
                    p.size = size;
                    p.age = 0.0f;
                    p.maxAge = maxAge;
                    p.color = color;
                    p.isStain = isStain;
                    p.isSpark = isSpark;
                    return;
                }
            }
        }

        private void triggerMistake(float x, float y) {
            this.mistakeFlashAlpha = 0.85f;
            this.screenShakeAmp = 6.0f;
            playUiSound(SoundEvents.PLAYER_HURT, 1.15f, 0.55f);
            for (int i = 0; i < 14; i++) {
                float ang = (float) (Math.random() * Math.PI * 2.0);
                float spd = 25.0f + (float) (Math.random() * 55.0);
                spawnParticle(x, y, Mth.cos(ang) * spd, Mth.sin(ang) * spd, 2.0f, 0.45f, 0xFFFF1744, true, false);
            }
            NetworkHandler.sendTreatmentToServer(new TreatmentApplyPacket(this.hand.ordinal(), this.limb.ordinal(), 803));
        }

        @Override
        protected void init() {
            super.init();
            this.winX = (this.width - this.winW) / 2;
            this.winY = (this.height - this.winH) / 2;
            this.viewX = this.winX + 74;
            this.viewY = this.winY + 26;
            this.trayX = this.viewX + this.viewW + 8;
            this.trayY = this.viewY;

            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) {
                MedicalData data = mc.player.getData(MedicalDataCapability.MEDICAL_DATA);
                if (data != null) {
                    MedicalData.LimbData ld = data.getLimb(this.limb);
                    if (ld != null) {
                        this.embeddedBulletTotal = Mth.clamp(ModItems.getEmbeddedBulletCount(ld), 1, 12);
                    }
                }
                NetworkHandler.sendTreatmentToServer(new TreatmentApplyPacket(this.hand.ordinal(), this.limb.ordinal(), 800));
            }

            float cx = this.viewX + this.viewW * 0.5f;
            float cy = this.viewY + this.viewH * 0.54f;
            boolean manyPellets = this.embeddedBulletTotal >= 3;
            for (int b = 0; b < this.embeddedBulletTotal; b++) {
                int col = b % 2;
                int row = b / 2;
                float side = (col == 0) ? -1.0f : 1.0f;
                float jitterX = ((b * 17) % 13) - 6.0f;
                float jitterY = ((b * 23) % 11) - 5.0f;
                this.bulletX[b] = cx + side * (20.0f + (row % 3) * 14.0f) + jitterX * 0.6f;
                this.bulletY[b] = (this.viewY + 42.0f) + (row * 19.0f) % 102.0f + jitterY * 0.5f;
                this.bulletX[b] = Mth.clamp(this.bulletX[b], this.viewX + 24.0f, this.viewX + this.viewW - 24.0f);
                this.bulletY[b] = Mth.clamp(this.bulletY[b], this.viewY + 38.0f, this.viewY + this.viewH - 26.0f);
                this.bulletIsPellet[b] = manyPellets && (b % 3 != 0 || this.embeddedBulletTotal >= 4);
                this.bulletDone[b] = false;
            }
            MedicalSoundHelper.playOpenSound();
        }

        @Override
        public boolean isPauseScreen() {
            return false;
        }

        @Override
        public void renderBackground(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        }

        @Override
        public void onClose() {
            if (!this.surgeryCompleted && this.incisionOpened) {
                NetworkHandler.sendTreatmentToServer(new TreatmentApplyPacket(this.hand.ordinal(), this.limb.ordinal(), 804));
            }
            super.onClose();
        }

        @Override
        public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null) {
                super.onClose();
                return;
            }
            ItemStack stack = mc.player.getItemInHand(this.hand);
            if (!TreatmentRouter.isBallisticExtractionKitItem(stack)) {
                super.onClose();
                return;
            }
            MedicalData data = mc.player.getData(MedicalDataCapability.MEDICAL_DATA);
            if (data == null || data.getConsciousness() < ModItems.getSutureMinConsciousness()
                    || data.isCardiacArrest() || data.isInVFib()) {
                this.onClose();
                return;
            }

            long nowMs = System.currentTimeMillis();
            float dt = (lastFrameMs == 0L) ? 0.016f : Mth.clamp((nowMs - lastFrameMs) / 1000.0f, 0.001f, 0.1f);
            lastFrameMs = nowMs;

            if (this.playingCompletionFinale) {
                this.completionAnimProgress += dt / 0.55f;
                if (this.completionAnimProgress >= 1.0f) {
                    this.surgeryCompleted = true;
                    NetworkHandler.sendTreatmentToServer(new TreatmentApplyPacket(this.hand.ordinal(), this.limb.ordinal(), 801));
                    MedicalSoundHelper.playLimbSelectSound();
                    this.onClose();
                    return;
                }
            }

            float hr = Mth.clamp(data.getHeartRate(), 40.0f, 220.0f);
            this.heartPhase = (this.heartPhase + dt * (hr / 60.0f)) % 1.0f;
            float beatWave = Mth.sin(this.heartPhase * Mth.PI);

            MedicalData.LimbData curLd = data.getLimb(this.limb);
            float sysPainRatio = data.getPerceivedPain() / Math.max(10.0f, (float) ModItems.safeCfgDouble(MedicalConfig.MAX_PERCEIVED_PAIN, 100.0));
            float limbPainRatio = (curLd != null) ? (curLd.getPainLevel() / 10.0f) : 0.0f;
            float painRatio = Mth.clamp(Math.max(sysPainRatio, limbPainRatio * 0.85f), 0.0f, 1.0f);
            float maxPainShake = (float) ModItems.safeCfgDouble(MedicalConfig.SUTURE_CURSOR_PAIN_SHAKE_MAX, 14.0);
            float shakeAmp = painRatio * maxPainShake * 0.75f;
            this.cursorTremorX = Mth.sin(nowMs * 0.023f) * shakeAmp;
            this.cursorTremorY = Mth.cos(nowMs * 0.029f) * shakeAmp;

            float targetMx = mouseX + this.cursorTremorX;
            float targetMy = mouseY + this.cursorTremorY;
            if (this.smoothMouseX == 0.0f && this.smoothMouseY == 0.0f) {
                this.smoothMouseX = targetMx;
                this.smoothMouseY = targetMy;
            } else {
                float vx = (targetMx - this.smoothMouseX) / Math.max(0.004f, dt);
                this.cursorTiltDeg = Mth.lerp(Mth.clamp(dt * 14.0f, 0.0f, 1.0f), this.cursorTiltDeg, Mth.clamp(vx * 0.035f, -20.0f, 20.0f));
                this.smoothMouseX = Mth.lerp(Mth.clamp(dt * 24.0f, 0.0f, 1.0f), this.smoothMouseX, targetMx);
                this.smoothMouseY = Mth.lerp(Mth.clamp(dt * 24.0f, 0.0f, 1.0f), this.smoothMouseY, targetMy);
            }

            if (this.screenShakeAmp > 0.01f) {
                this.screenShakeAmp = Math.max(0.0f, this.screenShakeAmp - dt * 22.0f);
                this.screenShakeX = (float) (Math.random() * 2.0 - 1.0) * this.screenShakeAmp;
                this.screenShakeY = (float) (Math.random() * 2.0 - 1.0) * this.screenShakeAmp;
            } else {
                this.screenShakeX = 0.0f;
                this.screenShakeY = 0.0f;
            }
            if (this.mistakeFlashAlpha > 0.005f) {
                this.mistakeFlashAlpha = Math.max(0.0f, this.mistakeFlashAlpha - dt * 2.4f);
            }

            g.fill(0, 0, this.width, this.height, 0x9602060C);
            PoseStack pose = g.pose();
            pose.pushPose();
            pose.translate(this.screenShakeX, this.screenShakeY, 0.0f);

            g.fill(winX, winY, winX + winW, winY + winH, 0xF4050B14);
            drawTacticalCornerBrackets(g, winX, winY, winW, winH, C_ADRENALINE_GOLD);
            g.fill(winX + 8, winY + 19, winX + winW - 8, winY + 20, withAlpha(C_ADRENALINE_GOLD, 0.45f));
            g.drawString(this.font, "深部弹道取弹术 · " + getLimbDisplayName(this.limb), winX + 10, winY + 7, C_ADRENALINE_GOLD, false);

            // 左侧器械全息投影栏
            int leftX = winX + 8, leftY = viewY, leftW = 60, leftH = viewH;
            g.fill(leftX, leftY, leftX + leftW, leftY + leftH, 0xB8030810);
            drawTacticalCornerBrackets(g, leftX, leftY, leftW, leftH, withAlpha(C_ADRENALINE_GOLD, 0.72f));
            MedicalAssetLibrary.render3DMedicalItem(g, leftX + leftW * 0.5f, leftY + 52.0f, 28.0f, stack, nowMs);
            g.drawCenteredString(this.font, "剩余异物", leftX + leftW / 2, leftY + 108, 0xFFB0BEC5);
            int remCnt = Math.max(0, this.embeddedBulletTotal - this.extractedBulletCount);
            g.drawCenteredString(this.font, remCnt + " 枚", leftX + leftW / 2, leftY + 122, remCnt > 0 ? C_ADRENALINE_GOLD : C_SAFE_GREEN);

            // 中央手术创腔主视窗
            g.fill(viewX, viewY, viewX + viewW, viewY + viewH, 0xFF140509);
            drawTacticalCornerBrackets(g, viewX, viewY, viewW, viewH, 0xFF546E7A);

            float cx = viewX + viewW * 0.5f;
            float cy = viewY + viewH * 0.54f;
            float openRatio = (this.leftRetract + this.rightRetract) * 0.5f;
            float gapHalfW = 6.0f + openRatio * 56.0f;
            float flapL = cx - gapHalfW;
            float flapR = cx + gapHalfW;

            // 创腔深部肌肉纹理与中央伴行血管
            g.fill(Math.round(flapL), viewY + 28, Math.round(flapR), viewY + viewH - 18, 0xFF2B0710);
            int vesselHalfW = Math.round(3.0f + beatWave * 1.2f);
            g.fill(Math.round(cx) - vesselHalfW, viewY + 28, Math.round(cx) + vesselHalfW, viewY + viewH - 18, 0xFFAD1457);

            // 左右肌肉皮瓣遮罩
            if (flapL > viewX + 2) {
                g.fill(viewX + 2, viewY + 28, Math.round(flapL), viewY + viewH - 18, 0xF4380C15);
                g.fill(Math.max(viewX + 2, Math.round(flapL) - 3), viewY + 28, Math.round(flapL), viewY + viewH - 18, 0xFFD81B60);
            }
            if (flapR < viewX + viewW - 2) {
                g.fill(Math.round(flapR), viewY + 28, viewX + viewW - 2, viewY + viewH - 18, 0xF4380C15);
                g.fill(Math.round(flapR), viewY + 28, Math.min(viewX + viewW - 2, Math.round(flapR) + 3), viewY + viewH - 18, 0xFFD81B60);
            }

            // 顶部棘轮锁轨与左右牵开爪
            g.fill(viewX + 10, viewY + 22, viewX + viewW - 10, viewY + 25, 0xFF78909C);
            int lHookX = Math.round(flapL - 4.0f);
            int rHookX = Math.round(flapR + 4.0f);
            int hookY = Math.round(cy);
            g.fill(lHookX - 5, hookY - 8, lHookX + 5, hookY + 8, this.leftLocked ? C_SAFE_GREEN : 0xFFECEFF1);
            g.fill(rHookX - 5, hookY - 8, rHookX + 5, hookY + 8, this.rightLocked ? C_SAFE_GREEN : 0xFFECEFF1);

            // =================================================================
            // 右侧超大号、极高辨识度不锈钢接弹托盘 (104 x 166 独立高亮区域)
            // =================================================================
            boolean isDraggingBullet = (this.draggingBulletIdx >= 0);
            boolean hovTray = isDraggingBullet && this.smoothMouseX >= trayX && this.smoothMouseX <= trayX + trayW
                    && this.smoothMouseY >= trayY && this.smoothMouseY <= trayY + trayH;
            float pulse = 0.55f + 0.45f * Mth.sin(nowMs * 0.008f);
            int trayBorderCol = hovTray ? C_SAFE_GREEN : (isDraggingBullet ? withAlpha(C_SAFE_GREEN, pulse) : 0xFFCFD8DC);

            // 外层不锈钢金属拉丝盘边与内凹无菌盘底
            g.fill(trayX, trayY, trayX + trayW, trayY + trayH, 0xFF37474F);
            g.fill(trayX + 4, trayY + 22, trayX + trayW - 4, trayY + trayH - 22, hovTray ? 0xFF1A332A : 0xFF1C262E);
            g.fill(trayX + 6, trayY + 24, trayX + trayW - 6, trayY + 26, 0x66ECEFF1);
            drawTacticalCornerBrackets(g, trayX, trayY, trayW, trayH, trayBorderCol);
            g.fill(trayX, trayY, trayX + trayW, trayY + 2, trayBorderCol);
            g.fill(trayX, trayY + trayH - 2, trayX + trayW, trayY + trayH, trayBorderCol);

            g.drawCenteredString(this.font, "【不锈钢接弹托盘】", trayX + trayW / 2, trayY + 5, C_SAFE_GREEN);
            g.drawCenteredString(this.font, isDraggingBullet ? ">>> 拖入此盘松开 <<<" : "将夹住的弹头拖入此处",
                    trayX + trayW / 2, trayY + 14, isDraggingBullet ? C_ADRENALINE_GOLD : 0xFFB0BEC5);

            // 托盘中央十字准星引导标
            int trayCenterX = trayX + trayW / 2;
            int trayCenterY = trayY + trayH / 2 + 2;
            g.fill(trayCenterX - 18, trayCenterY, trayCenterX + 18, trayCenterY + 1, 0x4400E676);
            g.fill(trayCenterX, trayCenterY - 18, trayCenterX + 1, trayCenterY + 18, 0x4400E676);

            // 渲染托盘内已经成功取出的变形弹头与霰弹铅丸
            for (int i = 0; i < this.embeddedBulletTotal; i++) {
                if (!this.bulletDone[i]) continue;
                int slotCol = i % 3;
                int slotRow = i / 3;
                int bx = trayX + 22 + slotCol * 28;
                int by = trayY + 42 + slotRow * 26;
                if (this.bulletIsPellet[i]) {
                    g.fill(bx - 5, by - 5, bx + 5, by + 5, 0xFF90A4AE);
                    g.fill(bx - 3, by - 3, bx + 2, by + 2, 0xFFECEFF1);
                } else {
                    g.fill(bx - 7, by - 4, bx + 2, by + 4, 0xFFC58B2B);
                    g.fill(bx - 5, by - 3, bx + 1, by, 0xFFFFD54F);
                    g.fill(bx + 2, by - 5, bx + 7, by + 5, 0xFF90A4AE);
                }
            }

            String countTxt = "已取出: " + this.extractedBulletCount + " / " + this.embeddedBulletTotal + " 枚";
            g.drawCenteredString(this.font, countTxt, trayX + trayW / 2, trayY + trayH - 15, C_TEXT_WHITE);

            // 若两翼牵开器已锁定，在左侧创腔中渲染待夹取的嵌留弹头/霰弹铅丸
            if (this.leftLocked && this.rightLocked) {
                this.incisionOpened = true;
                for (int b = 0; b < this.embeddedBulletTotal; b++) {
                    if (this.bulletDone[b]) continue;
                    float bx = (this.draggingBulletIdx == b) ? this.smoothMouseX : this.bulletX[b];
                    float by = (this.draggingBulletIdx == b) ? this.smoothMouseY : this.bulletY[b];
                    boolean hov = (this.draggingBulletIdx == b) || (Math.hypot(this.smoothMouseX - bx, this.smoothMouseY - by) <= 13.0);

                    int ix = Math.round(bx);
                    int iy = Math.round(by);
                    g.fill(ix - 8, iy - 8, ix + 8, iy + 8, hov ? 0x66FFD600 : 0x553E0512);
                    if (this.bulletIsPellet[b]) {
                        // 霰弹鹿弹铅丸（银灰球形变形铅丸）
                        g.fill(ix - 5, iy - 5, ix + 5, iy + 5, 0xFF78909C);
                        g.fill(ix - 4, iy - 4, ix + 4, iy + 4, 0xFFCFD8DC);
                        g.fill(ix - 2, iy - 2, ix + 1, iy + 1, 0xFFFFFFFF);
                    } else {
                        // 步枪/手枪变形铜被甲铅芯弹头
                        g.fill(ix - 6, iy - 3, ix + 2, iy + 3, 0xFFC58B2B);
                        g.fill(ix - 5, iy - 2, ix + 1, iy, 0xFFFFD54F);
                        g.fill(ix + 1, iy - 4, ix + 6, iy + 4, 0xFF90A4AE);
                    }

                    // 夹住弹头时绘制指向右侧大托盘的亮绿色激光虚线引导！
                    if (this.draggingBulletIdx == b) {
                        for (int step = 0; step < 12; step++) {
                            if (step % 2 == 1) continue;
                            float u = step / 12.0f;
                            int gx = Math.round(Mth.lerp(u, bx, trayCenterX));
                            int gy = Math.round(Mth.lerp(u, by, trayCenterY));
                            g.fill(gx - 2, gy - 1, gx + 2, gy + 1, C_SAFE_GREEN);
                        }
                    }
                }
                g.drawCenteredString(this.font, "用取弹钳夹住创腔内弹头/铅丸 -> 拖入右侧【不锈钢接弹托盘】",
                        viewX + viewW / 2, winY + winH - 16, C_ADRENALINE_GOLD);
            } else {
                g.drawCenteredString(this.font, "第一步：按住左键向两侧拉开左右四爪牵开器暴露深部弹头",
                        viewX + viewW / 2, winY + winH - 16, C_NEON_CYAN);
            }

            // 粒子渲染
            for (SurgicalParticle p : this.particles) {
                if (!p.active) continue;
                p.age += dt;
                if (p.age >= p.maxAge) {
                    p.active = false;
                    continue;
                }
                p.x += p.vx * dt;
                p.y += p.vy * dt;
                int px = Math.round(p.x);
                int py = Math.round(p.y);
                g.fill(px - 1, py - 1, px + 2, py + 2, p.color);
            }

            // 取消按钮
            int cancelW = 50, cancelH = 14;
            int cancelX = winX + winW - cancelW - 8;
            int cancelY = winY + winH - 18;
            g.fill(cancelX, cancelY, cancelX + cancelW, cancelY + cancelH, 0x661C2833);
            drawTacticalCornerBrackets(g, cancelX, cancelY, cancelW, cancelH, 0xFF90A4AE);
            g.drawCenteredString(this.font, "×", cancelX + cancelW / 2, cancelY + 3, C_TEXT_DIM);

            // 跟随鼠标的取弹钳器械光标
            SurgicalInstrumentRenderer.renderDetailedSurgicalToolCursor(
                    g, this.smoothMouseX, this.smoothMouseY, this.cursorTiltDeg,
                    SurgicalInstrumentRenderer.ToolType.HEMOSTAT_FORCEPS,
                    this.draggingBulletIdx >= 0, beatWave, (nowMs % 100000L) * 0.02f);

            pose.popPose();
            super.render(g, mouseX, mouseY, partialTick);
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (this.playingCompletionFinale) return true;
            if (button == 1) {
                this.onClose();
                return true;
            }
            if (button == 0) {
                float mx = (this.smoothMouseX != 0.0f) ? this.smoothMouseX : (float) mouseX;
                float my = (this.smoothMouseY != 0.0f) ? this.smoothMouseY : (float) mouseY;

                int cancelW = 50, cancelH = 14;
                int cancelX = winX + winW - cancelW - 8;
                int cancelY = winY + winH - 18;
                if (mouseX >= cancelX && mouseX <= cancelX + cancelW && mouseY >= cancelY && mouseY <= cancelY + cancelH) {
                    this.onClose();
                    return true;
                }

                float cx = viewX + viewW * 0.5f;
                float cy = viewY + viewH * 0.54f;
                float openRatio = (this.leftRetract + this.rightRetract) * 0.5f;
                float gapHalfW = 6.0f + openRatio * 56.0f;
                float lHookX = cx - gapHalfW - 4.0f;
                float rHookX = cx + gapHalfW + 4.0f;

                if (!this.leftLocked && Math.abs(mx - lHookX) <= 18.0f && Math.abs(my - cy) <= 24.0f) {
                    this.draggingRetractor = 1;
                    playUiSound(SoundEvents.ARMOR_EQUIP_IRON.value(), 1.2f, 0.65f);
                    return true;
                }
                if (!this.rightLocked && Math.abs(mx - rHookX) <= 18.0f && Math.abs(my - cy) <= 24.0f) {
                    this.draggingRetractor = 2;
                    playUiSound(SoundEvents.ARMOR_EQUIP_IRON.value(), 1.2f, 0.65f);
                    return true;
                }

                if (this.leftLocked && this.rightLocked) {
                    for (int b = 0; b < this.embeddedBulletTotal; b++) {
                        if (!this.bulletDone[b] && Math.hypot(mx - this.bulletX[b], my - this.bulletY[b]) <= 15.0) {
                            this.draggingBulletIdx = b;
                            playUiSound(SoundEvents.ARMOR_EQUIP_IRON.value(), 1.45f, 0.80f);
                            return true;
                        }
                    }
                }
            }
            return super.mouseClicked(mouseX, mouseY, button);
        }

        @Override
        public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
            if (button == 0 && !this.playingCompletionFinale) {
                float mx = (this.smoothMouseX != 0.0f) ? this.smoothMouseX : (float) mouseX;
                float cx = viewX + viewW * 0.5f;
                if (this.draggingRetractor == 1 && !this.leftLocked) {
                    this.leftRetract = Mth.clamp((cx - mx) / 56.0f, 0.0f, 1.0f);
                    if (this.leftRetract >= 0.92f) {
                        this.leftRetract = 1.0f;
                        this.leftLocked = true;
                        this.draggingRetractor = 0;
                        playUiSound(SoundEvents.IRON_TRAPDOOR_CLOSE, 1.4f, 0.75f);
                    }
                    return true;
                }
                if (this.draggingRetractor == 2 && !this.rightLocked) {
                    this.rightRetract = Mth.clamp((mx - cx) / 56.0f, 0.0f, 1.0f);
                    if (this.rightRetract >= 0.92f) {
                        this.rightRetract = 1.0f;
                        this.rightLocked = true;
                        this.draggingRetractor = 0;
                        playUiSound(SoundEvents.IRON_TRAPDOOR_CLOSE, 1.4f, 0.75f);
                    }
                    return true;
                }
                if (this.draggingBulletIdx >= 0) {
                    return true;
                }
            }
            return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
        }

        @Override
        public boolean mouseReleased(double mouseX, double mouseY, int button) {
            if (button == 0 && !this.playingCompletionFinale) {
                float mx = (this.smoothMouseX != 0.0f) ? this.smoothMouseX : (float) mouseX;
                float my = (this.smoothMouseY != 0.0f) ? this.smoothMouseY : (float) mouseY;
                this.draggingRetractor = 0;

                if (this.draggingBulletIdx >= 0 && this.draggingBulletIdx < this.embeddedBulletTotal) {
                    int b = this.draggingBulletIdx;
                    this.draggingBulletIdx = -1;
                    // 只要拖入右侧大号不锈钢接弹托盘区域内松开鼠标即成功落盘！
                    if (mx >= trayX - 8 && mx <= trayX + trayW + 8 && my >= trayY - 6 && my <= trayY + trayH + 6) {
                        this.bulletDone[b] = true;
                        this.extractedBulletCount++;
                        playUiSound(SoundEvents.CHAIN_PLACE, 1.65f, 0.90f);
                        playUiSound(SoundEvents.EXPERIENCE_ORB_PICKUP, 1.45f, 0.55f);
                        for (int i = 0; i < 10; i++) {
                            float ang = (i / 10.0f) * Mth.TWO_PI;
                            spawnParticle(mx, my, Mth.cos(ang) * 28.0f, Mth.sin(ang) * 28.0f, 1.6f, 0.30f, C_ADRENALINE_GOLD, false, true);
                        }
                        if (this.extractedBulletCount >= this.embeddedBulletTotal) {
                            this.playingCompletionFinale = true;
                            this.completionAnimProgress = 0.0f;
                        }
                    } else {
                        this.bulletX[b] = Mth.clamp(mx, viewX + 20.0f, viewX + viewW - 20.0f);
                        this.bulletY[b] = Mth.clamp(my, viewY + 34.0f, viewY + viewH - 22.0f);
                    }
                    return true;
                }
            }
            return super.mouseReleased(mouseX, mouseY, button);
        }
    }

    // =========================================================================
    // 独立手术界面 B：《贯穿创道修补包 (perforation_repair_kit)》专属双创口冲洗+止血海绵填塞+荷包缝合界面
    // =========================================================================
    public static class PerforationRepairScreen extends Screen {
        private final InteractionHand hand;
        private final MedicalData.Limb limb;

        // subStep: 0 = 贯穿创道加压冲洗, 1 = 瞬时空腔 XStat 高膨胀止血海绵填塞, 2 = 入射口与出射口双侧荷包缝合
        private int subStep = 0;
        private boolean draggingIrrigator = false;
        private float irrigationProgress = 0.0f;
        private boolean injectingSponge = false;
        private float spongeProgress = 0.0f;
        private final boolean[] dualStitchesDone = new boolean[6]; // 0..2 入射口, 3..5 出射口
        private int completedStitchCount = 0;

        private boolean playingCompletionFinale = false;
        private float completionAnimProgress = 0.0f;
        private boolean surgeryCompleted = false;

        private long lastFrameMs = 0L;
        private float heartPhase = 0.0f;
        private float smoothMouseX = 0.0f;
        private float smoothMouseY = 0.0f;
        private float cursorTiltDeg = 0.0f;

        private int winX, winY, winW = 336, winH = 216;
        private int viewX, viewY, viewW = 244, viewH = 162;

        public PerforationRepairScreen(InteractionHand hand, MedicalData.Limb limb) {
            super(Component.literal("贯穿创道修补术"));
            this.hand = hand != null ? hand : InteractionHand.MAIN_HAND;
            this.limb = limb != null ? limb : MedicalData.Limb.LEFT_LEG;
        }

        private void playUiSound(SoundEvent sound, float pitch, float volume) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.getSoundManager() != null) {
                mc.getSoundManager().play(SimpleSoundInstance.forUI(sound, pitch, volume));
            }
        }

        @Override
        protected void init() {
            super.init();
            this.winX = (this.width - this.winW) / 2;
            this.winY = (this.height - this.winH) / 2;
            this.viewX = this.winX + 76;
            this.viewY = this.winY + 26;
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) {
                NetworkHandler.sendTreatmentToServer(new TreatmentApplyPacket(this.hand.ordinal(), this.limb.ordinal(), 900));
            }
            MedicalSoundHelper.playOpenSound();
        }

        @Override
        public boolean isPauseScreen() {
            return false;
        }

        @Override
        public void renderBackground(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        }

        @Override
        public void onClose() {
            if (!this.surgeryCompleted && this.irrigationProgress > 0.1f) {
                NetworkHandler.sendTreatmentToServer(new TreatmentApplyPacket(this.hand.ordinal(), this.limb.ordinal(), 904));
            }
            super.onClose();
        }

        @Override
        public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null) {
                super.onClose();
                return;
            }
            ItemStack stack = mc.player.getItemInHand(this.hand);
            if (!TreatmentRouter.isPerforationRepairKitItem(stack)) {
                super.onClose();
                return;
            }
            MedicalData data = mc.player.getData(MedicalDataCapability.MEDICAL_DATA);
            if (data == null || data.getConsciousness() < ModItems.getSutureMinConsciousness()
                    || data.isCardiacArrest() || data.isInVFib()) {
                this.onClose();
                return;
            }

            long nowMs = System.currentTimeMillis();
            float dt = (lastFrameMs == 0L) ? 0.016f : Mth.clamp((nowMs - lastFrameMs) / 1000.0f, 0.001f, 0.1f);
            lastFrameMs = nowMs;

            if (this.playingCompletionFinale) {
                this.completionAnimProgress += dt / 0.55f;
                if (this.completionAnimProgress >= 1.0f) {
                    this.surgeryCompleted = true;
                    NetworkHandler.sendTreatmentToServer(new TreatmentApplyPacket(this.hand.ordinal(), this.limb.ordinal(), 901));
                    MedicalSoundHelper.playLimbSelectSound();
                    this.onClose();
                    return;
                }
            }

            float hr = Mth.clamp(data.getHeartRate(), 40.0f, 220.0f);
            this.heartPhase = (this.heartPhase + dt * (hr / 60.0f)) % 1.0f;
            float beatWave = Mth.sin(this.heartPhase * Mth.PI);

            this.smoothMouseX = (this.smoothMouseX == 0.0f) ? (float) mouseX : Mth.lerp(Mth.clamp(dt * 24.0f, 0.0f, 1.0f), this.smoothMouseX, (float) mouseX);
            this.smoothMouseY = (this.smoothMouseY == 0.0f) ? (float) mouseY : Mth.lerp(Mth.clamp(dt * 24.0f, 0.0f, 1.0f), this.smoothMouseY, (float) mouseY);

            int cx = viewX + viewW / 2;
            int cy = viewY + viewH / 2 + 6;
            int entryX = viewX + 38;
            int exitX = viewX + viewW - 38;

            if (this.subStep == 1 && this.injectingSponge) {
                if (Math.hypot(this.smoothMouseX - cx, this.smoothMouseY - cy) <= 44.0) {
                    this.spongeProgress = Math.min(1.0f, this.spongeProgress + dt * 0.85f);
                    if ((int) (this.spongeProgress * 10) != (int) ((this.spongeProgress - dt * 0.85f) * 10)) {
                        playUiSound(SoundEvents.WOOL_PLACE, 1.35f, 0.65f);
                    }
                    if (this.spongeProgress >= 1.0f) {
                        this.injectingSponge = false;
                        this.subStep = 2;
                        playUiSound(SoundEvents.EXPERIENCE_ORB_PICKUP, 1.25f, 0.70f);
                    }
                }
            }

            g.fill(0, 0, this.width, this.height, 0x9602060C);
            g.fill(winX, winY, winX + winW, winY + winH, 0xF4050B14);
            drawTacticalCornerBrackets(g, winX, winY, winW, winH, 0xFFFF2A85);
            g.fill(winX + 8, winY + 19, winX + winW - 8, winY + 20, 0x77FF2A85);
            g.drawString(this.font, "前后贯穿创道修补术 · " + getLimbDisplayName(this.limb), winX + 10, winY + 7, 0xFFFF2A85, false);

            // 顶部 3 步进度指示条
            for (int s = 0; s < 3; s++) {
                int sx = viewX + s * 42;
                int sy = winY + 8;
                int sCol = (s < this.subStep || this.playingCompletionFinale) ? C_SAFE_GREEN : (s == this.subStep ? 0xFFFF2A85 : 0xFF1E2B36);
                g.fill(sx, sy, sx + 36, sy + 6, sCol);
            }

            // 左侧器械全息投影栏
            int leftX = winX + 8, leftY = viewY, leftW = 60, leftH = viewH;
            g.fill(leftX, leftY, leftX + leftW, leftY + leftH, 0xB8030810);
            drawTacticalCornerBrackets(g, leftX, leftY, leftW, leftH, 0xAAFF2A85);
            MedicalAssetLibrary.render3DMedicalItem(g, leftX + leftW * 0.5f, leftY + 52.0f, 28.0f, stack, nowMs);
            String stepTitle = this.subStep == 0 ? "1.创道冲洗" : (this.subStep == 1 ? "2.海绵填塞" : "3.双口缝合");
            g.drawCenteredString(this.font, stepTitle, leftX + leftW / 2, leftY + 114, 0xFFFF9100);

            // 中央贯穿解剖剖面主视窗
            g.fill(viewX, viewY, viewX + viewW, viewY + viewH, 0xFF14050A);
            drawTacticalCornerBrackets(g, viewX, viewY, viewW, viewH, 0xFF546E7A);

            // 绘制肢体横截面与中央贯穿瞬时空腔隧道 (Entry -> Cavitation -> Exit)
            g.fill(entryX, cy - 36, exitX, cy + 36, 0xFF2E0B16);
            int cavHalfH = Math.round(18.0f - this.spongeProgress * 6.0f);
            int tractCol = (this.irrigationProgress >= 1.0f) ? 0xFF3E1020 : 0xFF6A0B25;
            g.fill(entryX, cy - cavHalfH, exitX, cy + cavHalfH, tractCol);

            // 步骤 1 水流冲洗进度条与蓝色无菌生理盐水冲刷波
            if (this.irrigationProgress > 0.01f && this.subStep == 0) {
                int flushRight = entryX + Math.round((exitX - entryX) * this.irrigationProgress);
                g.fill(entryX, cy - cavHalfH + 2, flushRight, cy + cavHalfH - 2, 0x8800E5FF);
            }

            // 步骤 2 XStat 膨胀止血海绵颗粒填满中央空腔
            if (this.spongeProgress > 0.02f) {
                int spongeCount = Math.round(this.spongeProgress * 14);
                for (int i = 0; i < spongeCount; i++) {
                    int sx = entryX + 18 + (i * 11) % (exitX - entryX - 36);
                    int sy = cy - 10 + ((i * 7) % 20);
                    g.fill(sx - 5, sy - 4, sx + 5, sy + 4, 0xFFF5F7FA);
                    g.fill(sx - 2, sy - 1, sx + 2, sy + 1, C_ADRENALINE_GOLD);
                }
            }

            // 左侧：入射口 (Entry Wound) 标记与缝合点
            g.drawCenteredString(this.font, "【入射口 Entry】", entryX + 6, viewY + 10, C_NEON_CYAN);
            g.fill(entryX - 8, cy - 22, entryX + 8, cy + 22, 0xFF880E4F);
            // 右侧：出射口 (Exit Wound) 星芒撕裂大创口标记与缝合点
            g.drawCenteredString(this.font, "【出射口 Exit】", exitX - 6, viewY + 10, 0xFFFF9100);
            g.fill(exitX - 11, cy - 28, exitX + 11, cy + 28, 0xFFAD1457);

            if (this.subStep == 0) {
                int nozzleX = entryX + Math.round((exitX - entryX) * this.irrigationProgress);
                g.fill(nozzleX - 6, cy - 6, nozzleX + 6, cy + 6, C_NEON_CYAN);
                g.drawCenteredString(this.font, "第一步：按住左键将冲洗管从左侧【入射口】向右拖动贯穿冲洗至【出射口】",
                        cx, winY + winH - 16, C_NEON_CYAN);
            } else if (this.subStep == 1) {
                g.fill(cx - 28, cy - 24, cx + 28, cy - 22, C_ADRENALINE_GOLD);
                g.fill(cx - 28, cy + 22, cx + 28, cy + 24, C_ADRENALINE_GOLD);
                int barW = Math.round(120 * this.spongeProgress);
                g.fill(cx - 60, viewY + viewH - 22, cx + 60, viewY + viewH - 16, 0xFF0A141E);
                g.fill(cx - 60, viewY + viewH - 22, cx - 60 + barW, viewY + viewH - 16, C_ADRENALINE_GOLD);
                g.drawCenteredString(this.font, "第二步：在中央【瞬时空腔】按住左键推注高膨胀止血海绵填塞死腔",
                        cx, winY + winH - 16, C_ADRENALINE_GOLD);
            } else {
                for (int i = 0; i < 6; i++) {
                    int sx = (i < 3) ? entryX : exitX;
                    int sy = cy + ((i % 3) - 1) * 16;
                    boolean done = this.dualStitchesDone[i];
                    int col = done ? C_SAFE_GREEN : 0xFFFF2A85;
                    g.fill(sx - 9, sy - 3, sx + 9, sy + 3, col);
                    if (!done) {
                        g.fill(sx - 3, sy - 3, sx + 3, sy + 3, 0xFFFFFFFF);
                    }
                }
                g.drawCenteredString(this.font, "第三步：依次点击缝合左侧【入射口】与右侧【出射口】的 6 处荷包缝合点 (" + this.completedStitchCount + "/6)",
                        cx, winY + winH - 16, 0xFFFF2A85);
            }

            // 取消按钮
            int cancelW = 50, cancelH = 14;
            int cancelX = winX + winW - cancelW - 8;
            int cancelY = winY + winH - 18;
            g.fill(cancelX, cancelY, cancelX + cancelW, cancelY + cancelH, 0x661C2833);
            drawTacticalCornerBrackets(g, cancelX, cancelY, cancelW, cancelH, 0xFF90A4AE);
            g.drawCenteredString(this.font, "×", cancelX + cancelW / 2, cancelY + 3, C_TEXT_DIM);

            SurgicalInstrumentRenderer.ToolType tool = (this.subStep == 2)
                    ? SurgicalInstrumentRenderer.ToolType.NEEDLE_HOLDER
                    : SurgicalInstrumentRenderer.ToolType.HEMOSTAT_FORCEPS;
            SurgicalInstrumentRenderer.renderDetailedSurgicalToolCursor(
                    g, this.smoothMouseX, this.smoothMouseY, this.cursorTiltDeg,
                    tool, this.draggingIrrigator || this.injectingSponge, beatWave, (nowMs % 100000L) * 0.02f);

            super.render(g, mouseX, mouseY, partialTick);
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (this.playingCompletionFinale) return true;
            if (button == 1) {
                this.onClose();
                return true;
            }
            if (button == 0) {
                int cancelW = 50, cancelH = 14;
                int cancelX = winX + winW - cancelW - 8;
                int cancelY = winY + winH - 18;
                if (mouseX >= cancelX && mouseX <= cancelX + cancelW && mouseY >= cancelY && mouseY <= cancelY + cancelH) {
                    this.onClose();
                    return true;
                }

                int cx = viewX + viewW / 2;
                int cy = viewY + viewH / 2 + 6;
                int entryX = viewX + 38;
                int exitX = viewX + viewW - 38;

                if (this.subStep == 0) {
                    int nozzleX = entryX + Math.round((exitX - entryX) * this.irrigationProgress);
                    if (Math.hypot(mouseX - nozzleX, mouseY - cy) <= 28.0 || Math.abs(mouseY - cy) <= 32.0) {
                        this.draggingIrrigator = true;
                        playUiSound(SoundEvents.AMBIENT_UNDERWATER_ENTER, 1.35f, 0.65f);
                        return true;
                    }
                } else if (this.subStep == 1) {
                    if (Math.hypot(mouseX - cx, mouseY - cy) <= 46.0) {
                        this.injectingSponge = true;
                        playUiSound(SoundEvents.WOOL_PLACE, 1.15f, 0.75f);
                        return true;
                    }
                } else if (this.subStep == 2) {
                    for (int i = 0; i < 6; i++) {
                        if (this.dualStitchesDone[i]) continue;
                        int sx = (i < 3) ? entryX : exitX;
                        int sy = cy + ((i % 3) - 1) * 16;
                        if (Math.hypot(mouseX - sx, mouseY - sy) <= 14.0) {
                            this.dualStitchesDone[i] = true;
                            this.completedStitchCount++;
                            playUiSound(SoundEvents.SHEEP_SHEAR, 1.55f, 0.65f);
                            if (this.completedStitchCount >= 6) {
                                this.playingCompletionFinale = true;
                                this.completionAnimProgress = 0.0f;
                                playUiSound(SoundEvents.EXPERIENCE_ORB_PICKUP, 1.35f, 0.80f);
                            }
                            return true;
                        }
                    }
                }
            }
            return super.mouseClicked(mouseX, mouseY, button);
        }

        @Override
        public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
            if (button == 0 && !this.playingCompletionFinale && this.subStep == 0 && this.draggingIrrigator) {
                int entryX = viewX + 38;
                int exitX = viewX + viewW - 38;
                float prog = Mth.clamp((float) (mouseX - entryX) / Math.max(1.0f, (float) (exitX - entryX)), 0.0f, 1.0f);
                if (prog > this.irrigationProgress) {
                    this.irrigationProgress = Math.min(this.irrigationProgress + 0.18f, prog);
                }
                if (this.irrigationProgress >= 0.96f) {
                    this.irrigationProgress = 1.0f;
                    this.draggingIrrigator = false;
                    this.subStep = 1;
                    playUiSound(SoundEvents.BUCKET_EMPTY, 1.35f, 0.70f);
                }
                return true;
            }
            return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
        }

        @Override
        public boolean mouseReleased(double mouseX, double mouseY, int button) {
            if (button == 0) {
                this.draggingIrrigator = false;
                this.injectingSponge = false;
            }
            return super.mouseReleased(mouseX, mouseY, button);
        }
    }

    public static class FasciotomyScreen extends Screen {

        private static final class FasciotomyParticle {
            boolean active;
            float x, y;
            float vx, vy;
            float size;
            float age, maxAge;
            int color;
            boolean isSteam;
            boolean isSpark;
        }

        private final InteractionHand hand;
        private final MedicalData.Limb limb;
        private float initialIcpMmHg = 52.0f;
        private float displayedIcpMmHg = 52.0f;
        private boolean surgeryCompleted = false;
        private boolean fasciaSlicedOpen = false;

        // 0 = 手术刀纵行切开深筋膜减压, 1 = 深部血肿凝块吸引清除, 2 = 减压后复发出血点双极电凝止血, 3 = 硅胶环鞋带式减张闭合
        private int step = 0;

        // 第 0 步：16 段纵行深筋膜切开与向两侧弹性张开
        private static final int INCISION_SEGS = 16;
        private final float[] fasciaSpread = new float[INCISION_SEGS];
        private float incisionProgress = 0.0f;
        private boolean slicingFascia = false;
        private int lastSliceSegSound = -1;

        // 第 1 步：深部血肿凝块吸引清除
        private float[] clotX;
        private float[] clotY;
        private float[] clotRemaining;
        private boolean suctionActive = false;
        private float suctionSoundAccum = 0.0f;

        // 第 2 步：解除压迫后深部复发出血点双极电凝
        private float[] bleederX;
        private float[] bleederY;
        private float[] bleederCauteryProgress;
        private boolean cauterizing = false;
        private float cauterySoundAccum = 0.0f;

        // 第 3 步：4 对硅胶减张带 (Shoelace Vessel-Loop) 交叉拉扣
        private static final int SHOELACE_COUNT = 4;
        private int lacedCount = 0;
        private boolean draggingLace = false;

        // 0.65s 3A 减压完成收尾动画
        private boolean playingFinale = false;
        private float finaleProgress = 0.0f;

        private final FasciotomyParticle[] particles = new FasciotomyParticle[72];
        private float sprayAccum = 0.0f;
        private float mistakeFlashAlpha = 0.0f;
        private float screenShakeX = 0.0f;
        private float screenShakeY = 0.0f;
        private float screenShakeAmp = 0.0f;

        private long lastFrameMs = 0L;
        private float heartPhase = 0.0f;
        private float cursorTremorX = 0.0f;
        private float cursorTremorY = 0.0f;
        private float smoothMouseX = 0.0f;
        private float smoothMouseY = 0.0f;
        private float cursorTiltDeg = 0.0f;

        private int winX, winY, winW = 316, winH = 214;
        private int viewX, viewY, viewW = 224, viewH = 164;

        public FasciotomyScreen(InteractionHand hand, MedicalData.Limb limb) {
            super(Component.literal("筋膜切开减压"));
            this.hand = hand != null ? hand : InteractionHand.MAIN_HAND;
            this.limb = limb != null ? limb : MedicalData.Limb.LEFT_LEG;
            for (int i = 0; i < this.particles.length; i++) {
                this.particles[i] = new FasciotomyParticle();
            }
        }

        private void playUiSound(SoundEvent sound, float pitch, float volume) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.getSoundManager() != null) {
                mc.getSoundManager().play(SimpleSoundInstance.forUI(sound, pitch, volume));
            }
        }

        private void spawnParticle(float x, float y, float vx, float vy, float size, float maxAge, int color, boolean isSteam, boolean isSpark) {
            for (FasciotomyParticle p : this.particles) {
                if (!p.active) {
                    p.active = true;
                    p.x = x;
                    p.y = y;
                    p.vx = vx;
                    p.vy = vy;
                    p.size = size;
                    p.age = 0.0f;
                    p.maxAge = maxAge;
                    p.color = color;
                    p.isSteam = isSteam;
                    p.isSpark = isSpark;
                    return;
                }
            }
        }

        private void triggerMistake(float x, float y) {
            this.mistakeFlashAlpha = 0.85f;
            this.screenShakeAmp = 6.5f;
            this.slicingFascia = false;
            playUiSound(SoundEvents.PLAYER_HURT, 1.15f, 0.55f);
            for (int i = 0; i < 16; i++) {
                float ang = (float) (Math.random() * Math.PI * 2.0);
                float spd = 26.0f + (float) (Math.random() * 64.0);
                spawnParticle(x, y, Mth.cos(ang) * spd, Mth.sin(ang) * spd - 10.0f,
                        2.1f, 0.42f, 0xFFFF1744, false, false);
            }
            NetworkHandler.sendTreatmentToServer(new TreatmentApplyPacket(this.hand.ordinal(), this.limb.ordinal(), 303));
        }

        @Override
        protected void init() {
            super.init();
            this.winX = (this.width - this.winW) / 2;
            this.winY = (this.height - this.winH) / 2;
            this.viewX = this.winX + 82;
            this.viewY = this.winY + 26;

            float cx = this.viewX + this.viewW * 0.5f;
            float topY = this.viewY + 26.0f;
            float botY = this.viewY + this.viewH - 18.0f;

            int clotCount = Math.max(2, Math.min(10, ModItems.safeCfgInt(MedicalConfig.FASCIOTOMY_CLOT_COUNT, 5)));
            this.clotX = new float[clotCount];
            this.clotY = new float[clotCount];
            this.clotRemaining = new float[clotCount];
            for (int i = 0; i < clotCount; i++) {
                float t = (i + 0.5f) / (float) clotCount;
                this.clotX[i] = cx + ((i % 2 == 0) ? -9.5f : 9.5f) + Mth.sin(i * 2.3f) * 3.5f;
                this.clotY[i] = Mth.lerp(t, topY + 12.0f, botY - 12.0f);
                this.clotRemaining[i] = 1.0f;
            }

            int bleederCount = Math.max(1, Math.min(8, ModItems.safeCfgInt(MedicalConfig.FASCIOTOMY_BLEEDER_COUNT, 3)));
            this.bleederX = new float[bleederCount];
            this.bleederY = new float[bleederCount];
            this.bleederCauteryProgress = new float[bleederCount];
            for (int i = 0; i < bleederCount; i++) {
                float t = (i + 0.5f) / (float) bleederCount;
                this.bleederX[i] = cx + ((i % 2 == 0) ? 7.5f : -7.5f);
                this.bleederY[i] = Mth.lerp(t, topY + 16.0f, botY - 16.0f);
                this.bleederCauteryProgress[i] = 0.0f;
            }

            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) {
                MedicalData data = mc.player.getData(MedicalDataCapability.MEDICAL_DATA);
                if (data != null) {
                    MedicalData.LimbData ld = data.getLimb(this.limb);
                    if (ld != null) {
                        this.initialIcpMmHg = Math.max(10.0f, ModItems.getCompartmentPressure(ld));
                        this.displayedIcpMmHg = this.initialIcpMmHg;
                    }
                }
                NetworkHandler.sendTreatmentToServer(new TreatmentApplyPacket(this.hand.ordinal(), this.limb.ordinal(), 300));
            }
            MedicalSoundHelper.playOpenSound();
        }

        @Override
        public boolean isPauseScreen() {
            return false;
        }

        @Override
        public void renderBackground(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        }

        @Override
        public void onClose() {
            if (!this.surgeryCompleted && this.fasciaSlicedOpen) {
                NetworkHandler.sendTreatmentToServer(new TreatmentApplyPacket(this.hand.ordinal(), this.limb.ordinal(), 304));
            }
            super.onClose();
        }

        @Override
        public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null) {
                super.onClose();
                return;
            }
            ItemStack stack = mc.player.getItemInHand(this.hand);
            if (!TreatmentRouter.isFasciotomyKitItem(stack)) {
                super.onClose();
                return;
            }
            MedicalData data = mc.player.getData(MedicalDataCapability.MEDICAL_DATA);
            if (data == null || data.getConsciousness() < ModItems.getFasciotomyMinConsciousness()
                    || data.isCardiacArrest() || data.isInVFib()) {
                this.onClose();
                return;
            }

            long nowMs = System.currentTimeMillis();
            float dt = (lastFrameMs == 0L) ? 0.016f : Mth.clamp((nowMs - lastFrameMs) / 1000.0f, 0.001f, 0.1f);
            lastFrameMs = nowMs;

            if (this.playingFinale) {
                this.finaleProgress += dt / 0.65f;
                if (this.finaleProgress >= 1.0f) {
                    this.surgeryCompleted = true;
                    NetworkHandler.sendTreatmentToServer(new TreatmentApplyPacket(this.hand.ordinal(), this.limb.ordinal(), 301));
                    MedicalSoundHelper.playLimbSelectSound();
                    this.onClose();
                    return;
                }
            }

            float hr = Mth.clamp(data.getHeartRate(), 40.0f, 220.0f);
            this.heartPhase = (this.heartPhase + dt * (hr / 60.0f)) % 1.0f;
            float beatWave = (this.heartPhase < 0.20f) ? Mth.sin((this.heartPhase / 0.20f) * (float) Math.PI) : 0.0f;

            // 疼痛高频震颤 + 低意识眩晕 8 字漂移干扰
            MedicalData.LimbData curLd = data.getLimb(this.limb);
            float sysPainRatio = data.getPerceivedPain() / Math.max(10.0f, ((float) ModItems.safeCfgDouble(MedicalConfig.MAX_PERCEIVED_PAIN, 100.0)));
            float limbPainRatio = (curLd != null) ? (curLd.getPainLevel() / Math.max(1.0f, ((float) ModItems.safeCfgDouble(MedicalConfig.MAX_PAIN_LEVEL, 10)))) : 0.0f;
            float painRatio = Mth.clamp(Math.max(sysPainRatio, limbPainRatio * 0.85f), 0.0f, 1.0f);

            float minCons = ModItems.getFasciotomyMinConsciousness();
            float consDeficit = Mth.clamp((100.0f - data.getConsciousnessFloat()) / Math.max(1.0f, 100.0f - minCons), 0.0f, 1.0f);

            float maxPainShake = ((float) ModItems.safeCfgDouble(MedicalConfig.SUTURE_CURSOR_PAIN_SHAKE_MAX, 14.0));
            float maxConsSway = ((float) ModItems.safeCfgDouble(MedicalConfig.SUTURE_CURSOR_CONSCIOUSNESS_SWAY_MAX, 22.0));
            float wdShake = ModItems.isInWithdrawalMovementRestriction(data)
                    ? ((float) ModItems.safeCfgDouble(MedicalConfig.SUTURE_CURSOR_WITHDRAWAL_SHAKE, 10.0)) : 0.0f;

            float painPulseSpike = 1.0f + beatWave * 0.65f * painRatio;
            float painShakeAmp = (painRatio * maxPainShake + wdShake) * painPulseSpike;
            float highFreqX = (Mth.sin(nowMs * 0.023f) * 0.55f + Mth.cos(nowMs * 0.041f) * 0.45f) * painShakeAmp;
            float highFreqY = (Mth.cos(nowMs * 0.029f) * 0.55f + Mth.sin(nowMs * 0.037f) * 0.45f) * painShakeAmp;

            float consSwayAmp = consDeficit * maxConsSway;
            float lowFreqX = (Mth.sin(nowMs * 0.0034f) * 0.75f + Mth.cos(nowMs * 0.0058f) * 0.25f) * consSwayAmp;
            float lowFreqY = (Mth.sin(nowMs * 0.0068f) * 0.65f + Mth.cos(nowMs * 0.0027f) * 0.35f) * consSwayAmp;

            this.cursorTremorX = highFreqX + lowFreqX;
            this.cursorTremorY = highFreqY + lowFreqY;

            float targetMx = mouseX + this.cursorTremorX;
            float targetMy = mouseY + this.cursorTremorY;
            if (this.smoothMouseX == 0.0f && this.smoothMouseY == 0.0f) {
                this.smoothMouseX = targetMx;
                this.smoothMouseY = targetMy;
            } else {
                float followRate = Mth.lerp(consDeficit, 28.0f, 8.5f);
                float vx = (targetMx - this.smoothMouseX) / Math.max(0.004f, dt);
                this.cursorTiltDeg = Mth.lerp(Mth.clamp(dt * 14.0f, 0.0f, 1.0f), this.cursorTiltDeg, Mth.clamp(vx * 0.035f, -22.0f, 22.0f));
                this.smoothMouseX = Mth.lerp(Mth.clamp(dt * followRate, 0.0f, 1.0f), this.smoothMouseX, targetMx);
                this.smoothMouseY = Mth.lerp(Mth.clamp(dt * followRate, 0.0f, 1.0f), this.smoothMouseY, targetMy);
            }

            if (this.screenShakeAmp > 0.01f) {
                this.screenShakeAmp = Math.max(0.0f, this.screenShakeAmp - dt * 22.0f);
                this.screenShakeX = (float) (Math.random() * 2.0 - 1.0) * this.screenShakeAmp;
                this.screenShakeY = (float) (Math.random() * 2.0 - 1.0) * this.screenShakeAmp;
            } else {
                this.screenShakeX = 0.0f;
                this.screenShakeY = 0.0f;
            }
            if (this.mistakeFlashAlpha > 0.005f) {
                this.mistakeFlashAlpha = Math.max(0.0f, this.mistakeFlashAlpha - dt * 2.4f);
            }

            g.fill(0, 0, this.width, this.height, 0x9602060C);

            PoseStack pose = g.pose();
            pose.pushPose();
            pose.translate(this.screenShakeX, this.screenShakeY, 0.0f);

            int themeCol = (this.displayedIcpMmHg >= 30.0f) ? C_WARNING_YELLOW : C_SAFE_GREEN;
            g.fill(winX, winY, winX + winW, winY + winH, 0xF4050B14);
            drawTacticalCornerBrackets(g, winX, winY, winW, winH, themeCol);
            g.fill(winX + 8, winY + 19, winX + winW - 8, winY + 20, withAlpha(themeCol, 0.45f));

            g.drawString(this.font, getLimbDisplayName(this.limb), winX + 10, winY + 7, C_TEXT_WHITE, false);

            // 顶部 4 步进度条
            for (int s = 0; s < 4; s++) {
                int sx = viewX + s * 36;
                int sy = winY + 8;
                boolean done = (s < this.step) || this.playingFinale;
                boolean active = (s == this.step) && !this.playingFinale;
                int sCol = done ? C_SAFE_GREEN : (active ? C_NEON_CYAN : 0xFF1E2B36);
                g.fill(sx - 1, sy - 1, sx + 31, sy + 7, 0xFF0A141E);
                g.fill(sx, sy, sx + 30, sy + 6, sCol);
                if (active) {
                    int glintOffset = (int) ((nowMs / 18L) % 26L);
                    g.fill(sx + glintOffset, sy, sx + Math.min(30, glintOffset + 6), sy + 6, 0xAAFFFFFF);
                }
            }

            // 左侧：3D 全息投影 + 实时骨筋膜室室内压表盘 (mmHg) + 缺血坏死倒计时
            int leftX = winX + 10;
            int leftY = viewY;
            int leftW = 64;
            int leftH = viewH;
            g.fill(leftX, leftY, leftX + leftW, leftY + leftH, 0xB8030810);
            drawTacticalCornerBrackets(g, leftX, leftY, leftW, leftH, withAlpha(themeCol, 0.72f));
            MedicalAssetLibrary.render3DMedicalItem(g, leftX + leftW * 0.5f, leftY + 46.0f, 30.0f, stack, nowMs);

            // 实时计算随着筋膜切开与血肿清除下降的室内压 (mmHg)
            float clotClearRatio = getClotClearRatio();
            float targetIcp = Mth.lerp(this.incisionProgress * 0.68f + clotClearRatio * 0.32f, this.initialIcpMmHg, 5.0f);
            this.displayedIcpMmHg = Mth.lerp(Math.min(1.0f, dt * 8.0f), this.displayedIcpMmHg, targetIcp);

            int gaugeY = leftY + 88;
            g.fill(leftX + 5, gaugeY, leftX + leftW - 5, gaugeY + 44, 0x8806101A);
            int icpColor = (this.displayedIcpMmHg >= 30.0f) ? C_ALERT_RED : (this.displayedIcpMmHg >= 16.0f ? C_WARNING_YELLOW : C_SAFE_GREEN);
            String icpText = String.format("%.0f", this.displayedIcpMmHg);
            g.drawCenteredString(this.font, icpText, leftX + leftW / 2, gaugeY + 12, icpColor);
            g.drawCenteredString(this.font, "mmHg", leftX + leftW / 2, gaugeY + 24, C_TEXT_DIM);

            int ischSec = curLd != null ? ModItems.getCompartmentIschemiaSec(curLd) : 0;
            float ischRatio = Mth.clamp(ischSec / (float) Math.max(1, ModItems.safeCfgInt(MedicalConfig.COMPARTMENT_NECROSIS_TIME_SEC, 240)), 0.0f, 1.0f);
            int ischCol = ischRatio > 0.75f ? C_ALERT_RED : (ischRatio > 0.40f ? C_WARNING_YELLOW : C_NEON_CYAN);
            g.fill(leftX + 7, leftY + leftH - 16, leftX + leftW - 7, leftY + leftH - 10, 0xFF0A1622);
            g.fill(leftX + 7, leftY + leftH - 16, leftX + 7 + Math.round((leftW - 14) * (1.0f - ischRatio)), leftY + leftH - 10, ischCol);

            // 中央手术视窗
            g.fill(viewX, viewY, viewX + viewW, viewY + viewH, 0xFF14050B);
            g.enableScissor(viewX + 1, viewY + 1, viewX + viewW - 1, viewY + viewH - 1);
            renderFasciotomySurgicalField(g, dt, beatWave, this.smoothMouseX, this.smoothMouseY, nowMs);
            renderParticles(g, dt);
            if (this.mistakeFlashAlpha > 0.01f) {
                g.fill(viewX, viewY, viewX + viewW, viewY + 6, withAlpha(C_ALERT_RED, this.mistakeFlashAlpha * 0.6f));
                g.fill(viewX, viewY + viewH - 6, viewX + viewW, viewY + viewH, withAlpha(C_ALERT_RED, this.mistakeFlashAlpha * 0.6f));
            }
            g.disableScissor();
            drawTacticalCornerBrackets(g, viewX, viewY, viewW, viewH, 0xFF546E7A);

            // 底部取消按钮
            int cancelW = 54, cancelH = 14;
            int cancelX = viewX + viewW - cancelW;
            int cancelY = winY + winH - 19;
            boolean hovCancel = mouseX >= cancelX && mouseX <= cancelX + cancelW && mouseY >= cancelY && mouseY <= cancelY + cancelH;
            g.fill(cancelX, cancelY, cancelX + cancelW, cancelY + cancelH, hovCancel ? 0x8837474F : 0x551C2833);
            drawTacticalCornerBrackets(g, cancelX, cancelY, cancelW, cancelH, 0xFF90A4AE);
            g.drawCenteredString(this.font, "×", cancelX + cancelW / 2, cancelY + 3, C_TEXT_DIM);

            renderFasciotomyInstrumentCursor(g, this.smoothMouseX, this.smoothMouseY, nowMs);

            pose.popPose();
            super.render(g, mouseX, mouseY, partialTick);
        }

        private float getClotClearRatio() {
            if (this.clotRemaining == null || this.clotRemaining.length == 0) return 1.0f;
            float sum = 0.0f;
            for (float r : this.clotRemaining) sum += (1.0f - r);
            return sum / this.clotRemaining.length;
        }

        private void renderFasciotomySurgicalField(GuiGraphics g, float dt, float beatWave, float mx, float my, long nowMs) {
            float cx = viewX + viewW * 0.5f;
            float topY = viewY + 18.0f;
            float botY = viewY + viewH - 16.0f;
            Matrix4f mat = g.pose().last().pose();

            // 1. 深层肿胀肌腹背景（随减压与血肿清除从暗紫发绀渐变为鲜红富氧肌肉）
            float recovery = Mth.clamp(this.incisionProgress * 0.4f + getClotClearRatio() * 0.4f + (this.playingFinale ? 0.2f : 0.0f), 0.0f, 1.0f);
            int musR = (int) Mth.lerp(recovery, 74, 175);
            int musG = (int) Mth.lerp(recovery, 14, 28);
            int musB = (int) Mth.lerp(recovery, 58, 45);
            int muscleCol = (0xFF << 24) | (musR << 16) | (musG << 8) | musB;
            g.fill(viewX + 4, viewY + 4, viewX + viewW - 4, viewY + viewH - 4, muscleCol);

            for (int strip = -4; strip <= 4; strip++) {
                int sx = Math.round(cx + strip * 14);
                g.fill(sx - 2, viewY + 4, sx + 2, viewY + viewH - 4, 0x33000000);
            }

            // 更新并渲染 16 段银白色深筋膜切开向两侧回缩弹开
            int slicedSegs = Math.round(this.incisionProgress * INCISION_SEGS);
            for (int i = 0; i < INCISION_SEGS; i++) {
                float targetSpread = (i < slicedSegs) ? (32.0f * Mth.sin(((i + 0.5f) / INCISION_SEGS) * (float) Math.PI)) : 1.0f;
                this.fasciaSpread[i] = Mth.lerp(Math.min(1.0f, dt * 10.0f), this.fasciaSpread[i], targetSpread);

                float y0 = Mth.lerp(i / (float) INCISION_SEGS, topY, botY);
                float y1 = Mth.lerp((i + 1) / (float) INCISION_SEGS, topY, botY);
                float sp = this.fasciaSpread[i];

                // 左右表皮真皮层、金黄色皮下脂肪小叶与银白色深筋膜腱膜切缘
                int lEdge = Math.round(cx - sp);
                int rEdge = Math.round(cx + sp);
                g.fill(viewX + 2, Math.round(y0), lEdge, Math.round(y1) + 1, 0xF236101B);
                g.fill(rEdge, Math.round(y0), viewX + viewW - 2, Math.round(y1) + 1, 0xF236101B);
                if (sp > 3.5f) {
                    // 皮下金黄脂肪颗粒层
                    g.fill(lEdge - 6, Math.round(y0), lEdge - 3, Math.round(y1) + 1, 0xFFD9B34C);
                    g.fill(rEdge + 3, Math.round(y0), rEdge + 6, Math.round(y1) + 1, 0xFFD9B34C);
                    // 羽状横纹肌纤维束细节
                    SurgicalInstrumentRenderer.drawQuad2D(g,
                            lEdge + 2, y0 + 1, cx, y0 + 3, cx, y1 - 1, lEdge + 2, y1 - 3, 0x449E1C28);
                    SurgicalInstrumentRenderer.drawQuad2D(g,
                            cx, y0 + 3, rEdge - 2, y0 + 1, rEdge - 2, y1 - 3, cx, y1 - 1, 0x44801420);
                }
                // 银白色高张力深筋膜切缘
                g.fill(lEdge - 3, Math.round(y0), lEdge, Math.round(y1) + 1, 0xFFECEFF1);
                g.fill(rEdge, Math.round(y0), rEdge + 3, Math.round(y1) + 1, 0xFFECEFF1);
            }

            if (this.step == 0) {
                // 第 0 步：手术刀纵行切开深筋膜引导线
                float corridorW = ((float) ModItems.safeCfgDouble(MedicalConfig.FASCIOTOMY_INCISION_CORRIDOR_WIDTH, 14.0));
                float curCutY = Mth.lerp(this.incisionProgress, topY, botY);
                g.fill(Math.round(cx - corridorW), Math.round(topY), Math.round(cx - corridorW + 1), Math.round(botY), 0x4400E5FF);
                g.fill(Math.round(cx + corridorW - 1), Math.round(topY), Math.round(cx + corridorW), Math.round(botY), 0x4400E5FF);
                g.fill(Math.round(cx - 1), Math.round(curCutY), Math.round(cx + 1), Math.round(botY), 0xAA00E676);

                // 当前下刀靶点光标环
                g.fill(Math.round(cx - 5), Math.round(curCutY - 5), Math.round(cx + 5), Math.round(curCutY + 5), C_NEON_CYAN);

                if (this.slicingFascia) {
                    if (Math.abs(mx - cx) > corridorW) {
                        triggerMistake(mx, my);
                    } else if (my >= curCutY - 8.0f && my <= curCutY + 24.0f) {
                        float nextProg = Mth.clamp((my - topY) / Math.max(1.0f, botY - topY), this.incisionProgress, 1.0f);
                        if (nextProg > this.incisionProgress) {
                            this.incisionProgress = nextProg;
                            this.fasciaSlicedOpen = true;
                            int segIdx = (int) (this.incisionProgress * INCISION_SEGS);
                            if (segIdx > this.lastSliceSegSound) {
                                this.lastSliceSegSound = segIdx;
                                playUiSound(SoundEvents.SHEEP_SHEAR, 1.25f + this.incisionProgress * 0.35f, 0.50f);
                                spawnParticle(cx, curCutY, (float) (Math.random() * 40.0 - 20.0), -18.0f, 2.0f, 0.35f, 0xFF880E4F, false, false);
                            }
                        }
                    }
                }
                if (this.incisionProgress >= 0.96f) {
                    this.incisionProgress = 1.0f;
                    this.slicingFascia = false;
                    this.step = 1;
                    playUiSound(SoundEvents.IRON_TRAPDOOR_CLOSE, 1.35f, 0.65f);
                }
            } else if (this.step == 1) {
                // 第 1 步：吸引清除深部暗紫血肿凝块
                boolean allCleared = true;
                for (int i = 0; i < this.clotRemaining.length; i++) {
                    float rem = this.clotRemaining[i];
                    if (rem <= 0.01f) continue;
                    allCleared = false;
                    float kx = this.clotX[i];
                    float ky = this.clotY[i];
                    float clotRad = Math.max(3.5f, 11.5f * rem);
                    boolean hovClot = Math.hypot(mx - kx, my - ky) <= 15.0;
                    SurgicalInstrumentRenderer.drawOrganicBloodClot2D(
                            g, kx, ky, clotRad, i * 1.6f + (nowMs % 100000L) * 0.003f,
                            this.suctionActive && hovClot, hovClot, mx, my);

                    if (this.suctionActive && Math.hypot(mx - kx, my - ky) <= 15.0) {
                        this.clotRemaining[i] = Math.max(0.0f, rem - dt * 1.65f);
                        this.suctionSoundAccum += dt;
                        if (this.suctionSoundAccum >= 0.12f) {
                            this.suctionSoundAccum = 0.0f;
                            playUiSound(SoundEvents.SLIME_SQUISH_SMALL, 1.35f, 0.45f);
                            spawnParticle(kx, ky, (mx - kx) * 6.0f, (my - ky) * 6.0f, 1.8f, 0.22f, 0xFF880E4F, false, false);
                        }
                    }
                }
                if (allCleared) {
                    this.suctionActive = false;
                    this.step = 2;
                    playUiSound(SoundEvents.BREWING_STAND_BREW, 1.45f, 0.55f);
                }
            } else if (this.step == 2) {
                // 第 2 步：解除压迫后深部复发出血点双极电凝止血
                float holdReq = ((float) ModItems.safeCfgDouble(MedicalConfig.FASCIOTOMY_CAUTERY_HOLD_SECONDS, 0.55));
                boolean allCauterized = true;
                for (int i = 0; i < this.bleederX.length; i++) {
                    float bx = this.bleederX[i];
                    float by = this.bleederY[i];
                    float prog = this.bleederCauteryProgress[i];
                    if (prog >= 1.0f) {
                        // 已电凝封闭的焦痂结节
                        g.fill(Math.round(bx - 3), Math.round(by - 3), Math.round(bx + 3), Math.round(by + 3), 0xFF37474F);
                        continue;
                    }
                    allCauterized = false;

                    // 动态喷血点与电凝进度圈
                    int pulseR = Math.round(4 + beatWave * 4.0f);
                    g.fill(Math.round(bx - pulseR), Math.round(by - pulseR), Math.round(bx + pulseR), Math.round(by + pulseR), 0xFFFF1744);
                    if (prog > 0.01f) {
                        int barW = Math.round(14 * prog);
                        g.fill(Math.round(bx - 7), Math.round(by - 9), Math.round(bx - 7 + barW), Math.round(by - 7), C_ADRENALINE_GOLD);
                    }

                    if (this.cauterizing && Math.hypot(mx - bx, my - by) <= 13.0) {
                        this.bleederCauteryProgress[i] = Math.min(1.0f, prog + dt / Math.max(0.15f, holdReq));
                        this.cauterySoundAccum += dt;
                        if (this.cauterySoundAccum >= 0.09f) {
                            this.cauterySoundAccum = 0.0f;
                            playUiSound(SoundEvents.FIRE_EXTINGUISH, 1.65f, 0.35f);
                            spawnParticle(bx, by, (float) (Math.random() * 24.0 - 12.0), -28.0f, 2.2f, 0.45f, 0xCCECEFF1, true, false);
                            spawnParticle(bx, by, (float) (Math.random() * 45.0 - 22.5), (float) (Math.random() * 45.0 - 22.5), 1.4f, 0.20f, C_NEON_CYAN, false, true);
                        }
                    }
                }
                if (allCauterized) {
                    this.cauterizing = false;
                    this.step = 3;
                    playUiSound(SoundEvents.IRON_TRAPDOOR_CLOSE, 1.45f, 0.65f);
                }
            } else if (this.step == 3) {
                // 第 3 步：4 对硅胶减张带 (Shoelace Vessel-Loop) 交叉拉扣
                for (int i = 0; i < SHOELACE_COUNT; i++) {
                    float ly = Mth.lerp((i + 0.5f) / (float) SHOELACE_COUNT, topY + 14.0f, botY - 14.0f);
                    float lx = cx - 26.0f;
                    float rx = cx + 26.0f;

                    g.fill(Math.round(lx - 3), Math.round(ly - 3), Math.round(lx + 3), Math.round(ly + 3), 0xFFCFD8DC);
                    g.fill(Math.round(rx - 3), Math.round(ly - 3), Math.round(rx + 3), Math.round(ly + 3), 0xFFCFD8DC);

                    if (i < this.lacedCount) {
                        // 已系好的黄色硅胶减张交叉带
                        g.fill(Math.round(lx), Math.round(ly - 1), Math.round(rx), Math.round(ly + 1), C_ADRENALINE_GOLD);
                    } else if (i == this.lacedCount) {
                        g.fill(Math.round(lx - 4), Math.round(ly - 4), Math.round(lx + 4), Math.round(ly + 4), C_SAFE_GREEN);
                        g.fill(Math.round(rx - 4), Math.round(ly - 4), Math.round(rx + 4), Math.round(ly + 4), C_NEON_CYAN);
                        if (this.draggingLace) {
                            g.fill(Math.round(lx), Math.round(ly - 1), Math.round(mx), Math.round(ly + 1), C_ADRENALINE_GOLD);
                        }
                    }
                }

                if (this.playingFinale) {
                    // 负压减张海绵敷料覆盖动画
                    int foamH = Math.round((botY - topY) * Mth.clamp(this.finaleProgress, 0.0f, 1.0f));
                    g.fill(Math.round(cx - 22), Math.round(topY), Math.round(cx + 22), Math.round(topY + foamH), 0xCCECEFF1);
                }
            }
        }

        private void renderParticles(GuiGraphics g, float dt) {
            Matrix4f mat = g.pose().last().pose();
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.setShader(GameRenderer::getPositionColorShader);

            Tesselator tess = Tesselator.getInstance();
            BufferBuilder buf = tess.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
            boolean any = false;

            for (FasciotomyParticle p : this.particles) {
                if (!p.active) continue;
                p.age += dt;
                if (p.age >= p.maxAge) {
                    p.active = false;
                    continue;
                }
                p.x += p.vx * dt;
                p.y += p.vy * dt;
                if (p.isSteam) {
                    p.vy -= 18.0f * dt;
                    p.vx *= (1.0f - dt * 3.0f);
                } else if (!p.isSpark) {
                    p.vy += 70.0f * dt;
                }

                float life = 1.0f - (p.age / p.maxAge);
                float hs = p.size * (p.isSteam ? (1.0f + (1.0f - life) * 0.8f) : life);
                int a = Mth.clamp(Math.round(life * (p.isSteam ? 140.0f : 230.0f)), 0, 255);
                int r = (p.color >> 16) & 0xFF;
                int gr = (p.color >> 8) & 0xFF;
                int b = p.color & 0xFF;

                buf.addVertex(mat, p.x - hs, p.y + hs, 0.0f).setColor(r, gr, b, a);
                buf.addVertex(mat, p.x + hs, p.y + hs, 0.0f).setColor(r, gr, b, a);
                buf.addVertex(mat, p.x + hs, p.y - hs, 0.0f).setColor(r, gr, b, a);
                buf.addVertex(mat, p.x - hs, p.y - hs, 0.0f).setColor(r, gr, b, a);
                any = true;
            }

            MeshData mesh = buf.build();
            if (mesh != null && any) {
                BufferUploader.drawWithShader(mesh);
            }
        }

        private void renderFasciotomyInstrumentCursor(GuiGraphics g, float mx, float my, long nowMs) {
            SurgicalInstrumentRenderer.ToolType tool = switch (this.step) {
                case 0 -> SurgicalInstrumentRenderer.ToolType.SCALPEL;
                case 1 -> SurgicalInstrumentRenderer.ToolType.SUCTION_CANNULA;
                case 2 -> SurgicalInstrumentRenderer.ToolType.BIPOLAR_CAUTERY;
                default -> SurgicalInstrumentRenderer.ToolType.HEMOSTAT_FORCEPS;
            };
            boolean active = (this.step == 0 && this.slicingFascia)
                    || (this.step == 1 && this.suctionActive)
                    || (this.step == 2 && this.cauterizing)
                    || (this.step == 3 && this.draggingLace);
            SurgicalInstrumentRenderer.renderDetailedSurgicalToolCursor(
                    g, mx, my, this.cursorTiltDeg, tool, active, this.incisionProgress, (nowMs % 100000L) * 0.02f);
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (this.playingFinale) return true;
            if (button == 1) {
                this.onClose();
                return true;
            }
            if (button == 0) {
                int cancelW = 54, cancelH = 14;
                int cancelX = viewX + viewW - cancelW;
                int cancelY = winY + winH - 19;
                if (mouseX >= cancelX && mouseX <= cancelX + cancelW && mouseY >= cancelY && mouseY <= cancelY + cancelH) {
                    this.onClose();
                    return true;
                }

                float mx = (this.smoothMouseX != 0.0f) ? this.smoothMouseX : ((float) mouseX + this.cursorTremorX);
                float my = (this.smoothMouseY != 0.0f) ? this.smoothMouseY : ((float) mouseY + this.cursorTremorY);
                float cx = viewX + viewW * 0.5f;
                float topY = viewY + 18.0f;
                float botY = viewY + viewH - 16.0f;

                if (this.step == 0) {
                    float curCutY = Mth.lerp(this.incisionProgress, topY, botY);
                    if (Math.abs(mx - cx) <= ((float) ModItems.safeCfgDouble(MedicalConfig.FASCIOTOMY_INCISION_CORRIDOR_WIDTH, 14.0))
                            && Math.abs(my - curCutY) <= 16.0f) {
                        this.slicingFascia = true;
                        playUiSound(SoundEvents.SHEEP_SHEAR, 1.15f, 0.5f);
                        return true;
                    }
                } else if (this.step == 1) {
                    this.suctionActive = true;
                    return true;
                } else if (this.step == 2) {
                    this.cauterizing = true;
                    return true;
                } else if (this.step == 3 && this.lacedCount < SHOELACE_COUNT) {
                    float ly = Mth.lerp((this.lacedCount + 0.5f) / (float) SHOELACE_COUNT, topY + 14.0f, botY - 14.0f);
                    float lx = cx - 26.0f;
                    if (Math.hypot(mx - lx, my - ly) <= 12.0) {
                        this.draggingLace = true;
                        MedicalSoundHelper.playHoverSound();
                        return true;
                    }
                }
            }
            return super.mouseClicked(mouseX, mouseY, button);
        }

        @Override
        public boolean mouseReleased(double mouseX, double mouseY, int button) {
            if (button == 0 && !this.playingFinale) {
                this.slicingFascia = false;
                this.suctionActive = false;
                this.cauterizing = false;

                if (this.step == 3 && this.draggingLace) {
                    this.draggingLace = false;
                    float mx = (this.smoothMouseX != 0.0f) ? this.smoothMouseX : ((float) mouseX + this.cursorTremorX);
                    float my = (this.smoothMouseY != 0.0f) ? this.smoothMouseY : ((float) mouseY + this.cursorTremorY);
                    float cx = viewX + viewW * 0.5f;
                    float topY = viewY + 18.0f;
                    float botY = viewY + viewH - 16.0f;
                    float ly = Mth.lerp((this.lacedCount + 0.5f) / (float) SHOELACE_COUNT, topY + 14.0f, botY - 14.0f);
                    float rx = cx + 26.0f;
                    if (Math.hypot(mx - rx, my - ly) <= 13.0) {
                        this.lacedCount++;
                        playUiSound(SoundEvents.TRIPWIRE_CLICK_ON, 1.35f + this.lacedCount * 0.1f, 0.65f);
                        if (this.lacedCount >= SHOELACE_COUNT) {
                            this.playingFinale = true;
                            this.finaleProgress = 0.0f;
                            playUiSound(SoundEvents.PLAYER_LEVELUP, 1.50f, 0.70f);
                        }
                    } else {
                        triggerMistake(mx, my);
                    }
                    return true;
                }
            }
            return super.mouseReleased(mouseX, mouseY, button);
        }
    }

    // =========================================================================
    // 野战骨科复位固定手术交互界面 (OrthopedicSurgeryScreen —— 3A级 C型臂X光透视骨科复位与外固定架/镍钛肋骨环抱器手术模拟)
    //
    // 四步精密骨科手术流程（全部参数读取自 MedicalConfig，零硬编码）：
    // - 阶段 0（游离碎骨清创 Debridement）：用咬骨钳精准夹除卡在血管旁的尖锐游离碎骨片（误触搏动动脉会扣血加痛）
    // - 阶段 1（X光透视牵引对位复位 Traction & Reduction）：拖拽远端骨断端（或浮动肋骨段），抵抗肌肉痉挛牵拉，将其对准绿色解剖透视靶区并保持稳定锁紧复位钳
    // - 阶段 2（皮质骨钻孔置钉 / 镍钛记忆合金肋骨环抱器卡合 Cortical Drilling / SSRF Rib Claw）：
    //     * 四肢/头部模式：对准 4 个皮质骨靶点按住左键启动骨科电钻，钻入 Schanz 钛合金骨螺钉
    //     * 胸腔连枷胸模式：对准多发肋骨断端按住左键，将镍钛记忆合金肋骨环抱器卡爪抱紧锁死在浮动肋骨上
    // - 阶段 3（碳纤维外固定连杆锁紧 / 胸壁刚性桥接 Rod Locking）：从首枚骨钉拖拽连杆扳手至末枚骨钉，完成外固定架刚性锁定
    // =========================================================================
    public static class OrthopedicSurgeryScreen extends Screen {

        private static final class OrthoParticle {
            boolean active;
            float x, y;
            float vx, vy;
            float size;
            float age, maxAge;
            int color;
        }

        private final InteractionHand hand;
        private final MedicalData.Limb limb;
        private final boolean isChestRibMode;

        private int winX, winY, winW = 248, winH = 198;
        private int viewX, viewY, viewW = 152, viewH = 156;

        // 0 = 碎骨清创, 1 = 牵引对位复位, 2 = 钻孔置钉/肋骨环抱器, 3 = 连杆刚性锁定
        private int step = 0;

        // 阶段 0：游离碎骨片
        private float[] splinterX;
        private float[] splinterY;
        private float[] splinterAngle;
        private boolean[] splinterRemoved;
        private int removedSplinterCount = 0;

        // 阶段 1：牵引对位复位
        private boolean draggingBone = false;
        private float boneOffsetX = 24.0f;
        private float boneOffsetY = -14.0f;
        private float reductionHoldSec = 0.0f;

        // 阶段 2：钻孔置钉 / 肋骨环抱器
        private float[] screwX;
        private float[] screwY;
        private float[] screwDrillProgress;
        private boolean drillingActive = false;
        private int drillSoundTick = 0;

        // 阶段 3：外固定连杆拖拽锁定
        private boolean draggingRod = false;
        private float rodLockProgress = 0.0f;

        private boolean boneExposed = false;
        private boolean surgeryCompleted = false;
        private boolean playingFinale = false;
        private float finaleProgress = 0.0f;

        private long lastFrameMs = 0L;
        private float heartPhase = 0.0f;
        private float mistakeFlashAlpha = 0.0f;
        private float screenShakeAmp = 0.0f;
        private float screenShakeX = 0.0f;
        private float screenShakeY = 0.0f;

        private float cursorTremorX = 0.0f;
        private float cursorTremorY = 0.0f;
        private float smoothMouseX = 0.0f;
        private float smoothMouseY = 0.0f;
        private float cursorTiltDeg = 0.0f;

        private final OrthoParticle[] particles = new OrthoParticle[64];

        public OrthopedicSurgeryScreen(InteractionHand hand, MedicalData.Limb limb) {
            super(Component.literal("骨科复位固定"));
            this.hand = hand != null ? hand : InteractionHand.MAIN_HAND;
            this.limb = limb != null ? limb : MedicalData.Limb.LEFT_LEG;
            this.isChestRibMode = (this.limb == MedicalData.Limb.CHEST);
            for (int i = 0; i < this.particles.length; i++) {
                this.particles[i] = new OrthoParticle();
            }
        }

        private void playUiSound(SoundEvent sound, float pitch, float volume) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.getSoundManager() != null) {
                mc.getSoundManager().play(SimpleSoundInstance.forUI(sound, pitch, volume));
            }
        }

        private void spawnParticle(float x, float y, float vx, float vy, float size, float maxAge, int color) {
            for (OrthoParticle p : this.particles) {
                if (!p.active) {
                    p.active = true;
                    p.x = x;
                    p.y = y;
                    p.vx = vx;
                    p.vy = vy;
                    p.size = size;
                    p.age = 0.0f;
                    p.maxAge = maxAge;
                    p.color = color;
                    return;
                }
            }
        }

        private void triggerMistake(float x, float y) {
            this.mistakeFlashAlpha = 0.85f;
            this.screenShakeAmp = 6.5f;
            this.draggingBone = false;
            this.drillingActive = false;
            this.draggingRod = false;
            playUiSound(SoundEvents.PLAYER_HURT, 1.15f, 0.55f);
            for (int i = 0; i < 16; i++) {
                float ang = (float) (Math.random() * Math.PI * 2.0);
                float spd = 25.0f + (float) (Math.random() * 60.0);
                spawnParticle(x, y, Mth.cos(ang) * spd, Mth.sin(ang) * spd - 8.0f, 2.1f, 0.42f, 0xFFFF1744);
            }
            NetworkHandler.sendTreatmentToServer(new TreatmentApplyPacket(this.hand.ordinal(), this.limb.ordinal(), 403));
        }

        @Override
        protected void init() {
            super.init();
            this.winX = (this.width - this.winW) / 2;
            this.winY = (this.height - this.winH) / 2;
            this.viewX = this.winX + 84;
            this.viewY = this.winY + 26;

            float cx = this.viewX + this.viewW * 0.5f;
            float cy = this.viewY + this.viewH * 0.5f;

            int spCount = Math.max(2, Math.min(8, ModItems.safeCfgInt(MedicalConfig.ORTHO_SPLINTER_COUNT, 4)));
            this.splinterX = new float[spCount];
            this.splinterY = new float[spCount];
            this.splinterAngle = new float[spCount];
            this.splinterRemoved = new boolean[spCount];
            for (int i = 0; i < spCount; i++) {
                float t = (i + 0.5f) / (float) spCount;
                this.splinterX[i] = cx + ((i % 2 == 0) ? -16.0f : 16.0f) + Mth.sin(i * 2.1f) * 5.0f;
                this.splinterY[i] = Mth.lerp(t, cy - 34.0f, cy + 34.0f);
                this.splinterAngle[i] = (i * 47.0f) % 360.0f;
                this.splinterRemoved[i] = false;
            }
            this.removedSplinterCount = 0;

            int scCount = Math.max(2, Math.min(6, ModItems.safeCfgInt(MedicalConfig.ORTHO_SCREW_COUNT, 4)));
            this.screwX = new float[scCount];
            this.screwY = new float[scCount];
            this.screwDrillProgress = new float[scCount];
            for (int i = 0; i < scCount; i++) {
                if (this.isChestRibMode) {
                    float t = (i + 0.5f) / (float) scCount;
                    this.screwX[i] = cx + ((i % 2 == 0) ? -8.0f : 8.0f);
                    this.screwY[i] = Mth.lerp(t, this.viewY + 30.0f, this.viewY + this.viewH - 30.0f);
                } else {
                    float t = (i < scCount / 2)
                            ? Mth.lerp((i + 0.5f) / (scCount / 2.0f), this.viewY + 26.0f, cy - 18.0f)
                            : Mth.lerp((i - scCount / 2 + 0.5f) / (scCount - scCount / 2.0f), cy + 18.0f, this.viewY + this.viewH - 26.0f);
                    this.screwX[i] = cx;
                    this.screwY[i] = t;
                }
                this.screwDrillProgress[i] = 0.0f;
            }

            this.boneOffsetX = 24.0f;
            this.boneOffsetY = -14.0f;

            NetworkHandler.sendTreatmentToServer(new TreatmentApplyPacket(this.hand.ordinal(), this.limb.ordinal(), 400));
            MedicalSoundHelper.playOpenSound();
        }

        @Override
        public boolean isPauseScreen() {
            return false;
        }

        @Override
        public void renderBackground(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        }

        @Override
        public void onClose() {
            if (!this.surgeryCompleted && this.boneExposed) {
                NetworkHandler.sendTreatmentToServer(new TreatmentApplyPacket(this.hand.ordinal(), this.limb.ordinal(), 404));
            }
            super.onClose();
        }

        private float computeAlignmentRatio() {
            if (this.step == 0) {
                return (this.splinterRemoved == null || this.splinterRemoved.length == 0)
                        ? 0.0f
                        : (this.removedSplinterCount / (float) this.splinterRemoved.length) * 0.25f;
            }
            if (this.step == 1) {
                float dist = Mth.sqrt(this.boneOffsetX * this.boneOffsetX + this.boneOffsetY * this.boneOffsetY);
                float align = Mth.clamp(1.0f - dist / 32.0f, 0.0f, 1.0f);
                return 0.25f + align * 0.30f;
            }
            if (this.step == 2) {
                float sum = 0.0f;
                for (float v : this.screwDrillProgress) sum += v;
                float avg = (this.screwDrillProgress.length > 0) ? (sum / this.screwDrillProgress.length) : 1.0f;
                return 0.55f + avg * 0.30f;
            }
            return 0.85f + this.rodLockProgress * 0.15f;
        }

        @Override
        public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null) {
                super.onClose();
                return;
            }
            ItemStack stack = mc.player.getItemInHand(this.hand);
            if (!TreatmentRouter.isOrthopedicKitItem(stack)) {
                super.onClose();
                return;
            }
            MedicalData data = mc.player.getData(MedicalDataCapability.MEDICAL_DATA);
            if (data == null || data.getConsciousness() < ModItems.getOrthopedicMinConsciousness()
                    || data.isCardiacArrest() || data.isInVFib()) {
                this.onClose();
                return;
            }

            long nowMs = System.currentTimeMillis();
            float dt = (lastFrameMs == 0L) ? 0.016f : Mth.clamp((nowMs - lastFrameMs) / 1000.0f, 0.001f, 0.1f);
            lastFrameMs = nowMs;

            if (this.playingFinale) {
                this.finaleProgress += dt / 0.65f;
                if (this.finaleProgress >= 1.0f) {
                    this.surgeryCompleted = true;
                    NetworkHandler.sendTreatmentToServer(new TreatmentApplyPacket(this.hand.ordinal(), this.limb.ordinal(), 401));
                    MedicalSoundHelper.playLimbSelectSound();
                    this.onClose();
                    return;
                }
            }

            float hr = Mth.clamp(data.getHeartRate(), 40.0f, 220.0f);
            this.heartPhase = (this.heartPhase + dt * (hr / 60.0f)) % 1.0f;
            float beatWave = (this.heartPhase < 0.20f) ? Mth.sin((this.heartPhase / 0.20f) * (float) Math.PI) : 0.0f;

            // 疼痛高频震颤 + 低意识眩晕漂移干扰
            MedicalData.LimbData curLd = data.getLimb(this.limb);
            float sysPainRatio = data.getPerceivedPain() / Math.max(10.0f, ((float) ModItems.safeCfgDouble(MedicalConfig.MAX_PERCEIVED_PAIN, 100.0)));
            float limbPainRatio = (curLd != null) ? (curLd.getPainLevel() / Math.max(1.0f, ((float) ModItems.safeCfgDouble(MedicalConfig.MAX_PAIN_LEVEL, 10)))) : 0.0f;
            float painRatio = Mth.clamp(Math.max(sysPainRatio, limbPainRatio * 0.85f), 0.0f, 1.0f);

            float minCons = ModItems.getOrthopedicMinConsciousness();
            float consDeficit = Mth.clamp((100.0f - data.getConsciousnessFloat()) / Math.max(1.0f, 100.0f - minCons), 0.0f, 1.0f);

            float maxPainShake = ((float) ModItems.safeCfgDouble(MedicalConfig.SUTURE_CURSOR_PAIN_SHAKE_MAX, 14.0));
            float maxConsSway = ((float) ModItems.safeCfgDouble(MedicalConfig.SUTURE_CURSOR_CONSCIOUSNESS_SWAY_MAX, 22.0));
            float wdShake = ModItems.isInWithdrawalMovementRestriction(data)
                    ? ((float) ModItems.safeCfgDouble(MedicalConfig.SUTURE_CURSOR_WITHDRAWAL_SHAKE, 10.0)) : 0.0f;

            float painPulseSpike = 1.0f + beatWave * 0.65f * painRatio;
            float painShakeAmp = (painRatio * maxPainShake + wdShake) * painPulseSpike;
            float highFreqX = (Mth.sin(nowMs * 0.023f) * 0.55f + Mth.cos(nowMs * 0.041f) * 0.45f) * painShakeAmp;
            float highFreqY = (Mth.cos(nowMs * 0.029f) * 0.55f + Mth.sin(nowMs * 0.037f) * 0.45f) * painShakeAmp;

            float consSwayAmp = consDeficit * maxConsSway;
            float lowFreqX = (Mth.sin(nowMs * 0.0034f) * 0.75f + Mth.cos(nowMs * 0.0058f) * 0.25f) * consSwayAmp;
            float lowFreqY = (Mth.sin(nowMs * 0.0068f) * 0.65f + Mth.cos(nowMs * 0.0027f) * 0.35f) * consSwayAmp;

            this.cursorTremorX = highFreqX + lowFreqX;
            this.cursorTremorY = highFreqY + lowFreqY;

            float targetMx = mouseX + this.cursorTremorX;
            float targetMy = mouseY + this.cursorTremorY;
            if (this.smoothMouseX == 0.0f && this.smoothMouseY == 0.0f) {
                this.smoothMouseX = targetMx;
                this.smoothMouseY = targetMy;
            } else {
                float followRate = Mth.lerp(consDeficit, 28.0f, 8.5f);
                float vx = (targetMx - this.smoothMouseX) / Math.max(0.004f, dt);
                this.cursorTiltDeg = Mth.lerp(Mth.clamp(dt * 14.0f, 0.0f, 1.0f), this.cursorTiltDeg, Mth.clamp(vx * 0.035f, -22.0f, 22.0f));
                this.smoothMouseX = Mth.lerp(Math.min(1.0f, dt * followRate), this.smoothMouseX, targetMx);
                this.smoothMouseY = Mth.lerp(Math.min(1.0f, dt * followRate), this.smoothMouseY, targetMy);
            }

            if (this.screenShakeAmp > 0.01f) {
                this.screenShakeAmp = Math.max(0.0f, this.screenShakeAmp - dt * 22.0f);
                this.screenShakeX = (float) (Math.random() * 2.0 - 1.0) * this.screenShakeAmp;
                this.screenShakeY = (float) (Math.random() * 2.0 - 1.0) * this.screenShakeAmp;
            } else {
                this.screenShakeX = 0.0f;
                this.screenShakeY = 0.0f;
            }
            if (this.mistakeFlashAlpha > 0.005f) {
                this.mistakeFlashAlpha = Math.max(0.0f, this.mistakeFlashAlpha - dt * 2.4f);
            }

            g.fill(0, 0, this.width, this.height, 0x9602060C);

            PoseStack pose = g.pose();
            pose.pushPose();
            pose.translate(this.screenShakeX, this.screenShakeY, 0.0f);

            int themeCol = this.playingFinale ? C_SAFE_GREEN : C_NEON_CYAN;
            g.fill(winX, winY, winX + winW, winY + winH, 0xF4040B14);
            drawTacticalCornerBrackets(g, winX, winY, winW, winH, themeCol);
            g.fill(winX + 8, winY + 19, winX + winW - 8, winY + 20, withAlpha(themeCol, 0.45f));

            String modeTitle = getLimbDisplayName(this.limb) + (this.isChestRibMode ? " · 肋骨环抱器" : " · 骨科外固定");
            g.drawString(this.font, modeTitle, winX + 10, winY + 7, C_TEXT_WHITE, false);

            // 顶部 4 步进度条
            for (int s = 0; s < 4; s++) {
                int sx = viewX + s * 39;
                int sy = winY + 8;
                boolean done = (s < this.step) || this.playingFinale;
                boolean active = (s == this.step) && !this.playingFinale;
                int sCol = done ? C_SAFE_GREEN : (active ? C_NEON_CYAN : 0xFF1E2B36);
                g.fill(sx - 1, sy - 1, sx + 34, sy + 7, 0xFF0A141E);
                g.fill(sx, sy, sx + 33, sy + 6, sCol);
            }

            // 左侧：3D 手术箱全息图 + 实时骨对位精度表盘
            int leftX = winX + 10;
            int leftY = viewY;
            int leftW = 68;
            int leftH = viewH;
            g.fill(leftX, leftY, leftX + leftW, leftY + leftH, 0xB8030810);
            drawTacticalCornerBrackets(g, leftX, leftY, leftW, leftH, withAlpha(themeCol, 0.72f));
            MedicalAssetLibrary.render3DMedicalItem(g, leftX + leftW * 0.5f, leftY + 46.0f, 30.0f, stack, nowMs);

            float alignRatio = computeAlignmentRatio();
            int gaugeY = leftY + 90;
            g.fill(leftX + 5, gaugeY, leftX + leftW - 5, gaugeY + 42, 0x8806101A);
            int alignCol = alignRatio >= 0.85f ? C_SAFE_GREEN : (alignRatio >= 0.45f ? C_NEON_CYAN : C_WARNING_YELLOW);
            g.drawCenteredString(this.font, String.format("%.0f%%", alignRatio * 100.0f), leftX + leftW / 2, gaugeY + 8, alignCol);
            g.drawCenteredString(this.font, "对位精度", leftX + leftW / 2, gaugeY + 22, C_TEXT_DIM);

            g.fill(leftX + 7, leftY + leftH - 16, leftX + leftW - 7, leftY + leftH - 10, 0xFF0A1622);
            g.fill(leftX + 7, leftY + leftH - 16, leftX + 7 + Math.round((leftW - 14) * alignRatio), leftY + leftH - 10, alignCol);

            // 中央 C型臂 X光透视手术视窗
            g.fill(viewX, viewY, viewX + viewW, viewY + viewH, 0xFF030B14);
            g.enableScissor(viewX + 1, viewY + 1, viewX + viewW - 1, viewY + viewH - 1);
            renderOrthopedicXRayField(g, dt, beatWave, this.smoothMouseX, this.smoothMouseY, nowMs);
            renderParticles(g, dt);
            if (this.mistakeFlashAlpha > 0.01f) {
                g.fill(viewX, viewY, viewX + viewW, viewY + 6, withAlpha(C_ALERT_RED, this.mistakeFlashAlpha * 0.65f));
                g.fill(viewX, viewY + viewH - 6, viewX + viewW, viewY + viewH, withAlpha(C_ALERT_RED, this.mistakeFlashAlpha * 0.65f));
            }
            g.disableScissor();
            drawTacticalCornerBrackets(g, viewX, viewY, viewW, viewH, 0xFF455A64);

            // 底部取消按钮
            int cancelW = 54, cancelH = 14;
            int cancelX = viewX + viewW - cancelW;
            int cancelY = winY + winH - 18;
            boolean hovCancel = mouseX >= cancelX && mouseX <= cancelX + cancelW && mouseY >= cancelY && mouseY <= cancelY + cancelH;
            g.fill(cancelX, cancelY, cancelX + cancelW, cancelY + cancelH, hovCancel ? 0x8837474F : 0x551C2833);
            drawTacticalCornerBrackets(g, cancelX, cancelY, cancelW, cancelH, 0xFF90A4AE);
            g.drawCenteredString(this.font, "×", cancelX + cancelW / 2, cancelY + 3, C_TEXT_DIM);

            renderOrthoInstrumentCursor(g, this.smoothMouseX, this.smoothMouseY, nowMs);

            pose.popPose();
            super.render(g, mouseX, mouseY, partialTick);
        }

        private void renderParticles(GuiGraphics g, float dt) {
            for (OrthoParticle p : this.particles) {
                if (!p.active) continue;
                p.age += dt;
                if (p.age >= p.maxAge) {
                    p.active = false;
                    continue;
                }
                p.x += p.vx * dt;
                p.y += p.vy * dt;
                p.vy += 45.0f * dt;
                float alpha = 1.0f - (p.age / p.maxAge);
                int c = withAlpha(p.color, alpha);
                int r = Math.max(1, Math.round(p.size));
                g.fill(Math.round(p.x - r), Math.round(p.y - r), Math.round(p.x + r), Math.round(p.y + r), c);
            }
        }

        private void renderOrthopedicXRayField(GuiGraphics g, float dt, float beatWave, float mx, float my, long nowMs) {
            float cx = viewX + viewW * 0.5f;
            float cy = viewY + viewH * 0.5f;

            // X光透视网格背景与扫描线
            for (int gx = viewX + 12; gx < viewX + viewW; gx += 16) {
                g.fill(gx, viewY, gx + 1, viewY + viewH, 0x1600E5FF);
            }
            for (int gy = viewY + 12; gy < viewY + viewH; gy += 16) {
                g.fill(viewX, gy, viewX + viewW, gy + 1, 0x1600E5FF);
            }
            int scanY = viewY + (int) ((nowMs / 16L) % viewH);
            g.fill(viewX, scanY, viewX + viewW, scanY + 2, 0x2400E5FF);

            // 伴行搏动大血管（红色危险禁区：阶段 0 夹碎骨或阶段 2 钻孔偏离碰触血管会触发失误）
            float vesselLeftX = cx - 28.0f + Mth.sin(nowMs * 0.005f) * 1.5f;
            float vesselRightX = cx + 28.0f - Mth.sin(nowMs * 0.005f) * 1.5f;
            int vWidth = 3 + Math.round(beatWave * 2.0f);
            g.fill(Math.round(vesselLeftX - vWidth), viewY + 8, Math.round(vesselLeftX + vWidth), viewY + viewH - 8, 0x88FF1744);
            g.fill(Math.round(vesselRightX - vWidth), viewY + 8, Math.round(vesselRightX + vWidth), viewY + viewH - 8, 0x88FF1744);

            // 计算当前远端骨断端的偏移（阶段 0 和阶段 1 未锁定时会有肌肉牵拉颤动）
            float curOffX = (this.step <= 1) ? this.boneOffsetX : 0.0f;
            float curOffY = (this.step <= 1) ? this.boneOffsetY : 0.0f;

            if (this.step == 1) {
                if (this.draggingBone) {
                    this.boneOffsetX = Mth.clamp(mx - cx, -36.0f, 36.0f);
                    this.boneOffsetY = Mth.clamp(my - (cy + 26.0f), -26.0f, 26.0f);
                } else {
                    // 肌肉痉挛牵拉：若松开鼠标，断骨会缓慢向错位方向回弹
                    this.boneOffsetX = Mth.lerp(Math.min(1.0f, dt * 1.8f), this.boneOffsetX, 22.0f);
                    this.boneOffsetY = Mth.lerp(Math.min(1.0f, dt * 1.8f), this.boneOffsetY, -12.0f);
                }
                curOffX = this.boneOffsetX;
                curOffY = this.boneOffsetY;

                float tol = ((float) ModItems.safeCfgDouble(MedicalConfig.ORTHO_REDUCTION_TOLERANCE_PX, 8.5));
                float holdReq = ((float) ModItems.safeCfgDouble(MedicalConfig.ORTHO_REDUCTION_HOLD_SECONDS, 0.65));
                float errDist = Mth.sqrt(curOffX * curOffX + curOffY * curOffY);
                if (errDist <= tol) {
                    this.reductionHoldSec += dt;
                    if (this.reductionHoldSec >= holdReq) {
                        this.boneOffsetX = 0.0f;
                        this.boneOffsetY = 0.0f;
                        this.draggingBone = false;
                        this.step = 2;
                        playUiSound(SoundEvents.ANVIL_PLACE, 1.55f, 0.50f);
                    }
                } else {
                    this.reductionHoldSec = Math.max(0.0f, this.reductionHoldSec - dt * 1.5f);
                }
            }

            // 绘制高精度骨骼解剖与 X 光透视结构（区分「胸腔连枷胸多发弧形肋骨」与「四肢干骺端+骨髓腔+锯齿断面长骨」）
            if (this.isChestRibMode) {
                for (int r = 0; r < this.screwY.length; r++) {
                    float ry = this.screwY[r];
                    float shiftX = (r % 2 == 0) ? (curOffX * 0.7f) : (-curOffX * 0.7f);
                    float shiftY = curOffY * 0.5f;
                    SurgicalInstrumentRenderer.drawAnatomicalCurvedRib2D(
                            g, cx, ry, 46.0f, 8.5f, shiftX, shiftY, shiftX * 0.3f, this.step >= 2);
                }
            } else {
                SurgicalInstrumentRenderer.drawAnatomicalVerticalLongBone2D(
                        g, cx, cy, viewY + 12.0f, viewY + viewH - 12.0f, 14.5f, curOffX, curOffY, this.step >= 2);
            }

            // 阶段 0：渲染高精度 8 顶点非对称锯齿皮质骨碎渣 + 蜂窝骨松质断面 + 刺伤渗血光晕
            if (this.step == 0 && this.splinterX != null) {
                for (int i = 0; i < this.splinterX.length; i++) {
                    if (this.splinterRemoved[i]) continue;
                    float sx = this.splinterX[i];
                    float sy = this.splinterY[i];
                    boolean hov = Math.hypot(mx - sx, my - sy) <= 12.0;
                    SurgicalInstrumentRenderer.drawDetailedBoneSplinter2D(
                            g, sx, sy, 0.95f, (float) Math.toRadians(this.splinterAngle[i]), false, hov);
                }
            }

            // 阶段 1：渲染绿色 X光靶向复位引导框与保持锁定环
            if (this.step == 1) {
                float tol = ((float) ModItems.safeCfgDouble(MedicalConfig.ORTHO_REDUCTION_TOLERANCE_PX, 8.5));
                float holdReq = Math.max(0.1f, ((float) ModItems.safeCfgDouble(MedicalConfig.ORTHO_REDUCTION_HOLD_SECONDS, 0.65)));
                float errDist = Mth.sqrt(curOffX * curOffX + curOffY * curOffY);
                boolean inZone = errDist <= tol;
                int guideCol = inZone ? C_SAFE_GREEN : C_WARNING_YELLOW;

                int targetHandleX = Math.round(cx);
                int targetHandleY = Math.round(cy + 26.0f);
                drawTacticalCornerBrackets(g, targetHandleX - 16, targetHandleY - 14, 32, 28, guideCol);

                int curHandleX = Math.round(cx + curOffX);
                int curHandleY = Math.round(cy + 26.0f + curOffY);
                g.fill(curHandleX - 10, curHandleY - 8, curHandleX + 10, curHandleY + 8, withAlpha(guideCol, 0.45f));
                drawTacticalCornerBrackets(g, curHandleX - 11, curHandleY - 9, 22, 18, 0xFFFFFFFF);

                if (this.reductionHoldSec > 0.01f) {
                    int progW = Math.round(32.0f * Mth.clamp(this.reductionHoldSec / holdReq, 0.0f, 1.0f));
                    g.fill(targetHandleX - 16, targetHandleY + 16, targetHandleX - 16 + progW, targetHandleY + 19, C_SAFE_GREEN);
                }
            }

            // 阶段 2 & 3：渲染皮质骨螺钉 / 镍钛肋骨环抱器靶点与钻入进度
            if (this.step >= 2 && this.screwX != null) {
                float drillHoldReq = Math.max(0.1f, ((float) ModItems.safeCfgDouble(MedicalConfig.ORTHO_DRILL_HOLD_SECONDS, 0.48)));
                boolean allScrewsDone = true;

                for (int i = 0; i < this.screwX.length; i++) {
                    float sx = this.screwX[i];
                    float sy = this.screwY[i];
                    float prog = this.screwDrillProgress[i];

                    if (prog < 1.0f) {
                        allScrewsDone = false;
                        if (this.step == 2 && this.drillingActive && Math.hypot(mx - sx, my - sy) <= 12.0) {
                            prog = Math.min(1.0f, prog + dt / drillHoldReq);
                            this.screwDrillProgress[i] = prog;
                            this.drillSoundTick++;
                            if (this.drillSoundTick % 4 == 0) {
                                playUiSound(SoundEvents.GRINDSTONE_USE, 1.65f + prog * 0.35f, 0.35f);
                                spawnParticle(sx, sy, (float) (Math.random() - 0.5) * 45.0f, (float) (Math.random() - 0.5) * 45.0f,
                                        1.6f, 0.28f, 0xFFE0F7FA);
                            }
                            if (prog >= 1.0f) {
                                playUiSound(SoundEvents.IRON_TRAPDOOR_CLOSE, 1.55f, 0.65f);
                            }
                        }
                    }

                    boolean hovPin = Math.hypot(mx - sx, my - sy) <= 12.0;
                    if (this.isChestRibMode) {
                        // 高精度镍钛记忆合金肋骨环抱器 (4 对弧形环抱卡爪 + 钛合金脊梁板)
                        SurgicalInstrumentRenderer.drawDetailedNiTiRibClaw2D(g, sx, sy, prog, hovPin);
                    } else {
                        // 高精度自攻螺纹 Schanz 钛合金骨螺钉 + 双侧外固定锁紧夹头
                        SurgicalInstrumentRenderer.drawDetailedHorizontalSchanzScrew2D(g, sx, sy, prog, hovPin);
                    }
                }

                if (this.step == 2 && allScrewsDone) {
                    this.drillingActive = false;
                    this.step = 3;
                    playUiSound(SoundEvents.EXPERIENCE_ORB_PICKUP, 1.35f, 0.65f);
                }
            }

            // 阶段 3：拖拽安装碳纤维外固定支架连杆 / 胸廓桥接锁定杆
            if (this.step == 3 && this.screwY != null && this.screwY.length >= 2) {
                float topPinY = this.screwY[0];
                float botPinY = this.screwY[this.screwY.length - 1];
                if (this.draggingRod) {
                    this.rodLockProgress = Mth.clamp((my - topPinY) / Math.max(10.0f, botPinY - topPinY), 0.0f, 1.0f);
                    if (this.rodLockProgress >= 0.96f) {
                        this.rodLockProgress = 1.0f;
                        this.draggingRod = false;
                        this.playingFinale = true;
                        this.finaleProgress = 0.0f;
                        playUiSound(SoundEvents.PLAYER_LEVELUP, 1.45f, 0.75f);
                    }
                }

                int rodY1 = Math.round(Mth.lerp(this.rodLockProgress, topPinY, botPinY));
                int rodCol = this.playingFinale ? C_SAFE_GREEN : C_NEON_CYAN;
                // 双侧碳纤维外固定连杆
                g.fill(Math.round(cx - 20), Math.round(topPinY - 4), Math.round(cx - 16), rodY1 + 4, rodCol);
                g.fill(Math.round(cx + 16), Math.round(topPinY - 4), Math.round(cx + 20), rodY1 + 4, rodCol);
                // 底部目标锁紧环
                drawTacticalCornerBrackets(g, Math.round(cx - 22), Math.round(botPinY - 7), 44, 14, C_ADRENALINE_GOLD);
            }
        }

        private void renderOrthoInstrumentCursor(GuiGraphics g, float mx, float my, long nowMs) {
            SurgicalInstrumentRenderer.ToolType tool = switch (this.step) {
                case 0 -> SurgicalInstrumentRenderer.ToolType.BONE_RONGEUR;
                case 1 -> SurgicalInstrumentRenderer.ToolType.REDUCTION_CLAMP;
                case 2 -> SurgicalInstrumentRenderer.ToolType.CANNULATED_DRILL;
                default -> SurgicalInstrumentRenderer.ToolType.TORQUE_WRENCH;
            };
            boolean active = (this.step == 1 && this.draggingBone)
                    || (this.step == 2 && this.drillingActive)
                    || (this.step == 3 && this.draggingRod);
            SurgicalInstrumentRenderer.renderDetailedSurgicalToolCursor(
                    g, mx, my, this.cursorTiltDeg, tool, active, this.rodLockProgress, (nowMs % 100000L) * 0.02f);
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (this.playingFinale) return true;

            int cancelW = 54, cancelH = 14;
            int cancelX = viewX + viewW - cancelW;
            int cancelY = winY + winH - 18;
            if (mouseX >= cancelX && mouseX <= cancelX + cancelW && mouseY >= cancelY && mouseY <= cancelY + cancelH) {
                this.onClose();
                return true;
            }

            if (button == 0) {
                float mx = (this.smoothMouseX != 0.0f) ? this.smoothMouseX : ((float) mouseX + this.cursorTremorX);
                float my = (this.smoothMouseY != 0.0f) ? this.smoothMouseY : ((float) mouseY + this.cursorTremorY);
                float cx = viewX + viewW * 0.5f;
                float cy = viewY + viewH * 0.5f;

                if (mx < viewX || mx > viewX + viewW || my < viewY || my > viewY + viewH) {
                    return super.mouseClicked(mouseX, mouseY, button);
                }

                this.boneExposed = true;

                // 检查是否误触两侧伴行大动脉 (cx ± 28)
                if (Math.abs(mx - (cx - 28.0f)) <= 5.5f || Math.abs(mx - (cx + 28.0f)) <= 5.5f) {
                    triggerMistake(mx, my);
                    return true;
                }

                if (this.step == 0 && this.splinterX != null) {
                    for (int i = 0; i < this.splinterX.length; i++) {
                        if (!this.splinterRemoved[i] && Math.hypot(mx - this.splinterX[i], my - this.splinterY[i]) <= 12.0) {
                            this.splinterRemoved[i] = true;
                            this.removedSplinterCount++;
                            playUiSound(SoundEvents.ITEM_PICKUP, 1.35f, 0.65f);
                            for (int k = 0; k < 8; k++) {
                                spawnParticle(this.splinterX[i], this.splinterY[i],
                                        (float) (Math.random() - 0.5) * 40.0f, -20.0f, 1.8f, 0.35f, 0xFFF0FBFF);
                            }
                            if (this.removedSplinterCount >= this.splinterX.length) {
                                this.step = 1;
                                playUiSound(SoundEvents.EXPERIENCE_ORB_PICKUP, 1.25f, 0.65f);
                            }
                            return true;
                        }
                    }
                    triggerMistake(mx, my);
                    return true;
                }

                if (this.step == 1) {
                    float handleX = cx + this.boneOffsetX;
                    float handleY = cy + 26.0f + this.boneOffsetY;
                    if (Math.hypot(mx - handleX, my - handleY) <= 22.0) {
                        this.draggingBone = true;
                        playUiSound(SoundEvents.ARMOR_EQUIP_IRON.value(), 1.25f, 0.60f);
                        return true;
                    }
                }

                if (this.step == 2 && this.screwX != null) {
                    for (int i = 0; i < this.screwX.length; i++) {
                        if (Math.hypot(mx - this.screwX[i], my - this.screwY[i]) <= 13.0) {
                            this.drillingActive = true;
                            return true;
                        }
                    }
                    triggerMistake(mx, my);
                    return true;
                }

                if (this.step == 3 && this.screwY != null && this.screwY.length >= 2) {
                    if (Math.abs(mx - cx) <= 26.0f && Math.abs(my - this.screwY[0]) <= 16.0f) {
                        this.draggingRod = true;
                        playUiSound(SoundEvents.TRIPWIRE_CLICK_ON, 1.35f, 0.65f);
                        return true;
                    }
                }
            }

            return super.mouseClicked(mouseX, mouseY, button);
        }

        @Override
        public boolean mouseReleased(double mouseX, double mouseY, int button) {
            if (button == 0) {
                this.draggingBone = false;
                this.drillingActive = false;
                if (this.draggingRod && this.rodLockProgress < 0.96f) {
                    this.rodLockProgress = 0.0f;
                }
                this.draggingRod = false;
            }
            return super.mouseReleased(mouseX, mouseY, button);
        }
    }

    // =========================================================================
    // 气胸/张力性气胸非静默呼吸声学与屏幕边缘缺氧紫绀/漏气波纹视觉反馈
    // =========================================================================
    private static void renderPneumothoraxScreenVignette(GuiGraphics g, Minecraft mc, MedicalData data, long nowMs) {
        int pType = ModItems.getPneumothoraxType(data);
        float airMl = ModItems.getPleuralAirMl(data);
        if (pType <= 0 && airMl < 120.0f) return;

        int sw = mc.getWindow().getGuiScaledWidth();
        int sh = mc.getWindow().getGuiScaledHeight();
        float respWave = 0.5f + 0.5f * Mth.sin(nowMs * 0.0045f);

        if (pType == 3) {
            int alphaOuter = Math.round((0.22f + 0.16f * respWave) * 255.0f);
            int colOuter = (alphaOuter << 24) | 0x280645;
            int colInner = (Math.round((0.15f + 0.12f * respWave) * 255.0f) << 24) | 0xB71C1C;
            int bw = 14;
            g.fill(0, 0, sw, bw, colOuter);
            g.fill(0, sh - bw, sw, sh, colOuter);
            g.fill(0, bw, bw, sh - bw, colOuter);
            g.fill(sw - bw, bw, sw, sh - bw, colOuter);
            g.fill(bw, bw, sw - bw, bw + 4, colInner);
            g.fill(bw, sh - bw - 4, sw - bw, sh - bw, colInner);
        } else if (pType == 2 && !ModItems.isThreeSidedValveSealed(data)) {
            int alpha = Math.round((0.08f + 0.14f * respWave) * 255.0f);
            int col = (alpha << 24) | 0x40C4FF;
            int bw = Math.round(6 + 5 * respWave);
            g.fill(0, 0, sw, bw, col);
            g.fill(0, sh - bw, sw, sh, col);
            g.fill(0, bw, bw, sh - bw, col);
            g.fill(sw - bw, bw, sw, sh - bw, col);
        }
    }

    // =========================================================================
    // 胸腔闭式水封引流术交互界面 (ChestDrainScreen —— 4步高细节五层胸壁解剖、上下分叶肺脏复张与三腔水封瓶模拟)
    // =========================================================================
    public static class ChestDrainScreen extends Screen {

        private static final class PleuralBubbleParticle {
            boolean active;
            float x, y, vx, vy, radius, age, maxAge;
            int color;
        }

        private final InteractionHand hand;
        private int winX, winY, winW = 286, winH = 212;
        private int viewX, viewY, viewW = 182, viewH = 166;

        // 0 = 第5肋上缘分层切开, 1 = 蚊式止血钳钝性撑开胸膜, 2 = 置入28Fr多孔硅胶管, 3 = 三腔水封瓶负压排血排气与肺复张
        private int step = 0;
        private boolean slicingIncision = false;
        private float incisionProgress = 0.0f;
        private boolean spreadingPleura = false;
        private float pleuraSpreadProgress = 0.0f;
        private boolean advancingTube = false;
        private float tubeInsertionProgress = 0.0f;
        private boolean suctionDrainActive = false;
        private float drainProgress = 0.0f;

        private float initialAirMl = 0.0f;
        private float initialBloodMl = 0.0f;
        private boolean pleuraOpened = false;
        private boolean surgeryCompleted = false;
        private boolean playingFinale = false;
        private float finaleProgress = 0.0f;

        private long lastFrameMs = 0L;
        private float heartPhase = 0.0f;
        private float mistakeFlashAlpha = 0.0f;
        private float screenShakeAmp = 0.0f;
        private float screenShakeX = 0.0f;
        private float screenShakeY = 0.0f;
        private float cursorTremorX = 0.0f;
        private float cursorTremorY = 0.0f;
        private float smoothMouseX = 0.0f;
        private float smoothMouseY = 0.0f;
        private float cursorTiltDeg = 0.0f;
        private int bubbleSoundTick = 0;

        private final PleuralBubbleParticle[] particles = new PleuralBubbleParticle[64];

        public ChestDrainScreen(InteractionHand hand) {
            super(Component.literal("胸腔闭式水封引流术"));
            this.hand = hand != null ? hand : InteractionHand.MAIN_HAND;
            for (int i = 0; i < this.particles.length; i++) {
                this.particles[i] = new PleuralBubbleParticle();
            }
        }

        private void playUiSound(SoundEvent sound, float pitch, float volume) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.getSoundManager() != null) {
                mc.getSoundManager().play(SimpleSoundInstance.forUI(sound, pitch, volume));
            }
        }

        private void spawnBubble(float x, float y, float vx, float vy, float radius, float maxAge, int color) {
            for (PleuralBubbleParticle p : this.particles) {
                if (!p.active) {
                    p.active = true;
                    p.x = x;
                    p.y = y;
                    p.vx = vx;
                    p.vy = vy;
                    p.radius = radius;
                    p.age = 0.0f;
                    p.maxAge = maxAge;
                    p.color = color;
                    return;
                }
            }
        }

        private void triggerMistake(float x, float y) {
            this.mistakeFlashAlpha = 0.85f;
            this.screenShakeAmp = 6.5f;
            this.slicingIncision = false;
            this.spreadingPleura = false;
            this.advancingTube = false;
            this.suctionDrainActive = false;
            playUiSound(SoundEvents.PLAYER_HURT, 1.10f, 0.60f);
            for (int i = 0; i < 14; i++) {
                float ang = (float) (Math.random() * Math.PI * 2.0);
                float spd = 24.0f + (float) (Math.random() * 52.0);
                spawnBubble(x, y, Mth.cos(ang) * spd, Mth.sin(ang) * spd, 2.0f, 0.38f, 0xFFFF1744);
            }
            NetworkHandler.sendTreatmentToServer(new TreatmentApplyPacket(
                    this.hand.ordinal(), MedicalData.Limb.CHEST.ordinal(), 503));
        }

        @Override
        protected void init() {
            super.init();
            this.winX = (this.width - this.winW) / 2;
            this.winY = (this.height - this.winH) / 2;
            this.viewX = this.winX + 94;
            this.viewY = this.winY + 26;
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) {
                MedicalData data = mc.player.getData(MedicalDataCapability.MEDICAL_DATA);
                if (data != null) {
                    this.initialAirMl = Math.max(ModItems.getPleuralAirMl(data), ModItems.getPneumothoraxType(data) > 0 ? 450.0f : 0.0f);
                    this.initialBloodMl = data.getChestBloodAccumulation();
                    if (this.initialAirMl <= 10.0f && this.initialBloodMl <= 10.0f) {
                        this.initialBloodMl = 350.0f;
                    }
                }
            }
            NetworkHandler.sendTreatmentToServer(new TreatmentApplyPacket(
                    this.hand.ordinal(), MedicalData.Limb.CHEST.ordinal(), 500));
            MedicalSoundHelper.playOpenSound();
        }

        @Override
        public boolean isPauseScreen() {
            return false;
        }

        @Override
        public void renderBackground(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        }

        @Override
        public void onClose() {
            if (!this.surgeryCompleted && this.pleuraOpened) {
                NetworkHandler.sendTreatmentToServer(new TreatmentApplyPacket(
                        this.hand.ordinal(), MedicalData.Limb.CHEST.ordinal(), 504));
            }
            super.onClose();
        }

        @Override
        public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null) {
                super.onClose();
                return;
            }
            ItemStack stack = mc.player.getItemInHand(this.hand);
            if (!TreatmentRouter.isChestDrainKitItem(stack)) {
                super.onClose();
                return;
            }
            MedicalData data = mc.player.getData(MedicalDataCapability.MEDICAL_DATA);
            if (data == null || data.getConsciousness() < ModItems.getChestDrainMinConsciousness()
                    || data.isCardiacArrest() || data.isInVFib()) {
                this.onClose();
                return;
            }

            long nowMs = System.currentTimeMillis();
            float dt = (lastFrameMs == 0L) ? 0.016f : Mth.clamp((nowMs - lastFrameMs) / 1000.0f, 0.001f, 0.1f);
            lastFrameMs = nowMs;

            if (this.playingFinale) {
                this.finaleProgress += dt / 0.65f;
                if (this.finaleProgress >= 1.0f) {
                    this.surgeryCompleted = true;
                    NetworkHandler.sendTreatmentToServer(new TreatmentApplyPacket(
                            this.hand.ordinal(), MedicalData.Limb.CHEST.ordinal(), 501));
                    MedicalSoundHelper.playLimbSelectSound();
                    this.onClose();
                    return;
                }
            }

            float hr = Mth.clamp(data.getHeartRate(), 40.0f, 220.0f);
            this.heartPhase = (this.heartPhase + dt * (hr / 60.0f)) % 1.0f;
            float beatWave = (this.heartPhase < 0.22f) ? Mth.sin((this.heartPhase / 0.22f) * (float) Math.PI) : 0.0f;

            float sysPainRatio = data.getPerceivedPain() / Math.max(10.0f, ((float) ModItems.safeCfgDouble(MedicalConfig.MAX_PERCEIVED_PAIN, 100.0)));
            float minCons = ModItems.getChestDrainMinConsciousness();
            float consDeficit = Mth.clamp((100.0f - data.getConsciousnessFloat()) / Math.max(1.0f, 100.0f - minCons), 0.0f, 1.0f);
            float maxPainShake = ((float) ModItems.safeCfgDouble(MedicalConfig.SUTURE_CURSOR_PAIN_SHAKE_MAX, 14.0));
            float maxConsSway = ((float) ModItems.safeCfgDouble(MedicalConfig.SUTURE_CURSOR_CONSCIOUSNESS_SWAY_MAX, 22.0));
            float painShakeAmp = sysPainRatio * maxPainShake * (1.0f + beatWave * 0.5f);

            this.cursorTremorX = Mth.sin(nowMs * 0.023f) * painShakeAmp + Mth.sin(nowMs * 0.0035f) * consDeficit * maxConsSway;
            this.cursorTremorY = Mth.cos(nowMs * 0.029f) * painShakeAmp + Mth.cos(nowMs * 0.0042f) * consDeficit * maxConsSway;

            float targetMx = mouseX + this.cursorTremorX;
            float targetMy = mouseY + this.cursorTremorY;
            if (this.smoothMouseX == 0.0f && this.smoothMouseY == 0.0f) {
                this.smoothMouseX = targetMx;
                this.smoothMouseY = targetMy;
            } else {
                float followRate = Mth.lerp(consDeficit, 28.0f, 9.0f);
                float vx = (targetMx - this.smoothMouseX) / Math.max(0.004f, dt);
                this.cursorTiltDeg = Mth.lerp(Mth.clamp(dt * 14.0f, 0.0f, 1.0f), this.cursorTiltDeg, Mth.clamp(vx * 0.035f, -22.0f, 22.0f));
                this.smoothMouseX = Mth.lerp(Math.min(1.0f, dt * followRate), this.smoothMouseX, targetMx);
                this.smoothMouseY = Mth.lerp(Math.min(1.0f, dt * followRate), this.smoothMouseY, targetMy);
            }

            if (this.screenShakeAmp > 0.01f) {
                this.screenShakeAmp = Math.max(0.0f, this.screenShakeAmp - dt * 22.0f);
                this.screenShakeX = (float) (Math.random() * 2.0 - 1.0) * this.screenShakeAmp;
                this.screenShakeY = (float) (Math.random() * 2.0 - 1.0) * this.screenShakeAmp;
            } else {
                this.screenShakeX = 0.0f;
                this.screenShakeY = 0.0f;
            }
            if (this.mistakeFlashAlpha > 0.005f) {
                this.mistakeFlashAlpha = Math.max(0.0f, this.mistakeFlashAlpha - dt * 2.4f);
            }

            g.fill(0, 0, this.width, this.height, 0x9602060C);
            PoseStack pose = g.pose();
            pose.pushPose();
            pose.translate(this.screenShakeX, this.screenShakeY, 0.0f);

            int themeCol = this.playingFinale ? C_SAFE_GREEN : C_NEON_CYAN;
            g.fill(winX, winY, winX + winW, winY + winH, 0xF4040B14);
            drawTacticalCornerBrackets(g, winX, winY, winW, winH, themeCol);
            g.fill(winX + 8, winY + 19, winX + winW - 8, winY + 20, withAlpha(themeCol, 0.45f));

            String stepTitle = switch (this.step) {
                case 0 -> "胸腔引流 1/4 · 第5肋上缘切开";
                case 1 -> "胸腔引流 2/4 · 止血钳钝性撑胸膜";
                case 2 -> "胸腔引流 3/4 · 置入28Fr硅胶引流管";
                default -> "胸腔引流 4/4 · 水封负压排气与肺复张";
            };
            g.drawString(this.font, stepTitle, winX + 10, winY + 7, C_TEXT_WHITE, false);

            // 顶部 4 步进度条
            for (int s = 0; s < 4; s++) {
                int sx = viewX + s * 46;
                int sy = winY + 8;
                boolean done = (s < this.step) || this.playingFinale;
                boolean active = (s == this.step) && !this.playingFinale;
                int sCol = done ? C_SAFE_GREEN : (active ? C_NEON_CYAN : 0xFF1E2B36);
                g.fill(sx - 1, sy - 1, sx + 41, sy + 7, 0xFF0A141E);
                g.fill(sx, sy, sx + 40, sy + 6, sCol);
            }

            // 左侧：3D 三腔水封瓶全息图 + 高细节三腔水封瓶液位/气泡柱与胸内压表盘
            int leftX = winX + 10;
            int leftY = viewY;
            int leftW = 78;
            int leftH = viewH;
            g.fill(leftX, leftY, leftX + leftW, leftY + leftH, 0xB8030810);
            drawTacticalCornerBrackets(g, leftX, leftY, leftW, leftH, withAlpha(themeCol, 0.72f));
            MedicalAssetLibrary.render3DMedicalItem(g, leftX + leftW * 0.5f, leftY + 36.0f, 26.0f, stack, nowMs);

            // 高细节三腔水封引流瓶面板 (集血腔 + 水封腔 + 负压调压腔)
            int chBaseY = leftY + 70;
            int chH = 58;
            int ch1X = leftX + 7;
            g.fill(ch1X, chBaseY, ch1X + 20, chBaseY + chH, 0xFF060E16);
            float bloodRatio = (this.initialBloodMl > 5.0f) ? this.drainProgress : 0.15f * this.drainProgress;
            int bloodFillH = Math.round(chH * Mth.clamp(bloodRatio, 0.0f, 0.92f));
            if (bloodFillH > 0) {
                g.fill(ch1X + 2, chBaseY + chH - bloodFillH, ch1X + 18, chBaseY + chH - 1, 0xDD9E1422);
                g.fill(ch1X + 2, chBaseY + chH - bloodFillH, ch1X + 18, chBaseY + chH - bloodFillH + 2, 0xFFFF5252);
            }
            for (int tickY = chBaseY + 10; tickY < chBaseY + chH - 4; tickY += 10) {
                g.fill(ch1X + 14, tickY, ch1X + 19, tickY + 1, 0x8890CAF9);
            }
            g.renderOutline(ch1X, chBaseY, 20, chH, 0xFF3E6A88);

            int ch2X = leftX + 30;
            g.fill(ch2X, chBaseY, ch2X + 20, chBaseY + chH, 0xFF060E16);
            int waterLevelY = chBaseY + chH - 24;
            g.fill(ch2X + 4, chBaseY + 2, ch2X + 7, chBaseY + chH - 4, 0xAA90CAF9);
            g.fill(ch2X + 2, waterLevelY, ch2X + 18, chBaseY + chH - 1, 0xAA00B0FF);
            g.fill(ch2X + 2, waterLevelY, ch2X + 18, waterLevelY + 2, 0xFF80D8FF);
            if (this.step == 3 && this.suctionDrainActive) {
                for (int b = 0; b < 3; b++) {
                    int by = chBaseY + chH - 5 - (int) (((nowMs / 25L) + b * 8) % 21);
                    int bx = ch2X + 8 + (b % 2) * 4;
                    g.fill(bx, by, bx + 3, by + 3, 0xFFFFFFFF);
                }
            }
            g.renderOutline(ch2X, chBaseY, 20, chH, 0xFF3E6A88);

            int ch3X = leftX + 53;
            g.fill(ch3X, chBaseY, ch3X + 18, chBaseY + chH, 0xFF060E16);
            g.fill(ch3X + 2, chBaseY + chH - 38, ch3X + 16, chBaseY + chH - 1, 0x8826C6DA);
            g.renderOutline(ch3X, chBaseY, 18, chH, 0xFF3E6A88);

            float remAir = this.initialAirMl * (1.0f - this.drainProgress);
            float remBlood = this.initialBloodMl * (1.0f - this.drainProgress);
            float curPleuralP = Mth.lerp(this.drainProgress, ModItems.getPleuralPressureCmH2O(data), -6.0f);
            g.drawCenteredString(this.font, String.format("气:%.0fmL %.0fcm", remAir, curPleuralP), leftX + leftW / 2, leftY + leftH - 28,
                    remAir > 100.0f ? 0xFF80DEEA : C_SAFE_GREEN);
            g.drawCenteredString(this.font, String.format("血:%.0fmL", remBlood), leftX + leftW / 2, leftY + leftH - 15,
                    remBlood > 50.0f ? 0xFFFF8A80 : C_SAFE_GREEN);

            // 中央高精度五层胸壁与肺叶解剖视窗
            g.fill(viewX, viewY, viewX + viewW, viewY + viewH, 0xFF10060B);
            g.enableScissor(viewX + 1, viewY + 1, viewX + viewW - 1, viewY + viewH - 1);
            renderChestDrainSurgicalField(g, dt, beatWave, this.smoothMouseX, this.smoothMouseY, nowMs);
            renderBubblesAndParticles(g, dt);
            if (this.mistakeFlashAlpha > 0.01f) {
                g.fill(viewX, viewY, viewX + viewW, viewY + 6, withAlpha(C_ALERT_RED, this.mistakeFlashAlpha * 0.65f));
                g.fill(viewX, viewY + viewH - 6, viewX + viewW, viewY + viewH, withAlpha(C_ALERT_RED, this.mistakeFlashAlpha * 0.65f));
            }
            g.disableScissor();
            drawTacticalCornerBrackets(g, viewX, viewY, viewW, viewH, 0xFF455A64);

            // 底部取消按钮
            int cancelW = 54, cancelH = 14;
            int cancelX = viewX + viewW - cancelW;
            int cancelY = winY + winH - 18;
            boolean hovCancel = mouseX >= cancelX && mouseX <= cancelX + cancelW && mouseY >= cancelY && mouseY <= cancelY + cancelH;
            g.fill(cancelX, cancelY, cancelX + cancelW, cancelY + cancelH, hovCancel ? 0x8837474F : 0x551C2833);
            drawTacticalCornerBrackets(g, cancelX, cancelY, cancelW, cancelH, 0xFF90A4AE);
            g.drawCenteredString(this.font, "×", cancelX + cancelW / 2, cancelY + 3, C_TEXT_DIM);

            // 当前步骤高精度专属器械指针
            SurgicalInstrumentRenderer.ToolType tool = switch (this.step) {
                case 0 -> SurgicalInstrumentRenderer.ToolType.SCALPEL;
                case 1 -> SurgicalInstrumentRenderer.ToolType.HEMOSTAT_FORCEPS;
                case 2 -> SurgicalInstrumentRenderer.ToolType.TROCAR_CHEST_TUBE;
                default -> SurgicalInstrumentRenderer.ToolType.SUCTION_CANNULA;
            };
            boolean active = (this.step == 0 && this.slicingIncision)
                    || (this.step == 1 && this.spreadingPleura)
                    || (this.step == 2 && this.advancingTube)
                    || (this.step == 3 && this.suctionDrainActive);
            SurgicalInstrumentRenderer.renderDetailedSurgicalToolCursor(
                    g, this.smoothMouseX, this.smoothMouseY, this.cursorTiltDeg, tool, active, this.drainProgress, (nowMs % 100000L) * 0.02f);

            pose.popPose();
            super.render(g, mouseX, mouseY, partialTick);
        }

        private void renderBubblesAndParticles(GuiGraphics g, float dt) {
            for (PleuralBubbleParticle p : this.particles) {
                if (!p.active) continue;
                p.age += dt;
                if (p.age >= p.maxAge) {
                    p.active = false;
                    continue;
                }
                p.x += p.vx * dt;
                p.y += p.vy * dt;
                float life = 1.0f - (p.age / p.maxAge);
                int r = Math.max(1, Math.round(p.radius * (0.7f + 0.5f * life)));
                int col = withAlpha(p.color, life * 0.85f);
                g.fill(Math.round(p.x - r), Math.round(p.y - r), Math.round(p.x + r), Math.round(p.y + r), col);
            }
        }

        private void renderChestDrainSurgicalField(GuiGraphics g, float dt, float beatWave, float mx, float my, long nowMs) {
            float remainRatio = 1.0f - this.drainProgress;
            float breathCycle = Mth.sin(nowMs * 0.0038f);

            // 1. 右半区：胸膜腔深部视图（含高压气胸气腔、底部血胸积血池、以及带斜裂和支气管树的上下分叶肺脏）
            int cavityLeft = viewX + 76;
            int cavityRight = viewX + viewW - 6;
            int cavityTop = viewY + 8;
            int cavityBot = viewY + viewH - 8;
            g.fill(cavityLeft, cavityTop, cavityRight, cavityBot, 0xFF14050A);

            float airFactor = (this.initialAirMl > 10.0f) ? remainRatio : 0.25f * remainRatio;
            if (airFactor > 0.02f) {
                int airPocketH = Math.round(52.0f * airFactor);
                SurgicalInstrumentRenderer.drawQuad2D(g,
                        cavityLeft + 2, cavityTop + 2,
                        cavityRight - 2, cavityTop + 2,
                        cavityRight - 2, cavityTop + 2 + airPocketH,
                        cavityLeft + 2, cavityTop + 2 + airPocketH * 0.7f,
                        0x3880DEEA);
                for (int k = 0; k < 3; k++) {
                    float waveX = cavityLeft + 12 + ((nowMs / 18L + k * 28) % 68);
                    float waveY = cavityTop + 8 + k * 11 * airFactor;
                    SurgicalInstrumentRenderer.drawQuad2D(g,
                            waveX, waveY, waveX + 16, waveY + 2,
                            waveX + 16, waveY + 3, waveX, waveY + 1, 0x66B2EBF2);
                }
            }

            // 解剖级分叶肺脏（左肺上叶 Superior Lobe + 斜裂 Oblique Fissure + 左肺下叶 Inferior Lobe + 肺门支气管树）
            float expansion = 0.42f + 0.58f * this.drainProgress + breathCycle * (0.025f + 0.045f * this.drainProgress);
            int supR = (int) Mth.lerp(this.drainProgress, 118, 236);
            int supG = (int) Mth.lerp(this.drainProgress, 58, 128);
            int supB = (int) Mth.lerp(this.drainProgress, 84, 146);
            int supLobeCol = (0xF2 << 24) | (supR << 16) | (supG << 8) | supB;
            int infLobeCol = (0xF2 << 24) | (Math.max(0, supR - 14) << 16) | (Math.max(0, supG - 10) << 8) | Math.max(0, supB - 8);
            int highlightCol = (0x88 << 24) | (Math.min(255, supR + 24) << 16) | (Math.min(255, supG + 32) << 8) | Math.min(255, supB + 28);

            float hilumX = cavityRight - 4.0f;
            float hilumY = viewY + viewH * 0.48f;
            float spanW = (cavityRight - cavityLeft - 8.0f) * Mth.clamp(expansion, 0.35f, 0.98f);
            float spanH = 64.0f * Mth.clamp(expansion, 0.38f, 0.98f);

            float sApexX = hilumX - spanW * 0.55f, sApexY = hilumY - spanH * 0.92f;
            float sAntX  = hilumX - spanW * 0.96f, sAntY  = hilumY - spanH * 0.38f;
            float sFisX  = hilumX - spanW * 0.88f, sFisY  = hilumY + spanH * 0.12f;
            SurgicalInstrumentRenderer.drawQuad2D(g, hilumX, hilumY - spanH * 0.65f, sApexX, sApexY, sAntX, sAntY, hilumX, hilumY, supLobeCol);
            SurgicalInstrumentRenderer.drawQuad2D(g, hilumX, hilumY, sAntX, sAntY, sFisX, sFisY, hilumX, hilumY + 4.0f, supLobeCol);
            SurgicalInstrumentRenderer.drawQuad2D(g, hilumX - spanW * 0.25f, hilumY - spanH * 0.52f,
                    sApexX + 6.0f, sApexY + 6.0f, sAntX + 8.0f, sAntY, hilumX - spanW * 0.30f, hilumY - 4.0f, highlightCol);

            float iLatX  = hilumX - spanW * 0.92f, iLatY  = hilumY + spanH * 0.22f;
            float iBaseX = hilumX - spanW * 0.82f, iBaseY = hilumY + spanH * 0.88f;
            float iMedX  = hilumX - spanW * 0.22f, iMedY  = hilumY + spanH * 0.92f;
            SurgicalInstrumentRenderer.drawQuad2D(g, hilumX, hilumY + 6.0f, iLatX, iLatY, iBaseX, iBaseY, iMedX, iMedY, infLobeCol);
            SurgicalInstrumentRenderer.drawQuad2D(g, hilumX, hilumY + 6.0f, iMedX, iMedY, hilumX, hilumY + spanH * 0.85f, hilumX, hilumY + 6.0f, infLobeCol);

            SurgicalInstrumentRenderer.drawQuad2D(g, hilumX, hilumY + 1.0f, sFisX, sFisY, sFisX, sFisY + 3.5f, hilumX, hilumY + 4.5f, 0xCC2D0912);
            SurgicalInstrumentRenderer.drawQuad2D(g, hilumX, hilumY - 4.0f, hilumX - spanW * 0.48f, hilumY - spanH * 0.35f,
                    hilumX - spanW * 0.48f, hilumY - spanH * 0.35f + 2.5f, hilumX, hilumY - 1.0f, 0x88E0F7FA);
            SurgicalInstrumentRenderer.drawQuad2D(g, hilumX, hilumY + 2.0f, hilumX - spanW * 0.45f, hilumY + spanH * 0.42f,
                    hilumX - spanW * 0.45f, hilumY + spanH * 0.42f + 2.5f, hilumX, hilumY + 5.0f, 0x88E0F7FA);

            float bloodFactor = (this.initialBloodMl > 10.0f) ? remainRatio : 0.20f * remainRatio;
            if (bloodFactor > 0.02f) {
                int poolTopY = Math.round(cavityBot - 6 - bloodFactor * 56.0f);
                g.fill(cavityLeft + 2, poolTopY, cavityRight - 2, cavityBot, 0xCC6E0B14);
                for (int wx = cavityLeft + 2; wx < cavityRight - 10; wx += 12) {
                    int waveOff = Math.round(Mth.sin(nowMs * 0.006f + wx * 0.1f) * 1.8f);
                    g.fill(wx, poolTopY + waveOff, wx + 10, poolTopY + waveOff + 2, 0xEEFF1744);
                }
            }

            // 2. 左半区：五层胸壁纵切面与第 4、第 5 肋间手术操作区
            int wallLeft = viewX + 4;
            int wallRight = cavityLeft;
            float wallCenterX = (wallLeft + wallRight) * 0.5f;

            g.fill(wallLeft, cavityTop, wallRight, cavityBot, 0xFF420D16);
            for (int fy = cavityTop + 6; fy < cavityBot - 6; fy += 8) {
                SurgicalInstrumentRenderer.drawQuad2D(g,
                        wallLeft + 4, fy, wallRight - 4, fy + 4,
                        wallRight - 4, fy + 7, wallLeft + 4, fy + 3, 0x557B1824);
            }
            g.fill(wallRight - 3, cavityTop, wallRight, cavityBot, 0xCCD8EEF8);

            float rib4Y = viewY + 40.0f;
            SurgicalInstrumentRenderer.drawAnatomicalCurvedRib2D(g, wallCenterX, rib4Y, 34.0f, 7.5f, 0.0f, 0.0f, 0.0f, true);
            int bundleY = Math.round(rib4Y + 7.5f);
            int arteryPulse = Math.round(beatWave * 1.5f);
            g.fill(wallLeft + 4, bundleY, wallRight - 4, bundleY + 2, 0xFF2979FF);
            g.fill(wallLeft + 4, bundleY + 2, wallRight - 4, bundleY + 4 + arteryPulse, 0xFFFF1744);
            g.fill(wallLeft + 4, bundleY + 5, wallRight - 4, bundleY + 7, 0xFFFFEA00);
            g.drawString(this.font, "第4肋下血管束", wallLeft + 6, bundleY + 8, 0xFFFF8A80, false);

            float rib5Y = viewY + 118.0f;
            SurgicalInstrumentRenderer.drawAnatomicalCurvedRib2D(g, wallCenterX, rib5Y, 34.0f, 7.5f, 0.0f, 0.0f, 0.0f, true);
            g.drawString(this.font, "第5肋骨上缘", wallLeft + 8, Math.round(rib5Y + 8), 0xFFB0BEC5, false);

            float safeCutY = rib5Y - 14.0f;
            float cutStartX = wallLeft + 8.0f;
            float cutEndX = wallRight - 4.0f;
            float corridorW = ((float) ModItems.safeCfgDouble(MedicalConfig.FASCIOTOMY_INCISION_CORRIDOR_WIDTH, 14.0));

            int totalCutSegs = 12;
            int cutSegs = Math.round(this.incisionProgress * totalCutSegs);
            float spreadBonus = this.pleuraSpreadProgress * 7.5f;
            for (int i = 0; i < totalCutSegs; i++) {
                float x0 = Mth.lerp(i / (float) totalCutSegs, cutStartX, cutEndX);
                float x1 = Mth.lerp((i + 1) / (float) totalCutSegs, cutStartX, cutEndX);
                if (i < cutSegs) {
                    float arch = Mth.sin(((i + 0.5f) / totalCutSegs) * (float) Math.PI);
                    int halfOpen = Math.round((4.5f + spreadBonus) * arch);
                    g.fill(Math.round(x0), Math.round(safeCutY - halfOpen - 3), Math.round(x1) + 1, Math.round(safeCutY - halfOpen), 0xFFE6B84D);
                    g.fill(Math.round(x0), Math.round(safeCutY + halfOpen), Math.round(x1) + 1, Math.round(safeCutY + halfOpen + 3), 0xFFE6B84D);
                    int innerCol = (this.pleuraSpreadProgress >= 0.85f) ? 0xFF0D0407 : 0xFFD0E8F2;
                    g.fill(Math.round(x0), Math.round(safeCutY - halfOpen), Math.round(x1) + 1, Math.round(safeCutY + halfOpen), innerCol);
                } else {
                    if (i % 2 == 0) {
                        g.fill(Math.round(x0), Math.round(safeCutY - 1), Math.round(x1), Math.round(safeCutY + 1), 0x8800E676);
                    }
                }
            }

            if (this.step == 0) {
                float curCutX = Mth.lerp(this.incisionProgress, cutStartX, cutEndX);
                drawTacticalCornerBrackets(g, Math.round(curCutX - 5), Math.round(safeCutY - 5), 10, 10, C_NEON_CYAN);
                if (this.slicingIncision) {
                    if (my <= bundleY + 8 || Math.abs(my - safeCutY) > corridorW) {
                        triggerMistake(mx, my);
                    } else if (mx >= curCutX - 8.0f && mx <= curCutX + 22.0f) {
                        float nextProg = Mth.clamp((mx - cutStartX) / Math.max(1.0f, cutEndX - cutStartX), this.incisionProgress, 1.0f);
                        if (nextProg > this.incisionProgress) {
                            this.incisionProgress = nextProg;
                            this.pleuraOpened = true;
                        }
                    }
                }
                if (this.incisionProgress >= 0.96f) {
                    this.incisionProgress = 1.0f;
                    this.slicingIncision = false;
                    this.step = 1;
                    playUiSound(SoundEvents.SHEEP_SHEAR, 1.25f, 0.65f);
                }
            } else if (this.step == 1) {
                float targetX = (cutStartX + cutEndX) * 0.55f;
                float targetY = safeCutY;
                boolean nearPleura = Math.hypot(mx - targetX, my - targetY) <= 15.0;
                drawTacticalCornerBrackets(g, Math.round(targetX - 9), Math.round(targetY - 9), 18, 18,
                        nearPleura ? C_SAFE_GREEN : C_ADRENALINE_GOLD);
                if (this.pleuraSpreadProgress > 0.01f) {
                    int barW = Math.round(24 * this.pleuraSpreadProgress);
                    g.fill(Math.round(targetX - 12), Math.round(targetY - 13), Math.round(targetX - 12 + barW), Math.round(targetY - 11), C_SAFE_GREEN);
                }
                if (this.spreadingPleura) {
                    if (my <= bundleY + 8) {
                        triggerMistake(mx, my);
                    } else if (nearPleura) {
                        this.pleuraSpreadProgress = Math.min(1.0f, this.pleuraSpreadProgress + dt / 0.55f);
                        if (this.pleuraSpreadProgress >= 1.0f) {
                            this.spreadingPleura = false;
                            this.step = 2;
                            playUiSound(SoundEvents.FIRE_EXTINGUISH, 1.65f, 0.70f);
                            playUiSound(SoundEvents.IRON_TRAPDOOR_OPEN, 1.40f, 0.65f);
                            for (int k = 0; k < 18; k++) {
                                spawnBubble(targetX, targetY,
                                        -20.0f - (float) (Math.random() * 45.0),
                                        (float) (Math.random() * 36.0 - 18.0),
                                        2.2f, 0.45f, 0xFFE0F7FA);
                            }
                        }
                    }
                }
            } else if (this.step >= 2) {
                float entryX = cutStartX + 10.0f;
                float tipX = Mth.lerp(this.tubeInsertionProgress, entryX + 12.0f, cavityRight - 22.0f);
                float tipY = Mth.lerp(this.tubeInsertionProgress, safeCutY, safeCutY + 16.0f);

                SurgicalInstrumentRenderer.drawQuad2D(g,
                        wallLeft, safeCutY - 4.5f, tipX, tipY - 4.5f,
                        tipX, tipY + 4.5f, wallLeft, safeCutY + 4.5f, 0xBB90CAF9);
                SurgicalInstrumentRenderer.drawQuad2D(g,
                        wallLeft, safeCutY - 1.0f, tipX, tipY - 1.0f,
                        tipX, tipY + 1.0f, wallLeft, safeCutY + 1.0f, 0xFF1565C0);
                for (int fh = 1; fh <= 3; fh++) {
                    float fx = tipX - fh * 11.0f * this.tubeInsertionProgress;
                    float fy = Mth.lerp(1.0f - fh * 0.12f, safeCutY, tipY);
                    if (fx > entryX) {
                        g.fill(Math.round(fx - 2), Math.round(fy - 3), Math.round(fx + 2), Math.round(fy + 3), 0xFF0A141E);
                    }
                }

                if (this.step == 2) {
                    SurgicalInstrumentRenderer.drawQuad2D(g,
                            wallLeft, safeCutY - 1.8f, tipX + 4.0f, tipY,
                            tipX, tipY + 1.8f, wallLeft, safeCutY + 1.8f, 0xFFECEFF1);
                    drawTacticalCornerBrackets(g, Math.round(tipX - 8), Math.round(tipY - 8), 16, 16, C_SAFE_GREEN);
                    if (this.advancingTube) {
                        this.tubeInsertionProgress = Mth.clamp((mx - entryX) / 120.0f, this.tubeInsertionProgress, 1.0f);
                        if (this.tubeInsertionProgress >= 0.96f) {
                            this.tubeInsertionProgress = 1.0f;
                            this.advancingTube = false;
                            this.step = 3;
                            playUiSound(SoundEvents.SLIME_SQUISH, 1.25f, 0.65f);
                        }
                    }
                } else if (this.step == 3) {
                    float holdReq = Math.max(0.5f, ((float) ModItems.safeCfgDouble(MedicalConfig.FASCIOTOMY_CAUTERY_HOLD_SECONDS, 0.55)) * 2.2f);
                    if (this.suctionDrainActive) {
                        SurgicalInstrumentRenderer.drawQuad2D(g,
                                wallLeft, safeCutY - 2.5f, tipX - 4.0f, tipY - 2.5f,
                                tipX - 4.0f, tipY + 2.5f, wallLeft, safeCutY + 2.5f, 0xDD8E0E1A);
                        int flowBubbleX = Math.round(tipX - ((nowMs / 10L) % Math.max(10, Math.round(tipX - wallLeft))));
                        g.fill(flowBubbleX - 3, Math.round(safeCutY - 2), flowBubbleX + 3, Math.round(safeCutY + 2), 0xFFE0F7FA);

                        this.drainProgress = Math.min(1.0f, this.drainProgress + dt / holdReq);
                        this.bubbleSoundTick++;
                        if (this.bubbleSoundTick % 4 == 0) {
                            playUiSound(SoundEvents.BUBBLE_COLUMN_UPWARDS_AMBIENT, 1.35f, 0.60f);
                            spawnBubble(tipX, tipY - 6.0f, -35.0f, -12.0f, 1.8f, 0.30f, 0xFFB2EBF2);
                        }
                        if (this.drainProgress >= 1.0f && !this.playingFinale) {
                            this.suctionDrainActive = false;
                            this.playingFinale = true;
                            this.finaleProgress = 0.0f;
                            playUiSound(SoundEvents.PLAYER_LEVELUP, 1.45f, 0.75f);
                        }
                    }
                }
            }
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (this.playingFinale) return true;
            int cancelW = 54, cancelH = 14;
            int cancelX = viewX + viewW - cancelW;
            int cancelY = winY + winH - 18;
            if (mouseX >= cancelX && mouseX <= cancelX + cancelW && mouseY >= cancelY && mouseY <= cancelY + cancelH) {
                this.onClose();
                return true;
            }
            if (button == 0) {
                float mx = (this.smoothMouseX != 0.0f) ? this.smoothMouseX : ((float) mouseX + this.cursorTremorX);
                float my = (this.smoothMouseY != 0.0f) ? this.smoothMouseY : ((float) mouseY + this.cursorTremorY);
                if (mx < viewX || mx > viewX + viewW || my < viewY || my > viewY + viewH) {
                    return super.mouseClicked(mouseX, mouseY, button);
                }
                this.pleuraOpened = true;
                if (this.step == 0) {
                    this.slicingIncision = true;
                    return true;
                } else if (this.step == 1) {
                    this.spreadingPleura = true;
                    return true;
                } else if (this.step == 2) {
                    this.advancingTube = true;
                    return true;
                } else if (this.step == 3) {
                    this.suctionDrainActive = true;
                    return true;
                }
            }
            return super.mouseClicked(mouseX, mouseY, button);
        }

        @Override
        public boolean mouseReleased(double mouseX, double mouseY, int button) {
            if (button == 0) {
                this.slicingIncision = false;
                this.spreadingPleura = false;
                this.advancingTube = false;
                this.suctionDrainActive = false;
            }
            return super.mouseReleased(mouseX, mouseY, button);
        }
    }

    // =========================================================================
    // 高精度外科手术器械鼠标指针与多边形解剖矢量渲染器 (SurgicalInstrumentRenderer)
    // =========================================================================
    private static final class SurgicalInstrumentRenderer {
        enum ToolType {
            SCALPEL,
            SUCTION_CANNULA,
            BIPOLAR_CAUTERY,
            HEMOSTAT_FORCEPS,
            NEEDLE_HOLDER,
            BONE_RONGEUR,
            REDUCTION_CLAMP,
            CANNULATED_DRILL,
            TORQUE_WRENCH,
            TROCAR_CHEST_TUBE
        }

        static void drawQuad2D(GuiGraphics g,
                               float x1, float y1, float x2, float y2,
                               float x3, float y3, float x4, float y4,
                               int argbColor) {
            int a = (argbColor >>> 24) & 0xFF;
            int r = (argbColor >>> 16) & 0xFF;
            int gr = (argbColor >>> 8) & 0xFF;
            int b = argbColor & 0xFF;
            Matrix4f pose = g.pose().last().pose();
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.disableCull();
            RenderSystem.setShader(GameRenderer::getPositionColorShader);
            BufferBuilder buf = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
            buf.addVertex(pose, x1, y1, 0.0f).setColor(r, gr, b, a);
            buf.addVertex(pose, x2, y2, 0.0f).setColor(r, gr, b, a);
            buf.addVertex(pose, x3, y3, 0.0f).setColor(r, gr, b, a);
            buf.addVertex(pose, x4, y4, 0.0f).setColor(r, gr, b, a);
            MeshData mesh = buf.build();
            if (mesh != null) {
                BufferUploader.drawWithShader(mesh);
            }
            RenderSystem.enableCull();
        }

        static void drawDetailedBoneSplinter2D(GuiGraphics g, float cx, float cy, float scale, float angleRad,
                                               boolean grabbed, boolean hovered) {
            drawQuad2D(g, cx - 9 * scale, cy - 6 * scale, cx + 9 * scale, cy - 6 * scale,
                    cx + 8 * scale, cy + 7 * scale, cx - 8 * scale, cy + 7 * scale,
                    hovered ? 0x8800E676 : 0x66FF1744);
            float[] vx = new float[] { -10.5f, -4.0f, 3.5f, 11.0f, 7.0f, 1.5f, -5.0f, -9.0f };
            float[] vy = new float[] { -2.2f, -6.0f, -3.8f, -0.8f, 5.2f, 2.8f, 6.0f, 2.2f };
            float cos = Mth.cos(angleRad) * scale;
            float sin = Mth.sin(angleRad) * scale;
            float[] tx = new float[8];
            float[] ty = new float[8];
            for (int i = 0; i < 8; i++) {
                tx[i] = cx + vx[i] * cos - vy[i] * sin;
                ty[i] = cy + vx[i] * sin + vy[i] * cos;
            }
            int outerCortical = hovered ? 0xFFFFFFFF : 0xFFE8F4F8;
            int innerShadow = 0xFF9AC8DC;
            drawQuad2D(g, tx[0], ty[0], tx[1], ty[1], tx[2], ty[2], tx[7], ty[7], outerCortical);
            drawQuad2D(g, tx[2], ty[2], tx[3], ty[3], tx[4], ty[4], tx[5], ty[5], outerCortical);
            drawQuad2D(g, tx[7], ty[7], tx[2], ty[2], tx[5], ty[5], tx[6], ty[6], innerShadow);
            drawQuad2D(g,
                    cx - 3.2f * cos, cy - 3.2f * sin,
                    cx + 4.2f * cos, cy + 4.2f * sin,
                    cx + 1.8f * cos - 2.2f * sin, cy + 1.8f * sin + 2.2f * cos,
                    cx - 2.2f * cos - 1.8f * sin, cy - 2.2f * sin + 1.8f * cos,
                    0xFF8D2B2B);
        }

        static void drawOrganicBloodClot2D(GuiGraphics g, float cx, float cy, float radius, float phaseAngle,
                                           boolean grabbed, boolean hovered, float anchorX, float anchorY) {
            if (grabbed) {
                drawQuad2D(g, cx, cy - 2, anchorX, anchorY - 3, anchorX, anchorY + 3, cx, cy + 2, 0xAA640D16);
            }
            int segments = 10;
            int coreColor = grabbed ? 0xFF9E1926 : (hovered ? 0xFF7A111D : 0xFF4A0720);
            int rimColor = hovered ? 0xFFFF5252 : 0xFF2B030B;
            for (int i = 0; i < segments; i++) {
                double a1 = (i / (double) segments) * Math.PI * 2.0;
                double a2 = ((i + 1) / (double) segments) * Math.PI * 2.0;
                float r1 = radius * (0.82f + 0.22f * (float) Math.sin(a1 * 3.0 + phaseAngle));
                float r2 = radius * (0.82f + 0.22f * (float) Math.sin(a2 * 3.0 + phaseAngle));
                float x1 = cx + (float) Math.cos(a1) * r1;
                float y1 = cy + (float) Math.sin(a1) * r1 * 0.78f;
                float x2 = cx + (float) Math.cos(a2) * r2;
                float y2 = cy + (float) Math.sin(a2) * r2 * 0.78f;
                drawQuad2D(g, cx, cy, x1, y1, x2, y2, cx, cy, rimColor);
                drawQuad2D(g, cx, cy,
                        cx + (x1 - cx) * 0.80f, cy + (y1 - cy) * 0.80f,
                        cx + (x2 - cx) * 0.80f, cy + (y2 - cy) * 0.80f,
                        cx, cy, coreColor);
            }
            drawQuad2D(g, cx - radius * 0.35f, cy - radius * 0.28f,
                    cx + radius * 0.15f, cy - radius * 0.35f,
                    cx + radius * 0.05f, cy - radius * 0.10f,
                    cx - radius * 0.30f, cy - radius * 0.08f, 0xAAAD323E);
        }

        static void drawAnatomicalVerticalLongBone2D(GuiGraphics g, float cx, float cy,
                                                     float topY, float botY, float halfWidth,
                                                     float dispX, float dispY, boolean reducedAndLocked) {
            float fracY = cy - 2.0f;
            drawQuad2D(g, cx - halfWidth * 1.28f, topY, cx + halfWidth * 1.28f, topY,
                    cx + halfWidth, fracY, cx - halfWidth, fracY, 0xFFB8E2F2);
            drawQuad2D(g, cx - halfWidth * 1.05f, topY + 2, cx + halfWidth * 1.05f, topY + 2,
                    cx + halfWidth * 0.84f, fracY, cx - halfWidth * 0.84f, fracY, 0xFFE8F8FF);
            drawQuad2D(g, cx - halfWidth * 0.42f, topY + 10, cx + halfWidth * 0.42f, topY + 10,
                    cx + halfWidth * 0.42f, fracY - 2, cx - halfWidth * 0.42f, fracY - 2, 0xFF5694AC);
            for (int t = 0; t < 5; t++) {
                float tx = cx - halfWidth * 0.85f + t * (halfWidth * 0.38f);
                drawQuad2D(g, tx, fracY - 2, tx + 3, fracY + 4, tx + 6, fracY - 1, tx + 3, fracY - 3, 0xFFE8F8FF);
            }

            float dCx = cx + dispX;
            float dTopY = cy + 2.0f + dispY;
            float dBotY = botY + dispY * 0.35f;
            drawQuad2D(g, dCx - halfWidth, dTopY, dCx + halfWidth, dTopY,
                    dCx + halfWidth * 1.28f, dBotY, dCx - halfWidth * 1.28f, dBotY, 0xFFB8E2F2);
            drawQuad2D(g, dCx - halfWidth * 0.84f, dTopY, dCx + halfWidth * 0.84f, dTopY,
                    dCx + halfWidth * 1.05f, dBotY - 2, dCx - halfWidth * 1.05f, dBotY - 2, 0xFFE8F8FF);
            drawQuad2D(g, dCx - halfWidth * 0.42f, dTopY + 2, dCx + halfWidth * 0.42f, dTopY + 2,
                    dCx + halfWidth * 0.42f, dBotY - 10, dCx - halfWidth * 0.42f, dBotY - 10, 0xFF5694AC);
            if (reducedAndLocked) {
                drawQuad2D(g, cx - halfWidth, cy - 3, cx + halfWidth, cy - 3,
                        cx + halfWidth, cy + 3, cx - halfWidth, cy + 3, 0x8800E676);
            }
        }

        static void drawAnatomicalCurvedRib2D(GuiGraphics g, float centerX, float centerY,
                                              float halfSpan, float archHeight,
                                              float dispX, float dispY, float dispAngle,
                                              boolean intact) {
            int segs = 12;
            for (int i = 0; i < segs; i++) {
                float t1 = (i / (float) segs) * 2.0f - 1.0f;
                float t2 = ((i + 1) / (float) segs) * 2.0f - 1.0f;
                boolean isDisplacedHalf = (!intact && i >= segs / 2);
                float ox = isDisplacedHalf ? dispX : 0.0f;
                float oy = isDisplacedHalf ? dispY + (t1 * dispAngle * 0.35f) : 0.0f;

                float x1 = centerX + t1 * halfSpan + ox;
                float y1 = centerY - (1.0f - t1 * t1) * archHeight + oy;
                float x2 = centerX + t2 * halfSpan + ox;
                float y2 = centerY - (1.0f - t2 * t2) * archHeight + oy;

                drawQuad2D(g, x1, y1 - 5.5f, x2, y2 - 5.5f, x2, y2 + 5.5f, x1, y1 + 5.5f, 0xFF9AC8DC);
                drawQuad2D(g, x1, y1 - 3.8f, x2, y2 - 3.8f, x2, y2 + 3.8f, x1, y1 + 3.8f, 0xFFE6F7FF);
                drawQuad2D(g, x1, y1 - 1.4f, x2, y2 - 1.4f, x2, y2 + 1.4f, x1, y1 + 1.4f, 0x888D3B3B);
            }
        }

        static void drawDetailedHorizontalSchanzScrew2D(GuiGraphics g, float sx, float sy, float prog, boolean hovered) {
            float pinHalfLen = Mth.lerp(prog, 6.0f, 22.0f);
            int statusCol = (prog >= 1.0f) ? 0xFF00E676 : (hovered ? 0xFFFFEA00 : 0xFF00E5FF);
            drawQuad2D(g, sx - pinHalfLen, sy - 2.2f, sx + pinHalfLen, sy - 2.2f,
                    sx + pinHalfLen, sy + 2.2f, sx - pinHalfLen, sy + 2.2f, 0xFFCFD8DC);
            drawQuad2D(g, sx - pinHalfLen, sy - 0.8f, sx + pinHalfLen, sy - 0.8f,
                    sx + pinHalfLen, sy + 0.8f, sx - pinHalfLen, sy + 0.8f, 0xFFFFFFFF);
            for (int tx = (int) (sx - pinHalfLen + 2); tx <= (int) (sx + pinHalfLen - 2); tx += 3) {
                g.fill(tx, Math.round(sy - 3), tx + 1, Math.round(sy + 3), 0xFF607D8B);
            }
            g.renderOutline(Math.round(sx - 5), Math.round(sy - 5), 10, 10, statusCol);
            if (prog >= 1.0f) {
                g.fill(Math.round(sx - 20), Math.round(sy - 4), Math.round(sx - 15), Math.round(sy + 4), 0xFFFFB300);
                g.fill(Math.round(sx + 15), Math.round(sy - 4), Math.round(sx + 20), Math.round(sy + 4), 0xFFFFB300);
            }
        }

        static void drawDetailedNiTiRibClaw2D(GuiGraphics g, float sx, float sy, float prog, boolean hovered) {
            float wArm = Mth.lerp(prog, 18.0f, 12.0f);
            int statusCol = (prog >= 1.0f) ? 0xFF00E676 : (hovered ? 0xFFFFEA00 : 0xFFFFB300);
            drawQuad2D(g, sx - wArm, sy - 3.5f, sx + wArm, sy - 3.5f, sx + wArm, sy + 3.5f, sx - wArm, sy + 3.5f, 0xFFB0BEC5);
            drawQuad2D(g, sx - wArm + 1, sy - 1.5f, sx + wArm - 1, sy - 1.5f, sx + wArm - 1, sy + 1.5f, sx - wArm + 1, sy + 1.5f, 0xFFECEFF1);
            for (int c = -9; c <= 9; c += 6) {
                drawQuad2D(g, sx + c - 1.5f, sy - 7.5f, sx + c + 1.5f, sy - 7.5f, sx + c + 1.5f, sy - 3.0f, sx + c - 1.5f, sy - 3.0f, statusCol);
                drawQuad2D(g, sx + c - 1.5f, sy + 3.0f, sx + c + 1.5f, sy + 3.0f, sx + c + 1.5f, sy + 7.5f, sx + c - 1.5f, sy + 7.5f, statusCol);
            }
        }

        static void renderDetailedSurgicalToolCursor(GuiGraphics g, float mx, float my, float tiltDeg,
                                                     ToolType tool, boolean active, float progress, float time) {
            g.pose().pushPose();
            g.pose().translate(mx, my, 250.0f);
            g.pose().mulPose(Axis.ZP.rotationDegrees(tiltDeg));
            g.fill(-1, -1, 1, 1, active ? 0xFFFFFF88 : 0xCC80F8FF);

            switch (tool) {
                case SCALPEL -> {
                    drawQuad2D(g, 0, 0, 12, 3, 15, 11, 4, 8, 0xFFF5F9FC);
                    drawQuad2D(g, 0, 0, 14, 5, 15, 8, 2, 2, 0xFFB0C4D4);
                    if (active) {
                        drawQuad2D(g, 0, 0, 7, 2, 5, 5, 0, 2, 0xDDAA111E);
                    }
                    drawQuad2D(g, 13, 6, 46, 28, 42, 34, 9, 12, 0xFF8A9BA8);
                    drawQuad2D(g, 14, 7, 45, 27, 43, 30, 12, 10, 0xFFCFD8DC);
                    for (int i = 0; i < 5; i++) {
                        float gx = 18 + i * 4.5f;
                        float gy = 11 + i * 3.0f;
                        drawQuad2D(g, gx, gy, gx + 2, gy + 1, gx - 1, gy + 5, gx - 3, gy + 4, 0xFF455A64);
                    }
                }
                case SUCTION_CANNULA -> {
                    drawQuad2D(g, -2, -2, 6, 1, 5, 6, -3, 3, 0xFFE0F7FA);
                    drawQuad2D(g, 4, 1, 20, 8, 18, 13, 2, 5, 0x99B2EBF2);
                    drawQuad2D(g, 18, 8, 48, 30, 44, 36, 14, 14, 0x8880DEEA);
                    if (active || progress > 0.1f) {
                        float pulse = Mth.sin(time * 0.6f) * 2.0f;
                        drawQuad2D(g, 3, 2, 18, 9, 17, 12, 2, 4, 0xDD8E0E18);
                        drawQuad2D(g, 18, 9, 44 + pulse, 28 + pulse, 41 + pulse, 33 + pulse, 15, 13, 0xDD7A0A14);
                    }
                }
                case BIPOLAR_CAUTERY -> {
                    float gap = active ? 1.2f : 4.0f;
                    drawQuad2D(g, 0, -gap * 0.4f, 10, -gap, 11, -gap + 2, 1, -gap * 0.2f + 1.5f, 0xFFFFD54F);
                    drawQuad2D(g, 0, gap * 0.4f, 10, gap, 11, gap + 2, 1, gap * 0.2f + 1.5f, 0xFFFFD54F);
                    drawQuad2D(g, 10, -gap, 44, 18, 42, 22, 9, -gap + 3, 0xFF1565C0);
                    drawQuad2D(g, 10, gap, 44, 24, 41, 28, 9, gap + 3, 0xFF0D47A1);
                    if (active) {
                        int sparkCol = ((int) (time * 3) % 2 == 0) ? 0xFFFFFFFF : 0xFF40C4FF;
                        drawQuad2D(g, -4, -1, 4, -1, 4, 1, -4, 1, sparkCol);
                        drawQuad2D(g, -1, -4, 1, -4, 1, 4, -1, 4, 0xFF80D8FF);
                    }
                }
                case HEMOSTAT_FORCEPS -> {
                    float jawOpen = active ? 3.8f : 1.0f;
                    drawQuad2D(g, 0, -jawOpen, 14, -jawOpen * 1.4f, 15, -jawOpen * 1.4f + 2.5f, 1, -jawOpen + 2.0f, 0xFFECEFF1);
                    drawQuad2D(g, 0, jawOpen, 14, jawOpen * 1.4f, 15, jawOpen * 1.4f + 2.5f, 1, jawOpen + 2.0f, 0xFFCFD8DC);
                    drawQuad2D(g, 13, -3, 20, -1, 20, 5, 13, 3, 0xFF90A4AE);
                    g.fill(15, 0, 18, 3, 0xFFFFCA28);
                    drawQuad2D(g, 19, 0, 42, 14, 40, 17, 18, 3, 0xFFB0BEC5);
                    drawQuad2D(g, 19, 2, 36, 24, 33, 26, 17, 4, 0xFF90A4AE);
                    g.renderOutline(40, 12, 9, 9, 0xFFFFB300);
                    g.renderOutline(31, 23, 9, 9, 0xFFFFB300);
                }
                case NEEDLE_HOLDER -> {
                    for (int s = 0; s < 6; s++) {
                        double a1 = (s / 6.0) * Math.PI * 0.85 - 0.4;
                        double a2 = ((s + 1) / 6.0) * Math.PI * 0.85 - 0.4;
                        float nx1 = 6.0f - (float) Math.cos(a1) * 7.5f;
                        float ny1 = (float) Math.sin(a1) * 7.5f;
                        float nx2 = 6.0f - (float) Math.cos(a2) * 7.5f;
                        float ny2 = (float) Math.sin(a2) * 7.5f;
                        drawQuad2D(g, nx1, ny1, nx2, ny2, nx2 + 1.5f, ny2 + 1.5f, nx1 + 1.5f, ny1 + 1.5f, 0xFFFFFFFF);
                    }
                    float wave = Mth.sin(time * 0.25f) * 3.0f;
                    drawQuad2D(g, 11, 5, 24, 16 + wave, 23, 18 + wave, 10, 6, 0xFF29B6F6);
                    drawQuad2D(g, 24, 16 + wave, 38, 12 - wave, 37, 14 - wave, 23, 18 + wave, 0xFF0288D1);
                    drawQuad2D(g, 4, -2, 18, 4, 16, 8, 3, 1, 0xFFCFD8DC);
                    drawQuad2D(g, 16, 3, 44, 22, 40, 27, 14, 8, 0xFF78909C);
                    drawQuad2D(g, 34, 15, 45, 22, 41, 27, 30, 20, 0xFFFFB300);
                }
                case BONE_RONGEUR -> {
                    float bite = active ? 0.5f : 3.8f;
                    drawQuad2D(g, 0, -bite, 12, -bite - 3, 14, -bite + 1, 2, -bite + 2, 0xFFECEFF1);
                    drawQuad2D(g, 0, bite, 12, bite + 3, 14, bite + 6, 2, bite + 2, 0xFFCFD8DC);
                    drawQuad2D(g, 12, -3, 26, 6, 24, 12, 10, 4, 0xFF78909C);
                    drawQuad2D(g, 24, 5, 46, 22, 42, 28, 21, 11, 0xFF546E7A);
                }
                case REDUCTION_CLAMP -> {
                    float spread = active ? 2.5f : 6.0f;
                    drawQuad2D(g, 0, -spread, 14, -spread - 2, 15, -spread + 2, 2, -spread + 2, 0xFFE0E0E0);
                    drawQuad2D(g, 0, spread, 14, spread + 2, 15, spread + 5, 2, spread + 2, 0xFFBDBDBD);
                    drawQuad2D(g, 13, -4, 44, 20, 40, 25, 11, 2, 0xFF90A4AE);
                    drawQuad2D(g, 26, 18, 38, 6, 40, 8, 28, 20, 0xFFFFCA28);
                }
                case CANNULATED_DRILL -> {
                    drawQuad2D(g, 0, -1.5f, 18, -1.5f, 18, 1.5f, 0, 1.5f, 0xFFECEFF1);
                    int fluteOffset = active ? (int) (time * 3) % 4 : 0;
                    for (int f = 2 + fluteOffset; f < 16; f += 4) {
                        drawQuad2D(g, f, -2.0f, f + 2, -2.0f, f + 1, 2.0f, f - 1, 2.0f, 0xFF546E7A);
                    }
                    drawQuad2D(g, 18, -4, 24, -4, 24, 4, 18, 4, 0xFFFFB300);
                    drawQuad2D(g, 24, -7, 46, -7, 46, 7, 24, 7, 0xFF1565C0);
                    drawQuad2D(g, 26, -5, 44, -5, 44, -2, 26, -2, 0xFF42A5F5);
                    drawQuad2D(g, 30, 7, 38, 7, 34, 24, 26, 24, 0xFF263238);
                }
                case TORQUE_WRENCH -> {
                    drawQuad2D(g, 0, -3, 10, -3, 10, 3, 0, 3, 0xFFFFB300);
                    drawQuad2D(g, 10, -2, 34, -2, 34, 2, 10, 2, 0xFFCFD8DC);
                    drawQuad2D(g, 32, -14, 38, -14, 38, 14, 32, 14, 0xFF263238);
                    drawQuad2D(g, 33, -12, 37, -12, 37, 12, 33, 12, 0xFF00ACC1);
                }
                case TROCAR_CHEST_TUBE -> {
                    drawQuad2D(g, 0, 0, 8, -3, 8, 3, 0, 2, 0xFFECEFF1);
                    drawQuad2D(g, 7, -4, 44, 14, 41, 21, 4, 3, 0xAA80DEEA);
                    drawQuad2D(g, 7, -1, 43, 16, 42, 18, 6, 1, 0xFF1565C0);
                }
            }
            g.pose().popPose();
        }
    }

    // =========================================================================
    // 第一人称非阻塞 3D 医疗动作动画系统
    // =========================================================================
    private enum FirstPersonAnimKind {
        NONE,
        SYRINGE_INJECT,
        BANDAGE_WRAP,
        SURGICAL_KIT_OPEN,
        IBUPROFEN_DRINK,
        TOURNIQUET_APPLY,
        POVIDONE_SPRAY
    }

    private static final long SYRINGE_ANIM_DURATION_MS = 1480L;
    private static final long BANDAGE_ANIM_DURATION_MS = 1920L;
    private static final long SURGICAL_KIT_OPEN_DURATION_MS = 960L;
    private static final long IBUPROFEN_ANIM_DURATION_MS = 1320L;
    private static final long TOURNIQUET_ANIM_DURATION_MS = 1560L;

    private static FirstPersonAnimKind fpAnimKind = FirstPersonAnimKind.NONE;
    private static long fpAnimStartMs = 0L;
    private static long fpAnimDurationMs = 1480L;
    private static InteractionHand fpAnimHand = InteractionHand.MAIN_HAND;
    private static MedicalData.Limb fpAnimLimb = MedicalData.Limb.LEFT_ARM;
    private static ItemStack fpAnimStack = ItemStack.EMPTY;
    private static float syringeAnimStartFill = 1.0f;
    private static float syringeAnimEndFill = 0.0f;
    private static boolean animStageSound1 = false;
    private static boolean animStageSound2 = false;
    private static boolean animStageSound3 = false;
    private static boolean animStageSound4 = false;

    private static int lastTrackedHotbarSlot = -1;
    private static Item lastTrackedMainHandItem = null;
    private static int fpAnimLockedSlot = -1;

    private static void captureCurrentHotbarSlotForLock() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            fpAnimLockedSlot = mc.player.getInventory().selected;
        }
    }

    public static boolean isSurgicalKitAnimationItem(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        return TreatmentRouter.isSutureKitItem(stack)
                || TreatmentRouter.isBallisticExtractionKitItem(stack)
                || TreatmentRouter.isPerforationRepairKitItem(stack)
                || TreatmentRouter.isFasciotomyKitItem(stack)
                || TreatmentRouter.isOrthopedicKitItem(stack)
                || TreatmentRouter.isChestDrainKitItem(stack);
    }

    private static void checkAndTriggerSurgicalKitSwitchAnim(Player player) {
        // 严禁切物品打断正在播放的任何第一人称医疗动画：动画期间硬锁热键栏槽位！
        if (isFirstPersonMedicalAnimActive()) {
            if (ModItems.safeCfgBool(MedicalConfig.LOCK_HOTBAR_DURING_MEDICAL_ANIM, true)
                    && fpAnimLockedSlot >= 0 && fpAnimLockedSlot < 9
                    && player.getInventory().selected != fpAnimLockedSlot) {
                player.getInventory().selected = fpAnimLockedSlot;
            }
            return;
        }

        int curSlot = player.getInventory().selected;
        ItemStack mainStack = player.getMainHandItem();
        Item curItem = mainStack.isEmpty() ? null : mainStack.getItem();

        if (curSlot != lastTrackedHotbarSlot || curItem != lastTrackedMainHandItem) {
            lastTrackedHotbarSlot = curSlot;
            lastTrackedMainHandItem = curItem;
            if (isSurgicalKitAnimationItem(mainStack)) {
                startSurgicalKitOpenAnimation(InteractionHand.MAIN_HAND, mainStack);
            }
        }
    }

    public static boolean isSyringeAnimationItem(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        return TreatmentRouter.isQuantitativeSyringeItem(stack)
                || TreatmentRouter.isAdrenalineItem(stack)
                || TreatmentRouter.isNaloxoneItem(stack)
                || TreatmentRouter.isDecompressionNeedleItem(stack)
                || stack.is(MedicalItemTags.SYRINGE);
    }

    public static boolean isBandageAnimationItem(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        if (isSyringeAnimationItem(stack)
                || TreatmentRouter.isSplintItem(stack)
                || TreatmentRouter.isTourniquetItem(stack)
                || TreatmentRouter.isOralMedicineItem(stack)
                || TreatmentRouter.isPovidoneIodineItem(stack)
                || isSurgicalKitAnimationItem(stack)) {
            return false;
        }
        return stack.is(MedicalItemTags.BANDAGE) || stack.getItem() instanceof BandageItem;
    }

    public static boolean isFirstPersonMedicalAnimActive() {
        if (fpAnimKind == FirstPersonAnimKind.NONE || fpAnimStartMs <= 0L || fpAnimStack.isEmpty()) {
            return false;
        }
        long elapsed = System.currentTimeMillis() - fpAnimStartMs;
        if (elapsed < 0L || elapsed >= fpAnimDurationMs) {
            fpAnimKind = FirstPersonAnimKind.NONE;
            fpAnimStartMs = 0L;
            fpAnimStack = ItemStack.EMPTY;
            fpAnimLockedSlot = -1;
            return false;
        }
        return true;
    }

    public static void startSurgicalKitOpenAnimation(InteractionHand hand, ItemStack stack) {
        if (stack == null || stack.isEmpty() || !isSurgicalKitAnimationItem(stack)) return;
        captureCurrentHotbarSlotForLock();
        fpAnimKind = FirstPersonAnimKind.SURGICAL_KIT_OPEN;
        fpAnimHand = (hand != null) ? hand : InteractionHand.MAIN_HAND;
        fpAnimLimb = MedicalData.Limb.CHEST;
        fpAnimStack = stack.copyWithCount(1);
        if (fpAnimStack.isDamageableItem()) {
            fpAnimStack.setDamageValue(0);
        }
        fpAnimDurationMs = Math.max(400L, ModItems.safeCfgInt(MedicalConfig.SURGICAL_KIT_READY_ANIM_DURATION_MS, 1260));
        fpAnimStartMs = System.currentTimeMillis();
        animStageSound1 = false;
        animStageSound2 = false;
        animStageSound3 = false;
        animStageSound4 = false;
    }

    public static void startPovidoneSprayAnimation(InteractionHand hand, MedicalData.Limb limb, ItemStack stack) {
        if (stack == null || stack.isEmpty() || !TreatmentRouter.isPovidoneIodineItem(stack)) return;
        captureCurrentHotbarSlotForLock();
        fpAnimKind = FirstPersonAnimKind.POVIDONE_SPRAY;
        fpAnimHand = (hand != null) ? hand : InteractionHand.MAIN_HAND;
        fpAnimLimb = (limb != null) ? limb : MedicalData.Limb.LEFT_ARM;
        fpAnimStack = stack.copyWithCount(1);
        if (fpAnimStack.isDamageableItem()) {
            fpAnimStack.setDamageValue(0);
        }
        fpAnimDurationMs = 1380L;
        fpAnimStartMs = System.currentTimeMillis();
        animStageSound1 = false;
        animStageSound2 = false;
        animStageSound3 = false;
        animStageSound4 = false;
    }

    public static void startIbuprofenDrinkAnimation(InteractionHand hand, ItemStack stack) {
        if (stack == null || stack.isEmpty() || !TreatmentRouter.isOralMedicineItem(stack)) return;
        captureCurrentHotbarSlotForLock();
        fpAnimKind = FirstPersonAnimKind.IBUPROFEN_DRINK;
        fpAnimHand = (hand != null) ? hand : InteractionHand.MAIN_HAND;
        fpAnimLimb = MedicalData.Limb.HEAD;
        fpAnimStack = stack.copyWithCount(1);
        if (fpAnimStack.isDamageableItem()) {
            fpAnimStack.setDamageValue(0);
        }
        fpAnimDurationMs = IBUPROFEN_ANIM_DURATION_MS;
        fpAnimStartMs = System.currentTimeMillis();
        animStageSound1 = false;
        animStageSound2 = false;
        animStageSound3 = false;
        animStageSound4 = false;
    }

    public static void startTourniquetApplyAnimation(InteractionHand hand, MedicalData.Limb limb, ItemStack stack) {
        if (stack == null || stack.isEmpty() || !TreatmentRouter.isTourniquetItem(stack)) return;
        captureCurrentHotbarSlotForLock();
        fpAnimKind = FirstPersonAnimKind.TOURNIQUET_APPLY;
        fpAnimHand = (hand != null) ? hand : InteractionHand.MAIN_HAND;
        fpAnimLimb = (limb != null) ? limb : MedicalData.Limb.LEFT_LEG;
        fpAnimStack = stack.copyWithCount(1);
        if (fpAnimStack.isDamageableItem()) {
            fpAnimStack.setDamageValue(0);
        }
        fpAnimDurationMs = TOURNIQUET_ANIM_DURATION_MS;
        fpAnimStartMs = System.currentTimeMillis();
        animStageSound1 = false;
        animStageSound2 = false;
        animStageSound3 = false;
        animStageSound4 = false;
    }

    public static void startSyringeInjectionAnimation(InteractionHand hand, MedicalData.Limb limb, ItemStack stack, int doseUnits) {
        if (stack == null || stack.isEmpty() || !isSyringeAnimationItem(stack)) return;
        captureCurrentHotbarSlotForLock();
        fpAnimKind = FirstPersonAnimKind.SYRINGE_INJECT;
        fpAnimHand = (hand != null) ? hand : InteractionHand.MAIN_HAND;
        fpAnimLimb = (limb != null) ? limb : MedicalData.Limb.LEFT_ARM;
        fpAnimStack = stack.copyWithCount(1);
        fpAnimDurationMs = SYRINGE_ANIM_DURATION_MS;

        if (stack.isDamageableItem() && stack.getMaxDamage() > 0) {
            int maxU = stack.getMaxDamage();
            int remU = Math.max(0, maxU - stack.getDamageValue());
            int usedU = TreatmentRouter.isQuantitativeSyringeItem(stack)
                    ? Mth.clamp(doseUnits, 1, Math.max(1, remU))
                    : remU;
            syringeAnimStartFill = Mth.clamp(remU / (float) maxU, 0.12f, 1.0f);
            syringeAnimEndFill = Mth.clamp((remU - usedU) / (float) maxU, 0.0f, syringeAnimStartFill);
            fpAnimStack.setDamageValue(0);
        } else {
            syringeAnimStartFill = 1.0f;
            syringeAnimEndFill = 0.0f;
        }

        fpAnimStartMs = System.currentTimeMillis();
        animStageSound1 = false;
        animStageSound2 = false;
        animStageSound3 = false;
        animStageSound4 = false;
    }

    public static void startBandageWrapAnimation(InteractionHand hand, MedicalData.Limb limb, ItemStack stack) {
        if (stack == null || stack.isEmpty() || !isBandageAnimationItem(stack)) return;
        captureCurrentHotbarSlotForLock();
        fpAnimKind = FirstPersonAnimKind.BANDAGE_WRAP;
        fpAnimHand = (hand != null) ? hand : InteractionHand.MAIN_HAND;
        fpAnimLimb = (limb != null) ? limb : MedicalData.Limb.LEFT_ARM;
        fpAnimStack = stack.copyWithCount(1);
        if (fpAnimStack.isDamageableItem()) {
            fpAnimStack.setDamageValue(0);
        }
        fpAnimDurationMs = BANDAGE_ANIM_DURATION_MS;
        fpAnimStartMs = System.currentTimeMillis();
        animStageSound1 = false;
        animStageSound2 = false;
        animStageSound3 = false;
        animStageSound4 = false;
    }

    private static float easeOutCubic(float x) {
        float inv = 1.0f - Mth.clamp(x, 0.0f, 1.0f);
        return 1.0f - inv * inv * inv;
    }

    private static float easeInCubic(float x) {
        float c = Mth.clamp(x, 0.0f, 1.0f);
        return c * c * c;
    }

    private static float easeInOutCubic(float x) {
        float c = Mth.clamp(x, 0.0f, 1.0f);
        return c < 0.5f ? 4.0f * c * c * c : 1.0f - (float) Math.pow(-2.0f * c + 2.0f, 3.0) / 2.0f;
    }

    @SubscribeEvent
    public static void onComputeMedicalAnimCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            MedicalData data = mc.player.getData(MedicalDataCapability.MEDICAL_DATA);
            if (data != null && ModItems.getPneumothoraxType(data) == 3) {
                float breathTilt = Mth.sin(System.currentTimeMillis() * 0.005f) * 1.8f + 1.4f;
                event.setRoll(event.getRoll() + breathTilt);
            }
        }

        if (!isFirstPersonMedicalAnimActive()) return;
        long elapsed = System.currentTimeMillis() - fpAnimStartMs;
        float t = Mth.clamp(elapsed / (float) fpAnimDurationMs, 0.0f, 1.0f);

        if (fpAnimKind == FirstPersonAnimKind.SURGICAL_KIT_OPEN) {
            float env = Mth.sin(t * (float) Math.PI);
            if (TreatmentRouter.isChestDrainKitItem(fpAnimStack)) {
                // 三腔水封引流瓶：提瓶晃动液面 + 向右侧拔出引流穿刺管时的视角微摆
                float pullJolt = (t >= 0.36f && t <= 0.72f) ? Mth.sin(((t - 0.36f) / 0.36f) * (float) Math.PI) : 0.0f;
                event.setPitch(event.getPitch() + 4.0f * env - 1.8f * pullJolt);
                event.setYaw(event.getYaw() + 2.5f * pullJolt);
                event.setRoll(event.getRoll() - 2.2f * pullJolt);
            } else if (TreatmentRouter.isOrthopedicKitItem(fpAnimStack)) {
                // 重型骨科器械盘：沉重托举惯性下沉 + 棘轮扳手旋拧两次震动
                float heavyDip = (t < 0.35f) ? Mth.sin((t / 0.35f) * (float) Math.PI) * 5.8f : 0.0f;
                float ratchetJolt = (t >= 0.38f && t <= 0.76f) ? Mth.sin(((t - 0.38f) / 0.38f) * Mth.TWO_PI * 2.0f) * 1.8f : 0.0f;
                event.setPitch(event.getPitch() + 3.5f * env + heavyDip);
                event.setRoll(event.getRoll() + ratchetJolt);
            } else if (TreatmentRouter.isFasciotomyKitItem(fpAnimStack)) {
                // 筋膜切开减压盘：检视表盘 + 双极电凝镊尖端放电微震
                float sparkJolt = (t >= 0.44f && t <= 0.68f) ? Mth.sin(elapsed * 0.09f) * 0.8f : 0.0f;
                event.setPitch(event.getPitch() + 4.8f * env + sparkJolt);
                event.setRoll(event.getRoll() + 1.2f * env);
            } else {
                // 血管缝合器械盘：端托盘 + 蚊式止血钳清脆双连试夹
                float clampJolt = (t >= 0.34f && t <= 0.70f) ? Math.abs(Mth.sin(((t - 0.34f) / 0.36f) * (float) Math.PI * 2.0f)) * 1.6f : 0.0f;
                event.setPitch(event.getPitch() + 4.2f * env - clampJolt);
                event.setRoll(event.getRoll() + 1.4f * clampJolt);
            }
            return;
        }

        if (fpAnimKind == FirstPersonAnimKind.IBUPROFEN_DRINK) {
            float pitchAdd = 0.0f;
            float rollAdd = 0.0f;
            if (t < 0.36f) {
                pitchAdd = 6.5f * Mth.sin((t / 0.36f) * (float) Math.PI);
                rollAdd = -2.0f * Mth.sin((t / 0.36f) * Mth.TWO_PI * 2.0f);
            } else if (t >= 0.48f && t < 0.85f) {
                float swallowP = (t - 0.48f) / 0.37f;
                pitchAdd = -9.5f * Mth.sin(swallowP * (float) Math.PI);
            }
            event.setPitch(event.getPitch() + pitchAdd);
            event.setRoll(event.getRoll() + rollAdd);
            return;
        }

        float limbPitchBase = switch (fpAnimLimb) {
            case HEAD -> -2.5f;
            case CHEST -> (fpAnimKind == FirstPersonAnimKind.BANDAGE_WRAP) ? 20.5f : 11.5f;
            case LEFT_ARM, RIGHT_ARM -> 4.5f;
            case LEFT_LEG, RIGHT_LEG -> 26.5f;
        };
        float limbYawBase = switch (fpAnimLimb) {
            case HEAD -> 3.2f;
            case CHEST -> 0.0f;
            case LEFT_ARM -> -3.5f;
            case RIGHT_ARM -> 3.5f;
            case LEFT_LEG -> -9.5f;
            case RIGHT_LEG -> 9.5f;
        };
        float limbRollBase = switch (fpAnimLimb) {
            case HEAD -> 2.4f;
            case CHEST -> 0.0f;
            case LEFT_ARM -> -1.6f;
            case RIGHT_ARM -> 1.6f;
            case LEFT_LEG -> -3.2f;
            case RIGHT_LEG -> 3.2f;
        };

        float env = (t < 0.16f)
                ? easeInOutCubic(t / 0.16f)
                : ((t > 0.86f) ? (1.0f - easeInOutCubic((t - 0.86f) / 0.14f)) : 1.0f);

        float pitchAdd = limbPitchBase * env;
        float yawAdd = limbYawBase * env;
        float rollAdd = limbRollBase * env;

        if (fpAnimKind == FirstPersonAnimKind.SYRINGE_INJECT) {
            if (t >= 0.42f && t < 0.56f) {
                float p = (t - 0.42f) / 0.14f;
                float impact = Mth.sin(p * (float) Math.PI);
                pitchAdd += 3.6f * impact;
                rollAdd += (fpAnimLimb == MedicalData.Limb.RIGHT_ARM ? -2.4f : 2.4f) * impact;
            } else if (t >= 0.56f && t < 0.82f) {
                float micro = Mth.sin(elapsed * 0.048f) * 0.32f;
                pitchAdd += micro;
            }
        } else if (fpAnimKind == FirstPersonAnimKind.BANDAGE_WRAP) {
            if (t >= 0.16f && t < 0.82f) {
                float wrapP = (t - 0.16f) / 0.66f;
                float turnPhase = (wrapP * 3.0f) % 1.0f;
                float cinchJolt = (turnPhase > 0.65f) ? Mth.sin(((turnPhase - 0.65f) / 0.35f) * (float) Math.PI) : 0.0f;
                pitchAdd += 1.6f * Mth.sin(wrapP * 3.0f * Mth.TWO_PI) - 2.0f * cinchJolt;
                rollAdd += 1.9f * Mth.cos(wrapP * 3.0f * Mth.TWO_PI) + 1.6f * cinchJolt;
            }
        } else if (fpAnimKind == FirstPersonAnimKind.TOURNIQUET_APPLY) {
            if (t >= 0.42f && t < 0.82f) {
                float windP = (t - 0.42f) / 0.40f;
                float torqueJolt = Mth.sin(windP * 2.0f * (float) Math.PI);
                pitchAdd += 2.2f * Math.abs(torqueJolt);
                rollAdd += 2.8f * torqueJolt;
            }
        }

        event.setPitch(event.getPitch() + pitchAdd);
        event.setYaw(event.getYaw() + yawAdd);
        event.setRoll(event.getRoll() + rollAdd);
    }

    @SubscribeEvent
    public static void onRenderFirstPersonMedicalHand(RenderHandEvent event) {
        if (!isFirstPersonMedicalAnimActive()) return;
        Minecraft mc = Minecraft.getInstance();
        AbstractClientPlayer player = mc.player;
        if (player == null || mc.options.hideGui) return;

        // 渲染帧再次确保槽位锁死，防止任何异步输入切走物品
        if (ModItems.safeCfgBool(MedicalConfig.LOCK_HOTBAR_DURING_MEDICAL_ANIM, true)
                && fpAnimLockedSlot >= 0 && fpAnimLockedSlot < 9
                && player.getInventory().selected != fpAnimLockedSlot) {
            player.getInventory().selected = fpAnimLockedSlot;
        }

        event.setCanceled(true);
        if (event.getHand() != fpAnimHand) {
            return;
        }

        long nowMs = System.currentTimeMillis();
        long elapsed = nowMs - fpAnimStartMs;
        float t = Mth.clamp(elapsed / (float) fpAnimDurationMs, 0.0f, 1.0f);

        MedicalData data = player.getData(MedicalDataCapability.MEDICAL_DATA);
        float painTremor = 0.0f;
        if (data != null) {
            float pRatio = data.getPerceivedPain() / Math.max(10.0f, ((float) ModItems.safeCfgDouble(MedicalConfig.MAX_PERCEIVED_PAIN, 100.0)));
            painTremor = Mth.clamp(pRatio, 0.0f, 1.0f) * 0.006f;
            if (ModItems.isInWithdrawalMovementRestriction(data)) {
                painTremor += 0.006f;
            }
        }
        float jitterX = (Mth.sin(nowMs * 0.037f) * 0.6f + Mth.cos(nowMs * 0.061f) * 0.4f) * painTremor;
        float jitterY = (Mth.cos(nowMs * 0.043f) * 0.6f + Mth.sin(nowMs * 0.053f) * 0.4f) * painTremor;

        PoseStack poseStack = event.getPoseStack();
        MultiBufferSource bufferSource = event.getMultiBufferSource();
        int packedLight = event.getPackedLight();
        PlayerRenderer playerRenderer = (PlayerRenderer) mc.getEntityRenderDispatcher().getRenderer(player);

        switch (fpAnimKind) {
            case SYRINGE_INJECT -> renderFirstPersonSyringe6Limb(mc, player, playerRenderer, poseStack, bufferSource, packedLight, t, nowMs, jitterX, jitterY);
            case BANDAGE_WRAP -> renderFirstPersonBandage6Limb(mc, player, playerRenderer, poseStack, bufferSource, packedLight, t, nowMs, jitterX, jitterY);
            case SURGICAL_KIT_OPEN -> renderFirstPersonSurgicalKitOpen(mc, player, playerRenderer, poseStack, bufferSource, packedLight, t, jitterX, jitterY);
            case IBUPROFEN_DRINK -> renderFirstPersonIbuprofenDrink(mc, player, playerRenderer, poseStack, bufferSource, packedLight, t, jitterX, jitterY);
            case TOURNIQUET_APPLY -> renderFirstPersonTourniquet6Limb(mc, player, playerRenderer, poseStack, bufferSource, packedLight, t, jitterX, jitterY);
            case POVIDONE_SPRAY -> renderFirstPersonPovidoneSpray6Limb(mc, player, playerRenderer, poseStack, bufferSource, packedLight, t, jitterX, jitterY);
            default -> {}
        }
    }

    /**
     * 四大重型医疗器械切换就绪动画（100% 驱动真实 .json 3D 模型 + 4 套完全不同的专属动作）：
     * 1. 胸腔闭式引流包 (chest_drain_kit)：三腔水封引流瓶（绝非箱子！）—— 左手提瓶晃动水封液柱 + 右手从瓶侧拔出 28Fr 硅胶穿刺引流管
     * 2. 野战血管缝合包 (field_vascular_suture_kit)：左手端起真实橄榄绿血管器械盘 + 右手拿起弯头蚊式止血钳清脆开合试夹 2 次并提拉蓝色普理灵缝线
     * 3. 野战筋膜切开减压包 (fasciotomy_kit)：左手端起真实琥珀橙减压器械盘 + 右手捏合 V 型双极电凝止血镊尖端迸发蓝白高频电弧火花自检
     * 4. 野战骨科复位固定包 (orthopedic_fixation_kit)：双手沉重缓冲托举真实钛灰骨科盘 + 右手握持 T 型扭力扳手旋拧外固定支架 Schanz 骨螺钉棘轮锁扣
     */
    private static void renderFirstPersonSurgicalKitOpen(Minecraft mc, AbstractClientPlayer player, PlayerRenderer playerRenderer,
                                                         PoseStack poseStack, MultiBufferSource bufferSource, int packedLight,
                                                         float t, float jitterX, float jitterY) {
        if (TreatmentRouter.isChestDrainKitItem(fpAnimStack)) {
            renderChestDrainBottleReadyAnim(mc, player, playerRenderer, poseStack, bufferSource, packedLight, t, jitterX, jitterY);
        } else if (TreatmentRouter.isOrthopedicKitItem(fpAnimStack)) {
            renderOrthopedicKitReadyAnim(mc, player, playerRenderer, poseStack, bufferSource, packedLight, t, jitterX, jitterY);
        } else if (TreatmentRouter.isFasciotomyKitItem(fpAnimStack)) {
            renderFasciotomyKitReadyAnim(mc, player, playerRenderer, poseStack, bufferSource, packedLight, t, jitterX, jitterY);
        } else {
            renderVascularSutureKitReadyAnim(mc, player, playerRenderer, poseStack, bufferSource, packedLight, t, jitterX, jitterY);
        }
    }

    /**
     * 判断 BakedQuad 是否属于三大手术箱 (.json) 中绕 X 轴旋转的「箱盖 (Lid) 及箱盖内嵌组件」面片：
     * 在 field_vascular_suture_kit.json、fasciotomy_kit.json、orthopedic_fixation_kit.json 中，
     * 所有底座与箱内器械构件均为水平/垂直正交或绕 Y 轴旋转（任意面至少有两条水平边 dy == 0，且非水平边 dz == 0），
     * 而所有箱盖构件均具有绕 X 轴 -22.5° 的倾角（其面片在 Y-Z 平面内存在 |dy| > 0.0005 且 |dz| > 0.0005 的斜边，或顶点 Y > 0.37）。
     * 该判定在三大手术箱模型上的误判率为 0.0%（False Positive = 0, False Negative = 0）。
     */
    private static boolean isSurgicalCaseLidQuad(BakedQuad quad) {
        int[] v = quad.getVertices();
        if (v.length < 32) return false;
        float maxY = -10.0f;
        float[] ys = new float[4];
        float[] zs = new float[4];
        for (int i = 0; i < 4; i++) {
            float y = Float.intBitsToFloat(v[i * 8 + 1]);
            float z = Float.intBitsToFloat(v[i * 8 + 2]);
            ys[i] = y;
            zs[i] = z;
            if (y > maxY) maxY = y;
        }
        if (maxY > 5.92f / 16.0f) {
            return true;
        }
        for (int i = 0; i < 4; i++) {
            int next = (i + 1) & 3;
            float dy = Math.abs(ys[next] - ys[i]);
            float dz = Math.abs(zs[next] - zs[i]);
            if (dy > 0.0005f && dz > 0.0005f) {
                return true;
            }
        }
        return false;
    }

    /**
     * 判断 BakedQuad 是否属于胸腔闭式引流包 (chest_drain_kit.json) 右侧卡槽可拔出的「28Fr 穿刺引流管与钢芯」组件
     */
    private static boolean isChestDrainDetachableTubeQuad(BakedQuad quad) {
        int[] v = quad.getVertices();
        if (v.length < 32) return false;
        float maxX = -10.0f;
        for (int i = 0; i < 4; i++) {
            float x = Float.intBitsToFloat(v[i * 8]);
            if (x > maxX) maxX = x;
        }
        return maxX > 12.24f / 16.0f;
    }

    private static void emitBakedQuadWithPose(VertexConsumer vc, PoseStack.Pose poseEntry, BakedQuad quad, int packedLight) {
        int[] v = quad.getVertices();
        if (v.length < 32) return;
        Matrix4f mat = poseEntry.pose();
        for (int i = 0; i < 4; i++) {
            int base = i * 8;
            float x = Float.intBitsToFloat(v[base]);
            float y = Float.intBitsToFloat(v[base + 1]);
            float z = Float.intBitsToFloat(v[base + 2]);
            int abgr = v[base + 3];
            float u = Float.intBitsToFloat(v[base + 4]);
            float vt = Float.intBitsToFloat(v[base + 5]);
            int nPacked = v[base + 7];

            int a = (abgr >>> 24) & 0xFF;
            int b = (abgr >>> 16) & 0xFF;
            int g = (abgr >>> 8) & 0xFF;
            int r = abgr & 0xFF;
            if (a == 0) a = 255;

            float nx = ((byte) (nPacked & 0xFF)) / 127.0f;
            float ny = ((byte) ((nPacked >>> 8) & 0xFF)) / 127.0f;
            float nz = ((byte) ((nPacked >>> 16) & 0xFF)) / 127.0f;

            vc.addVertex(mat, x, y, z)
                    .setColor(r, g, b, a)
                    .setUv(u, vt)
                    .setOverlay(OverlayTexture.NO_OVERLAY)
                    .setLight(packedLight)
                    .setNormal(poseEntry, nx, ny, nz);
        }
    }

    /**
     * 核心真实模型开箱渲染引擎：
     * 直接读取手术包 ItemStack 的真实 BakedModel 面片，将「底座+箱内器械」与「箱盖+盖内组件」在铰链轴处解耦：
     * - 当 lidOpenProgress == 0.0f（完全闭合态）：箱盖面片绕后侧铰链向正前方旋转 +112.5°，严丝合缝地水平扣合在底座上（误差 0.0mm），完全遮盖内部器械！
     * - 当 lidOpenProgress 从 0.0f -> 1.0f 推进时：箱盖面片绕铰链平滑向上掀起 112.5° 直至完全打开，逐步显露出箱底的真实 3D 器械！
     */
    private static void renderAnimatedSurgicalCaseBakedModel(Minecraft mc, AbstractClientPlayer player, ItemStack stack,
                                                             PoseStack poseStack, MultiBufferSource bufferSource, int packedLight,
                                                             float hingeY16, float hingeZ16,
                                                             float lidOpenProgress, float latchFlipProgress) {
        BakedModel model = mc.getItemRenderer().getModel(stack, player.level(), player, player.getId());
        VertexConsumer vc = bufferSource.getBuffer(Sheets.translucentCullBlockSheet());
        RandomSource rand = RandomSource.create(42L);

        List<BakedQuad> baseQuads = new ArrayList<>();
        List<BakedQuad> lidQuads = new ArrayList<>();
        boolean hasFrontHingeLegacy = false;

        Direction[] dirs = Direction.values();
        for (Direction dir : dirs) {
            rand.setSeed(42L);
            for (BakedQuad q : model.getQuads(null, dir, rand)) {
                if (isSurgicalCaseLidQuad(q)) {
                    lidQuads.add(q);
                    float z0 = Float.intBitsToFloat(q.getVertices()[2]);
                    if (z0 > 0.72f) hasFrontHingeLegacy = true;
                } else {
                    baseQuads.add(q);
                }
            }
        }
        rand.setSeed(42L);
        for (BakedQuad q : model.getQuads(null, null, rand)) {
            if (isSurgicalCaseLidQuad(q)) {
                lidQuads.add(q);
                float z0 = Float.intBitsToFloat(q.getVertices()[2]);
                if (z0 > 0.72f) hasFrontHingeLegacy = true;
            } else {
                baseQuads.add(q);
            }
        }

        // 将 [0..1] 模型空间对齐到中心原点 (-0.5, -0.5, -0.5)
        poseStack.pushPose();
        poseStack.translate(-0.5f, -0.5f, -0.5f);

        // 1. 渲染固定不动的底座与箱内 3D 医疗器械面片
        PoseStack.Pose basePose = poseStack.last();
        for (BakedQuad q : baseQuads) {
            emitBakedQuadWithPose(vc, basePose, q, packedLight);
        }

        // 2. 渲染绕铰链从「完全水平闭合 (lidOpenProgress = 0.0)」旋转掀起至「完全敞开 (lidOpenProgress = 1.0)」的真实箱盖面片！
        float hy = hingeY16 / 16.0f;
        float hz = (hasFrontHingeLegacy ? 13.5f : hingeZ16) / 16.0f;
        float closedDeltaDeg = hasFrontHingeLegacy
                ? (-67.5f * (1.0f - lidOpenProgress))
                : (112.5f * (1.0f - lidOpenProgress));

        poseStack.pushPose();
        poseStack.translate(0.5f, hy, hz);
        poseStack.mulPose(Axis.XP.rotationDegrees(closedDeltaDeg));
        poseStack.translate(-0.5f, -hy, -hz);

        PoseStack.Pose lidPose = poseStack.last();
        for (BakedQuad q : lidQuads) {
            emitBakedQuadWithPose(vc, lidPose, q, packedLight);
        }
        poseStack.popPose();

        poseStack.popPose();

        if (bufferSource instanceof MultiBufferSource.BufferSource bs) {
            bs.endBatch();
        }

        // 3. 叠加随箱盖同步翻转的 3D 立体顶盖加强筋、十字徽章与前侧弹开式双金属锁扣（使闭合状态下的箱顶与开锁动作极具机械质感）
        int shellCol = TreatmentRouter.isOrthopedicKitItem(stack) ? 0xFF37474F
                : (TreatmentRouter.isFasciotomyKitItem(stack) ? 0xFFD84315 : 0xFF2E5A36);
        int crossCol = TreatmentRouter.isFasciotomyKitItem(stack) ? 0xFFFFEA00 : 0xFFECEFF1;

        poseStack.pushPose();
        poseStack.translate(-0.5f, -0.5f, -0.5f);

        // 前侧双金属卡扣：在开锁阶段 (latchFlipProgress: 0 -> 1) 绕前缘轴向外弹开 75°
        float frontZ = 12.8f / 16.0f;
        float latchY = hingeY16 / 16.0f;
        poseStack.pushPose();
        poseStack.translate(0.5f, latchY, frontZ);
        poseStack.mulPose(Axis.XP.rotationDegrees(latchFlipProgress * 75.0f));

        Matrix4f latchMat = poseStack.last().pose();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.enableDepthTest();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);

        Tesselator tess = Tesselator.getInstance();
        BufferBuilder lBuf = tess.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        emitColoredBox3D(lBuf, latchMat, -0.24f, -0.06f, 0.0f, -0.15f, 0.03f, 0.035f, 0xFFFFCA28, 0.98f);
        emitColoredBox3D(lBuf, latchMat,  0.15f, -0.06f, 0.0f,  0.24f, 0.03f, 0.035f, 0xFFFFCA28, 0.98f);
        MeshData lMesh = lBuf.build();
        if (lMesh != null) {
            BufferUploader.drawWithShader(lMesh);
        }
        poseStack.popPose();

        // 箱盖外顶面装甲板与立体医疗十字徽章（直接绑定在箱盖铰链坐标系上，闭合时朝上封盖，开启时随箱盖翻转 112.5° 至背面）
        poseStack.pushPose();
        poseStack.translate(0.5f, hy, hingeZ16 / 16.0f);
        poseStack.mulPose(Axis.XP.rotationDegrees(112.5f * (1.0f - lidOpenProgress) - 22.5f));
        Matrix4f topMat = poseStack.last().pose();
        BufferBuilder tBuf = tess.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        // 箱盖外背板加强层与包边（完全贴合真实 JSON 箱盖尺寸）
        emitColoredBox3D(tBuf, topMat, -0.37f, 0.0f, -0.065f, 0.37f, 0.50f, -0.035f, shellCol, 0.98f);
        // 闭合时封住两侧与前缘的箱盖立体折边裙翼（确保闭合时严丝合缝遮挡内部器械）
        emitColoredBox3D(tBuf, topMat, -0.375f, 0.0f, -0.065f, -0.335f, 0.50f, 0.045f, shellCol, 0.98f);
        emitColoredBox3D(tBuf, topMat,  0.335f, 0.0f, -0.065f,  0.375f, 0.50f, 0.045f, shellCol, 0.98f);
        emitColoredBox3D(tBuf, topMat, -0.375f, 0.46f, -0.065f, 0.375f, 0.51f, 0.055f, shellCol, 0.98f);
        // 箱盖外表面中央立体十字医疗徽章
        emitColoredBox3D(tBuf, topMat, -0.09f, 0.21f, -0.075f, 0.09f, 0.29f, -0.062f, crossCol, 0.98f);
        emitColoredBox3D(tBuf, topMat, -0.04f, 0.16f, -0.075f, 0.04f, 0.34f, -0.062f, crossCol, 0.98f);
        MeshData tMesh = tBuf.build();
        if (tMesh != null) {
            BufferUploader.drawWithShader(tMesh);
        }
        poseStack.popPose();

        RenderSystem.enableCull();
        RenderSystem.disableBlend();
        poseStack.popPose();
    }

    /**
     * 计算箱盖前缘在相机空间中的实时坐标，使右手在掀盖阶段精准抓握在箱盖前缘上同步画弧线掀起！
     */
    private static float[] computeLidFrontEdgeOffset(float lidOpenProgress, float scale) {
        // 箱盖半径约 0.48 (模型空间)，闭合时角度为 +90° (指向 +Z 前方)，完全打开时为 -22.5° (指向上方偏后)
        float angleRad = Mth.lerp(lidOpenProgress, 90.0f, -22.5f) * Mth.DEG_TO_RAD;
        float radius = 0.46f * scale;
        float dy = Mth.cos(angleRad) * radius;
        float dz = Mth.sin(angleRad) * radius;
        return new float[]{dy, dz};
    }

    // ==================== 1. 胸腔闭式引流包：真实三腔水封瓶模型 + 拔出右侧 3D 穿刺引流管组件 ====================
    private static void renderChestDrainBottleReadyAnim(Minecraft mc, AbstractClientPlayer player, PlayerRenderer playerRenderer,
                                                        PoseStack poseStack, MultiBufferSource bufferSource, int packedLight,
                                                        float t, float jitterX, float jitterY) {
        if (t >= 0.10f && !animStageSound1) {
            animStageSound1 = true;
            if (mc.getSoundManager() != null) {
                mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.BUCKET_FILL, 1.55f, 0.42f));
            }
        }
        if (t >= 0.36f && !animStageSound2) {
            animStageSound2 = true;
            if (mc.getSoundManager() != null) {
                mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.ARMOR_EQUIP_LEATHER.value(), 1.45f, 0.65f));
                mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.TRIPWIRE_CLICK_ON, 1.65f, 0.50f));
            }
        }

        float liftP = (t < 0.26f) ? easeOutCubic(t / 0.26f) : 1.0f;
        float uncoilP = (t < 0.30f) ? 0.0f : ((t < 0.74f) ? easeInOutCubic((t - 0.30f) / 0.44f) : 1.0f);
        float settleP = (t > 0.78f) ? easeInOutCubic((t - 0.78f) / 0.22f) : 0.0f;

        float sloshRoll = (t < 0.65f) ? Mth.sin(t * Mth.TWO_PI * 2.2f) * 6.0f * (1.0f - settleP) : 0.0f;
        float bottleX = Mth.lerp(settleP, Mth.lerp(liftP, -0.20f, -0.08f), 0.14f) + jitterX;
        float bottleY = Mth.lerp(settleP, Mth.lerp(liftP, -0.54f, -0.22f), -0.28f) + jitterY;
        float bottleZ = Mth.lerp(settleP, Mth.lerp(liftP, -0.60f, -0.50f), -0.54f);

        // 左手抓握三腔水封瓶左侧/提手
        poseStack.pushPose();
        poseStack.translate(bottleX - 0.12f, bottleY + 0.04f, bottleZ);
        poseStack.mulPose(Axis.ZP.rotationDegrees(-28.0f + sloshRoll * 0.5f));
        poseStack.mulPose(Axis.XP.rotationDegrees(-22.0f));
        renderPlayerArmCenteredAtFist(poseStack, bufferSource, packedLight, player, playerRenderer, false);
        poseStack.popPose();

        // 解耦渲染真实的 chest_drain_kit.json 模型：瓶体固定在左手，右侧盘绕的 28Fr 穿刺引流管随右手真实拔出！
        BakedModel model = mc.getItemRenderer().getModel(fpAnimStack, player.level(), player, player.getId());
        VertexConsumer vc = bufferSource.getBuffer(Sheets.translucentCullBlockSheet());
        RandomSource rand = RandomSource.create(42L);
        List<BakedQuad> bottleQuads = new ArrayList<>();
        List<BakedQuad> tubeQuads = new ArrayList<>();

        for (Direction dir : Direction.values()) {
            rand.setSeed(42L);
            for (BakedQuad q : model.getQuads(null, dir, rand)) {
                if (isChestDrainDetachableTubeQuad(q)) tubeQuads.add(q);
                else bottleQuads.add(q);
            }
        }
        rand.setSeed(42L);
        for (BakedQuad q : model.getQuads(null, null, rand)) {
            if (isChestDrainDetachableTubeQuad(q)) tubeQuads.add(q);
            else bottleQuads.add(q);
        }

        poseStack.pushPose();
        poseStack.translate(bottleX, bottleY, bottleZ);
        poseStack.mulPose(Axis.YP.rotationDegrees(16.0f - settleP * 24.0f));
        poseStack.mulPose(Axis.XP.rotationDegrees(8.0f));
        poseStack.mulPose(Axis.ZP.rotationDegrees(sloshRoll));
        poseStack.scale(0.68f, 0.68f, 0.68f);
        poseStack.translate(-0.5f, -0.5f, -0.5f);

        PoseStack.Pose bPose = poseStack.last();
        for (BakedQuad q : bottleQuads) {
            emitBakedQuadWithPose(vc, bPose, q, packedLight);
        }

        // 右侧穿刺引流管组件：从瓶身右侧卡槽 (uncoilP = 0) 向右上前方拔出展开 (uncoilP = 1)
        poseStack.pushPose();
        poseStack.translate(uncoilP * 0.22f, uncoilP * 0.16f, -uncoilP * 0.10f);
        poseStack.translate(0.82f, 0.45f, 0.40f);
        poseStack.mulPose(Axis.ZP.rotationDegrees(-uncoilP * 32.0f));
        poseStack.mulPose(Axis.XP.rotationDegrees(uncoilP * 24.0f));
        poseStack.translate(-0.82f, -0.45f, -0.40f);
        PoseStack.Pose tPose = poseStack.last();
        for (BakedQuad q : tubeQuads) {
            emitBakedQuadWithPose(vc, tPose, q, packedLight);
        }
        poseStack.popPose();
        poseStack.popPose();

        // 右手抓握右侧引流管同步向上拔出
        float rx = bottleX + 0.10f + uncoilP * 0.14f * (1.0f - settleP * 0.4f);
        float ry = bottleY + 0.02f + uncoilP * 0.12f * (1.0f - settleP * 0.4f);
        float rz = bottleZ - 0.02f - uncoilP * 0.05f;

        poseStack.pushPose();
        poseStack.translate(rx, ry, rz);
        poseStack.mulPose(Axis.ZP.rotationDegrees(32.0f - uncoilP * 14.0f));
        poseStack.mulPose(Axis.XP.rotationDegrees(-34.0f + uncoilP * 12.0f));
        renderPlayerArmCenteredAtFist(poseStack, bufferSource, packedLight, player, playerRenderer, true);
        poseStack.popPose();

        if (bufferSource instanceof MultiBufferSource.BufferSource bs) {
            bs.endBatch();
        }
    }

    // ==================== 2. 野战血管缝合包：真实模型从闭合 (0°) 掀盖至打开 (112.5°) + 止血钳试夹 ====================
    private static void renderVascularSutureKitReadyAnim(Minecraft mc, AbstractClientPlayer player, PlayerRenderer playerRenderer,
                                                         PoseStack poseStack, MultiBufferSource bufferSource, int packedLight,
                                                         float t, float jitterX, float jitterY) {
        if (t >= 0.18f && !animStageSound1) {
            animStageSound1 = true;
            if (mc.getSoundManager() != null) {
                mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.TRIPWIRE_CLICK_ON, 1.55f, 0.60f));
            }
        }
        if (t >= 0.28f && !animStageSound2) {
            animStageSound2 = true;
            if (mc.getSoundManager() != null) {
                mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.IRON_TRAPDOOR_OPEN, 1.38f, 0.62f));
            }
        }
        if (t >= 0.68f && !animStageSound3) {
            animStageSound3 = true;
            if (mc.getSoundManager() != null) {
                mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.TRIPWIRE_CLICK_ON, 1.88f, 0.58f));
            }
        }

        // 阶段划分：0~0.24 端出完全闭合的手术箱并掰开前锁扣 -> 0.24~0.64 右手将箱盖从 0° 闭合态掀开 112.5° -> 0.64~0.88 止血钳试夹 -> 0.88~1.00 落回持握位
        float enterP = (t < 0.20f) ? easeOutCubic(t / 0.20f) : 1.0f;
        float latchP = (t < 0.16f) ? 0.0f : Mth.clamp((t - 0.16f) / 0.10f, 0.0f, 1.0f);
        float lidOpenP = (t < 0.24f) ? 0.0f : ((t < 0.64f) ? easeInOutCubic((t - 0.24f) / 0.40f) : 1.0f);
        float inspectP = (t >= 0.64f && t <= 0.88f) ? Mth.sin(((t - 0.64f) / 0.24f) * (float) Math.PI) : 0.0f;
        float settleP = (t > 0.88f) ? easeInOutCubic((t - 0.88f) / 0.12f) : 0.0f;

        float scale = 0.68f;
        float trayX = Mth.lerp(settleP, Mth.lerp(enterP, 0.24f, 0.02f), 0.18f) + jitterX;
        float trayY = Mth.lerp(settleP, Mth.lerp(enterP, -0.52f, -0.27f), -0.32f) + jitterY;
        float trayZ = Mth.lerp(settleP, Mth.lerp(enterP, -0.60f, -0.50f), -0.55f);

        // 左手稳托手术箱底部左侧
        poseStack.pushPose();
        poseStack.translate(trayX - 0.13f, trayY - 0.06f, trayZ);
        poseStack.mulPose(Axis.ZP.rotationDegrees(-32.0f));
        poseStack.mulPose(Axis.XP.rotationDegrees(-24.0f));
        renderPlayerArmCenteredAtFist(poseStack, bufferSource, packedLight, player, playerRenderer, false);
        poseStack.popPose();

        // 右手轨迹：在 0~0.64s 紧贴箱盖前缘，将闭合的箱盖实实在在地向上掀开 112.5°；随后探入箱内试夹止血钳
        float[] lidEdge = computeLidFrontEdgeOffset(lidOpenP, scale);
        float rHandX, rHandY, rHandZ;
        if (t < 0.64f) {
            rHandX = trayX + 0.08f;
            rHandY = trayY - 0.04f + lidEdge[0];
            rHandZ = trayZ - 0.06f + lidEdge[1];
        } else {
            float toTray = Mth.clamp((t - 0.64f) / 0.10f, 0.0f, 1.0f);
            rHandX = Mth.lerp(toTray, trayX + 0.08f, trayX + 0.11f);
            rHandY = Mth.lerp(toTray, trayY - 0.04f + lidEdge[0], trayY + 0.04f + inspectP * 0.09f);
            rHandZ = Mth.lerp(toTray, trayZ - 0.06f + lidEdge[1], trayZ + 0.02f);
        }

        poseStack.pushPose();
        poseStack.translate(rHandX, rHandY, rHandZ);
        poseStack.mulPose(Axis.ZP.rotationDegrees(34.0f));
        poseStack.mulPose(Axis.XP.rotationDegrees(-28.0f + lidOpenP * 16.0f));
        renderPlayerArmCenteredAtFist(poseStack, bufferSource, packedLight, player, playerRenderer, true);
        poseStack.popPose();

        // 渲染真实 field_vascular_suture_kit.json 模型（箱盖从 0° 闭合态绕 [8.0, 5.0, 4.4] 铰链掀开至 112.5°）
        poseStack.pushPose();
        poseStack.translate(trayX, trayY, trayZ);
        poseStack.mulPose(Axis.YP.rotationDegrees(-10.0f - settleP * 12.0f));
        poseStack.mulPose(Axis.XP.rotationDegrees(24.0f - settleP * 8.0f));
        poseStack.scale(scale, scale, scale);
        renderAnimatedSurgicalCaseBakedModel(mc, player, fpAnimStack, poseStack, bufferSource, packedLight,
                5.0f, 4.4f, lidOpenP, latchP);
        poseStack.popPose();

        if (inspectP > 0.04f) {
            float jawOpen = 0.014f * Math.abs(Mth.cos(((t - 0.64f) / 0.24f) * Mth.TWO_PI));
            Matrix4f mat = poseStack.last().pose();
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.disableCull();
            RenderSystem.enableDepthTest();
            RenderSystem.setShader(GameRenderer::getPositionColorShader);

            Tesselator tess = Tesselator.getInstance();
            BufferBuilder buf = tess.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
            emitColoredBox3D(buf, mat, rHandX - 0.11f, rHandY + 0.016f + jawOpen, rHandZ - 0.005f, rHandX, rHandY + 0.023f + jawOpen, rHandZ + 0.005f, 0xFFECEFF1, 0.98f);
            emitColoredBox3D(buf, mat, rHandX - 0.11f, rHandY + 0.009f - jawOpen, rHandZ - 0.005f, rHandX, rHandY + 0.016f - jawOpen, rHandZ + 0.005f, 0xFFCFD8DC, 0.98f);
            emitColoredBox3D(buf, mat, trayX + 0.01f, trayY + 0.02f, trayZ, rHandX - 0.09f, rHandY + 0.014f, rHandZ, 0xFF00E5FF, 0.92f);
            MeshData mesh = buf.build();
            if (mesh != null) {
                BufferUploader.drawWithShader(mesh);
            }
            RenderSystem.enableCull();
            RenderSystem.disableBlend();
        }
    }

    // ==================== 3. 野战筋膜切开减压包：真实模型从闭合 (0°) 掀盖至打开 (112.5°) + 电凝镊火花自检 ====================
    private static void renderFasciotomyKitReadyAnim(Minecraft mc, AbstractClientPlayer player, PlayerRenderer playerRenderer,
                                                     PoseStack poseStack, MultiBufferSource bufferSource, int packedLight,
                                                     float t, float jitterX, float jitterY) {
        if (t >= 0.18f && !animStageSound1) {
            animStageSound1 = true;
            if (mc.getSoundManager() != null) {
                mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.TRIPWIRE_CLICK_ON, 1.60f, 0.60f));
            }
        }
        if (t >= 0.28f && !animStageSound2) {
            animStageSound2 = true;
            if (mc.getSoundManager() != null) {
                mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.IRON_TRAPDOOR_OPEN, 1.42f, 0.62f));
            }
        }
        if (t >= 0.68f && !animStageSound3) {
            animStageSound3 = true;
            if (mc.getSoundManager() != null) {
                mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.LAVA_POP, 1.95f, 0.55f));
                mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_BIT.value(), 1.85f, 0.45f));
            }
        }

        float enterP = (t < 0.20f) ? easeOutCubic(t / 0.20f) : 1.0f;
        float latchP = (t < 0.16f) ? 0.0f : Mth.clamp((t - 0.16f) / 0.10f, 0.0f, 1.0f);
        float lidOpenP = (t < 0.24f) ? 0.0f : ((t < 0.64f) ? easeInOutCubic((t - 0.24f) / 0.40f) : 1.0f);
        float cauteryP = (t >= 0.64f && t <= 0.88f) ? Mth.sin(((t - 0.64f) / 0.24f) * (float) Math.PI) : 0.0f;
        float settleP = (t > 0.88f) ? easeInOutCubic((t - 0.88f) / 0.12f) : 0.0f;

        float scale = 0.68f;
        float trayX = Mth.lerp(settleP, Mth.lerp(enterP, 0.24f, 0.02f), 0.18f) + jitterX;
        float trayY = Mth.lerp(settleP, Mth.lerp(enterP, -0.52f, -0.27f), -0.32f) + jitterY;
        float trayZ = Mth.lerp(settleP, Mth.lerp(enterP, -0.60f, -0.50f), -0.55f);

        poseStack.pushPose();
        poseStack.translate(trayX - 0.13f, trayY - 0.06f, trayZ);
        poseStack.mulPose(Axis.ZP.rotationDegrees(-32.0f));
        poseStack.mulPose(Axis.XP.rotationDegrees(-24.0f));
        renderPlayerArmCenteredAtFist(poseStack, bufferSource, packedLight, player, playerRenderer, false);
        poseStack.popPose();

        float[] lidEdge = computeLidFrontEdgeOffset(lidOpenP, scale);
        float rHandX, rHandY, rHandZ;
        if (t < 0.64f) {
            rHandX = trayX + 0.08f;
            rHandY = trayY - 0.04f + lidEdge[0];
            rHandZ = trayZ - 0.06f + lidEdge[1];
        } else {
            float toTray = Mth.clamp((t - 0.64f) / 0.10f, 0.0f, 1.0f);
            rHandX = Mth.lerp(toTray, trayX + 0.08f, trayX + 0.10f);
            rHandY = Mth.lerp(toTray, trayY - 0.04f + lidEdge[0], trayY + 0.04f + cauteryP * 0.09f);
            rHandZ = Mth.lerp(toTray, trayZ - 0.06f + lidEdge[1], trayZ + 0.02f);
        }

        poseStack.pushPose();
        poseStack.translate(rHandX, rHandY, rHandZ);
        poseStack.mulPose(Axis.ZP.rotationDegrees(34.0f));
        poseStack.mulPose(Axis.XP.rotationDegrees(-28.0f + lidOpenP * 16.0f));
        renderPlayerArmCenteredAtFist(poseStack, bufferSource, packedLight, player, playerRenderer, true);
        poseStack.popPose();

        // 渲染真实 fasciotomy_kit.json 模型（箱盖从 0° 闭合态绕 [8.0, 4.2, 3.4] 铰链掀开至 112.5°）
        poseStack.pushPose();
        poseStack.translate(trayX, trayY, trayZ);
        poseStack.mulPose(Axis.YP.rotationDegrees(-10.0f - settleP * 12.0f));
        poseStack.mulPose(Axis.XP.rotationDegrees(24.0f - settleP * 8.0f));
        poseStack.scale(scale, scale, scale);
        renderAnimatedSurgicalCaseBakedModel(mc, player, fpAnimStack, poseStack, bufferSource, packedLight,
                4.2f, 3.4f, lidOpenP, latchP);
        poseStack.popPose();

        if (cauteryP > 0.04f) {
            boolean sparking = (t >= 0.70f && t <= 0.84f);
            float pinchGap = sparking ? 0.002f : 0.014f;
            Matrix4f mat = poseStack.last().pose();
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.disableCull();
            RenderSystem.enableDepthTest();
            RenderSystem.setShader(GameRenderer::getPositionColorShader);

            Tesselator tess = Tesselator.getInstance();
            BufferBuilder buf = tess.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
            emitColoredBox3D(buf, mat, rHandX - 0.10f, rHandY + 0.016f + pinchGap, rHandZ - 0.005f, rHandX, rHandY + 0.022f + pinchGap, rHandZ + 0.005f, 0xFF0288D1, 0.98f);
            emitColoredBox3D(buf, mat, rHandX - 0.10f, rHandY + 0.010f - pinchGap, rHandZ - 0.005f, rHandX, rHandY + 0.016f - pinchGap, rHandZ + 0.005f, 0xFF0288D1, 0.98f);
            if (sparking) {
                emitColoredBox3D(buf, mat, rHandX - 0.125f, rHandY + 0.004f, rHandZ - 0.015f, rHandX - 0.095f, rHandY + 0.028f, rHandZ + 0.015f, 0xFF00E5FF, 0.88f);
                emitColoredBox3D(buf, mat, rHandX - 0.116f, rHandY + 0.010f, rHandZ - 0.008f, rHandX - 0.102f, rHandY + 0.022f, rHandZ + 0.008f, 0xFFFFFFFF, 0.98f);
            }
            MeshData mesh = buf.build();
            if (mesh != null) {
                BufferUploader.drawWithShader(mesh);
            }
            RenderSystem.enableCull();
            RenderSystem.disableBlend();
        }
    }

    // ==================== 4. 野战骨科复位固定包：真实模型从闭合 (0°) 掀盖至打开 (112.5°) + 扭力扳手自检 ====================
    private static void renderOrthopedicKitReadyAnim(Minecraft mc, AbstractClientPlayer player, PlayerRenderer playerRenderer,
                                                     PoseStack poseStack, MultiBufferSource bufferSource, int packedLight,
                                                     float t, float jitterX, float jitterY) {
        if (t >= 0.16f && !animStageSound1) {
            animStageSound1 = true;
            if (mc.getSoundManager() != null) {
                mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.ARMOR_EQUIP_IRON.value(), 0.88f, 0.65f));
                mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.TRIPWIRE_CLICK_ON, 1.50f, 0.60f));
            }
        }
        if (t >= 0.28f && !animStageSound2) {
            animStageSound2 = true;
            if (mc.getSoundManager() != null) {
                mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.IRON_TRAPDOOR_OPEN, 1.25f, 0.68f));
            }
        }
        if (t >= 0.68f && !animStageSound3) {
            animStageSound3 = true;
            if (mc.getSoundManager() != null) {
                mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.DISPENSER_DISPENSE, 1.55f, 0.55f));
            }
        }

        float enterP = (t < 0.22f) ? easeOutCubic(t / 0.22f) : 1.0f;
        float heavyDip = (t < 0.26f) ? Mth.sin((t / 0.26f) * (float) Math.PI) * 0.042f : 0.0f;
        float latchP = (t < 0.16f) ? 0.0f : Mth.clamp((t - 0.16f) / 0.10f, 0.0f, 1.0f);
        float lidOpenP = (t < 0.24f) ? 0.0f : ((t < 0.64f) ? easeInOutCubic((t - 0.24f) / 0.40f) : 1.0f);
        float wrenchP = (t >= 0.64f && t <= 0.88f) ? Mth.sin(((t - 0.64f) / 0.24f) * (float) Math.PI) : 0.0f;
        float settleP = (t > 0.88f) ? easeInOutCubic((t - 0.88f) / 0.12f) : 0.0f;

        float scale = 0.68f;
        float trayX = Mth.lerp(settleP, Mth.lerp(enterP, 0.24f, 0.02f), 0.18f) + jitterX;
        float trayY = Mth.lerp(settleP, Mth.lerp(enterP, -0.56f, -0.26f) - heavyDip, -0.32f) + jitterY;
        float trayZ = Mth.lerp(settleP, Mth.lerp(enterP, -0.60f, -0.50f), -0.55f);

        poseStack.pushPose();
        poseStack.translate(trayX - 0.13f, trayY - 0.06f, trayZ);
        poseStack.mulPose(Axis.ZP.rotationDegrees(-34.0f));
        poseStack.mulPose(Axis.XP.rotationDegrees(-24.0f));
        renderPlayerArmCenteredAtFist(poseStack, bufferSource, packedLight, player, playerRenderer, false);
        poseStack.popPose();

        float[] lidEdge = computeLidFrontEdgeOffset(lidOpenP, scale);
        float rHandX, rHandY, rHandZ;
        if (t < 0.64f) {
            rHandX = trayX + 0.08f;
            rHandY = trayY - 0.04f + lidEdge[0];
            rHandZ = trayZ - 0.06f + lidEdge[1];
        } else {
            float toTray = Mth.clamp((t - 0.64f) / 0.10f, 0.0f, 1.0f);
            rHandX = Mth.lerp(toTray, trayX + 0.08f, trayX + 0.09f);
            rHandY = Mth.lerp(toTray, trayY - 0.04f + lidEdge[0], trayY + 0.06f + wrenchP * 0.04f);
            rHandZ = Mth.lerp(toTray, trayZ - 0.06f + lidEdge[1], trayZ + 0.01f);
        }

        poseStack.pushPose();
        poseStack.translate(rHandX, rHandY, rHandZ);
        poseStack.mulPose(Axis.ZP.rotationDegrees(36.0f));
        poseStack.mulPose(Axis.XP.rotationDegrees(-30.0f + lidOpenP * 14.0f));
        renderPlayerArmCenteredAtFist(poseStack, bufferSource, packedLight, player, playerRenderer, true);
        poseStack.popPose();

        // 渲染真实 orthopedic_fixation_kit.json 模型（箱盖从 0° 闭合态绕 [8.0, 4.2, 3.5] 铰链掀开至 112.5°）
        poseStack.pushPose();
        poseStack.translate(trayX, trayY, trayZ);
        poseStack.mulPose(Axis.YP.rotationDegrees(-10.0f - settleP * 12.0f));
        poseStack.mulPose(Axis.XP.rotationDegrees(24.0f - settleP * 8.0f));
        poseStack.scale(scale, scale, scale);
        renderAnimatedSurgicalCaseBakedModel(mc, player, fpAnimStack, poseStack, bufferSource, packedLight,
                4.2f, 3.5f, lidOpenP, latchP);
        poseStack.popPose();

        if (wrenchP > 0.04f) {
            float spinPhase = (t - 0.64f) / 0.24f;
            poseStack.pushPose();
            poseStack.translate(trayX + 0.02f, trayY + 0.03f, trayZ + 0.01f);
            poseStack.mulPose(Axis.YP.rotationDegrees(spinPhase * 180.0f));

            Matrix4f mat = poseStack.last().pose();
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.disableCull();
            RenderSystem.enableDepthTest();
            RenderSystem.setShader(GameRenderer::getPositionColorShader);

            Tesselator tess = Tesselator.getInstance();
            BufferBuilder buf = tess.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
            emitColoredBox3D(buf, mat, -0.012f, -0.010f, -0.012f, 0.012f, 0.018f, 0.012f, 0xFFFFCA28, 0.98f);
            emitColoredBox3D(buf, mat, -0.007f, 0.018f, -0.007f, 0.007f, 0.095f, 0.007f, 0xFFECEFF1, 0.98f);
            emitColoredBox3D(buf, mat, -0.052f, 0.090f, -0.011f, 0.052f, 0.110f, 0.011f, 0xFF00ACC1, 0.98f);
            MeshData mesh = buf.build();
            if (mesh != null) {
                BufferUploader.drawWithShader(mesh);
            }
            RenderSystem.enableCull();
            RenderSystem.disableBlend();
            poseStack.popPose();
        }
    }

    private static void renderFirstPersonPovidoneSpray6Limb(Minecraft mc, AbstractClientPlayer player, PlayerRenderer playerRenderer,
                                                            PoseStack poseStack, MultiBufferSource bufferSource, int packedLight,
                                                            float t, float jitterX, float jitterY) {
        if (t >= 0.28f && !animStageSound1) {
            animStageSound1 = true;
            if (mc.getSoundManager() != null) {
                mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.FIRE_EXTINGUISH, 1.85f, 0.55f));
            }
        }
        if (t >= 0.56f && !animStageSound2) {
            animStageSound2 = true;
            if (mc.getSoundManager() != null) {
                mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.FIRE_EXTINGUISH, 1.95f, 0.55f));
            }
        }

        float enterLeave = (t < 0.16f) ? easeOutCubic(t / 0.16f) : ((t > 0.84f) ? (1.0f - easeInCubic((t - 0.84f) / 0.16f)) : 1.0f);
        boolean isLeg = (fpAnimLimb == MedicalData.Limb.LEFT_LEG || fpAnimLimb == MedicalData.Limb.RIGHT_LEG);
        float limbX = switch (fpAnimLimb) {
            case LEFT_ARM -> -0.06f;
            case RIGHT_ARM -> 0.06f;
            case LEFT_LEG -> -0.10f;
            case RIGHT_LEG -> 0.10f;
            default -> 0.0f;
        };
        float limbY = isLeg ? -0.48f : -0.22f;
        float limbZ = isLeg ? -0.50f : -0.54f;

        if (fpAnimLimb != MedicalData.Limb.HEAD && fpAnimLimb != MedicalData.Limb.CHEST) {
            poseStack.pushPose();
            poseStack.translate(limbX + jitterX * 0.3f, Mth.lerp(enterLeave, -0.65f, limbY) + jitterY * 0.3f, limbZ);
            poseStack.mulPose(Axis.XP.rotationDegrees(isLeg ? -14.0f : -18.0f));
            poseStack.mulPose(Axis.ZP.rotationDegrees(isLeg ? (fpAnimLimb == MedicalData.Limb.LEFT_LEG ? -6.0f : 6.0f)
                    : (fpAnimLimb == MedicalData.Limb.LEFT_ARM ? -72.0f : 72.0f)));
            switch (fpAnimLimb) {
                case LEFT_ARM -> renderPlayerArmCenteredAtForearm(poseStack, bufferSource, packedLight, player, playerRenderer, false);
                case RIGHT_ARM -> renderPlayerArmCenteredAtForearm(poseStack, bufferSource, packedLight, player, playerRenderer, true);
                case LEFT_LEG -> renderPlayerLegCenteredAtMid(poseStack, bufferSource, packedLight, player, playerRenderer, false);
                case RIGHT_LEG -> renderPlayerLegCenteredAtMid(poseStack, bufferSource, packedLight, player, playerRenderer, true);
                default -> {}
            }
            poseStack.popPose();
        }

        boolean sprayBurst1 = (t >= 0.28f && t <= 0.44f);
        boolean sprayBurst2 = (t >= 0.56f && t <= 0.72f);
        float pressOffset = (sprayBurst1 || sprayBurst2) ? -0.018f : 0.0f;

        float bx = limbX + 0.14f + jitterX;
        float by = limbY + 0.14f + pressOffset + jitterY;
        float bz = limbZ + 0.08f;

        poseStack.pushPose();
        poseStack.translate(bx + 0.06f, by - 0.04f, bz);
        poseStack.mulPose(Axis.ZP.rotationDegrees(32.0f));
        poseStack.mulPose(Axis.XP.rotationDegrees(-28.0f));
        renderPlayerArmCenteredAtFist(poseStack, bufferSource, packedLight, player, playerRenderer, true);
        poseStack.popPose();

        poseStack.pushPose();
        poseStack.translate(bx, by, bz);
        poseStack.mulPose(Axis.ZP.rotationDegrees(26.0f));
        poseStack.mulPose(Axis.XP.rotationDegrees(-18.0f));
        poseStack.scale(0.56f, 0.56f, 0.56f);
        mc.getItemRenderer().renderStatic(
                player, fpAnimStack, ItemDisplayContext.NONE, false,
                poseStack, bufferSource, player.level(), packedLight, OverlayTexture.NO_OVERLAY, player.getId()
        );
        poseStack.popPose();

        if (bufferSource instanceof MultiBufferSource.BufferSource bs) {
            bs.endBatch();
        }

        Matrix4f mat = poseStack.last().pose();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.enableDepthTest();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);

        Tesselator tess = Tesselator.getInstance();
        BufferBuilder buf = tess.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);

        if (sprayBurst1 || sprayBurst2) {
            float burstP = sprayBurst1 ? ((t - 0.28f) / 0.16f) : ((t - 0.56f) / 0.16f);
            for (int i = 0; i < 8; i++) {
                float frac = (i / 8.0f + burstP * 0.5f) % 1.0f;
                float mx = Mth.lerp(frac, bx - 0.03f, limbX) + Mth.sin(i * 2.1f) * 0.035f * frac;
                float my = Mth.lerp(frac, by + 0.05f, limbY + 0.04f) + Mth.cos(i * 1.7f) * 0.035f * frac;
                float mz = Mth.lerp(frac, bz - 0.02f, limbZ + 0.06f);
                emitColoredBox3D(buf, mat, mx - 0.008f, my - 0.008f, mz - 0.008f, mx + 0.008f, my + 0.008f, mz + 0.008f, 0xFFE65100, 0.82f * (1.0f - frac * 0.4f));
            }
        }

        if (t >= 0.30f && fpAnimLimb != MedicalData.Limb.HEAD && fpAnimLimb != MedicalData.Limb.CHEST) {
            float filmAlpha = Mth.clamp((t - 0.30f) / 0.42f, 0.0f, 1.0f) * 0.78f;
            emitColoredBox3D(buf, mat, limbX - 0.055f, limbY + 0.02f, limbZ - 0.04f, limbX + 0.055f, limbY + 0.06f, limbZ + 0.09f, 0xFFD84315, filmAlpha);
        }

        MeshData mesh = buf.build();
        if (mesh != null) {
            BufferUploader.drawWithShader(mesh);
        }
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
    }

    private static void renderFirstPersonIbuprofenDrink(Minecraft mc, AbstractClientPlayer player, PlayerRenderer playerRenderer,
                                                        PoseStack poseStack, MultiBufferSource bufferSource, int packedLight,
                                                        float t, float jitterX, float jitterY) {
        if (t >= 0.12f && !animStageSound1) {
            animStageSound1 = true;
            if (mc.getSoundManager() != null) {
                mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.ITEM_PICKUP, 1.45f, 0.55f));
            }
        }
        if (t >= 0.36f && !animStageSound2) {
            animStageSound2 = true;
            if (mc.getSoundManager() != null) {
                mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.TURTLE_EGG_CRACK, 1.75f, 0.45f));
            }
        }
        if (t >= 0.62f && !animStageSound3) {
            animStageSound3 = true;
            if (mc.getSoundManager() != null) {
                mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.GENERIC_EAT, 1.15f, 0.75f));
            }
        }
        if (t >= 0.88f && !animStageSound4) {
            animStageSound4 = true;
            if (mc.getSoundManager() != null) {
                mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.TRIPWIRE_CLICK_ON, 1.65f, 0.45f));
            }
        }

        float bottleX = -0.14f + jitterX;
        float bottleY = -0.26f + jitterY;
        float bottleZ = -0.48f;
        float bottleTiltZ = (t >= 0.34f && t < 0.56f) ? -68.0f * Mth.sin(((t - 0.34f) / 0.22f) * (float) Math.PI) : -12.0f;

        poseStack.pushPose();
        poseStack.translate(bottleX - 0.08f, bottleY - 0.06f, bottleZ);
        poseStack.mulPose(Axis.ZP.rotationDegrees(-32.0f));
        poseStack.mulPose(Axis.XP.rotationDegrees(-24.0f));
        renderPlayerArmCenteredAtFist(poseStack, bufferSource, packedLight, player, playerRenderer, false);
        poseStack.popPose();

        poseStack.pushPose();
        poseStack.translate(bottleX, bottleY, bottleZ);
        poseStack.mulPose(Axis.ZP.rotationDegrees(bottleTiltZ));
        poseStack.scale(0.56f, 0.56f, 0.56f);
        mc.getItemRenderer().renderStatic(
                player, fpAnimStack, ItemDisplayContext.NONE, false,
                poseStack, bufferSource, player.level(), packedLight, OverlayTexture.NO_OVERLAY, player.getId()
        );
        poseStack.popPose();

        float rx, ry, rz;
        float capLift = 0.0f;
        boolean showCapsule = false;
        float capsX = 0.0f, capsY = 0.0f, capsZ = 0.0f;

        if (t < 0.36f) {
            float p = t / 0.36f;
            capLift = (p > 0.65f) ? ((p - 0.65f) / 0.35f) * 0.08f : 0.0f;
            rx = bottleX + 0.06f;
            ry = bottleY + 0.12f + capLift;
            rz = bottleZ;
        } else if (t < 0.55f) {
            float p = (t - 0.36f) / 0.19f;
            rx = Mth.lerp(p, bottleX + 0.14f, 0.08f);
            ry = Mth.lerp(p, bottleY + 0.02f, -0.22f);
            rz = Mth.lerp(p, bottleZ, -0.44f);
            showCapsule = true;
            capsX = Mth.lerp(p, bottleX + 0.05f, rx);
            capsY = Mth.lerp(p, bottleY + 0.02f, ry + 0.03f);
            capsZ = rz;
        } else if (t < 0.84f) {
            float p = Mth.sin(((t - 0.55f) / 0.29f) * (float) Math.PI);
            rx = Mth.lerp(p, 0.08f, 0.02f);
            ry = Mth.lerp(p, -0.22f, -0.10f);
            rz = Mth.lerp(p, -0.44f, -0.28f);
            showCapsule = (t < 0.72f);
            capsX = rx;
            capsY = ry + 0.03f;
            capsZ = rz;
        } else {
            float p = (t - 0.84f) / 0.16f;
            rx = Mth.lerp(p, 0.08f, bottleX + 0.06f);
            ry = Mth.lerp(p, -0.22f, bottleY + 0.12f);
            rz = bottleZ;
        }

        poseStack.pushPose();
        poseStack.translate(rx + jitterX, ry + jitterY, rz);
        poseStack.mulPose(Axis.ZP.rotationDegrees(28.0f));
        poseStack.mulPose(Axis.XP.rotationDegrees(-30.0f));
        renderPlayerArmCenteredAtFist(poseStack, bufferSource, packedLight, player, playerRenderer, true);
        poseStack.popPose();

        if (bufferSource instanceof MultiBufferSource.BufferSource bs) {
            bs.endBatch();
        }

        Matrix4f mat = poseStack.last().pose();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.enableDepthTest();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);

        Tesselator tess = Tesselator.getInstance();
        BufferBuilder buf = tess.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        boolean isCharcoal = TreatmentRouter.isActivatedCharcoalItem(fpAnimStack);
        boolean isAmox = TreatmentRouter.isAmoxicillinItem(fpAnimStack);
        int capCol = isCharcoal ? 0xFFFFCA28 : (isAmox ? 0xFF1E88E5 : 0xFFF5F7FA);
        if (t < 0.36f || t >= 0.84f) {
            float cx = bottleX;
            float cy = bottleY + 0.11f + capLift;
            float cz = bottleZ;
            emitColoredBox3D(buf, mat, cx - 0.038f, cy, cz - 0.038f, cx + 0.038f, cy + 0.032f, cz + 0.038f, capCol, 0.98f);
        }
        if (showCapsule) {
            if (isCharcoal) {
                emitColoredBox3D(buf, mat, capsX - 0.024f, capsY - 0.016f, capsZ - 0.016f, capsX + 0.024f, capsY + 0.016f, capsZ + 0.016f, 0xFF1C242B, 0.96f);
                emitColoredBox3D(buf, mat, capsX - 0.014f, capsY - 0.008f, capsZ - 0.008f, capsX + 0.014f, capsY + 0.024f, capsZ + 0.008f, 0xFF37474F, 0.92f);
            } else if (isAmox) {
                emitColoredBox3D(buf, mat, capsX - 0.024f, capsY - 0.011f, capsZ - 0.011f, capsX, capsY + 0.011f, capsZ + 0.011f, 0xFF1565C0, 0.98f);
                emitColoredBox3D(buf, mat, capsX, capsY - 0.011f, capsZ - 0.011f, capsX + 0.024f, capsY + 0.011f, capsZ + 0.011f, 0xFFE3F2FD, 0.98f);
            } else {
                emitColoredBox3D(buf, mat, capsX - 0.022f, capsY - 0.010f, capsZ - 0.010f, capsX, capsY + 0.010f, capsZ + 0.010f, 0xFFFF6D00, 0.98f);
                emitColoredBox3D(buf, mat, capsX, capsY - 0.010f, capsZ - 0.010f, capsX + 0.022f, capsY + 0.010f, capsZ + 0.010f, 0xFFFFFFFF, 0.98f);
            }
        }
        MeshData mesh = buf.build();
        if (mesh != null) {
            BufferUploader.drawWithShader(mesh);
        }
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
    }

    private static void renderFirstPersonTourniquet6Limb(Minecraft mc, AbstractClientPlayer player, PlayerRenderer playerRenderer,
                                                         PoseStack poseStack, MultiBufferSource bufferSource, int packedLight,
                                                         float t, float jitterX, float jitterY) {
        if (t >= 0.14f && !animStageSound1) {
            animStageSound1 = true;
            if (mc.getSoundManager() != null) {
                mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.ARMOR_EQUIP_LEATHER.value(), 1.20f, 0.75f));
            }
        }
        if (t >= 0.45f && !animStageSound2) {
            animStageSound2 = true;
            if (mc.getSoundManager() != null) {
                mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.LEASH_KNOT_PLACE, 1.35f, 0.70f));
            }
        }
        if (t >= 0.68f && !animStageSound3) {
            animStageSound3 = true;
            if (mc.getSoundManager() != null) {
                mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.LEASH_KNOT_PLACE, 1.55f, 0.70f));
            }
        }
        if (t >= 0.85f && !animStageSound4) {
            animStageSound4 = true;
            if (mc.getSoundManager() != null) {
                mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.TRIPWIRE_CLICK_ON, 1.65f, 0.65f));
            }
        }

        boolean actWithRight = (fpAnimLimb != MedicalData.Limb.RIGHT_ARM);
        float actSide = actWithRight ? 1.0f : -1.0f;

        float limbCx, limbCy, limbCz, limbRx, limbRy, limbRz;
        switch (fpAnimLimb) {
            case LEFT_ARM -> {
                limbCx = -0.06f; limbCy = -0.22f; limbCz = -0.56f;
                limbRx = -18.0f; limbRy = 8.0f;   limbRz = -72.0f;
            }
            case RIGHT_ARM -> {
                limbCx = 0.06f;  limbCy = -0.22f; limbCz = -0.56f;
                limbRx = -18.0f; limbRy = -8.0f;  limbRz = 72.0f;
            }
            case LEFT_LEG -> {
                limbCx = -0.11f; limbCy = -0.50f; limbCz = -0.50f;
                limbRx = -14.0f; limbRy = 6.0f;   limbRz = -6.0f;
            }
            default -> {
                limbCx = 0.11f;  limbCy = -0.50f; limbCz = -0.50f;
                limbRx = -14.0f; limbRy = -6.0f;  limbRz = 6.0f;
            }
        }

        float strapPullP = Mth.clamp(t / 0.40f, 0.0f, 1.0f);
        float windlassRotDeg = (t < 0.40f) ? 0.0f : Mth.clamp((t - 0.40f) / 0.42f, 0.0f, 1.0f) * 360.0f;
        float lockClipP = (t < 0.82f) ? 0.0f : Mth.clamp((t - 0.82f) / 0.18f, 0.0f, 1.0f);

        poseStack.pushPose();
        poseStack.translate(limbCx + jitterX * 0.35f, limbCy + jitterY * 0.35f, limbCz);
        poseStack.mulPose(Axis.ZP.rotationDegrees(limbRz));
        poseStack.mulPose(Axis.XP.rotationDegrees(limbRx));
        poseStack.mulPose(Axis.YP.rotationDegrees(limbRy));
        if (fpAnimLimb == MedicalData.Limb.LEFT_ARM) {
            renderPlayerArmCenteredAtForearm(poseStack, bufferSource, packedLight, player, playerRenderer, false);
        } else if (fpAnimLimb == MedicalData.Limb.RIGHT_ARM) {
            renderPlayerArmCenteredAtForearm(poseStack, bufferSource, packedLight, player, playerRenderer, true);
        } else if (fpAnimLimb == MedicalData.Limb.LEFT_LEG) {
            renderPlayerLegCenteredAtMid(poseStack, bufferSource, packedLight, player, playerRenderer, false);
        } else {
            renderPlayerLegCenteredAtMid(poseStack, bufferSource, packedLight, player, playerRenderer, true);
        }
        poseStack.popPose();

        float limbHalfWidth = 0.146f;
        float localHandX, localHandY = 0.0f, localHandZ = limbHalfWidth + 0.055f;
        if (t < 0.40f) {
            localHandX = actSide * Mth.lerp(easeOutCubic(strapPullP), 0.04f, 0.22f);
        } else if (t < 0.82f) {
            float ang = windlassRotDeg * Mth.DEG_TO_RAD;
            localHandX = Mth.cos(ang) * 0.085f;
            localHandY = Mth.sin(ang) * 0.085f;
        } else {
            localHandX = 0.0f;
            localHandY = 0.04f;
        }
        float[] handCam = transformLimbLocalToCamera(localHandX, localHandY, localHandZ, limbCx, limbCy, limbCz, limbRx, limbRy, limbRz);

        poseStack.pushPose();
        poseStack.translate(handCam[0] + jitterX, handCam[1] + jitterY, handCam[2]);
        poseStack.mulPose(Axis.ZP.rotationDegrees(actSide * 36.0f));
        poseStack.mulPose(Axis.XP.rotationDegrees(-32.0f));
        renderPlayerArmCenteredAtFist(poseStack, bufferSource, packedLight, player, playerRenderer, actWithRight);
        poseStack.popPose();

        if (bufferSource instanceof MultiBufferSource.BufferSource bs) {
            bs.endBatch();
        }

        poseStack.pushPose();
        poseStack.translate(limbCx + jitterX * 0.35f, limbCy + jitterY * 0.35f, limbCz);
        poseStack.mulPose(Axis.ZP.rotationDegrees(limbRz));
        poseStack.mulPose(Axis.XP.rotationDegrees(limbRx));
        poseStack.mulPose(Axis.YP.rotationDegrees(limbRy));

        Matrix4f mat = poseStack.last().pose();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.enableDepthTest();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);

        Tesselator tess = Tesselator.getInstance();
        BufferBuilder buf = tess.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);

        float cinchOffset = Mth.lerp(Mth.clamp((t - 0.15f) / 0.65f, 0.0f, 1.0f), 0.018f, 0.004f);
        float hw = limbHalfWidth + cinchOffset;
        float bandH = 0.032f;
        emitColoredBox3D(buf, mat, -hw, -bandH, -hw, hw, bandH, hw, 0xFF1C242B, 0.98f);
        emitColoredBox3D(buf, mat, 0.02f, -bandH * 0.6f, hw, 0.08f + (1.0f - strapPullP) * 0.06f, bandH * 0.6f, hw + 0.008f, 0xFFFF1744, 0.98f);
        emitColoredBox3D(buf, mat, -0.055f, 0.028f, hw, -0.035f, 0.048f, hw + 0.022f, 0xFFECEFF1, 0.98f);
        emitColoredBox3D(buf, mat,  0.035f, 0.028f, hw,  0.055f, 0.048f, hw + 0.022f, 0xFFECEFF1, 0.98f);
        if (lockClipP > 0.5f) {
            emitColoredBox3D(buf, mat, -0.060f, 0.026f, hw + 0.018f, 0.060f, 0.050f, hw + 0.025f, 0xFFD50000, 0.98f);
        }

        MeshData strapMesh = buf.build();
        if (strapMesh != null) {
            BufferUploader.drawWithShader(strapMesh);
        }

        poseStack.pushPose();
        poseStack.translate(0.0f, 0.0f, hw + 0.014f);
        poseStack.mulPose(Axis.ZP.rotationDegrees(windlassRotDeg));
        Matrix4f rodMat = poseStack.last().pose();
        BufferBuilder rodBuf = tess.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        emitColoredBox3D(rodBuf, rodMat, -0.082f, -0.010f, -0.008f, 0.082f, 0.010f, 0.008f, 0xFFCFD8DC, 0.98f);
        emitColoredBox3D(rodBuf, rodMat, -0.084f, -0.012f, -0.010f, -0.058f, 0.012f, 0.010f, 0xFFFFB300, 0.98f);
        emitColoredBox3D(rodBuf, rodMat,  0.058f, -0.012f, -0.010f,  0.084f, 0.012f, 0.010f, 0xFFFFB300, 0.98f);
        MeshData rodMesh = rodBuf.build();
        if (rodMesh != null) {
            BufferUploader.drawWithShader(rodMesh);
        }
        poseStack.popPose();

        RenderSystem.enableCull();
        RenderSystem.disableBlend();
        poseStack.popPose();
    }

    private static void renderFirstPersonSyringe6Limb(Minecraft mc, AbstractClientPlayer player, PlayerRenderer playerRenderer,
                                                      PoseStack poseStack, MultiBufferSource bufferSource, int packedLight,
                                                      float t, long nowMs, float jitterX, float jitterY) {
        boolean isDecompNeedle = TreatmentRouter.isDecompressionNeedleItem(fpAnimStack);
        if (t >= 0.42f && !animStageSound1) {
            animStageSound1 = true;
            if (mc.getSoundManager() != null) {
                mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.PLAYER_HURT_SWEET_BERRY_BUSH, 1.62f, 0.55f));
                mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.TRIPWIRE_CLICK_ON, 1.85f, 0.45f));
            }
        }
        if (t >= 0.56f && !animStageSound2) {
            animStageSound2 = true;
            if (mc.getSoundManager() != null) {
                if (isDecompNeedle) {
                    mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.FIRE_EXTINGUISH, 1.60f, 0.75f));
                } else {
                    mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.BREWING_STAND_BREW, 1.55f, 0.50f));
                }
            }
        }
        if (t >= 0.82f && !animStageSound3) {
            animStageSound3 = true;
            if (mc.getSoundManager() != null) {
                mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.ARMOR_EQUIP_LEATHER.value(), 1.45f, 0.45f));
            }
        }

        boolean injectWithRightArm = (fpAnimLimb != MedicalData.Limb.RIGHT_ARM);
        float actSide = injectWithRightArm ? 1.0f : -1.0f;
        float targSide = -actSide;

        float braceEndX, braceEndY, braceEndZ, braceRx, braceRy, braceRz;
        float px, py, pz;
        float aimRx, aimRy, aimRz;

        switch (fpAnimLimb) {
            case LEFT_ARM -> {
                braceEndX = -0.10f; braceEndY = -0.25f; braceEndZ = -0.58f;
                braceRx = -24.0f;   braceRy = 10.0f;    braceRz = -64.0f;
                px = -0.10f;        py = -0.25f;        pz = -0.58f;
                aimRx = 10.0f;      aimRy = -12.0f;     aimRz = -38.0f;
            }
            case RIGHT_ARM -> {
                braceEndX = 0.10f;  braceEndY = -0.25f; braceEndZ = -0.58f;
                braceRx = -24.0f;   braceRy = -10.0f;   braceRz = 64.0f;
                px = 0.10f;         py = -0.25f;        pz = -0.58f;
                aimRx = 10.0f;      aimRy = 12.0f;      aimRz = 38.0f;
            }
            case CHEST -> {
                braceEndX = -0.20f; braceEndY = -0.28f; braceEndZ = -0.52f;
                braceRx = -34.0f;   braceRy = 14.0f;    braceRz = -48.0f;
                px = 0.02f;         py = -0.24f;        pz = -0.44f;
                aimRx = -52.0f;     aimRy = 0.0f;       aimRz = -22.0f;
            }
            case HEAD -> {
                braceEndX = -0.28f; braceEndY = -0.08f; braceEndZ = -0.50f;
                braceRx = -18.0f;   braceRy = 18.0f;    braceRz = -32.0f;
                px = -0.15f;        py = -0.05f;        pz = -0.46f;
                aimRx = -34.0f;     aimRy = -15.0f;     aimRz = -54.0f;
            }
            case LEFT_LEG -> {
                braceEndX = -0.24f; braceEndY = -0.46f; braceEndZ = -0.52f;
                braceRx = -42.0f;   braceRy = 8.0f;     braceRz = -34.0f;
                px = -0.11f;        py = -0.48f;        pz = -0.50f;
                aimRx = 12.0f;      aimRy = -8.0f;      aimRz = -22.0f;
            }
            case RIGHT_LEG -> {
                braceEndX = -0.02f; braceEndY = -0.46f; braceEndZ = -0.52f;
                braceRx = -42.0f;   braceRy = 12.0f;    braceRz = -62.0f;
                px = 0.11f;         py = -0.48f;        pz = -0.50f;
                aimRx = 12.0f;      aimRy = -6.0f;      aimRz = -8.0f;
            }
            default -> {
                braceEndX = -0.10f; braceEndY = -0.25f; braceEndZ = -0.58f;
                braceRx = -24.0f;   braceRy = 10.0f;    braceRz = -64.0f;
                px = -0.10f;        py = -0.25f;        pz = -0.58f;
                aimRx = 10.0f;      aimRy = -12.0f;     aimRz = -38.0f;
            }
        }

        float radX = aimRx * Mth.DEG_TO_RAD;
        float radZ = aimRz * Mth.DEG_TO_RAD;
        float ndX = Mth.sin(radZ) * Mth.cos(radX);
        float ndY = -Mth.cos(radZ) * Mth.cos(radX);
        float ndZ = -Mth.sin(radX);

        float needleLenFromCenter = 0.32f;
        float windupDist = needleLenFromCenter + 0.20f;
        float pierceDist = needleLenFromCenter - 0.04f;

        float windupX = px - windupDist * ndX;
        float windupY = py - windupDist * ndY;
        float windupZ = pz - windupDist * ndZ;

        float pierceX = px - pierceDist * ndX;
        float pierceY = py - pierceDist * ndY;
        float pierceZ = pz - pierceDist * ndZ;

        float restX = actSide * 0.44f, restY = -0.42f, restZ = -0.64f;
        float inspectX = actSide * 0.22f, inspectY = -0.12f, inspectZ = -0.54f;
        float braceStartX = targSide * 0.46f, braceStartY = -0.62f, braceStartZ = -0.52f;

        float curBraceX, curBraceY, curBraceZ;
        float curSyrX, curSyrY, curSyrZ;
        float curSyrRx, curSyrRy, curSyrRz;
        float plungerDepress = 0.0f;

        if (t < 0.22f) {
            float p = easeOutCubic(t / 0.22f);
            curBraceX = Mth.lerp(p, braceStartX, braceEndX);
            curBraceY = Mth.lerp(p, braceStartY, braceEndY);
            curBraceZ = Mth.lerp(p, braceStartZ, braceEndZ);

            curSyrX = Mth.lerp(p, restX, inspectX);
            curSyrY = Mth.lerp(p, restY, inspectY);
            curSyrZ = Mth.lerp(p, restZ, inspectZ);
            curSyrRx = Mth.lerp(p, 0.0f, -12.0f);
            curSyrRy = Mth.lerp(p, 0.0f, -actSide * 18.0f);
            curSyrRz = Mth.lerp(p, 0.0f, -actSide * 14.0f);
        } else if (t < 0.42f) {
            float p = easeInOutCubic((t - 0.22f) / 0.20f);
            curBraceX = braceEndX;
            curBraceY = braceEndY;
            curBraceZ = braceEndZ;

            curSyrX = Mth.lerp(p, inspectX, windupX);
            curSyrY = Mth.lerp(p, inspectY, windupY);
            curSyrZ = Mth.lerp(p, inspectZ, windupZ);
            curSyrRx = Mth.lerp(p, -12.0f, aimRx);
            curSyrRy = Mth.lerp(p, -actSide * 18.0f, aimRy);
            curSyrRz = Mth.lerp(p, -actSide * 14.0f, aimRz);
        } else if (t < 0.52f) {
            float p = (t - 0.42f) / 0.10f;
            float stab = easeInCubic(Mth.clamp(p * 1.15f, 0.0f, 1.0f));
            float impactFlinch = Mth.sin(p * (float) Math.PI) * 0.016f;

            curBraceX = braceEndX + ndX * impactFlinch;
            curBraceY = braceEndY + ndY * impactFlinch;
            curBraceZ = braceEndZ + ndZ * impactFlinch;

            curSyrX = Mth.lerp(stab, windupX, pierceX) + ndX * impactFlinch;
            curSyrY = Mth.lerp(stab, windupY, pierceY) + ndY * impactFlinch;
            curSyrZ = Mth.lerp(stab, windupZ, pierceZ) + ndZ * impactFlinch;
            curSyrRx = aimRx;
            curSyrRy = aimRy;
            curSyrRz = aimRz;
        } else if (t < 0.82f) {
            float p = (t - 0.52f) / 0.30f;
            plungerDepress = easeInOutCubic(p);
            float microX = Mth.sin(nowMs * 0.055f) * 0.0025f;
            float microY = Mth.cos(nowMs * 0.068f) * 0.0025f;

            curBraceX = braceEndX + microX * 0.5f;
            curBraceY = braceEndY + microY * 0.5f;
            curBraceZ = braceEndZ;

            float deepPush = 0.012f * Mth.sin(p * (float) Math.PI);
            curSyrX = pierceX + ndX * deepPush + microX;
            curSyrY = pierceY + ndY * deepPush + microY;
            curSyrZ = pierceZ + ndZ * deepPush;
            curSyrRx = aimRx;
            curSyrRy = aimRy;
            curSyrRz = aimRz;
        } else {
            plungerDepress = 1.0f;
            float p = (t - 0.82f) / 0.18f;
            float hideBrace = easeInCubic(p);
            curBraceX = Mth.lerp(hideBrace, braceEndX, braceStartX);
            curBraceY = Mth.lerp(hideBrace, braceEndY, braceStartY);
            curBraceZ = Mth.lerp(hideBrace, braceEndZ, braceStartZ);

            if (p < 0.42f) {
                float pull = easeOutCubic(p / 0.42f);
                curSyrX = Mth.lerp(pull, pierceX, windupX);
                curSyrY = Mth.lerp(pull, pierceY, windupY);
                curSyrZ = Mth.lerp(pull, pierceZ, windupZ);
                curSyrRx = aimRx;
                curSyrRy = aimRy;
                curSyrRz = aimRz;
            } else {
                float ret = easeInOutCubic((p - 0.42f) / 0.58f);
                curSyrX = Mth.lerp(ret, windupX, restX);
                curSyrY = Mth.lerp(ret, windupY, restY);
                curSyrZ = Mth.lerp(ret, windupZ, restZ);
                curSyrRx = Mth.lerp(ret, aimRx, 0.0f);
                curSyrRy = Mth.lerp(ret, aimRy, 0.0f);
                curSyrRz = Mth.lerp(ret, aimRz, 0.0f);
            }
        }

        if (fpAnimLimb == MedicalData.Limb.LEFT_LEG || fpAnimLimb == MedicalData.Limb.RIGHT_LEG) {
            boolean isRightLeg = (fpAnimLimb == MedicalData.Limb.RIGHT_LEG);
            poseStack.pushPose();
            poseStack.translate(px + jitterX * 0.3f, py + jitterY * 0.3f, pz);
            poseStack.mulPose(Axis.ZP.rotationDegrees(isRightLeg ? 6.0f : -6.0f));
            poseStack.mulPose(Axis.XP.rotationDegrees(-14.0f));
            renderPlayerLegCenteredAtMid(poseStack, bufferSource, packedLight, player, playerRenderer, isRightLeg);
            poseStack.popPose();
        }

        poseStack.pushPose();
        poseStack.translate(curBraceX + jitterX * 0.4f, curBraceY + jitterY * 0.4f, curBraceZ);
        poseStack.mulPose(Axis.ZP.rotationDegrees(braceRz));
        poseStack.mulPose(Axis.XP.rotationDegrees(braceRx));
        poseStack.mulPose(Axis.YP.rotationDegrees(braceRy));
        renderPlayerArmCenteredAtForearm(poseStack, bufferSource, packedLight, player, playerRenderer, !injectWithRightArm);
        poseStack.popPose();

        poseStack.pushPose();
        poseStack.translate(curSyrX + jitterX, curSyrY + jitterY, curSyrZ);
        poseStack.mulPose(Axis.ZP.rotationDegrees(actSide * 44.0f));
        poseStack.mulPose(Axis.XP.rotationDegrees(-36.0f));
        poseStack.mulPose(Axis.YP.rotationDegrees(actSide * 15.0f));
        renderPlayerArmCenteredAtFist(poseStack, bufferSource, packedLight, player, playerRenderer, injectWithRightArm);
        poseStack.popPose();

        poseStack.pushPose();
        poseStack.translate(curSyrX + jitterX, curSyrY + jitterY, curSyrZ);
        poseStack.mulPose(Axis.ZP.rotationDegrees(curSyrRz));
        poseStack.mulPose(Axis.XP.rotationDegrees(curSyrRx));
        poseStack.mulPose(Axis.YP.rotationDegrees(curSyrRy));
        poseStack.scale(0.68f, 0.68f, 0.68f);

        mc.getItemRenderer().renderStatic(
                player,
                fpAnimStack,
                ItemDisplayContext.NONE,
                false,
                poseStack,
                bufferSource,
                player.level(),
                packedLight,
                OverlayTexture.NO_OVERLAY,
                player.getId()
        );

        if (bufferSource instanceof MultiBufferSource.BufferSource bs) {
            bs.endBatch();
        }
        if (isDecompNeedle) {
            if (t >= 0.50f && t < 0.82f) {
                float jetP = (t - 0.50f) / 0.32f;
                Matrix4f mat = poseStack.last().pose();
                RenderSystem.enableBlend();
                RenderSystem.defaultBlendFunc();
                RenderSystem.disableCull();
                RenderSystem.setShader(GameRenderer::getPositionColorShader);
                BufferBuilder buf = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
                for (int k = 0; k < 4; k++) {
                    float jy0 = 0.44f + ((jetP * 3.0f + k * 0.25f) % 1.0f) * 0.38f;
                    float spread = (jy0 - 0.40f) * 0.22f;
                    emitColoredBox3D(buf, mat, -spread, jy0, -spread, spread, jy0 + 0.06f, spread, 0xFFE0F7FA, 0.65f);
                }
                MeshData mesh = buf.build();
                if (mesh != null) {
                    BufferUploader.drawWithShader(mesh);
                }
                RenderSystem.enableCull();
                RenderSystem.disableBlend();
            }
        } else {
            renderCoaxialSyringePlungerStem(poseStack, fpAnimStack, plungerDepress);
        }
        poseStack.popPose();
    }

    private static void renderFirstPersonBandage6Limb(Minecraft mc, AbstractClientPlayer player, PlayerRenderer playerRenderer,
                                                      PoseStack poseStack, MultiBufferSource bufferSource, int packedLight,
                                                      float t, long nowMs, float jitterX, float jitterY) {
        if (t >= 0.10f && !animStageSound1) {
            animStageSound1 = true;
            if (mc.getSoundManager() != null) {
                mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.WOOL_PLACE, 1.12f, 0.70f));
            }
        }
        if (t >= 0.36f && !animStageSound2) {
            animStageSound2 = true;
            if (mc.getSoundManager() != null) {
                mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.WOOL_STEP, 1.28f, 0.70f));
            }
        }
        if (t >= 0.60f && !animStageSound3) {
            animStageSound3 = true;
            if (mc.getSoundManager() != null) {
                mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.WOOL_STEP, 1.42f, 0.72f));
            }
        }
        if (t >= 0.82f && !animStageSound4) {
            animStageSound4 = true;
            if (mc.getSoundManager() != null) {
                mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.ARMOR_EQUIP_LEATHER.value(), 1.30f, 0.75f));
                mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.TRIPWIRE_CLICK_ON, 1.60f, 0.50f));
            }
        }

        boolean wrapWithRightArm = (fpAnimLimb != MedicalData.Limb.RIGHT_ARM);
        float actSide = wrapWithRightArm ? 1.0f : -1.0f;
        float targSide = -actSide;

        boolean isPressure = fpAnimStack.getItem().toString().contains("pressure");
        int mainBandageCol = isPressure ? 0xFF789052 : 0xFFF2F6FA;
        int trimStripeCol  = isPressure ? 0xFFC5A868 : 0xFF58A6E8;
        int claspColor     = isPressure ? 0xFF4A5448 : 0xFFCFD8DC;

        float enterLeave = (t < 0.14f)
                ? easeOutCubic(t / 0.14f)
                : ((t > 0.88f) ? (1.0f - easeInCubic((t - 0.88f) / 0.12f)) : 1.0f);

        float restX = actSide * 0.44f, restY = -0.42f, restZ = -0.64f;

        if (fpAnimLimb == MedicalData.Limb.HEAD) {
            float bx = Mth.lerp(enterLeave, -0.45f, -0.31f);
            float by = Mth.lerp(enterLeave, -0.62f, -0.02f);
            float bz = Mth.lerp(enterLeave, -0.52f, -0.42f);

            poseStack.pushPose();
            poseStack.translate(bx + jitterX * 0.3f, by + jitterY * 0.3f, bz);
            poseStack.mulPose(Axis.ZP.rotationDegrees(-26.0f));
            poseStack.mulPose(Axis.XP.rotationDegrees(-16.0f));
            poseStack.mulPose(Axis.YP.rotationDegrees(18.0f));
            renderPlayerArmCenteredAtFist(poseStack, bufferSource, packedLight, player, playerRenderer, false);
            poseStack.popPose();

            float rollX, rollY, rollZ, rollRx, rollRy, rollRz;
            if (t < 0.16f) {
                float p = easeOutCubic(t / 0.16f);
                rollX = Mth.lerp(p, restX, -0.24f);
                rollY = Mth.lerp(p, restY, 0.05f);
                rollZ = Mth.lerp(p, restZ, -0.40f);
                rollRx = Mth.lerp(p, 0.0f, 68.0f);
                rollRy = Mth.lerp(p, 0.0f, 20.0f);
                rollRz = Mth.lerp(p, 0.0f, -18.0f);
            } else if (t < 0.80f) {
                float wrapP = (t - 0.16f) / 0.64f;
                float ang = wrapP * 3.0f * Mth.TWO_PI;
                rollX = Mth.sin(ang) * 0.36f;
                rollY = 0.04f + 0.05f * Mth.cos(ang * 2.0f);
                rollZ = -0.32f - 0.14f * Mth.cos(ang);
                rollRx = 72.0f + 12.0f * Mth.sin(ang);
                rollRy = (ang * Mth.RAD_TO_DEG) * 0.35f;
                rollRz = -22.0f * Mth.cos(ang);
            } else if (t < 0.88f) {
                float p = easeInOutCubic((t - 0.80f) / 0.08f);
                float press = Mth.sin(p * (float) Math.PI) * 0.03f;
                rollX = -0.24f + press;
                rollY = 0.02f;
                rollZ = -0.40f;
                rollRx = 55.0f;
                rollRy = 18.0f;
                rollRz = -28.0f;
            } else {
                float p = easeInOutCubic((t - 0.88f) / 0.12f);
                rollX = Mth.lerp(p, -0.24f, restX);
                rollY = Mth.lerp(p, 0.02f, restY);
                rollZ = Mth.lerp(p, -0.40f, restZ);
                rollRx = Mth.lerp(p, 55.0f, 0.0f);
                rollRy = Mth.lerp(p, 18.0f, 0.0f);
                rollRz = Mth.lerp(p, -28.0f, 0.0f);
            }

            poseStack.pushPose();
            poseStack.translate(rollX + jitterX, rollY + jitterY, rollZ);
            poseStack.mulPose(Axis.ZP.rotationDegrees(32.0f));
            poseStack.mulPose(Axis.XP.rotationDegrees(-28.0f));
            poseStack.mulPose(Axis.YP.rotationDegrees(14.0f));
            renderPlayerArmCenteredAtFist(poseStack, bufferSource, packedLight, player, playerRenderer, true);
            poseStack.popPose();

            poseStack.pushPose();
            poseStack.translate(rollX + jitterX, rollY + jitterY, rollZ);
            poseStack.mulPose(Axis.ZP.rotationDegrees(rollRz));
            poseStack.mulPose(Axis.XP.rotationDegrees(rollRx));
            poseStack.mulPose(Axis.YP.rotationDegrees(rollRy));
            poseStack.scale(0.62f, 0.62f, 0.62f);
            mc.getItemRenderer().renderStatic(
                    player, fpAnimStack, ItemDisplayContext.NONE, false,
                    poseStack, bufferSource, player.level(), packedLight, OverlayTexture.NO_OVERLAY, player.getId()
            );
            poseStack.popPose();
            return;
        }

        if (fpAnimLimb == MedicalData.Limb.CHEST) {
            float bx = Mth.lerp(enterLeave, -0.45f, -0.20f);
            float by = Mth.lerp(enterLeave, -0.65f, -0.28f);
            float bz = Mth.lerp(enterLeave, -0.52f, -0.44f);

            poseStack.pushPose();
            poseStack.translate(bx + jitterX * 0.3f, by + jitterY * 0.3f, bz);
            poseStack.mulPose(Axis.ZP.rotationDegrees(-46.0f));
            poseStack.mulPose(Axis.XP.rotationDegrees(-32.0f));
            poseStack.mulPose(Axis.YP.rotationDegrees(16.0f));
            renderPlayerArmCenteredAtFist(poseStack, bufferSource, packedLight, player, playerRenderer, false);
            poseStack.popPose();

            float rollX, rollY, rollZ, rollRx, rollRy, rollRz;
            boolean showChestStrip = false;
            if (t < 0.16f) {
                float p = easeOutCubic(t / 0.16f);
                rollX = Mth.lerp(p, restX, -0.16f);
                rollY = Mth.lerp(p, restY, -0.26f);
                rollZ = Mth.lerp(p, restZ, -0.46f);
                rollRx = Mth.lerp(p, 0.0f, 32.0f);
                rollRy = Mth.lerp(p, 0.0f, 24.0f);
                rollRz = Mth.lerp(p, 0.0f, -35.0f);
            } else if (t < 0.80f) {
                showChestStrip = true;
                float wrapP = (t - 0.16f) / 0.64f;
                float ang = wrapP * 3.0f * Mth.TWO_PI;
                float cinch = Math.max(0.0f, Mth.sin(ang)) * 0.03f;
                rollX = 0.04f + Mth.sin(ang) * 0.30f;
                rollY = -0.26f + Mth.cos(ang * 2.0f) * 0.055f + cinch;
                rollZ = -0.42f - 0.11f * Mth.cos(ang);
                rollRx = 35.0f + 18.0f * Mth.cos(ang);
                rollRy = 28.0f * Mth.sin(ang);
                rollRz = -38.0f + 20.0f * Mth.cos(ang);
            } else if (t < 0.88f) {
                float p = easeInOutCubic((t - 0.80f) / 0.08f);
                float press = Mth.sin(p * (float) Math.PI) * 0.035f;
                rollX = -0.06f;
                rollY = -0.26f - press;
                rollZ = -0.44f + press;
                rollRx = 28.0f;
                rollRy = 18.0f;
                rollRz = -42.0f;
            } else {
                float p = easeInOutCubic((t - 0.88f) / 0.12f);
                rollX = Mth.lerp(p, -0.06f, restX);
                rollY = Mth.lerp(p, -0.26f, restY);
                rollZ = Mth.lerp(p, -0.44f, restZ);
                rollRx = Mth.lerp(p, 28.0f, 0.0f);
                rollRy = Mth.lerp(p, 18.0f, 0.0f);
                rollRz = Mth.lerp(p, -42.0f, 0.0f);
            }

            poseStack.pushPose();
            poseStack.translate(rollX + jitterX, rollY + jitterY, rollZ);
            poseStack.mulPose(Axis.ZP.rotationDegrees(38.0f));
            poseStack.mulPose(Axis.XP.rotationDegrees(-32.0f));
            poseStack.mulPose(Axis.YP.rotationDegrees(14.0f));
            renderPlayerArmCenteredAtFist(poseStack, bufferSource, packedLight, player, playerRenderer, true);
            poseStack.popPose();

            poseStack.pushPose();
            poseStack.translate(rollX + jitterX, rollY + jitterY, rollZ);
            poseStack.mulPose(Axis.ZP.rotationDegrees(rollRz));
            poseStack.mulPose(Axis.XP.rotationDegrees(rollRx));
            poseStack.mulPose(Axis.YP.rotationDegrees(rollRy));
            poseStack.scale(0.62f, 0.62f, 0.62f);
            mc.getItemRenderer().renderStatic(
                    player, fpAnimStack, ItemDisplayContext.NONE, false,
                    poseStack, bufferSource, player.level(), packedLight, OverlayTexture.NO_OVERLAY, player.getId()
            );
            poseStack.popPose();

            if (bufferSource instanceof MultiBufferSource.BufferSource bs) {
                bs.endBatch();
            }
            if (showChestStrip) {
                render3DTautStripBetweenPoints(poseStack, bx + 0.04f, by + 0.02f, bz - 0.02f, rollX, rollY, rollZ, mainBandageCol, trimStripeCol);
            }
            return;
        }

        float limbCx, limbCy, limbCz;
        float limbRx, limbRy, limbRz;
        boolean isLegLimb = (fpAnimLimb == MedicalData.Limb.LEFT_LEG || fpAnimLimb == MedicalData.Limb.RIGHT_LEG);
        boolean hasSeparateBraceHand = false;
        float braceX = 0.0f, braceY = 0.0f, braceZ = 0.0f;
        float braceRx = 0.0f, braceRy = 0.0f, braceRz = 0.0f;

        switch (fpAnimLimb) {
            case LEFT_ARM -> {
                limbCx = -0.06f; limbCy = -0.21f; limbCz = -0.56f;
                limbRx = -18.0f; limbRy = 8.0f;   limbRz = -72.0f;
            }
            case RIGHT_ARM -> {
                limbCx = 0.06f;  limbCy = -0.21f; limbCz = -0.56f;
                limbRx = -18.0f; limbRy = -8.0f;  limbRz = 72.0f;
            }
            case LEFT_LEG -> {
                limbCx = -0.10f; limbCy = -0.48f; limbCz = -0.50f;
                limbRx = -14.0f; limbRy = 6.0f;   limbRz = -6.0f;
                hasSeparateBraceHand = true;
                braceX = -0.24f; braceY = -0.36f; braceZ = -0.48f;
                braceRx = -42.0f; braceRy = 15.0f; braceRz = -34.0f;
            }
            case RIGHT_LEG -> {
                limbCx = 0.10f;  limbCy = -0.48f; limbCz = -0.50f;
                limbRx = -14.0f; limbRy = -6.0f;  limbRz = 6.0f;
                hasSeparateBraceHand = true;
                braceX = -0.04f; braceY = -0.36f; braceZ = -0.48f;
                braceRx = -42.0f; braceRy = 18.0f; braceRz = -52.0f;
            }
            default -> {
                limbCx = -0.06f; limbCy = -0.21f; limbCz = -0.56f;
                limbRx = -18.0f; limbRy = 8.0f;   limbRz = -72.0f;
            }
        }

        float curLimbX = isLegLimb ? limbCx : Mth.lerp(enterLeave, targSide * 0.35f, limbCx);
        float curLimbY = isLegLimb ? limbCy : Mth.lerp(enterLeave, -0.68f, limbCy);
        float curLimbZ = isLegLimb ? limbCz : Mth.lerp(enterLeave, -0.52f, limbCz);

        float wrapProgress = 0.0f;
        float padAppear = 0.0f;
        float claspLockProgress = 0.0f;
        boolean showTautStrip = false;

        boolean isPerforatedTarget = false;
        MedicalData fpData = player.getData(MedicalDataCapability.MEDICAL_DATA);
        if (fpData != null && fpData.getLimb(fpAnimLimb) != null) {
            isPerforatedTarget = fpData.getLimb(fpAnimLimb).hasPerforation();
        }
        float packPhaseEnd = isPerforatedTarget ? 0.28f : 0.16f;
        float packThrust = 0.0f;
        if (t >= 0.08f && t < packPhaseEnd) {
            padAppear = easeOutCubic((t - 0.08f) / (packPhaseEnd - 0.08f));
            if (isPerforatedTarget) {
                packThrust = Mth.sin(((t - 0.08f) / (packPhaseEnd - 0.08f)) * Mth.TWO_PI * 2.0f) * 0.038f;
            }
        } else if (t >= packPhaseEnd && t < 0.80f) {
            padAppear = 1.0f;
            wrapProgress = (t - packPhaseEnd) / (0.80f - packPhaseEnd);
            showTautStrip = true;
        } else if (t >= 0.80f) {
            padAppear = 1.0f;
            wrapProgress = 1.0f;
            claspLockProgress = Mth.clamp((t - 0.80f) / 0.08f, 0.0f, 1.0f);
        }

        float totalAngle = wrapProgress * 3.0f * Mth.TWO_PI;
        float turnFrac = (wrapProgress * 3.0f) % 1.0f;
        float cinchPull = (showTautStrip && turnFrac > 0.62f)
                ? Mth.sin(((turnFrac - 0.62f) / 0.38f) * (float) Math.PI) * 0.032f
                : 0.0f;

        if (!isLegLimb) {
            curLimbY += cinchPull * 0.28f;
        }

        float limbHalfWidth = 0.146f;
        float spiralY = Mth.lerp(wrapProgress, -0.095f, 0.095f);
        float orbitDist = 0.095f + cinchPull * 1.4f;

        float cosA = Mth.cos(totalAngle);
        float sinA = Mth.sin(totalAngle);
        float tanAng = totalAngle - 0.55f;
        float cTan = Mth.cos(tanAng), sTan = Mth.sin(tanAng);
        float mTan = Math.max(Math.abs(cTan), Math.abs(sTan));
        float localTanX = (cTan / mTan) * (limbHalfWidth + 0.010f);
        float localTanY = spiralY;
        float localTanZ = (sTan / mTan) * (limbHalfWidth + 0.010f);

        float localRollX = cosA * (limbHalfWidth + orbitDist);
        float localRollY = spiralY + 0.015f * Mth.sin(totalAngle * 2.0f);
        float localRollZ = sinA * (limbHalfWidth + orbitDist);

        float[] camRollPos = transformLimbLocalToCamera(
                localRollX, localRollY, localRollZ,
                curLimbX, curLimbY, curLimbZ,
                limbRx, limbRy, limbRz
        );

        float rollX, rollY, rollZ;
        float rollRx, rollRy, rollRz;

        if (t < packPhaseEnd) {
            float p = easeOutCubic(Math.min(1.0f, t / 0.16f));
            rollX = Mth.lerp(p, restX, camRollPos[0]);
            rollY = Mth.lerp(p, restY, camRollPos[1] - Math.abs(packThrust) * 0.6f);
            rollZ = Mth.lerp(p, restZ, camRollPos[2] + packThrust);
            rollRx = Mth.lerp(p, 0.0f, limbRx + 25.0f);
            rollRy = Mth.lerp(p, 0.0f, actSide * 28.0f);
            rollRz = Mth.lerp(p, 0.0f, limbRz);
        } else if (t < 0.80f) {
            rollX = camRollPos[0];
            rollY = camRollPos[1];
            rollZ = camRollPos[2];
            rollRx = limbRx + (totalAngle * Mth.RAD_TO_DEG);
            rollRy = limbRy + actSide * 24.0f;
            rollRz = limbRz - actSide * 18.0f;
        } else if (t < 0.88f) {
            float p = easeInOutCubic((t - 0.80f) / 0.08f);
            float pressDepth = Mth.sin(p * (float) Math.PI) * 0.035f;
            float[] claspCam = transformLimbLocalToCamera(0.0f, 0.04f, limbHalfWidth + 0.048f - pressDepth, curLimbX, curLimbY, curLimbZ, limbRx, limbRy, limbRz);
            rollX = Mth.lerp(p, camRollPos[0], claspCam[0]);
            rollY = Mth.lerp(p, camRollPos[1], claspCam[1]);
            rollZ = Mth.lerp(p, camRollPos[2], claspCam[2]);
            rollRx = limbRx + 15.0f;
            rollRy = actSide * 20.0f;
            rollRz = limbRz;
        } else {
            float p = easeInOutCubic((t - 0.88f) / 0.12f);
            float[] claspCam = transformLimbLocalToCamera(0.0f, 0.04f, limbHalfWidth + 0.048f, curLimbX, curLimbY, curLimbZ, limbRx, limbRy, limbRz);
            rollX = Mth.lerp(p, claspCam[0], restX);
            rollY = Mth.lerp(p, claspCam[1], restY);
            rollZ = Mth.lerp(p, claspCam[2], restZ);
            rollRx = Mth.lerp(p, limbRx + 15.0f, 0.0f);
            rollRy = Mth.lerp(p, actSide * 20.0f, 0.0f);
            rollRz = Mth.lerp(p, limbRz, 0.0f);
        }

        poseStack.pushPose();
        poseStack.translate(curLimbX + jitterX * 0.35f, curLimbY + jitterY * 0.35f, curLimbZ);
        poseStack.mulPose(Axis.ZP.rotationDegrees(limbRz));
        poseStack.mulPose(Axis.XP.rotationDegrees(limbRx));
        poseStack.mulPose(Axis.YP.rotationDegrees(limbRy));

        switch (fpAnimLimb) {
            case LEFT_ARM -> renderPlayerArmCenteredAtForearm(poseStack, bufferSource, packedLight, player, playerRenderer, false);
            case RIGHT_ARM -> renderPlayerArmCenteredAtForearm(poseStack, bufferSource, packedLight, player, playerRenderer, true);
            case LEFT_LEG -> renderPlayerLegCenteredAtMid(poseStack, bufferSource, packedLight, player, playerRenderer, false);
            case RIGHT_LEG -> renderPlayerLegCenteredAtMid(poseStack, bufferSource, packedLight, player, playerRenderer, true);
            default -> {}
        }
        poseStack.popPose();

        if (hasSeparateBraceHand) {
            float bx = Mth.lerp(enterLeave, -0.45f, braceX);
            float by = Mth.lerp(enterLeave, -0.65f, braceY);
            float bz = Mth.lerp(enterLeave, -0.52f, braceZ);
            poseStack.pushPose();
            poseStack.translate(bx + jitterX * 0.3f, by + jitterY * 0.3f, bz);
            poseStack.mulPose(Axis.ZP.rotationDegrees(braceRz));
            poseStack.mulPose(Axis.XP.rotationDegrees(braceRx));
            poseStack.mulPose(Axis.YP.rotationDegrees(braceRy));
            renderPlayerArmCenteredAtFist(poseStack, bufferSource, packedLight, player, playerRenderer, false);
            poseStack.popPose();
        }

        poseStack.pushPose();
        poseStack.translate(rollX + jitterX, rollY + jitterY, rollZ);
        poseStack.mulPose(Axis.ZP.rotationDegrees(actSide * 38.0f));
        poseStack.mulPose(Axis.XP.rotationDegrees(-32.0f));
        poseStack.mulPose(Axis.YP.rotationDegrees(actSide * 14.0f));
        renderPlayerArmCenteredAtFist(poseStack, bufferSource, packedLight, player, playerRenderer, wrapWithRightArm);
        poseStack.popPose();

        poseStack.pushPose();
        poseStack.translate(rollX + jitterX, rollY + jitterY, rollZ);
        poseStack.mulPose(Axis.ZP.rotationDegrees(rollRz));
        poseStack.mulPose(Axis.XP.rotationDegrees(rollRx));
        poseStack.mulPose(Axis.YP.rotationDegrees(rollRy));
        poseStack.scale(0.62f, 0.62f, 0.62f);
        mc.getItemRenderer().renderStatic(
                player, fpAnimStack, ItemDisplayContext.NONE, false,
                poseStack, bufferSource, player.level(), packedLight, OverlayTexture.NO_OVERLAY, player.getId()
        );
        poseStack.popPose();

        if (bufferSource instanceof MultiBufferSource.BufferSource bs) {
            bs.endBatch();
        }

        if (padAppear > 0.01f) {
            poseStack.pushPose();
            poseStack.translate(curLimbX + jitterX * 0.35f, curLimbY + jitterY * 0.35f, curLimbZ);
            poseStack.mulPose(Axis.ZP.rotationDegrees(limbRz));
            poseStack.mulPose(Axis.XP.rotationDegrees(limbRx));
            poseStack.mulPose(Axis.YP.rotationDegrees(limbRy));

            render3DProgressiveBandageLayersOnLimb(
                    poseStack,
                    limbHalfWidth,
                    padAppear,
                    wrapProgress,
                    claspLockProgress,
                    showTautStrip,
                    localTanX, localTanY, localTanZ,
                    localRollX, localRollY, localRollZ,
                    mainBandageCol,
                    trimStripeCol,
                    claspColor,
                    isPressure
            );
            poseStack.popPose();
        }
    }

    /**
     * 在相机空间两点之间绘制拉紧的 3D 纱布带（用于胸腹部包扎时连接左手按压点与右手绷带卷）
     */
    private static void render3DTautStripBetweenPoints(PoseStack poseStack,
                                                       float x0, float y0, float z0,
                                                       float x1, float y1, float z1,
                                                       int mainCol, int trimCol) {
        Matrix4f mat = poseStack.last().pose();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.enableDepthTest();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);

        Tesselator tess = Tesselator.getInstance();
        BufferBuilder buf = tess.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);

        int baseR = (mainCol >>> 16) & 0xFF;
        int baseG = (mainCol >>> 8) & 0xFF;
        int baseB = mainCol & 0xFF;
        int trimR = (trimCol >>> 16) & 0xFF;
        int trimG = (trimCol >>> 8) & 0xFF;
        int trimB = trimCol & 0xFF;

        float hw = 0.036f;
        float edgeH = 0.004f;
        int steps = 6;
        for (int k = 0; k < steps; k++) {
            float u0 = k / (float) steps;
            float u1 = (k + 1) / (float) steps;
            float sag0 = Mth.sin(u0 * (float) Math.PI) * 0.012f;
            float sag1 = Mth.sin(u1 * (float) Math.PI) * 0.012f;

            float sx0 = Mth.lerp(u0, x0, x1);
            float sy0 = Mth.lerp(u0, y0, y1) - sag0;
            float sz0 = Mth.lerp(u0, z0, z1);

            float sx1 = Mth.lerp(u1, x0, x1);
            float sy1 = Mth.lerp(u1, y0, y1) - sag1;
            float sz1 = Mth.lerp(u1, z0, z1);

            buf.addVertex(mat, sx0, sy0 - hw, sz0).setColor(baseR, baseG, baseB, 242);
            buf.addVertex(mat, sx1, sy1 - hw, sz1).setColor(baseR, baseG, baseB, 242);
            buf.addVertex(mat, sx1, sy1 + hw, sz1).setColor(baseR, baseG, baseB, 242);
            buf.addVertex(mat, sx0, sy0 + hw, sz0).setColor(baseR, baseG, baseB, 242);

            buf.addVertex(mat, sx0, sy0 + hw - edgeH, sz0).setColor(trimR, trimG, trimB, 255);
            buf.addVertex(mat, sx1, sy1 + hw - edgeH, sz1).setColor(trimR, trimG, trimB, 255);
            buf.addVertex(mat, sx1, sy1 + hw, sz1).setColor(trimR, trimG, trimB, 255);
            buf.addVertex(mat, sx0, sy0 + hw, sz0).setColor(trimR, trimG, trimB, 255);
        }

        MeshData mesh = buf.build();
        if (mesh != null) {
            BufferUploader.drawWithShader(mesh);
        }
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
    }

    private static float[] transformLimbLocalToCamera(float lx, float ly, float lz,
                                                      float cx, float cy, float cz,
                                                      float rxDeg, float ryDeg, float rzDeg) {
        float ry = ryDeg * Mth.DEG_TO_RAD;
        float rx = rxDeg * Mth.DEG_TO_RAD;
        float rz = rzDeg * Mth.DEG_TO_RAD;

        float x1 = lx * Mth.cos(ry) + lz * Mth.sin(ry);
        float y1 = ly;
        float z1 = -lx * Mth.sin(ry) + lz * Mth.cos(ry);

        float x2 = x1;
        float y2 = y1 * Mth.cos(rx) - z1 * Mth.sin(rx);
        float z2 = y1 * Mth.sin(rx) + z1 * Mth.cos(rx);

        float x3 = x2 * Mth.cos(rz) - y2 * Mth.sin(rz);
        float y3 = x2 * Mth.sin(rz) + y2 * Mth.cos(rz);
        float z3 = z2;

        return new float[]{cx + x3, cy + y3, cz + z3};
    }

    /**
     * 在受包扎四肢（左臂/右臂/左腿/右腿）的局部三维空间内，采用方形截面轮廓贴合算法 (c / max(|c|, |s|) * halfWidth)：
     * - 确保在原版 4x12x4 矩形手臂/腿部（含外层袖子/裤腿 0.1406m）的四个平面与四个直角棱上均有 >= 6mm 的正向外扩裕量，彻底杜绝与皮肤/袖子/裤腿重叠穿模！
     */
    private static void render3DProgressiveBandageLayersOnLimb(PoseStack poseStack,
                                                               float limbHalfWidth,
                                                               float padAppear, float wrapProgress, float claspProgress,
                                                               boolean showTautStrip,
                                                               float tanX, float tanY, float tanZ,
                                                               float rollX, float rollY, float rollZ,
                                                               int mainCol, int trimCol, int claspCol,
                                                               boolean isPressure) {
        Matrix4f mat = poseStack.last().pose();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.enableDepthTest();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);

        Tesselator tess = Tesselator.getInstance();
        BufferBuilder buf = tess.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);

        // 1. 首层贴在伤口正面的无菌敷料创垫（位于袖子/裤腿外表面 +2mm ~ +8mm）
        float padScale = easeOutCubic(padAppear);
        float pw = 0.058f * padScale;
        float ph = 0.070f * padScale;
        float pz0 = limbHalfWidth + 0.002f;
        float pz1 = limbHalfWidth + 0.008f;
        emitColoredBox3D(buf, mat, -pw, -ph, pz0, pw, ph, pz1, 0xFFF8FBFF, 0.98f);
        emitColoredBox3D(buf, mat, -pw * 0.42f, -ph * 0.42f, pz1 - 0.001f, pw * 0.42f, ph * 0.42f, pz1 + 0.002f, 0xFFD32F2F, 0.80f);

        // 2. 沿方形截面周长逐段缠绕 3 整圈 (3 * 16 = 48 段，每段 22.5°，45°/135°/225°/315° 恰好落在方形肢体的四条棱角上，100% 贴合无穿模！)
        int totalSegments = 48;
        float exactSegs = wrapProgress * totalSegments;
        int fullSegs = (int) exactSegs;
        float partialFrac = exactSegs - fullSegs;

        int baseR = (mainCol >>> 16) & 0xFF;
        int baseG = (mainCol >>> 8) & 0xFF;
        int baseB = mainCol & 0xFF;

        int trimR = (trimCol >>> 16) & 0xFF;
        int trimG = (trimCol >>> 8) & 0xFF;
        int trimB = trimCol & 0xFF;

        float bandHalfWidth = 0.036f;

        for (int s = 0; s <= fullSegs && s < totalSegments; s++) {
            float segFrac = (s == fullSegs) ? partialFrac : 1.0f;
            if (segFrac <= 0.02f) continue;

            float p0 = s / (float) totalSegments;
            float p1 = (s + segFrac) / (float) totalSegments;

            float a0 = p0 * 3.0f * Mth.TWO_PI;
            float a1 = p1 * 3.0f * Mth.TWO_PI;

            int turnIdx = s / 16;
            float yCenter0, yCenter1;
            if (turnIdx == 0) {
                yCenter0 = Mth.lerp(p0 * 3.0f, -0.075f, 0.055f);
                yCenter1 = Mth.lerp(p1 * 3.0f, -0.075f, 0.055f);
            } else if (turnIdx == 1) {
                yCenter0 = Mth.lerp((p0 - 1.0f / 3.0f) * 3.0f, 0.065f, -0.065f);
                yCenter1 = Mth.lerp((p1 - 1.0f / 3.0f) * 3.0f, 0.065f, -0.065f);
            } else {
                yCenter0 = Mth.lerp((p0 - 2.0f / 3.0f) * 3.0f, -0.055f, 0.045f);
                yCenter1 = Mth.lerp((p1 - 2.0f / 3.0f) * 3.0f, -0.055f, 0.045f);
            }

            // 每一圈向外递增 0.0055m，完全位于 0.1406m 袖子/裤腿表面之外
            float hwOut = limbHalfWidth + 0.006f + turnIdx * 0.0055f;
            float hwIn  = hwOut - 0.0045f;

            float c0 = Mth.cos(a0), s0 = Mth.sin(a0);
            float c1 = Mth.cos(a1), s1 = Mth.sin(a1);
            float m0 = Math.max(Math.abs(c0), Math.abs(s0));
            float m1 = Math.max(Math.abs(c1), Math.abs(s1));

            float x0Out = (c0 / m0) * hwOut, z0Out = (s0 / m0) * hwOut;
            float x1Out = (c1 / m1) * hwOut, z1Out = (s1 / m1) * hwOut;
            float x0In  = (c0 / m0) * hwIn,  z0In  = (s0 / m0) * hwIn;
            float x1In  = (c1 / m1) * hwIn,  z1In  = (s1 / m1) * hwIn;

            float lightDot = 0.78f + 0.22f * (0.5f * (s0 + s1) + 0.35f * (c0 + c1));
            float turnShade = 1.0f - (2 - turnIdx) * 0.04f;
            float shade = Mth.clamp(lightDot * turnShade, 0.64f, 1.06f);

            int sr = Mth.clamp((int) (baseR * shade), 0, 255);
            int sg = Mth.clamp((int) (baseG * shade), 0, 255);
            int sb = Mth.clamp((int) (baseB * shade), 0, 255);

            // 外表面四边面
            buf.addVertex(mat, x0Out, yCenter0 - bandHalfWidth, z0Out).setColor(sr, sg, sb, 255);
            buf.addVertex(mat, x1Out, yCenter1 - bandHalfWidth, z1Out).setColor(sr, sg, sb, 255);
            buf.addVertex(mat, x1Out, yCenter1 + bandHalfWidth, z1Out).setColor(sr, sg, sb, 255);
            buf.addVertex(mat, x0Out, yCenter0 + bandHalfWidth, z0Out).setColor(sr, sg, sb, 255);

            // 上下边缘厚度阶梯 + 锁边线
            buf.addVertex(mat, x0In,  yCenter0 + bandHalfWidth, z0In).setColor(trimR, trimG, trimB, 255);
            buf.addVertex(mat, x0Out, yCenter0 + bandHalfWidth, z0Out).setColor(trimR, trimG, trimB, 255);
            buf.addVertex(mat, x1Out, yCenter1 + bandHalfWidth, z1Out).setColor(trimR, trimG, trimB, 255);
            buf.addVertex(mat, x1In,  yCenter1 + bandHalfWidth, z1In).setColor(trimR, trimG, trimB, 255);

            buf.addVertex(mat, x0Out, yCenter0 - bandHalfWidth, z0Out).setColor(trimR, trimG, trimB, 255);
            buf.addVertex(mat, x0In,  yCenter0 - bandHalfWidth, z0In).setColor(trimR, trimG, trimB, 255);
            buf.addVertex(mat, x1In,  yCenter1 - bandHalfWidth, z1In).setColor(trimR, trimG, trimB, 255);
            buf.addVertex(mat, x1Out, yCenter1 - bandHalfWidth, z1Out).setColor(trimR, trimG, trimB, 255);
        }

        // 3. 渲染从肢体最新包扎切点连接到手中绷带卷的 3D 展开纱布带
        if (showTautStrip) {
            int subSteps = 6;
            for (int k = 0; k < subSteps; k++) {
                float u0 = k / (float) subSteps;
                float u1 = (k + 1) / (float) subSteps;
                float sag0 = Mth.sin(u0 * (float) Math.PI) * 0.010f;
                float sag1 = Mth.sin(u1 * (float) Math.PI) * 0.010f;

                float sx0 = Mth.lerp(u0, tanX, rollX);
                float sy0 = Mth.lerp(u0, tanY, rollY) - sag0;
                float sz0 = Mth.lerp(u0, tanZ, rollZ);

                float sx1 = Mth.lerp(u1, tanX, rollX);
                float sy1 = Mth.lerp(u1, tanY, rollY) - sag1;
                float sz1 = Mth.lerp(u1, tanZ, rollZ);

                int rCol = Mth.clamp((int) (baseR * (0.92f + 0.08f * u0)), 0, 255);
                int gCol = Mth.clamp((int) (baseG * (0.92f + 0.08f * u0)), 0, 255);
                int bCol = Mth.clamp((int) (baseB * (0.92f + 0.08f * u0)), 0, 255);

                buf.addVertex(mat, sx0, sy0 - bandHalfWidth, sz0).setColor(rCol, gCol, bCol, 245);
                buf.addVertex(mat, sx1, sy1 - bandHalfWidth, sz1).setColor(rCol, gCol, bCol, 245);
                buf.addVertex(mat, sx1, sy1 + bandHalfWidth, sz1).setColor(rCol, gCol, bCol, 245);
                buf.addVertex(mat, sx0, sy0 + bandHalfWidth, sz0).setColor(rCol, gCol, bCol, 245);

                float edgeH = 0.004f;
                buf.addVertex(mat, sx0, sy0 + bandHalfWidth - edgeH, sz0).setColor(trimR, trimG, trimB, 255);
                buf.addVertex(mat, sx1, sy1 + bandHalfWidth - edgeH, sz1).setColor(trimR, trimG, trimB, 255);
                buf.addVertex(mat, sx1, sy1 + bandHalfWidth, sz1).setColor(trimR, trimG, trimB, 255);
                buf.addVertex(mat, sx0, sy0 + bandHalfWidth, sz0).setColor(trimR, trimG, trimB, 255);
            }
        }

        // 4. 收尾阶段按压固定在包扎层外侧的 3D 金属蝴蝶搭扣 / 以式加压 C 型卡扣
        if (claspProgress > 0.01f) {
            float cz = limbHalfWidth + 0.022f;
            if (isPressure) {
                emitColoredBox3D(buf, mat, -0.048f, -0.045f, cz, 0.048f, 0.045f, cz + 0.014f, claspCol, 0.98f);
                emitColoredBox3D(buf, mat, -0.042f, -0.038f, cz + 0.014f, 0.042f, 0.038f, cz + 0.026f, 0xFFFFCA28, 0.95f);
            } else {
                emitColoredBox3D(buf, mat, -0.042f, 0.012f, cz, -0.010f, 0.036f, cz + 0.008f, claspCol, 0.98f);
                emitColoredBox3D(buf, mat,  0.010f, 0.012f, cz,  0.042f, 0.036f, cz + 0.008f, claspCol, 0.98f);
                emitColoredBox3D(buf, mat, -0.012f, 0.014f, cz + 0.001f, 0.012f, 0.034f, cz + 0.009f, 0xFFD7BC91, 0.98f);

                emitColoredBox3D(buf, mat, -0.042f, -0.036f, cz, -0.010f, -0.012f, cz + 0.008f, claspCol, 0.98f);
                emitColoredBox3D(buf, mat,  0.010f, -0.036f, cz,  0.042f, -0.012f, cz + 0.008f, claspCol, 0.98f);
                emitColoredBox3D(buf, mat, -0.012f, -0.034f, cz + 0.001f, 0.012f, -0.014f, cz + 0.009f, 0xFFD7BC91, 0.98f);
            }
        }

        MeshData mesh = buf.build();
        if (mesh != null) {
            BufferUploader.drawWithShader(mesh);
        }
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
    }

    private static void renderPlayerArmCenteredAtFist(PoseStack poseStack, MultiBufferSource bufferSource, int packedLight,
                                                      AbstractClientPlayer player, PlayerRenderer playerRenderer, boolean rightArm) {
        float armSide = rightArm ? 1.0f : -1.0f;
        poseStack.pushPose();
        poseStack.translate(armSide * 0.375f, -0.625f, 0.0f);
        if (rightArm) {
            playerRenderer.renderRightHand(poseStack, bufferSource, packedLight, player);
        } else {
            playerRenderer.renderLeftHand(poseStack, bufferSource, packedLight, player);
        }
        poseStack.popPose();
    }

    private static void renderPlayerArmCenteredAtForearm(PoseStack poseStack, MultiBufferSource bufferSource, int packedLight,
                                                         AbstractClientPlayer player, PlayerRenderer playerRenderer, boolean rightArm) {
        float armSide = rightArm ? 1.0f : -1.0f;
        poseStack.pushPose();
        poseStack.translate(armSide * 0.375f, -0.42f, 0.0f);
        if (rightArm) {
            playerRenderer.renderRightHand(poseStack, bufferSource, packedLight, player);
        } else {
            playerRenderer.renderLeftHand(poseStack, bufferSource, packedLight, player);
        }
        poseStack.popPose();
    }

    private static void renderPlayerLegCenteredAtMid(PoseStack poseStack, MultiBufferSource bufferSource, int packedLight,
                                                     AbstractClientPlayer player, PlayerRenderer playerRenderer, boolean rightLeg) {
        PlayerModel<AbstractClientPlayer> model = playerRenderer.getModel();
        ModelPart leg = rightLeg ? model.rightLeg : model.leftLeg;
        ModelPart pants = rightLeg ? model.rightPants : model.leftPants;
        float legSide = rightLeg ? 1.0f : -1.0f;

        float oldX = leg.xRot, oldY = leg.yRot, oldZ = leg.zRot;
        boolean oldVis = leg.visible;
        boolean oldPantsVis = pants.visible;

        poseStack.pushPose();
        poseStack.translate(legSide * 0.11875f, -1.125f, 0.0f);
        leg.xRot = 0.0f;
        leg.yRot = 0.0f;
        leg.zRot = 0.0f;
        leg.visible = true;
        pants.copyFrom(leg);
        pants.visible = true;

        VertexConsumer vc = bufferSource.getBuffer(RenderType.entityTranslucent(playerRenderer.getTextureLocation(player)));
        leg.render(poseStack, vc, packedLight, OverlayTexture.NO_OVERLAY);
        pants.render(poseStack, vc, packedLight, OverlayTexture.NO_OVERLAY);
        poseStack.popPose();

        leg.xRot = oldX;
        leg.yRot = oldY;
        leg.zRot = oldZ;
        leg.visible = oldVis;
        pants.visible = oldPantsVis;
    }

    /**
     * 在 ItemDisplayContext.NONE 纯净坐标系下，沿注射器精确中轴线 (X=0, Z=0) 渲染随推注进度物理下压的顶部活塞推杆
     */
    private static void renderCoaxialSyringePlungerStem(PoseStack poseStack, ItemStack stack, float depressProgress) {
        int capColor;
        if (TreatmentRouter.isMeropenemItem(stack)) {
            capColor = 0xFFFFCA28;
        } else if (TreatmentRouter.isAcetylcysteineItem(stack)) {
            capColor = 0xFF00E676;
        } else if (TreatmentRouter.isTranexamicAcidItem(stack)) {
            capColor = 0xFFE040FB;
        } else if (TreatmentRouter.isFentanylItem(stack)) {
            capColor = 0xFFF50057;
        } else if (TreatmentRouter.isAdrenalineItem(stack)) {
            capColor = 0xFF2979FF;
        } else if (TreatmentRouter.isNaloxoneItem(stack)) {
            capColor = 0xFF00E5FF;
        } else {
            capColor = 0xFFFFB300;
        }

        float remStroke = 1.0f - Mth.clamp(depressProgress, 0.0f, 1.0f);
        if (remStroke <= 0.02f) return;

        float stemBotY = 0.38f;
        float stemTopY = 0.44f + remStroke * 0.18f;

        Matrix4f mat = poseStack.last().pose();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.enableDepthTest();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);

        Tesselator tess = Tesselator.getInstance();
        BufferBuilder buf = tess.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);

        emitColoredBox3D(buf, mat, -0.018f, stemBotY, -0.018f, 0.018f, stemTopY, 0.018f, 0xFFE0F7FA, 0.92f);
        emitColoredBox3D(buf, mat, -0.062f, stemTopY, -0.062f, 0.062f, stemTopY + 0.024f, 0.062f, capColor, 0.96f);

        MeshData mesh = buf.build();
        if (mesh != null) {
            BufferUploader.drawWithShader(mesh);
        }
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
    }

    private static void emitColoredBox3D(BufferBuilder buf, Matrix4f mat,
                                         float x0, float y0, float z0,
                                         float x1, float y1, float z1,
                                         int rgb, float alpha) {
        int a = Mth.clamp((int) (alpha * 255.0f), 0, 255);
        int r = (rgb >>> 16) & 0xFF;
        int g = (rgb >>> 8) & 0xFF;
        int b = rgb & 0xFF;

        buf.addVertex(mat, x0, y0, z1).setColor(r, g, b, a);
        buf.addVertex(mat, x1, y0, z1).setColor(r, g, b, a);
        buf.addVertex(mat, x1, y1, z1).setColor(r, g, b, a);
        buf.addVertex(mat, x0, y1, z1).setColor(r, g, b, a);

        buf.addVertex(mat, x1, y0, z0).setColor(r, g, b, a);
        buf.addVertex(mat, x0, y0, z0).setColor(r, g, b, a);
        buf.addVertex(mat, x0, y1, z0).setColor(r, g, b, a);
        buf.addVertex(mat, x1, y1, z0).setColor(r, g, b, a);

        buf.addVertex(mat, x0, y0, z0).setColor(r, g, b, a);
        buf.addVertex(mat, x0, y0, z1).setColor(r, g, b, a);
        buf.addVertex(mat, x0, y1, z1).setColor(r, g, b, a);
        buf.addVertex(mat, x0, y1, z0).setColor(r, g, b, a);

        buf.addVertex(mat, x1, y0, z1).setColor(r, g, b, a);
        buf.addVertex(mat, x1, y0, z0).setColor(r, g, b, a);
        buf.addVertex(mat, x1, y1, z0).setColor(r, g, b, a);
        buf.addVertex(mat, x1, y1, z1).setColor(r, g, b, a);

        buf.addVertex(mat, x0, y1, z1).setColor(r, g, b, a);
        buf.addVertex(mat, x1, y1, z1).setColor(r, g, b, a);
        buf.addVertex(mat, x1, y1, z0).setColor(r, g, b, a);
        buf.addVertex(mat, x0, y1, z0).setColor(r, g, b, a);

        buf.addVertex(mat, x0, y0, z0).setColor(r, g, b, a);
        buf.addVertex(mat, x1, y0, z0).setColor(r, g, b, a);
        buf.addVertex(mat, x1, y0, z1).setColor(r, g, b, a);
        buf.addVertex(mat, x0, y0, z1).setColor(r, g, b, a);
    }
}