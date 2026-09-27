package com.invrestore.server;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.invrestore.data.Provenance;

import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.PlayerEnderChestContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;

/**
 * Locates every reachable item stack belonging to a given death.
 *
 * <p>Scanning is only performed on demand (during a restore). It covers online
 * players' inventories, armour, off-hands and ender chests, every loaded item
 * entity, and - if enabled - the block-entity containers of currently loaded
 * chunks.
 */
public final class ItemScanner {
	/** Equipment slots that are dropped and tracked on death. */
	public static final List<EquipmentSlot> TRACKED_EQUIPMENT = List.of(
			EquipmentSlot.HEAD,
			EquipmentSlot.CHEST,
			EquipmentSlot.LEGS,
			EquipmentSlot.FEET,
			EquipmentSlot.OFFHAND);

	/** A single tracked stack together with the action that removes it. */
	public record Found(ItemStack stack, Runnable clear, String where) {
	}

	private ItemScanner() {
	}

	public static List<Found> find(MinecraftServer server, UUID deathId, boolean containers) {
		List<Found> found = new ArrayList<>();
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			scanPlayer(player, deathId, found);
		}
		for (ServerLevel level : server.getAllLevels()) {
			scanItemEntities(level, deathId, found);
			if (containers) {
				scanContainers(level, deathId, found);
			}
		}
		return found;
	}

	private static void scanPlayer(ServerPlayer player, UUID deathId, List<Found> found) {
		Inventory inventory = player.getInventory();
		NonNullList<ItemStack> main = inventory.getNonEquipmentItems();
		for (int i = 0; i < main.size(); i++) {
			ItemStack stack = main.get(i);
			if (Provenance.isFor(stack, deathId)) {
				final int slot = i;
				found.add(new Found(stack, () -> inventory.setItem(slot, ItemStack.EMPTY),
						player.getScoreboardName() + " inventory slot " + slot));
			}
		}
		for (EquipmentSlot slot : TRACKED_EQUIPMENT) {
			ItemStack stack = player.getItemBySlot(slot);
			if (Provenance.isFor(stack, deathId)) {
				final EquipmentSlot equipmentSlot = slot;
				found.add(new Found(stack, () -> player.setItemSlot(equipmentSlot, ItemStack.EMPTY),
						player.getScoreboardName() + " " + slot.getSerializedName()));
			}
		}
		PlayerEnderChestContainer enderChest = player.getEnderChestInventory();
		for (int i = 0; i < enderChest.getContainerSize(); i++) {
			ItemStack stack = enderChest.getItem(i);
			if (Provenance.isFor(stack, deathId)) {
				final int slot = i;
				found.add(new Found(stack, () -> enderChest.setItem(slot, ItemStack.EMPTY),
						player.getScoreboardName() + " ender chest slot " + slot));
			}
		}
	}

	private static void scanItemEntities(ServerLevel level, UUID deathId, List<Found> found) {
		for (Entity entity : level.getAllEntities()) {
			if (entity instanceof ItemEntity itemEntity && Provenance.isFor(itemEntity.getItem(), deathId)) {
				found.add(new Found(itemEntity.getItem(), itemEntity::discard,
						"dropped item in " + level.dimension().identifier()));
			}
		}
	}

	private static void scanContainers(ServerLevel level, UUID deathId, List<Found> found) {
		for (long chunkKey : TrackedContainers.loadedChunks(level)) {
			ChunkPos chunkPos = ChunkPos.unpack(chunkKey);
			LevelChunk chunk = level.getChunkSource().getChunkNow(chunkPos.x(), chunkPos.z());
			if (chunk == null) {
				continue;
			}
			for (BlockPos blockPos : chunk.getBlockEntitiesPos()) {
				BlockEntity blockEntity = level.getBlockEntity(blockPos);
				if (blockEntity instanceof Container container) {
					scanContainer(container, deathId, found, blockPos, level);
				}
			}
		}
	}

	private static void scanContainer(Container container, UUID deathId, List<Found> found,
			BlockPos pos, ServerLevel level) {
		for (int i = 0; i < container.getContainerSize(); i++) {
			ItemStack stack = container.getItem(i);
			if (Provenance.isFor(stack, deathId)) {
				final int slot = i;
				found.add(new Found(stack, () -> container.setItem(slot, ItemStack.EMPTY),
						"container " + pos.toShortString() + " in " + level.dimension().identifier()
								+ " slot " + slot));
			}
		}
	}
}
