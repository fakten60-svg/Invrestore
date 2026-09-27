package com.invrestore.data;

import com.mojang.serialization.Codec;

/** Lifecycle status of a single death backup. */
public enum BackupStatus {
	/** Freshly created, never restored. */
	AVAILABLE,
	/** A restore is currently in progress (transient, persisted for crash recovery). */
	RESTORING,
	/** Successfully restored. Never restored again. */
	RESTORED,
	/** A restore failed or the server crashed mid-restore; retry is allowed. */
	FAILED,
	/** Only part of the original items could be located and returned. */
	PARTIAL;

	public static final Codec<BackupStatus> CODEC = Codec.STRING.xmap(BackupStatus::valueOf, BackupStatus::name);

	/**
	 * A restore may be started from these states. RESTORING and RESTORED are
	 * blocked, so a backup can never be fully restored twice. FAILED and PARTIAL
	 * allow a safe retry: a retry only ever moves items that still physically
	 * exist, so it can never duplicate anything.
	 */
	public boolean canStartRestore() {
		return this == AVAILABLE || this == FAILED || this == PARTIAL;
	}
}
