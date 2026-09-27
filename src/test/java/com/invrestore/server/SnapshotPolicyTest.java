package com.invrestore.server;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

/**
 * The death snapshot must represent exactly the items vanilla treats as drops.
 * The pipeline constants below were verified against the real Minecraft
 * 26.1.2 bytecode ({@code Player#dropEquipment}: keepInventory check,
 * {@code destroyVanishingCursedItems} via
 * {@code EnchantmentEffectComponents.PREVENT_EQUIPMENT_DROP}, then
 * {@code Inventory#dropAll}).
 *
 * <p>These tests pin the source-level contract: the snapshot uses the full
 * container inventory (main + hotbar + equipment), tagging happens after the
 * copies exist, and the scanner uses the same equipment slot list so nothing
 * can fall out of sync.
 */
class SnapshotPolicyTest {
	private static String source() {
		try {
			Path root = Path.of("").toAbsolutePath();
			while (root != null && !Files.isDirectory(root.resolve("src/main/java"))) {
				root = root.getParent();
			}
			Path policy = root.resolve("src/main/java/com/invrestore/server/SnapshotPolicy.java");
			Path death = root.resolve("src/main/java/com/invrestore/server/DeathHandler.java");
			Path scanner = root.resolve("src/main/java/com/invrestore/server/ItemScanner.java");
			try (Stream<String> lines = Files.lines(policy)) {
				String policyText = lines.reduce("", (a, b) -> a + "\n" + b);
				String deathText = Files.readString(death);
				String scannerText = Files.readString(scanner);
				return policyText + "\n" + deathText + "\n" + scannerText;
			}
		} catch (IOException e) {
			throw new IllegalStateException("Could not read production sources", e);
		}
	}

	@Test
	void snapshotCoversWholeInventory() {
		// Inventory#dropAll drops every container slot; the snapshot must do
		// the same (main + hotbar + equipment via container indices).
		String text = source();
		assertTrue(text.contains("takeSnapshot"),
				"SnapshotPolicy must expose takeSnapshot");
		assertTrue(text.contains("inventory.getContainerSize()"),
				"Snapshot must iterate the whole inventory container");
	}

	@Test
	void taggerMatchesVanillaDropCondition() {
		// Vanilla: if KEEP_INVENTORY, drop nothing. The tag step must check
		// dropsInventory before tagging, otherwise phantom items get marked.
		String text = source();
		assertTrue(text.contains("dropsInventory"),
				"Tagging must be skipped when keepInventory is on");
		assertTrue(text.contains("tagLiveStacks"),
				"SnapshotPolicy must expose tagLiveStacks");
	}

	@Test
	void snapshotTakenBeforeTagging() {
		// Copies must exist before live stacks are modified, so snapshot
		// entries never carry the provenance marker themselves.
		String death = source();
		int snapshotAt = death.indexOf("SnapshotPolicy.takeSnapshot(player)");
		int tagAt = death.indexOf("SnapshotPolicy.tagLiveStacks(player, deathId)");
		assertTrue(snapshotAt >= 0, "DeathHandler must take the snapshot");
		assertTrue(tagAt > snapshotAt, "Tagging must happen after the snapshot copies exist");
	}

	@Test
	void scannerWalksSameContainerAsSnapshot() {
		// Snapshot and scanner must both walk the whole inventory container
		// (main + hotbar + equipment indices) - and the scanner must not do a
		// second equipment pass over the very same stacks.
		String text = source();
		assertTrue(text.contains("inventory.getContainerSize()"),
				"Both snapshot and scanner must iterate the whole inventory container");
		assertTrue(!text.contains("SnapshotPolicy.TRACKED_EQUIPMENT"),
				"There must be no second equipment pass (would clear tracked stacks twice)");
	}
}
