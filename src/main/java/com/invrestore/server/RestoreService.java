package com.invrestore.server;

import java.util.ArrayList;
import java.util.Iterator;
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
import net.minecraft.world.item.ItemStack;

/**
 * Executes a restore.
 *
 * <p>A restore never spawns items. It only ever moves stacks that physically
 * exist and that carry the exact death id of the selected backup. That
 * property alone rules out duplication: unrelated items are never touched and
 * the same physical stack can never be counted twice.
 *
 * <p>Order of operations (anti-duplication has priority over anti-loss):
 * <ol>
 *   <li>Status check (only from AVAILABLE / FAILED / PARTIAL).</li>
 *   <li>Per-death lock so concurrent commands cannot double-restore.</li>
 *   <li>Owner must be online, otherwise abort before touching anything.</li>
 *   <li>Persist status RESTORING (crash marker).</li>
 *   <li>Scan for every stack with the matching death id.</li>
 *   <li>Remove those stacks from the world first.</li>
 *   <li>Deliver to the owner; overflow is dropped at the owner, never deleted.</li>
 *   <li>Persist final status (RESTORED or PARTIAL), FAILED on error.</li>
 * </ol>
 *
 * <p>Because the sources are cleared before the delivery, a mid-restore crash
 * can at worst lose the transfer (sources cleared, delivery unsaved) - it can
 * never duplicate items. A crash leaves the persisted RESTORING marker behind,
 * which is reset to FAILED on the next start so a safe retry is possible.
 * The whole restore runs synchronously on the server thread.
 */
public final class RestoreService {
	private static final Set<UUID> IN_PROGRESS = ConcurrentHashMap.newKeySet();

	private RestoreService() {
	}

	public record Result(boolean success, int stacksReturned, long itemsReturned, long itemsExpected, String message) {
	}

	public static Result restore(MinecraftServer server, DeathBackup backup) {
		if (!backup.status().canStartRestore()) {
			return new Result(false, 0, 0, 0,
					"This backup cannot be restored (current status: " + backup.status() + ").");
		}
		if (!IN_PROGRESS.add(backup.id())) {
			return new Result(false, 0, 0, 0, "A restore for this backup is already running.");
		}
		try {
			ServerPlayer owner = server.getPlayerList().getPlayer(backup.owner());
			if (owner == null) {
				return new Result(false, 0, 0, 0,
						"The owner " + backup.ownerName() + " must be online to receive the restored items.");
			}

			InvRestoreSavedData data = InvRestoreSavedData.get(server);
			backup.setStatus(BackupStatus.RESTORING);
			data.markDirty();

			InvRestore.LOGGER.info("[InvRestore] Restore started for {}: {}", backup.ownerName(), backup.id());

			boolean containers = InvRestoreConfig.get().enableContainerTracking;
			List<ItemScanner.Found> found = ItemScanner.find(server, backup.id(), containers);

			// Remove the tracked stacks from the world before handing copies
			// to the owner, so the same physical stack is never counted twice.
			List<ItemStack> gathered = new ArrayList<>();
			for (ItemScanner.Found entry : found) {
				gathered.add(entry.stack().copy());
				entry.clear().run();
			}

			long itemsFound = 0L;
			for (ItemStack stack : gathered) {
				itemsFound += stack.getCount();
			}
			debug("Found " + itemsFound + " item(s) across " + found.size() + " tracked location(s)");

			deliver(owner, gathered);
			if (!gathered.isEmpty()) {
				// Deliver anything that could not be handed over as a last resort.
				dropRemaining(owner, gathered);
			}

			long expected = backup.totalItemCount();
			backup.setRestoredAt(System.currentTimeMillis());
			backup.setStatus(itemsFound >= expected ? BackupStatus.RESTORED : BackupStatus.PARTIAL);
			data.markDirty();

			InvRestore.LOGGER.info("[InvRestore] Restore completed for {}: {} - returned {} item(s) from {} location(s)",
					backup.ownerName(), backup.id(), itemsFound, found.size());

			String message = "Restored " + itemsFound + " item(s) from " + found.size()
					+ " location(s) for " + backup.ownerName() + ".";
			if (itemsFound < expected) {
				message += " " + (expected - itemsFound)
						+ " item(s) could no longer be found (consumed or unreachable); no items were created.";
			}
			return new Result(true, found.size(), itemsFound, expected, message);
		} catch (Throwable t) {
			InvRestore.LOGGER.error("[InvRestore] Restore failed for backup " + backup.id(), t);
			backup.setStatus(BackupStatus.FAILED);
			InvRestoreSavedData.get(server).markDirty();
			return new Result(false, 0, 0, 0, "Restore failed: " + t.getMessage()
					+ " (no items were duplicated; the backup can be retried).");
		} finally {
			IN_PROGRESS.remove(backup.id());
		}
	}

	/**
	 * Adds every stack to the owner's inventory. Whatever does not fit is left
	 * in the list for the caller to drop, so a full inventory never destroys an
	 * item. The provenance marker is removed on delivery: the stacks become
	 * ordinary items again and can never be captured twice.
	 */
	private static void deliver(ServerPlayer owner, List<ItemStack> stacks) {
		Iterator<ItemStack> iterator = stacks.iterator();
		while (iterator.hasNext()) {
			ItemStack stack = iterator.next();
			Provenance.untag(stack);
			owner.getInventory().add(stack);
			if (stack.isEmpty()) {
				iterator.remove();
			}
		}
	}

	private static void dropRemaining(ServerPlayer owner, List<ItemStack> stacks) {
		for (ItemStack stack : stacks) {
			if (!stack.isEmpty()) {
				Provenance.untag(stack);
				owner.drop(stack, false);
			}
		}
		stacks.clear();
	}

	private static void debug(String message) {
		if (InvRestoreConfig.get().debugLogging) {
			InvRestore.LOGGER.info("[InvRestore] {}", message);
		}
	}
}
