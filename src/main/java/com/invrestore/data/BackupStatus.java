package com.invrestore.data;

import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;

/**
 * Lifecycle status of a single death backup.
 */
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

	/**
	 * Lenient codec: unknown or corrupted status strings decode to {@link
	 * #FAILED} instead of failing the whole saved-data file. A corrupt entry
	 * then simply shows up as retryable, which is always the safe default -
	 * it can never mark an unrestored backup as RESTORED.
	 */
	public static final Codec<BackupStatus> CODEC = new LenientCodec();

	/**
	 * A restore may be started from these states. RESTORING and RESTORED are
	 * blocked, so a backup can never be fully restored twice. FAILED and PARTIAL
	 * allow a safe retry: a retry only ever moves items that still physically
	 * exist, so it can never duplicate anything.
	 */
	public boolean canStartRestore() {
		return this == AVAILABLE || this == FAILED || this == PARTIAL;
	}

	private static final class LenientCodec implements Codec<BackupStatus> {
		@Override
		public <T> DataResult<Pair<BackupStatus, T>> decode(DynamicOps<T> ops, T input) {
			DataResult<String> raw = ops.getStringValue(input);
			if (raw.error().isPresent()) {
				return DataResult.error(() -> "not a string: " + raw.error().get().message());
			}
			String name = raw.result().orElse("");
			BackupStatus status;
			try {
				status = BackupStatus.valueOf(name);
			} catch (IllegalArgumentException e) {
				status = BackupStatus.FAILED;
			}
			return DataResult.success(Pair.of(status, input));
		}

		@Override
		public <T> DataResult<T> encode(BackupStatus input, DynamicOps<T> ops, T prefix) {
			return DataResult.success(ops.createString(input.name()));
		}
	}
}
