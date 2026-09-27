package com.invrestore.data;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class BackupStatusTest {
	@Test
	void availableAndFailedMayRestore() {
		assertTrue(BackupStatus.AVAILABLE.canStartRestore());
		assertTrue(BackupStatus.FAILED.canStartRestore());
		assertTrue(BackupStatus.PARTIAL.canStartRestore());
	}

	@Test
	void restoringAndRestoredMayNotRestore() {
		assertFalse(BackupStatus.RESTORING.canStartRestore());
		assertFalse(BackupStatus.RESTORED.canStartRestore());
	}

	@Test
	void codecDecodesEveryRealStatus() {
		for (BackupStatus status : BackupStatus.values()) {
			assertSame(status, BackupStatus.CODEC.decode(
					com.mojang.serialization.JsonOps.INSTANCE, 
					com.google.gson.JsonParser.parseString("\"" + status.name() + "\"")).getOrThrow().getFirst(),
				"decoding " + status.name() + " must be stable");
		}
	}

	@Test
	void codecFallsBackToFailedForCorruptEntries() {
		var corrupt = com.google.gson.JsonParser.parseString("\"TOTALYZER\"");
		assertSame(BackupStatus.FAILED, BackupStatus.CODEC.decode(
				com.mojang.serialization.JsonOps.INSTANCE, corrupt).getOrThrow().getFirst());
	}
}
