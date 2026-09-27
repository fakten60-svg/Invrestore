package com.invrestore.data;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.mojang.serialization.Codec;

import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/**
 * Server-wide persistent store for every death backup. It lives in the
 * server's {@code data/} folder and is managed by the vanilla saved-data
 * system, so it survives server restarts automatically.
 */
public final class InvRestoreSavedData extends SavedData {
	public static final Codec<InvRestoreSavedData> CODEC = DeathBackup.CODEC.listOf()
			.xmap(InvRestoreSavedData::new, InvRestoreSavedData::toList);

	public static final SavedDataType<InvRestoreSavedData> TYPE = new SavedDataType<>(
			Identifier.fromNamespaceAndPath("invrestore", "backups"),
			InvRestoreSavedData::new,
			CODEC,
			DataFixTypes.SAVED_DATA_RANDOM_SEQUENCES);

	private final List<DeathBackup> backups = new ArrayList<>();

	public InvRestoreSavedData() {
	}

	private InvRestoreSavedData(List<DeathBackup> backups) {
		this.backups.addAll(backups);
	}

	private List<DeathBackup> toList() {
		return new ArrayList<>(backups);
	}

	public static InvRestoreSavedData get(MinecraftServer server) {
		return server.getDataStorage().computeIfAbsent(TYPE);
	}

	public void addBackup(DeathBackup backup, int maxPerPlayer) {
		backups.add(backup);
		prune(backup.owner(), maxPerPlayer);
		setDirty();
	}

	public void markDirty() {
		setDirty();
	}

	/** Returns every backup of an owner, newest first. */
	public List<DeathBackup> backupsOf(UUID owner) {
		List<DeathBackup> result = new ArrayList<>();
		for (DeathBackup backup : backups) {
			if (backup.owner().equals(owner)) {
				result.add(backup);
			}
		}
		result.sort(Comparator.comparingLong(DeathBackup::timestamp).reversed());
		return result;
	}

	public Optional<DeathBackup> find(UUID owner, UUID id) {
		for (DeathBackup backup : backups) {
			if (backup.owner().equals(owner) && backup.id().equals(id)) {
				return Optional.of(backup);
			}
		}
		return Optional.empty();
	}

	/**
	 * Enforces the per-player backup limit. RESTORED backups are removed first,
	 * then FAILED ones. AVAILABLE backups are only removed as a last resort and
	 * never silently drop unrecoverable items below the limit if restored/failed
	 * entries were available to prune.
	 */
	public void prune(UUID owner, int maxPerPlayer) {
		List<DeathBackup> owned = backupsOf(owner);
		int toRemove = owned.size() - maxPerPlayer;
		if (toRemove <= 0) {
			return;
		}
		// Oldest first.
		List<DeathBackup> candidates = new ArrayList<>(owned);
		candidates.sort(Comparator.comparingLong(DeathBackup::timestamp));
		for (DeathBackup backup : candidates) {
			if (toRemove <= 0) {
				break;
			}
			if (backup.status() == BackupStatus.RESTORED || backup.status() == BackupStatus.FAILED) {
				backups.remove(backup);
				toRemove--;
			}
		}
		// If still over the limit we keep AVAILABLE backups (they still hold
		// recoverable items) rather than delete them.
		setDirty();
	}

	/**
	 * A restore that was interrupted by a crash leaves the backup in RESTORING.
	 * Reset those to FAILED so they can safely be retried.
	 */
	public int recoverInterruptedRestores() {
		int recovered = 0;
		for (DeathBackup backup : backups) {
			if (backup.status() == BackupStatus.RESTORING) {
				backup.setStatus(BackupStatus.FAILED);
				recovered++;
			}
		}
		if (recovered > 0) {
			setDirty();
		}
		return recovered;
	}

	public List<DeathBackup> allBackups() {
		return new ArrayList<>(backups);
	}

	public int totalBackups() {
		return backups.size();
	}
}
