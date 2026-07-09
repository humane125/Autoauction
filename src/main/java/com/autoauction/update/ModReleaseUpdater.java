package com.autoauction.update;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

public final class ModReleaseUpdater {
	private static final HttpClient HTTP = HttpClient.newBuilder()
		.connectTimeout(Duration.ofSeconds(10))
		.build();
	private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(20);
	private static final String DOWNLOAD_TEMP_SUFFIX = ".download";

	private ModReleaseUpdater() {
	}

	public static void checkForUpdateAsync(
		String modId,
		String displayName,
		String apiBaseUrl,
		String apiKey,
		Runnable closeClient,
		Consumer<String> infoLog,
		Consumer<String> warnLog
	) {
		if (isBlank(apiBaseUrl) || isBlank(apiKey)) {
			return;
		}
		CompletableFuture.runAsync(() -> {
			try {
				checkForUpdate(modId, displayName, apiBaseUrl, apiKey, closeClient, infoLog, warnLog);
			} catch (Exception error) {
				warn(warnLog, displayName + " update check failed: " + error.getMessage());
			}
		});
	}

	static void checkForUpdate(
		String modId,
		String displayName,
		String apiBaseUrl,
		String apiKey,
		Runnable closeClient,
		Consumer<String> infoLog,
		Consumer<String> warnLog
	) throws Exception {
		Optional<Path> currentJar = currentModJarPath(modId);
		if (currentJar.isEmpty()) {
			info(infoLog, displayName + " updater skipped: current mod is not running from a jar.");
			return;
		}
		String currentSha = sha256(currentJar.get());
		List<Release> releases = fetchReleases(apiBaseUrl, apiKey);
		Optional<Release> release = findRelease(releases, displayName);
		if (release.isEmpty()) {
			return;
		}
		if (!shouldUpdate(currentSha, release.get().sha256())) {
			return;
		}
		Path targetJar = currentJar.get();
		Path tempJar = downloadRelease(apiBaseUrl, apiKey, release.get(), targetJar.getParent());
		String downloadedSha = sha256(tempJar);
		if (!downloadedSha.equalsIgnoreCase(release.get().sha256())) {
			Files.deleteIfExists(tempJar);
			warn(warnLog, displayName + " update rejected: downloaded SHA-256 did not match manifest.");
			return;
		}
		stageReplacement(tempJar, targetJar, displayName);
		info(infoLog, displayName + " updated mods; closing instance so the new jar can be applied.");
		if (closeClient != null) {
			closeClient.run();
		}
	}

	static URI releaseManifestUri(String apiBaseUrl) {
		return URI.create(stripTrailingSlash(apiBaseUrl) + "/api/mod/releases");
	}

	static URI resolveDownloadUri(String apiBaseUrl, String downloadUrl) {
		String cleanDownload = String.valueOf(downloadUrl == null ? "" : downloadUrl).trim();
		if (cleanDownload.startsWith("http://") || cleanDownload.startsWith("https://")) {
			return URI.create(cleanDownload);
		}
		if (!cleanDownload.startsWith("/")) {
			cleanDownload = "/" + cleanDownload;
		}
		return URI.create(stripTrailingSlash(apiBaseUrl) + cleanDownload);
	}

	static Optional<Release> findRelease(List<Release> releases, String modName) {
		String target = normalizeName(modName);
		return releases.stream()
			.filter(release -> normalizeName(release.modName()).equals(target))
			.findFirst();
	}

	static boolean shouldUpdate(String currentSha256, String releaseSha256) {
		return isSha256(currentSha256) && isSha256(releaseSha256) && !currentSha256.equalsIgnoreCase(releaseSha256);
	}

	static String downloadTempSuffix() {
		return DOWNLOAD_TEMP_SUFFIX;
	}

	private static List<Release> fetchReleases(String apiBaseUrl, String apiKey) throws Exception {
		HttpRequest request = HttpRequest.newBuilder()
			.uri(releaseManifestUri(apiBaseUrl))
			.timeout(REQUEST_TIMEOUT)
			.header("Authorization", "Bearer " + apiKey)
			.GET()
			.build();
		HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
		if (response.statusCode() != 200) {
			throw new IOException("manifest returned HTTP " + response.statusCode());
		}
		JsonObject body = JsonParser.parseString(response.body()).getAsJsonObject();
		JsonArray array = body.has("releases") && body.get("releases").isJsonArray()
			? body.getAsJsonArray("releases")
			: new JsonArray();
		List<Release> releases = new ArrayList<>();
		for (JsonElement element : array) {
			if (!element.isJsonObject()) {
				continue;
			}
			JsonObject object = element.getAsJsonObject();
			releases.add(new Release(
				stringValue(object, "filename"),
				stringValue(object, "modName"),
				stringValue(object, "updatedAt"),
				stringValue(object, "downloadUrl"),
				stringValue(object, "sha256")
			));
		}
		return releases;
	}

	private static Path downloadRelease(String apiBaseUrl, String apiKey, Release release, Path modsDir) throws Exception {
		Files.createDirectories(modsDir);
		Path temp = Files.createTempFile(modsDir, "mod-update-", DOWNLOAD_TEMP_SUFFIX);
		HttpRequest request = HttpRequest.newBuilder()
			.uri(resolveDownloadUri(apiBaseUrl, release.downloadUrl()))
			.timeout(REQUEST_TIMEOUT)
			.header("Authorization", "Bearer " + apiKey)
			.GET()
			.build();
		HttpResponse<byte[]> response = HTTP.send(request, HttpResponse.BodyHandlers.ofByteArray());
		if (response.statusCode() != 200) {
			Files.deleteIfExists(temp);
			throw new IOException("download returned HTTP " + response.statusCode());
		}
		Files.write(temp, response.body(), StandardOpenOption.TRUNCATE_EXISTING);
		return temp;
	}

	private static void stageReplacement(Path sourceJar, Path targetJar, String displayName) throws IOException {
		Path script = targetJar.resolveSibling(normalizeName(displayName) + "-apply-update.ps1");
		String content = """
			param([int64]$TargetPid, [string]$SourceJar, [string]$TargetJar)
			try { Wait-Process -Id $TargetPid -Timeout 90 -ErrorAction SilentlyContinue } catch {}
			Start-Sleep -Milliseconds 500
			Move-Item -LiteralPath $SourceJar -Destination $TargetJar -Force
			Remove-Item -LiteralPath $MyInvocation.MyCommand.Path -Force -ErrorAction SilentlyContinue
			""";
		Files.writeString(script, content, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
		new ProcessBuilder(
			"powershell.exe",
			"-NoProfile",
			"-ExecutionPolicy",
			"Bypass",
			"-File",
			script.toString(),
			String.valueOf(ProcessHandle.current().pid()),
			sourceJar.toString(),
			targetJar.toString()
		).start();
	}

	private static Optional<Path> currentModJarPath(String modId) {
		return FabricLoader.getInstance().getModContainer(modId)
			.flatMap(container -> container.getOrigin().getPaths().stream().findFirst())
			.filter(path -> Files.isRegularFile(path) && path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".jar"));
	}

	private static String sha256(Path path) throws Exception {
		MessageDigest digest = MessageDigest.getInstance("SHA-256");
		return HexFormat.of().formatHex(digest.digest(Files.readAllBytes(path)));
	}

	private static boolean isSha256(String value) {
		return String.valueOf(value == null ? "" : value).matches("(?i)^[a-f0-9]{64}$");
	}

	private static String stripTrailingSlash(String value) {
		String result = String.valueOf(value == null ? "" : value).trim();
		while (result.endsWith("/")) {
			result = result.substring(0, result.length() - 1);
		}
		return result;
	}

	private static String normalizeName(String value) {
		return String.valueOf(value == null ? "" : value).toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
	}

	private static String stringValue(JsonObject object, String field) {
		return object.has(field) && !object.get(field).isJsonNull() ? object.get(field).getAsString() : "";
	}

	private static boolean isBlank(String value) {
		return value == null || value.isBlank();
	}

	private static void info(Consumer<String> sink, String message) {
		if (sink != null) {
			sink.accept(message);
		}
	}

	private static void warn(Consumer<String> sink, String message) {
		if (sink != null) {
			sink.accept(message);
		}
	}

	public record Release(String filename, String modName, String updatedAt, String downloadUrl, String sha256) {
	}
}
