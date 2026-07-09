package com.autoauction.client.macro;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.zip.CRC32;

public final class NebulaLatestLogWatcher {
	private final Path latestLog;
	private long lastFileSize;
	private long lastFingerprint;

	public NebulaLatestLogWatcher(Path latestLog) {
		this.latestLog = latestLog;
	}

	public List<String> pollMacroMessages() throws IOException {
		if (!Files.exists(latestLog)) {
			lastFileSize = 0L;
			return List.of();
		}

		try (RandomAccessFile file = new RandomAccessFile(latestLog.toFile(), "r")) {
			long fileSize = file.length();
			long fingerprint = fingerprint(file);
			if (fileSize < lastFileSize || (lastFileSize > 0L && fingerprint != lastFingerprint)) {
				lastFileSize = 0L;
			}
			file.seek(lastFileSize);

			List<String> messages = new ArrayList<>();
			String line;
			while ((line = file.readLine()) != null) {
				String decodedLine = decodeLogLine(line);
				if (isNebulaMacroLine(decodedLine) || isNebulaBanwaveLine(decodedLine)) {
					messages.add(decodedLine);
				}
			}
			lastFileSize = file.getFilePointer();
			lastFingerprint = fingerprint;
			return messages;
		}
	}

	private static long fingerprint(RandomAccessFile file) throws IOException {
		long originalPosition = file.getFilePointer();
		file.seek(0L);
		byte[] buffer = new byte[(int) Math.min(4096L, file.length())];
		int read = file.read(buffer);
		file.seek(originalPosition);
		if (read <= 0) {
			return 0L;
		}
		CRC32 crc = new CRC32();
		crc.update(buffer, 0, read);
		return crc.getValue();
	}

	public static boolean isNebulaMacroLine(String line) {
		String normalized = normalize(line);
		if (normalized.contains("autoauctiondebug/nebula")) {
			return false;
		}
		return normalized.contains("nebulaclient")
			&& (normalized.contains("combatmacro:") || normalized.contains("combat macro:"))
			&& (normalized.contains("enabled") || normalized.contains("disabled"));
	}

	public static boolean isNebulaBanwaveLine(String line) {
		String normalized = normalize(line);
		if (normalized.contains("autoauctiondebug/nebula")) {
			return false;
		}
		return normalized.contains("nebulaclient") && normalized.contains("banwave");
	}

	public static String displayMessage(String line) {
		String value = String.valueOf(line == null ? "" : line);
		int chatMarker = value.toLowerCase(Locale.ROOT).lastIndexOf("[chat]");
		if (chatMarker >= 0) {
			return value.substring(chatMarker + "[chat]".length()).trim();
		}
		int infoMarker = value.indexOf("]:");
		if (infoMarker >= 0) {
			return value.substring(infoMarker + 2).trim();
		}
		return value.trim();
	}

	private static String decodeLogLine(String line) {
		byte[] bytes = line.getBytes(StandardCharsets.ISO_8859_1);
		return new String(bytes, StandardCharsets.UTF_8);
	}

	private static String normalize(String line) {
		return String.valueOf(line == null ? "" : line)
			.replaceAll("(?i)(?:\u00C2?\u00A7|&)[0-9A-FK-OR]", "")
			.replace("Â§", "")
			.replace(" ", "")
			.toLowerCase(Locale.ROOT);
	}
}
