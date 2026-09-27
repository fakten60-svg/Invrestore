package com.invrestore.server;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

/**
 * Keeps track of which chunks are currently loaded, per dimension, using the
 * chunk load/unload events. Container scanning only ever visits these chunks,
 * so the mod never walks the whole world and never keeps a permanent scanner
 * running.
 */
public final class TrackedContainers {
	private static final Map<ResourceKey<Level>, Set<Long>> LOADED = new ConcurrentHashMap<>();

	private TrackedContainers() {
	}

	public static void register() {
		ServerChunkEvents.CHUNK_LOAD.register((level, chunk, newlyLoaded) ->
				LOADED.computeIfAbsent(level.dimension(), key -> ConcurrentHashMap.newKeySet())
						.add(chunk.getPos().pack()));
		ServerChunkEvents.CHUNK_UNLOAD.register((level, chunk) -> {
			Set<Long> chunks = LOADED.get(level.dimension());
			if (chunks != null) {
				chunks.remove(chunk.getPos().pack());
			}
		});
	}

	public static Set<Long> loadedChunks(ServerLevel level) {
		Set<Long> chunks = LOADED.get(level.dimension());
		return chunks == null ? Set.of() : chunks;
	}

	public static void clear() {
		LOADED.clear();
	}
}
