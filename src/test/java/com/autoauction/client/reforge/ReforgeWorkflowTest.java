package com.autoauction.client.reforge;

import com.autoauction.client.minecraft.MinecraftGameActions;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ReforgeWorkflowTest {
	@Test
	void failureCallbackDoesNotRunSuccessHandler() throws Exception {
		AtomicInteger failures = new AtomicInteger();
		AtomicInteger successes = new AtomicInteger();
		ReforgeWorkflow workflow = new ReforgeWorkflow(
			ReforgeTargetPlan.parse("fd armor", "Fierce").orElseThrow(),
			new MinecraftGameActions(),
			100,
			message -> {},
			message -> failures.incrementAndGet(),
			successes::incrementAndGet
		);

		invokeFail(workflow);

		assertEquals(1, failures.get());
		assertEquals(0, successes.get());
	}

	private void invokeFail(ReforgeWorkflow workflow) throws Exception {
		Method fail = ReforgeWorkflow.class.getDeclaredMethod("fail", String.class);
		fail.setAccessible(true);
		try {
			fail.invoke(workflow, "expected failure");
		} catch (InvocationTargetException e) {
			Throwable cause = e.getCause();
			if (cause instanceof Exception exception) {
				throw exception;
			}
			throw e;
		}
	}
}
