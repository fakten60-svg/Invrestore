package com.invrestore.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import net.minecraft.resources.Identifier;

class InvRestoreSavedDataTest {
	private static final Identifier DIM = Identifier.fromNamespaceAndPath("minecraft", "overworld");

	private static DeathBackup backup(UUID owner, long time, BackupStatus status) {
		return new DeathBackup(UUID.randomUUID(), owner, "Steve", time, DIM, 0, 0, 0, status, 0L, List.of());
	}

	@Test
	void backupsAreOrderedNewestFirst() {
		UUID owner = UUID.randomUUID();
		InvRestoreSavedData data = new InvRestoreSavedData();
		DeathBackup middle = backup(owner, 2000L, BackupStatus.AVAILABLE);
		DeathBackup oldest = backup(owner, 1000L, BackupStatus.AVAILABLE);
		DeathBackup newest = backup(owner, 3000L, BackupStatus.AVAILABLE);
		data.addBackup(middle, 100);
		data.addBackup(oldest, 100);
		data.addBackup(newest, 100);

		List<DeathBackup> result = data.backupsOf(owner);
		assertEquals(3, result.size());
		assertSame(newest, result.get(0));
		assertSame(middle, result.get(1));
		assertSame(oldest, result.get(2));
	}

	@Test
	void findIsScopedToOwner() {
		UUID owner = UUID.randomUUID();
		InvRestoreSavedData data = new InvRestoreSavedData();
		DeathBackup backup = backup(owner, 1000L, BackupStatus.AVAILABLE);
		data.addBackup(backup, 100);

		assertSame(backup, data.find(owner, backup.id()).orElseThrow());
		assertTrue(data.find(UUID.randomUUID(), backup.id()).isEmpty());
	}

	@Test
	void pruneRemovesRestoredBeforeAvailable() {
		UUID owner = UUID.randomUUID();
		InvRestoreSavedData data = new InvRestoreSavedData();
		DeathBackup restored = backup(owner, 1000L, BackupStatus.RESTORED);
		DeathBackup availableOld = backup(owner, 2000L, BackupStatus.AVAILABLE);
		DeathBackup availableNew = backup(owner, 3000L, BackupStatus.AVAILABLE);
		data.addBackup(restored, 100);
		data.addBackup(availableOld, 100);
		data.addBackup(availableNew, 2); // over the limit -> prunes restored first

		List<DeathBackup> result = data.backupsOf(owner);
		assertEquals(2, result.size());
		assertFalse(result.contains(restored));
		assertTrue(result.contains(availableOld));
		assertTrue(result.contains(availableNew));
	}

	@Test
	void pruneKeepsAvailableWhenNothingPrunable() {
		UUID owner = UUID.randomUUID();
		InvRestoreSavedData data = new InvRestoreSavedData();
		data.addBackup(backup(owner, 1000L, BackupStatus.AVAILABLE), 100);
		data.addBackup(backup(owner, 2000L, BackupStatus.AVAILABLE), 1);

		// AVAILABLE backups hold recoverable items, so they are not deleted.
		assertEquals(2, data.backupsOf(owner).size());
	}

	@Test
	void interruptedRestoresBecomeFailed() {
		UUID owner = UUID.randomUUID();
		InvRestoreSavedData data = new InvRestoreSavedData();
		DeathBackup restoring = backup(owner, 1000L, BackupStatus.RESTORING);
		data.addBackup(restoring, 100);

		int recovered = data.recoverInterruptedRestores();

		assertEquals(1, recovered);
		assertSame(BackupStatus.FAILED, restoring.status());
		assertTrue(restoring.status().canStartRestore());
	}
}
