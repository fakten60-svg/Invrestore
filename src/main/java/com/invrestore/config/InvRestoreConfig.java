package com.invrestore.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.invrestore.InvRestore;

import net.fabricmc.loader.api.FabricLoader;

/**
 * Server configuration for Inventory Restore. Loaded from
 * {@code config/invrestore.json} and re-written on load so the file always
 * contains every documented option.
 */
public final class InvRestoreConfig {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
	private static InvRestoreConfig instance = new InvRestoreConfig();

	/** Maximum number of death backups stored per player. */
	public int maxBackupsPerPlayer = 20;
	/** Allow OPs (permission level 2+) to restore other players' backups. */
	public boolean enableAdminRestore = true;
	/** Include (loaded) container block entities when searching for tracked items. */
	public boolean enableContainerTracking = true;
	/** Emit verbose diagnostics to the server log. */
	public boolean debugLogging = false;

	public static InvRestoreConfig get() {
		return instance;
	}

	public static void load() {
		Path path = FabricLoader.getInstance().getConfigDir().resolve("invrestore.json");
		InvRestoreConfig loaded = null;
		if (Files.exists(path)) {
			try {
				loaded = GSON.fromJson(Files.readString(path), InvRestoreConfig.class);
			} catch (Exception e) {
				InvRestore.LOGGER.error("[InvRestore] Could not read config, using defaults", e);
			}
		}
		if (loaded == null) {
			loaded = new InvRestoreConfig();
		}
		loaded.clamp();
		instance = loaded;
		instance.save(path);
	}

	private void clamp() {
		if (maxBackupsPerPlayer < 1) {
			maxBackupsPerPlayer = 1;
		}
		if (maxBackupsPerPlayer > 1000) {
			maxBackupsPerPlayer = 1000;
		}
	}

	private void save(Path path) {
		try {
			if (path.getParent() != null) {
				Files.createDirectories(path.getParent());
			}
			Files.writeString(path, GSON.toJson(this));
		} catch (IOException e) {
			InvRestore.LOGGER.error("[InvRestore] Could not write config", e);
		}
	}
}
