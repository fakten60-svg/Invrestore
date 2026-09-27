package com.invrestore.server;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.invrestore.InvRestore;
import com.invrestore.config.InvRestoreConfig;
import com.invrestore.data.BackupStatus;
import com.invrestore.data.DeathBackup;
import com.invrestore.data.InvRestoreSavedData;
import com.invrestore.data.Provenance;

import net.minecraft.core.NonNullList;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

/**
 * Creates the death snapshot. This runs from an injection at the head of
 * {@code ServerPlayer#die}, i.e. before vanilla drops the inventory, so the
 * snapshot reflects the exact inventory the player died with.
 *
 * <p>The live stacks are tagged with the new death id at the same moment, so
 * the items vanilla drops a few lines later carry their provenance with them.
 */
public final class DeathHandler {
	private static final Set<UUID> HANDLED = ConcurrentHashMap.newKeySet();

	private DeathHandler() {
	}

	public static void onPlayerDeath(ServerPlayer player, DamageSource source) {
		try {
			MinecraftServer server = player.level().getServer();
			if (server == null) {
				return;
			}
			UUID playerId = player.getUUID();
			if (!HANDLED.add(playerId)) {
				debug("Death already handled for " + player.getScoreboardName());
				return;
			}

			List<ItemStack> liveStacks = collectLiveStacks(player);
			List<ItemStack> snapshot = new ArrayList<>();
			for (ItemStack stack : liveStacks) {
				if (!stack.isEmpty()) {
					snapshot.add(stack.copy());
				}
			}
			if (snapshot.isEmpty()) {
				debug("No items to snapshot for " + player.getScoreboardName());
				return;
			}

			UUID deathId = UUID.randomUUID();
			DeathBackup backup = new DeathBackup(
					deathId,
					playerId,
					player.getScoreboardName(),
					System.currentTimeMillis(),
					player.level().dimension().identifier(),
					player.getX(),
					player.getY(),
					player.getZ(),
					BackupStatus.AVAILABLE,
					0L,
					snapshot);

			InvRestoreSavedData data = InvRestoreSavedData.get(server);
			data.addBackup(backup, InvRestoreConfig.get().maxBackupsPerPlayer);

			// Tag the live stacks so the eventual drops carry their provenance.
			for (ItemStack stack : liveStacks) {
				if (!stack.isEmpty()) {
					Provenance.tag(stack, deathId);
				}
			}

			InvRestore.LOGGER.info("[InvRestore] Death backup created for {}: {} ({} stacks, {} items)",
					backup.ownerName(), deathId, backup.stackCount(), backup.totalItemCount());
		} catch (Throwable t) {
			InvRestore.LOGGER.error("[InvRestore] Failed to create death backup", t);
		}
	}

	public static void onRespawn(UUID playerId) {
		HANDLED.remove(playerId);
	}

	private static List<ItemStack> collectLiveStacks(ServerPlayer player) {
		List<ItemStack> stacks = new ArrayList<>();
		Inventory inventory = player.getInventory();
		NonNullList<ItemStack> main = inventory.getNonEquipmentItems();
		for (int i = 0; i < main.size(); i++) {
			stacks.add(main.get(i));
		}
		for (EquipmentSlot slot : ItemScanner.TRACKED_EQUIPMENT) {
			stacks.add(player.getItemBySlot(slot));
		}
		return stacks;
	}

	private static void debug(String message) {
		if (InvRestoreConfig.get().debugLogging) {
			InvRestore.LOGGER.info("[InvRestore] {}", message);
		}
	}
}
