package com.autoauction.client.macro;

import org.junit.jupiter.api.Test;
import org.lwjgl.glfw.GLFW;

import java.util.OptionalInt;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NebulaMacroToggleKeyTest {
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
}
