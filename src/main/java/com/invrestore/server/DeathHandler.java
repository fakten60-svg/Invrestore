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

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.item.ItemStack;

/**
 * Creates the death snapshot. This runs from an injection at the head of
 * {@code ServerPlayer#die}, i.e. before vanilla touches the inventory, so the
 * snapshot reflects the exact inventory the player died with.
 *
 * <p>Two steps, in this order:
 * <ol>
 *   <li>Deep-copy every carried stack into the snapshot ({@link
 *       SnapshotPolicy#takeSnapshot}) - the copies are taken before any
 *       modification.</li>
 *   <li>Tag the live stacks with the death id ({@link
 *       SnapshotPolicy#tagLiveStacks}) so the items vanilla drops shortly
 *       afterwards carry their provenance.</li>
 * </ol>
 *
 * <p>Tagging must happen after the copies are taken - otherwise the snapshot
 * entries themselves would carry the marker, and the component order on the
 * live stacks would differ from vanilla expectations. When {@code keepInventory}
 * is enabled nothing is dropped, so no backup is created at all (documented
 * behaviour). Stacks with the Curse of Vanishing are destroyed by vanilla, so
 * they appear in the snapshot only when they will not be dropped.
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

			// keepInventory: vanilla drops nothing, so there is nothing to
			// restore and no provenance to track. No backup, no tagging.
			if (!SnapshotPolicy.dropsInventory(player)) {
				debug("keepInventory is enabled; skipping death backup for "
						+ player.getScoreboardName());
				return;
			}

			// 1. Snapshot first: pure deep copies, unmodified stacks.
			List<ItemStack> snapshot = SnapshotPolicy.takeSnapshot(player);
			if (SnapshotPolicy.isEmpty(snapshot)) {
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

			// 2. Tag the live stacks afterwards so the future drops carry
			// their provenance (vanishing-cursed stacks stay untagged).
			int tagged = SnapshotPolicy.tagLiveStacks(player, deathId);

			InvRestoreSavedData data = InvRestoreSavedData.get(server);
			data.addBackup(backup, InvRestoreConfig.get().maxBackupsPerPlayer);

			InvRestore.LOGGER.info(
					"[InvRestore] Death backup created for {}: {} ({} stacks, {} items, {} tagged)",
					backup.ownerName(), deathId, backup.stackCount(), backup.totalItemCount(), tagged);
		} catch (Throwable t) {
			InvRestore.LOGGER.error("[InvRestore] Failed to create death backup", t);
		}
	}

	public static void onRespawn(UUID playerId) {
		HANDLED.remove(playerId);
	}

	private static void debug(String message) {
		if (InvRestoreConfig.get().debugLogging) {
			InvRestore.LOGGER.info("[InvRestore] {}", message);
		}
	}
}
