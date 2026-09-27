package com.invrestore.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.invrestore.data.BackupStatus;
import com.invrestore.data.DeathBackup;

import net.minecraft.resources.Identifier;

class SelectorTest {
	private static final Identifier DIM = Identifier.fromNamespaceAndPath("minecraft", "overworld");

	private static DeathBackup backup(long time, BackupStatus status) {
		return new DeathBackup(UUID.randomUUID(), UUID.randomUUID(), "Steve", time, DIM, 0, 0, 0, status, 0L,
				List.of());
	}

	@Test
	void indexDetection() {
		assertTrue(Selector.isIndex("1"));
		assertTrue(Selector.isIndex("42"));
		assertFalse(Selector.isIndex(""));
		assertFalse(Selector.isIndex("latest"));
		assertFalse(Selector.isIndex("-1"));
		assertFalse(Selector.isIndex("1234567890"));
	}

	@Test
	void uuidDetection() {
		assertTrue(Selector.isUuidLike(UUID.randomUUID().toString()));
		assertTrue(Selector.isUuidLike("abcdef"));
		assertFalse(Selector.isUuidLike("Steve"));
		assertFalse(Selector.isUuidLike("abc"));
	}

	@Test
	void selfSelectorDetection() {
		assertTrue(Selector.isSelfSelector("latest"));
		assertTrue(Selector.isSelfSelector("2"));
		assertTrue(Selector.isSelfSelector("abcdef"));
		assertFalse(Selector.isSelfSelector("Steve"));
	}

	@Test
	void latestPicksNewestRestorable() {
		// newest first: RESTORED then AVAILABLE
		DeathBackup newest = backup(2000L, BackupStatus.RESTORED);
		DeathBackup older = backup(1000L, BackupStatus.AVAILABLE);
		List<DeathBackup> backups = new ArrayList<>(List.of(newest, older));

		assertSame(older, Selector.resolve(backups, "latest"));
	}

	@Test
	void latestReturnsNullWhenAllRestored() {
		List<DeathBackup> backups = new ArrayList<>(List.of(backup(2000L, BackupStatus.RESTORED)));
		assertNull(Selector.resolve(backups, "latest"));
	}

	@Test
	void indexResolvesPositionally() {
		DeathBackup first = backup(2000L, BackupStatus.RESTORED);
		DeathBackup second = backup(1000L, BackupStatus.AVAILABLE);
		List<DeathBackup> backups = new ArrayList<>(List.of(first, second));

		assertSame(first, Selector.resolve(backups, "1"));
		assertSame(second, Selector.resolve(backups, "2"));
		assertNull(Selector.resolve(backups, "3"));
		assertNull(Selector.resolve(backups, "0"));
	}

	@Test
	void uuidPrefixResolves() {
		DeathBackup only = backup(1000L, BackupStatus.AVAILABLE);
		List<DeathBackup> backups = new ArrayList<>(List.of(only));
		String prefix = only.id().toString().substring(0, 8);

		assertSame(only, Selector.resolve(backups, prefix));
		assertNull(Selector.resolve(backups, UUID.randomUUID().toString()));
	}

	@Test
	void emptyListResolvesToNull() {
		assertNull(Selector.resolve(new ArrayList<>(), "latest"));
	}

	@Test
	void resolveIsCaseInsensitiveForLatest() {
		DeathBackup only = backup(1000L, BackupStatus.AVAILABLE);
		List<DeathBackup> backups = new ArrayList<>(List.of(only));
		assertEquals(only.id(), Selector.resolve(backups, "LATEST").id());
	}
}
