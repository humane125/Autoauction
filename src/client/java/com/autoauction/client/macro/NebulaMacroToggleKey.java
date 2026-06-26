package com.autoauction.client.macro;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.lwjgl.glfw.GLFW;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
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

	public static OptionalInt resolveFromGameDirectory(Path gameDirectory) throws IOException {
		Path configPath = configPath(gameDirectory);
		if (!Files.exists(configPath)) {
			return OptionalInt.empty();
		}
		JsonObject root = JsonParser.parseString(Files.readString(configPath, StandardCharsets.UTF_8)).getAsJsonObject();
		if (!root.has("Combat Macro") || !root.get("Combat Macro").isJsonObject()) {
			return OptionalInt.empty();
		}
		JsonObject combatMacro = root.getAsJsonObject("Combat Macro");
		if (!combatMacro.has("Toggle Combat Macro") || !combatMacro.get("Toggle Combat Macro").isJsonObject()) {
			return OptionalInt.empty();
		}
		JsonObject toggle = combatMacro.getAsJsonObject("Toggle Combat Macro");
		if (!toggle.has("type") || !toggle.get("type").isJsonPrimitive()
			|| !"KEYSYM".equalsIgnoreCase(toggle.get("type").getAsString())) {
			return OptionalInt.empty();
		}
		if (!toggle.has("code") || !toggle.get("code").isJsonPrimitive()
			|| !toggle.get("code").getAsJsonPrimitive().isNumber()) {
			return OptionalInt.empty();
		}
		return OptionalInt.of(toggle.get("code").getAsInt());
	}

	public static Path configPath(Path gameDirectory) {
		return gameDirectory.resolve("Nebula").resolve("Combat Module").resolve("config.json");
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
