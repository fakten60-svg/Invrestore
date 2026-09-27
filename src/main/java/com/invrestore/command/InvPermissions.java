package com.invrestore.command;

import com.invrestore.config.InvRestoreConfig;

import net.fabricmc.fabric.api.permission.v1.PermissionNode;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.permissions.Permissions;

/**
 * Permission nodes used by the mod. They integrate with Fabric's permission API
 * so permission mods (LuckPerms, etc.) can grant or revoke them per player.
 * When no permission provider answers, the default value applies:
 *
 * <ul>
 *   <li>{@code invrestore.self} - default: allowed for everyone.</li>
 *   <li>{@code invrestore.admin} - default: permission level 2 (gamemasters).</li>
 *   <li>{@code invrestore.admin.others} - default: permission level 2.</li>
 * </ul>
 */
public final class InvPermissions {
	public static final PermissionNode<Boolean> SELF = PermissionNode.of("invrestore", "self");
	public static final PermissionNode<Boolean> ADMIN = PermissionNode.of("invrestore", "admin");
	public static final PermissionNode<Boolean> ADMIN_OTHERS = PermissionNode.of("invrestore", "admin.others");

	private InvPermissions() {
	}

	/** Whether the source may see and restore its own backups. */
	public static boolean canUseSelf(CommandSourceStack source) {
		return source.checkPermission(SELF, true);
	}

	/** Whether the source may list other players' backups. */
	public static boolean canAdmin(CommandSourceStack source) {
		if (!InvRestoreConfig.get().enableAdminRestore) {
			return false;
		}
		return source.checkPermission(ADMIN, isOperator(source));
	}

	/** Whether the source may restore another player's backup. */
	public static boolean canRestoreOthers(CommandSourceStack source) {
		if (!InvRestoreConfig.get().enableAdminRestore) {
			return false;
		}
		return source.checkPermission(ADMIN_OTHERS, isOperator(source));
	}

	/** Default fallback: permission level 2 or higher. */
	private static boolean isOperator(CommandSourceStack source) {
		return source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER);
	}
}
