package ink.sakura.autofeed;

import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.gui.screen.Screen;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.entity.Entity;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.entity.passive.CowEntity;
import net.minecraft.entity.passive.PigEntity;
import net.minecraft.entity.passive.SheepEntity;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.glfw.GLFW;

/**
 * The module to auto feed animals
 *
 * @author Hakutyan_bai
 * @since 2025/6/29
 */

public class AutoFeeder implements ClientModInitializer {
    private final MinecraftClient mc = MinecraftClient.getInstance();
    private final KeyBinding toggleKey = new KeyBinding("key.autofeed.toggle", GLFW.GLFW_KEY_F6, "key.categories.misc");
    private boolean enabled = false;
    private int tickCounter = 0;
    private static final int FEED_INTERVAL_TICKS = 4; // 0.2秒（4 tick）
    private final KeyBinding replantToggleKey = new KeyBinding("key.autofeed.replant_toggle", GLFW.GLFW_KEY_F7, "key.categories.misc");
    private boolean replantEnabled = true;
    private final KeyBinding configKey = new KeyBinding("key.autofeed.config", GLFW.GLFW_KEY_F8, "key.categories.misc");
    private int feedIntervalTicks = 4;
    private int feedRange = 5;

    public AutoFeeder() {
        // 注册按键（如需）
        // mc.options.keyBindings = ...
    }

    @Override
    public void onInitializeClient() {
        // 注册F6按键
        KeyBindingHelper.registerKeyBinding(toggleKey);
        KeyBindingHelper.registerKeyBinding(replantToggleKey);
        KeyBindingHelper.registerKeyBinding(configKey);
        // 注册客户端tick事件
        ClientTickEvents.END_CLIENT_TICK.register(client -> this.onTick());
        UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
            if (player != mc.player || hand != Hand.MAIN_HAND) return net.minecraft.util.ActionResult.PASS;
            net.minecraft.util.math.BlockPos pos = hitResult.getBlockPos();
            net.minecraft.block.BlockState state = world.getBlockState(pos);
            if (onRightClickBlock(pos, state)) {
                return net.minecraft.util.ActionResult.SUCCESS;
            }
            return net.minecraft.util.ActionResult.PASS;
        });
    }

    public void onTick() {
        if (mc.player == null || mc.world == null) return;
        if (configKey.wasPressed()){
            mc.setScreen(new AutoFeederConfigScreen(this));
        }
        if (toggleKey.wasPressed()) {
            enabled = !enabled;
            if (mc.player != null && mc.player.networkHandler != null) {
                String key = enabled ? "message.autofeed.enabled" : "message.autofeed.disabled";
                mc.player.sendMessage(
                    net.minecraft.text.Text.translatable(key),
                    false
                );
            }
        }
        if (replantToggleKey.wasPressed()) {
            replantEnabled = !replantEnabled;
            if (mc.player != null && mc.player.networkHandler != null) {
                String key = replantEnabled ? "message.replant.enabled" : "message.replant.disabled";
                mc.player.sendMessage(
                    net.minecraft.text.Text.translatable(key),
                    false
                );
            }
        }
        if (!enabled) return;
        tickCounter++;
        //if (tickCounter % FEED_INTERVAL_TICKS != 0) return;
        if (tickCounter % feedIntervalTicks != 0) return;

        Vec3d pos = mc.player.getPos();
        //Box box = new Box(pos.add(-5, -5, -5), pos.add(5, 5, 5));
        Box box = new Box(pos.add(-feedRange, -feedRange, -feedRange), pos.add(feedRange, feedRange, feedRange));
        for (Entity entity : mc.world.getOtherEntities(mc.player, box)) {
            if (entity instanceof CowEntity || entity instanceof SheepEntity || entity instanceof PigEntity) {
                AnimalEntity animal = (AnimalEntity) entity;
                if (animal.isBaby() || animal.getLoveTicks() > 0) continue;
                Item food = getFoodForAnimal(animal);
                int slot = findHotbar(food);
                if (slot == -1) continue;
                int currentSlot = mc.player.getInventory().getSlotWithStack(mc.player.getMainHandStack());
                if (currentSlot != slot) {
                    // 通过发送切换栏位的网络包实现主手栏切换，避免直接访问private字段
                    mc.player.networkHandler.sendPacket(new net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket(slot));
                    return;
                }
                mc.interactionManager.interactEntity(mc.player, animal, Hand.MAIN_HAND);
            }
        }
    }

    private Item getFoodForAnimal(AnimalEntity animal) {
        if (animal instanceof CowEntity || animal instanceof SheepEntity) return Items.WHEAT;
        if (animal instanceof PigEntity) return Items.CARROT;
        return null;
    }

    private int findHotbar(Item item) {
        for (int i = 0; i < 9; i++) {
            if (mc.player.getInventory().getStack(i).getItem() == item) return i;
        }
        return -1;
    }

    // 自动补种逻辑
    // 监听玩家收获农作物事件，自动补种相应种子
    // 需要在事件注册处注册此方法
    public void onBlockBreak(net.minecraft.util.math.BlockPos pos, net.minecraft.block.BlockState state) {
        if (!replantEnabled || mc.player == null || mc.world == null) return;
        net.minecraft.block.Block block = state.getBlock();
        net.minecraft.item.Item seed = getSeedForCrop(block);
        if (seed == null) return;
        int slot = findHotbar(seed);
        if (slot == -1) return;
        // 判断是否为成熟作物
        if (block instanceof net.minecraft.block.CropBlock cropBlock && cropBlock.isMature(state)) {
            // 切换到种子栏位
            int currentSlot = mc.player.getInventory().getSlotWithStack(mc.player.getMainHandStack());
            if (currentSlot != slot) {
                mc.player.networkHandler.sendPacket(new net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket(slot));
            }
            // 延迟一tick后种下种子，防止和破坏事件冲突
            mc.execute(() -> {
                mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, new net.minecraft.util.hit.BlockHitResult(
                    pos.toCenterPos(), net.minecraft.util.math.Direction.UP, pos, false
                ));
            });
        }
    }

    private Item getSeedForCrop(net.minecraft.block.Block block) {
        if (block == net.minecraft.block.Blocks.WHEAT) return Items.WHEAT_SEEDS;
        if (block == net.minecraft.block.Blocks.CARROTS) return Items.CARROT;
        if (block == net.minecraft.block.Blocks.POTATOES) return Items.POTATO;
        if (block == net.minecraft.block.Blocks.BEETROOTS) return Items.BEETROOT_SEEDS;
        if (block == net.minecraft.block.Blocks.NETHER_WART) return Items.NETHER_WART;
        // 可扩展其他作物
        return null;
    }

    // 自动补种与收获逻辑：对着成熟作物右键即可收获并补种
    // 需在客户端拦截右键事件
    public boolean onRightClickBlock(net.minecraft.util.math.BlockPos pos, net.minecraft.block.BlockState state) {
        if (!replantEnabled || mc.player == null || mc.world == null) return false;
        net.minecraft.block.Block block = state.getBlock();
        net.minecraft.item.Item seed = getSeedForCrop(block);
        if (seed == null) return false;
        // 判断是否为成熟作物或下届疣
        if ((block instanceof net.minecraft.block.CropBlock cropBlock && cropBlock.isMature(state))
            || (block == net.minecraft.block.Blocks.NETHER_WART && state.get(net.minecraft.state.property.Properties.AGE_3) == 3)) {
            // 收获作物（客户端模拟破坏）
            mc.interactionManager.attackBlock(pos, net.minecraft.util.math.Direction.UP);
            // 补种
            int slot = findHotbar(seed);
            if (slot == -1) return true; // 没有种子则只收获
            // 立即补种，无需延迟
            final int[] currentSlotHolder = new int[1];
            currentSlotHolder[0] = mc.player.getInventory().getSlotWithStack(mc.player.getMainHandStack());
            // 反射获取 selectedSlot
            try {
                java.lang.reflect.Field field = mc.player.getInventory().getClass().getDeclaredField("selectedSlot");
                field.setAccessible(true);
                currentSlotHolder[0] = field.getInt(mc.player.getInventory());
            } catch (Exception e) {
                // 兜底
            }
            if (currentSlotHolder[0] != slot) {
                mc.player.networkHandler.sendPacket(new net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket(slot));
            }
            // 延迟一tick后种下种子
            mc.execute(() -> {
                mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, new net.minecraft.util.hit.BlockHitResult(
                    pos.toCenterPos(), net.minecraft.util.math.Direction.UP, pos, false
                ));
                // 补种后切回原栏位
                if (currentSlotHolder[0] != slot) {
                    mc.player.networkHandler.sendPacket(new net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket(currentSlotHolder[0]));
                }
            });
            return true;
        }
        return false;
    }
    public int getFeedIntervalTicks() { return feedIntervalTicks; }
    public void setFeedIntervalTicks(int ticks) { this.feedIntervalTicks = ticks; }
    public int getFeedRange() { return feedRange; }
    public void setFeedRange(int range) { this.feedRange = range; }
}

