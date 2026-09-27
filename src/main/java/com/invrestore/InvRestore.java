package com.invrestore;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.invrestore.command.InvRestoreCommand;
import com.invrestore.config.InvRestoreConfig;
import com.invrestore.data.InvRestoreSavedData;
import com.invrestore.server.DeathHandler;
import com.invrestore.server.TrackedContainers;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.server.MinecraftServer;

/** Inventory Restore: server-side death backup and provenance-aware restore. */
public final class InvRestore implements ModInitializer {
	public static final String MOD_ID = "invrestore";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	private static volatile MinecraftServer server;

	@Override
	public void onInitialize() {
		InvRestoreConfig.load();

		TrackedContainers.register();

		CommandRegistrationCallback.EVENT.register(
				(dispatcher, registryAccess, environment) -> InvRestoreCommand.register(dispatcher));

		ServerLifecycleEvents.SERVER_STARTED.register(startedServer -> {
			server = startedServer;
			int recovered = InvRestoreSavedData.get(startedServer).recoverInterruptedRestores();
			if (recovered > 0) {
				LOGGER.warn("[InvRestore] Marked {} interrupted restore(s) as FAILED after restart", recovered);
			}
			LOGGER.info("[InvRestore] Inventory Restore ready ({} backups loaded).",
					InvRestoreSavedData.get(startedServer).totalBackups());
		});

		ServerLifecycleEvents.SERVER_STOPPING.register(stoppingServer -> {
			TrackedContainers.clear();
			server = null;
		});

		// Clear the "death already handled" guard once the player is back.
		ServerPlayerEvents.AFTER_RESPAWN.register(
				(oldPlayer, newPlayer, alive) -> DeathHandler.onRespawn(oldPlayer.getUUID()));
		ServerPlayerEvents.JOIN.register(player -> DeathHandler.onRespawn(player.getUUID()));

		LOGGER.info("[InvRestore] Inventory Restore initialized");
	}

	public static MinecraftServer server() {
		return server;
	}
}
