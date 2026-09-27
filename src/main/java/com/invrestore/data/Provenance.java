package com.invrestore.data;

import java.util.Optional;
import java.util.UUID;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

/**
 * Provenance tagging of item stacks.
 *
 * <p>Every stack that is part of a death's drop is marked with the owning
 * death id inside the vanilla {@code minecraft:custom_data} component. The
 * vanilla component is used on purpose: a server-only mod cannot register a
 * brand new data component, because vanilla clients would not know it and the
 * inventory sync packet would fail to decode.
 *
 * <p>Because the marker is part of the stack's components, two stacks only
 * stack together when they share the exact same provenance. That keeps tracked
 * stacks distinguishable from ordinary items and from other deaths' items,
 * which is what guarantees that a restore can never touch unrelated items.
 *
 * <p>NBT layout (root is the {@code minecraft:custom_data} compound):
 *
 * <pre>
 * {
 *   "invrestore": { "v": 1, "death": "&lt;uuid&gt;" }
 * }
 * </pre>
 */
public final class Provenance {
	public static final String ROOT_KEY = "invrestore";
	private static final String VERSION_KEY = "v";
	private static final String DEATH_KEY = "death";
	private static final int VERSION = 1;

	private Provenance() {
	}

	public static Optional<UUID> deathId(ItemStack stack) {
		if (stack == null || stack.isEmpty()) {
			return Optional.empty();
		}
		CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
		if (customData == null) {
			return Optional.empty();
		}
		CompoundTag data = customData.copyTag().getCompoundOrEmpty(ROOT_KEY);
		if (data.isEmpty()) {
			return Optional.empty();
		}
		String raw = data.getStringOr(DEATH_KEY, "");
		if (raw.isEmpty()) {
			return Optional.empty();
		}
		try {
			return Optional.of(UUID.fromString(raw));
		} catch (IllegalArgumentException e) {
			return Optional.empty();
		}
	}

	public static boolean isFor(ItemStack stack, UUID deathId) {
		return deathId(stack).map(deathId::equals).orElse(false);
	}

	public static boolean isTracked(ItemStack stack) {
		return deathId(stack).isPresent();
	}

	public static void tag(ItemStack stack, UUID deathId) {
		if (stack == null || stack.isEmpty()) {
			return;
		}
		CustomData.update(DataComponents.CUSTOM_DATA, stack, root -> {
			CompoundTag data = new CompoundTag();
			data.putInt(VERSION_KEY, VERSION);
			data.putString(DEATH_KEY, deathId.toString());
			root.put(ROOT_KEY, data);
		});
	}

	public static void untag(ItemStack stack) {
		if (stack == null || stack.isEmpty()) {
			return;
		}
		CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
		if (customData == null) {
			return;
		}
		CustomData.update(DataComponents.CUSTOM_DATA, stack, root -> root.remove(ROOT_KEY));
		CustomData after = stack.get(DataComponents.CUSTOM_DATA);
		if (after != null && after.isEmpty()) {
			stack.remove(DataComponents.CUSTOM_DATA);
		}
	}
}
