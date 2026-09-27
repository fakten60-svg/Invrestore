package com.invrestore.data;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.UUIDUtil;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

/**
 * A single death snapshot: everything a player carried at the moment of death,
 * plus the metadata needed to list and restore it later.
 */
public final class DeathBackup {
	public static final Codec<DeathBackup> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			UUIDUtil.CODEC.fieldOf("id").forGetter(DeathBackup::id),
			UUIDUtil.CODEC.fieldOf("owner").forGetter(DeathBackup::owner),
			Codec.STRING.fieldOf("owner_name").forGetter(DeathBackup::ownerName),
			Codec.LONG.fieldOf("timestamp").forGetter(DeathBackup::timestamp),
			Identifier.CODEC.fieldOf("dimension").forGetter(DeathBackup::dimension),
			Codec.DOUBLE.fieldOf("x").forGetter(DeathBackup::x),
			Codec.DOUBLE.fieldOf("y").forGetter(DeathBackup::y),
			Codec.DOUBLE.fieldOf("z").forGetter(DeathBackup::z),
			BackupStatus.CODEC.fieldOf("status").forGetter(DeathBackup::status),
			Codec.LONG.optionalFieldOf("restored_at", 0L).forGetter(DeathBackup::restoredAt),
			ItemStack.CODEC.listOf().fieldOf("items").forGetter(DeathBackup::items)
	).apply(instance, DeathBackup::new));

	private final UUID id;
	private final UUID owner;
	private final String ownerName;
	private final long timestamp;
	private final Identifier dimension;
	private final double x;
	private final double y;
	private final double z;
	private BackupStatus status;
	private long restoredAt;
	private final List<ItemStack> items;

	public DeathBackup(UUID id, UUID owner, String ownerName, long timestamp, Identifier dimension,
			double x, double y, double z, BackupStatus status, long restoredAt, List<ItemStack> items) {
		this.id = id;
		this.owner = owner;
		this.ownerName = ownerName;
		this.timestamp = timestamp;
		this.dimension = dimension;
		this.x = x;
		this.y = y;
		this.z = z;
		this.status = status;
		this.restoredAt = restoredAt;
		this.items = new ArrayList<>(items);
	}

	public UUID id() {
		return id;
	}

	public UUID owner() {
		return owner;
	}

	public String ownerName() {
		return ownerName;
	}

	public long timestamp() {
		return timestamp;
	}

	public Identifier dimension() {
		return dimension;
	}

	public double x() {
		return x;
	}

	public double y() {
		return y;
	}

	public double z() {
		return z;
	}

	public BackupStatus status() {
		return status;
	}

	public void setStatus(BackupStatus status) {
		this.status = status;
	}

	public long restoredAt() {
		return restoredAt;
	}

	public void setRestoredAt(long restoredAt) {
		this.restoredAt = restoredAt;
	}

	public List<ItemStack> items() {
		return items;
	}

	/** Total number of individual items the player was carrying at death. */
	public long totalItemCount() {
		long total = 0L;
		for (ItemStack stack : items) {
			total += stack.getCount();
		}
		return total;
	}

	/** Number of non-empty stacks in the snapshot. */
	public int stackCount() {
		int count = 0;
		for (ItemStack stack : items) {
			if (!stack.isEmpty()) {
				count++;
			}
		}
		return count;
	}
}
