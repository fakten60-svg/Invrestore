package com.invrestore.server;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.invrestore.data.Provenance;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentEffectComponents;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.gamerules.GameRules;

/**
 * Defines which stacks belong into a death snapshot.
 *
 * <p>The snapshot must describe exactly the stacks that vanilla is about to
 * handle in {@code Player#dropEquipment}, which runs a few ticks after the
 * injection point (head of {@code ServerPlayer#die}). Vanilla, verified against
 * the 26.1.2 bytecode:
 *
 * <ol>
 *   <li>If the {@code keepInventory} game rule is enabled, the inventory is
 *       kept and <em>nothing</em> is dropped.</li>
 *   <li>Otherwise {@code destroyVanishingCursedItems()} first removes every
 *       stack carrying the {@code minecraft:prevent_equipment_drop} enchantment
 *       effect (Verdammnis der Vergänglichkeit / Curse of Vanishing) - these
 *       stacks are destroyed, never dropped.</li>
 *   <li>Finally {@code Inventory#dropAll()} drops main inventory plus
 *       equipment slots and empties them.</li>
 * </ol>
 *
 * <p>{@link #takeSnapshot(ServerPlayer)} therefore skips nothing and mirrors
 * vanilla exactly, while {@link #tagLiveStacks} only runs when vanilla will
 * actually drop the items. Tagging stacks that are never dropped would mark
 * phantom items, so it is deliberately skipped for keepInventory and for
 * vanishing-cursed stacks.
 *
 * <p>Must be called on the main thread during death, before any drop happens.
 * Snapshot entries are deep copies; the live stacks keep their identity and are
 * later tagged in place, so the dropped items carry their provenance.
 */
public final class SnapshotPolicy {
	private SnapshotPolicy() {
	}

	/** Equipment slots vanilla drops from a player inventory on death. */
	public static final List<EquipmentSlot> TRACKED_EQUIPMENT = List.of(
			EquipmentSlot.HEAD,
			EquipmentSlot.CHEST,
			EquipmentSlot.LEGS,
			EquipmentSlot.FEET,
			EquipmentSlot.OFFHAND);

	/** Whether vanilla will drop the player's inventory on this death. */
	public static boolean dropsInventory(ServerPlayer player) {
		ServerLevel level = player.level() instanceof ServerLevel serverLevel ? serverLevel : null;
		if (level == null) {
			return false;
		}
		return !Boolean.TRUE.equals(level.getGameRules().get(GameRules.KEEP_INVENTORY));
	}

	/** Whether vanilla destroys this stack through the Curse of Vanishing. */
	public static boolean isVanishingCursed(ItemStack stack) {
		return EnchantmentHelper.has(stack, EnchantmentEffectComponents.PREVENT_EQUIPMENT_DROP);
	}

	/**
	 * Deep copies every stack vanilla would drop (main inventory + equipment).
	 * Purely read-only: live stacks are not modified and not tagged here.
	 *
	 * @return snapshot copies in vanilla order (never empty-checked here)
	 */
	public static List<ItemStack> takeSnapshot(ServerPlayer player) {
		List<ItemStack> snapshot = new ArrayList<>();
		Inventory inventory = player.getInventory();
		for (int i = 0; i < inventory.getContainerSize(); i++) {
			ItemStack stack = inventory.getItem(i);
			if (!stack.isEmpty()) {
				snapshot.add(stack.copy());
			}
		}
		return snapshot;
	}

	/**
	 * Tags the live stacks in place so the items vanilla drops carry their
	 * provenance. Skipped entirely when {@link #dropsInventory} is false, and
	 * per stack when {@link #isVanishingCursed} is true - vanilla will neither
	 * drop nor preserve those items, so they must not be tagged.
	 *
	 * @return the number of tagged stacks
	 */
	public static int tagLiveStacks(ServerPlayer player, UUID deathId) {
		if (!dropsInventory(player)) {
			return 0;
		}
		int tagged = 0;
		Inventory inventory = player.getInventory();
		for (int i = 0; i < inventory.getContainerSize(); i++) {
			ItemStack stack = inventory.getItem(i);
			if (!stack.isEmpty() && !isVanishingCursed(stack)) {
				Provenance.tag(stack, deathId);
				tagged++;
			}
		}
		return tagged;
	}

	/** Only keep snapshots when there is anything to restore later. */
	public static boolean isEmpty(List<ItemStack> snapshot) {
		return snapshot.isEmpty();
	}
}
