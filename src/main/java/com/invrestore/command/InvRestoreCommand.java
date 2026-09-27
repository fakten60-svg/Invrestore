package com.invrestore.command;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.invrestore.data.BackupStatus;
import com.invrestore.data.DeathBackup;
import com.invrestore.data.InvRestoreSavedData;
import com.invrestore.server.RestoreService;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * The {@code /invre} command tree.
 *
 * <pre>
 * /invre                       - list your own backups
 * /invre latest                - restore your latest restorable backup
 * /invre &lt;index|uuid&gt;          - restore one of your backups
 * /invre &lt;player&gt;              - list another player's backups (admin)
 * /invre &lt;player&gt; latest       - restore another player's latest (admin)
 * /invre &lt;player&gt; &lt;index|uuid&gt; - restore one of another player's (admin)
 * </pre>
 */
public final class InvRestoreCommand {
	private static final String ARG_FIRST = "selector";
	private static final String ARG_SECOND = "target";
	private static final DateTimeFormatter TIME_FORMAT =
			DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm").withZone(ZoneId.systemDefault());

	private InvRestoreCommand() {
	}

	public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(Commands.literal("invre")
				.executes(context -> listBackups(context.getSource(), context.getSource().getPlayer(), false))
				.then(Commands.argument(ARG_FIRST, StringArgumentType.word())
						.suggests(InvRestoreCommand::suggestFirst)
						.executes(context -> handleFirst(context.getSource(),
								StringArgumentType.getString(context, ARG_FIRST)))
						.then(Commands.argument(ARG_SECOND, StringArgumentType.word())
								.suggests(InvRestoreCommand::suggestSecond)
								.executes(context -> handleSecond(context.getSource(),
										StringArgumentType.getString(context, ARG_FIRST),
										StringArgumentType.getString(context, ARG_SECOND))))));
	}

	// ------------------------------------------------------------------
	// Execution
	// ------------------------------------------------------------------

	private static int handleFirst(CommandSourceStack source, String argument) {
		MinecraftServer server = source.getServer();
		InvRestoreSavedData data = InvRestoreSavedData.get(server);

		ServerPlayer self = source.getPlayer();
		if (Selector.isSelfSelector(argument)) {
			if (self == null) {
				source.sendFailure(Component.literal("Only players have their own backups; use /invre <player> as an operator."));
				return 0;
			}
			if (!InvPermissions.canUseSelf(source)) {
				source.sendFailure(Component.literal("You are not allowed to use your own backups."));
				return 0;
			}
			return restoreFor(source, data, self.getUUID(), self.getScoreboardName(), argument);
		}

		// Otherwise the argument is treated as a player name.
		if (!InvPermissions.canAdmin(source)) {
			source.sendFailure(Component.literal("You may only use your own backups. Operators can target other players."));
			return 0;
		}
		Optional<UUID> owner = resolvePlayer(server, argument);
		if (owner.isEmpty()) {
			source.sendFailure(Component.literal("Unknown player or backup: " + argument));
			return 0;
		}
		ServerPlayer onlineOwner = server.getPlayerList().getPlayer(owner.get());
		return listBackups(source, onlineOwner, true);
	}

	private static int handleSecond(CommandSourceStack source, String playerName, String selector) {
		MinecraftServer server = source.getServer();
		if (!InvPermissions.canRestoreOthers(source)) {
			source.sendFailure(Component.literal("You are not allowed to restore another player's backups."));
			return 0;
		}
		Optional<UUID> owner = resolvePlayer(server, playerName);
		if (owner.isEmpty()) {
			source.sendFailure(Component.literal("Unknown player: " + playerName));
			return 0;
		}
		String ownerName = InvRestoreSavedData.get(server).backupsOf(owner.get()).stream()
				.findFirst().map(DeathBackup::ownerName).orElse(playerName);
		return restoreFor(source, InvRestoreSavedData.get(server), owner.get(), ownerName, selector);
	}

	/** Resolves the selector and runs the restore, reporting the outcome. */
	private static int restoreFor(CommandSourceStack source, InvRestoreSavedData data, UUID ownerId,
			String ownerName, String selector) {
		List<DeathBackup> backups = data.backupsOf(ownerId);
		if (backups.isEmpty()) {
			source.sendFailure(Component.literal("No death backups found for " + ownerName + "."));
			return 0;
		}
		DeathBackup backup = Selector.resolve(backups, selector);
		if (backup == null) {
			if (selector.equalsIgnoreCase("latest")) {
				source.sendFailure(Component.literal("All of " + ownerName + "'s backups have already been restored."));
			} else {
				source.sendFailure(Component.literal("No matching backup '" + selector + "' for " + ownerName + "."));
			}
			return 0;
		}
		RestoreService.Result result = RestoreService.restore(source.getServer(), backup);
		if (result.success()) {
			source.sendSuccess(() -> Component.literal(result.message()).withStyle(ChatFormatting.GREEN), true);
			return 1;
		}
		source.sendFailure(Component.literal(result.message()));
		return 0;
	}

	// ------------------------------------------------------------------
	// Listing / chat UI
	// ------------------------------------------------------------------

	private static int listBackups(CommandSourceStack source, ServerPlayer owner, boolean adminView) {
		MinecraftServer server = source.getServer();
		if (owner == null) {
			if (!InvPermissions.canAdmin(source)) {
				source.sendFailure(Component.literal("You must be a player to list your own backups."));
				return 0;
			}
			source.sendFailure(Component.literal("Specify a player: /invre <player>"));
			return 0;
		}
		if (!adminView && !InvPermissions.canUseSelf(source)) {
			source.sendFailure(Component.literal("You are not allowed to view your backups."));
			return 0;
		}
		InvRestoreSavedData data = InvRestoreSavedData.get(server);
		List<DeathBackup> backups = data.backupsOf(owner.getUUID());
		String ownerName = owner.getScoreboardName();
		boolean selfView = source.getPlayer() != null && source.getPlayer().getUUID().equals(owner.getUUID());

		source.sendSuccess(() -> Component.literal("Death backups for " + ownerName + ":")
				.withStyle(ChatFormatting.GOLD), false);
		if (backups.isEmpty()) {
			source.sendSuccess(() -> Component.literal("  (none)").withStyle(ChatFormatting.GRAY), false);
			return 0;
		}
		int index = 1;
		for (DeathBackup backup : backups) {
			MutableComponent line = Component.literal("  Death #" + index + " ")
					.append(Component.literal("[" + backup.status() + "]").withStyle(color(backup.status())))
					.append(Component.literal(" " + TIME_FORMAT.format(Instant.ofEpochMilli(backup.timestamp()))))
					.append(Component.literal(" " + backup.dimension() + " "
							+ (int) backup.x() + " " + (int) backup.y() + " " + (int) backup.z()))
					.append(Component.literal(" items=" + backup.totalItemCount()));

			if (backup.status().canStartRestore()) {
				String restoreCommand = buildRestoreCommand(ownerName, selfView, index);
				MutableComponent button = Component.literal(" [RESTORE]")
						.withStyle(style -> style.withColor(ChatFormatting.AQUA)
								.withClickEvent(new ClickEvent.RunCommand(restoreCommand)));
				line.append(button);
			}
			source.sendSuccess(() -> line, false);
			index++;
		}
		return backups.size();
	}

	/** Clickable command for the [RESTORE] button - always {@code /invre}. */
	private static String buildRestoreCommand(String ownerName, boolean self, int index) {
		if (self) {
			return "/invre " + index;
		}
		return "/invre " + ownerName + " " + index;
	}

	private static ChatFormatting color(BackupStatus status) {
		return switch (status) {
			case AVAILABLE -> ChatFormatting.GREEN;
			case RESTORING -> ChatFormatting.YELLOW;
			case RESTORED -> ChatFormatting.GRAY;
			case FAILED -> ChatFormatting.RED;
			case PARTIAL -> ChatFormatting.GOLD;
		};
	}

	/**
	 * Resolves a player name to its UUID. Online players are checked first,
	 * then the stored owner names of existing backups (case-insensitive).
	 */
	private static Optional<UUID> resolvePlayer(MinecraftServer server, String name) {
		ServerPlayer online = server.getPlayerList().getPlayerByName(name);
		if (online != null) {
			return Optional.of(online.getUUID());
		}
		for (DeathBackup backup : InvRestoreSavedData.get(server).allBackups()) {
			if (backup.ownerName().equalsIgnoreCase(name)) {
				return Optional.of(backup.owner());
			}
		}
		return Optional.empty();
	}

	// ------------------------------------------------------------------
	// Tab completion
	// ------------------------------------------------------------------

	private static CompletableFuture<Suggestions> suggestFirst(CommandContext<CommandSourceStack> context,
			SuggestionsBuilder builder) {
		CommandSourceStack source = context.getSource();
		MinecraftServer server = source.getServer();
		InvRestoreSavedData data = InvRestoreSavedData.get(server);
		List<String> options = new ArrayList<>();
		options.add("latest");

		ServerPlayer self = source.getPlayer();
		if (self != null) {
			List<DeathBackup> own = data.backupsOf(self.getUUID());
			for (int i = 1; i <= own.size(); i++) {
				options.add(Integer.toString(i));
			}
		}
		if (InvPermissions.canAdmin(source)) {
			SortedSet<String> names = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				names.add(player.getScoreboardName());
			}
			for (DeathBackup backup : data.allBackups()) {
				names.add(backup.ownerName());
			}
			options.addAll(names);
		}
		return SharedSuggestionProvider.suggest(options, builder);
	}

	private static CompletableFuture<Suggestions> suggestSecond(CommandContext<CommandSourceStack> context,
			SuggestionsBuilder builder) {
		CommandSourceStack source = context.getSource();
		if (!InvPermissions.canRestoreOthers(source)) {
			return Suggestions.empty();
		}
		List<String> options = new ArrayList<>();
		options.add("latest");
		String playerName = StringArgumentType.getString(context, ARG_FIRST);
		Optional<UUID> owner = resolvePlayer(source.getServer(), playerName);
		if (owner.isPresent()) {
			List<DeathBackup> backups = InvRestoreSavedData.get(source.getServer()).backupsOf(owner.get());
			for (int i = 1; i <= backups.size(); i++) {
				options.add(Integer.toString(i));
			}
		}
		return SharedSuggestionProvider.suggest(options, builder);
	}
}
