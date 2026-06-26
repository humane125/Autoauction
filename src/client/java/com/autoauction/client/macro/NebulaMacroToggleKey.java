package com.autoauction.client.macro;

import org.lwjgl.glfw.GLFW;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Locale;
import java.util.OptionalInt;

public final class NebulaMacroToggleKey {
	private NebulaMacroToggleKey() {
	}

	public static OptionalInt resolve(String configuredKey) {
		String keyName = normalize(configuredKey);
		if (keyName.isBlank()) {
			return OptionalInt.empty();
		}
		if (keyName.length() == 1) {
			char value = keyName.charAt(0);
			if (value >= 'A' && value <= 'Z') {
				return OptionalInt.of(GLFW.GLFW_KEY_A + (value - 'A'));
			}
			if (value >= '0' && value <= '9') {
				return OptionalInt.of(GLFW.GLFW_KEY_0 + (value - '0'));
			}
		}
		try {
			Field field = GLFW.class.getField("GLFW_KEY_" + keyName);
			if (Modifier.isStatic(field.getModifiers()) && field.getType() == int.class) {
				return OptionalInt.of(field.getInt(null));
			}
		} catch (ReflectiveOperationException ignored) {
		}
		return OptionalInt.empty();
	}

	private static String normalize(String configuredKey) {
		String value = String.valueOf(configuredKey == null ? "" : configuredKey)
			.trim()
			.toUpperCase(Locale.ROOT);
		if (value.startsWith("KEY.KEYBOARD.")) {
			value = value.substring("KEY.KEYBOARD.".length());
		} else if (value.startsWith("KEYBOARD.")) {
			value = value.substring("KEYBOARD.".length());
		}
		if (value.startsWith("GLFW_KEY_")) {
			value = value.substring("GLFW_KEY_".length());
		}
		return value.replace('-', '_').replace(' ', '_');
	}
}
