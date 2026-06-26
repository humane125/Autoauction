package com.autoauction.client.macro;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.lwjgl.glfw.GLFW;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.OptionalInt;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NebulaMacroToggleKeyTest {
	@TempDir
	Path tempDir;

	@Test
	void resolvesSimpleKeyboardNames() {
		assertEquals(OptionalInt.of(GLFW.GLFW_KEY_P), NebulaMacroToggleKey.resolve("P"));
		assertEquals(OptionalInt.of(GLFW.GLFW_KEY_F8), NebulaMacroToggleKey.resolve("F8"));
	}

	@Test
	void resolvesMinecraftAndGlfwStyleNames() {
		assertEquals(OptionalInt.of(GLFW.GLFW_KEY_P), NebulaMacroToggleKey.resolve("key.keyboard.p"));
		assertEquals(OptionalInt.of(GLFW.GLFW_KEY_LEFT_SHIFT), NebulaMacroToggleKey.resolve("GLFW_KEY_LEFT_SHIFT"));
		assertEquals(OptionalInt.of(GLFW.GLFW_KEY_SPACE), NebulaMacroToggleKey.resolve("space"));
	}

	@Test
	void rejectsBlankOrUnknownNames() {
		assertEquals(OptionalInt.empty(), NebulaMacroToggleKey.resolve(""));
		assertEquals(OptionalInt.empty(), NebulaMacroToggleKey.resolve("not-a-key"));
	}

	@Test
	void resolvesCodeFromNebulaCombatModuleConfigUnderGameDirectory() throws Exception {
		Path config = tempDir.resolve("Nebula").resolve("Combat Module").resolve("config.json");
		Files.createDirectories(config.getParent());
		Files.writeString(config, """
			{
			  "Combat Macro": {
			    "Toggle Combat Macro": {
			      "type": "KEYSYM",
			      "code": 80
			    }
			  }
			}
			""", StandardCharsets.UTF_8);

		assertEquals(OptionalInt.of(GLFW.GLFW_KEY_P), NebulaMacroToggleKey.resolveFromGameDirectory(tempDir));
	}

	@Test
	void rejectsMissingOrNonKeyboardNebulaCombatModuleConfig() throws Exception {
		assertEquals(OptionalInt.empty(), NebulaMacroToggleKey.resolveFromGameDirectory(tempDir));

		Path config = tempDir.resolve("Nebula").resolve("Combat Module").resolve("config.json");
		Files.createDirectories(config.getParent());
		Files.writeString(config, """
			{
			  "Combat Macro": {
			    "Toggle Combat Macro": {
			      "type": "MOUSE",
			      "code": 0
			    }
			  }
			}
			""", StandardCharsets.UTF_8);

		assertEquals(OptionalInt.empty(), NebulaMacroToggleKey.resolveFromGameDirectory(tempDir));
	}
}
