package com.invrestore.data;

import static org.junit.jupiter.api.Assertions.assertFalse;
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
}
