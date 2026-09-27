package com.invrestore.command;

import java.util.List;

import com.invrestore.data.DeathBackup;

/**
 * Pure parsing and resolution of the {@code /infor} selector argument. Kept
 * free of any server/world state so it can be unit tested directly.
 */
public final class Selector {
	private Selector() {
	}

	/** True for "latest", a 1-based index, or a UUID (prefix). */
	public static boolean isSelfSelector(String argument) {
		return argument.equalsIgnoreCase("latest") || isIndex(argument) || isUuidLike(argument);
	}

	public static boolean isIndex(String argument) {
		if (argument.isEmpty() || argument.length() > 9) {
			return false;
		}
		for (int i = 0; i < argument.length(); i++) {
			if (!Character.isDigit(argument.charAt(i))) {
				return false;
			}
		}
		return true;
	}

	public static boolean isUuidLike(String argument) {
		if (argument.length() < 6 || argument.length() > 36) {
			return false;
		}
		for (int i = 0; i < argument.length(); i++) {
			char c = argument.charAt(i);
			boolean hex = (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F') || c == '-';
			if (!hex) {
				return false;
			}
		}
		return true;
	}

	/**
	 * Resolves a selector against a newest-first list of backups.
	 *
	 * @return the matching backup, or {@code null} when nothing matches
	 */
	public static DeathBackup resolve(List<DeathBackup> backups, String selector) {
		if (backups.isEmpty()) {
			return null;
		}
		if (selector.equalsIgnoreCase("latest")) {
			for (DeathBackup backup : backups) {
				if (backup.status().canStartRestore()) {
					return backup;
				}
			}
			return null;
		}
		if (isIndex(selector)) {
			int index = Integer.parseInt(selector);
			if (index >= 1 && index <= backups.size()) {
				return backups.get(index - 1);
			}
			return null;
		}
		String prefix = selector.toLowerCase();
		for (DeathBackup backup : backups) {
			if (backup.id().toString().startsWith(prefix)) {
				return backup;
			}
		}
		return null;
	}
}
