package com.player2.playerengine.interpreter;

import java.io.IOException;
import java.lang.reflect.Method;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;

/**
 * Minimal local-only adapter: the actual OAuth flow and tokens are NEVER held by Minecraft.
 * Returns null only when the provider is explicitly disabled. An enabled bridge that is
 * unavailable throws, rather than silently falling back to a different billable service.
 */
public final class LocalChatGPTBridge {
    private static final String CONFIG_NAME = "ellie-chatgpt.properties";
    private static final int MAX_REQUEST = 192 * 1024;
    private static final int MAX_RESPONSE = 256 * 1024;
    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(2))
            .version(HttpClient.Version.HTTP_1_1)
            .build();
    private LocalChatGPTBridge() {}

    public static Map<String, Object> maybeComplete(Object request, Object taskClass) throws Exception {
        Properties config = loadConfig();
        if (!"chatgpt".equalsIgnoreCase(config.getProperty("provider", "player2").trim())) {
            return null;
        }
        String secret = config.getProperty("secret", "").trim();
        String portText = config.getProperty("port", "38457").trim();
        int port;
        try { port = Integer.parseInt(portText); }
        catch (NumberFormatException e) { throw new IOException("Invalid Ellie ChatGPT bridge port", e); }
        if (port < 1025 || port > 65535 || !secret.matches("[0-9a-fA-F]{64}")) {
            throw new IOException("Invalid Ellie ChatGPT bridge configuration (run Start-Ellie-ChatGPT.ps1)");
        }
        String body = request == null ? "" : request.toString();
        if (body.isEmpty() || body.getBytes(StandardCharsets.UTF_8).length > MAX_REQUEST) {
            throw new IOException("Ellie ChatGPT request empty or too large");
        }
        String kind = taskClass == null ? "DECISION" : taskClass.toString();
        HttpRequest outbound = HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+"/complete"))
                .header("Content-Type", "application/json")
                .header("X-Ellie-Bridge-Secret", secret)
                .header("X-Ellie-Task-Class", kind)
                .timeout(Duration.ofSeconds(125))
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();
        report("CHATGPT_INTERPRETER_REQUEST", "task=" + kind);
        HttpResponse<String> result;
        try {
            result = CLIENT.send(outbound, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            report("CHATGPT_INTERPRETER_ERROR", "Request cancelled/interrupted");
            throw e;
        } catch (Exception e) {
            report("CHATGPT_INTERPRETER_ERROR", "Local interpreter unreachable or timed out; Player2 not charged as fallback");
            throw new IOException("ChatGPT local bridge unavailable; start the bridge or set provider=player2 explicitly", e);
        }
        if (result.statusCode() != 200) {
            report("CHATGPT_INTERPRETER_ERROR", "Bridge HTTP="+result.statusCode()+"; no automatic Player2 fallback");
            throw new IOException("ChatGPT interpreter returned HTTP " + result.statusCode() +
                    " (see bridge terminal; no automatic Player2 fallback)");
        }
        if (result.body().getBytes(StandardCharsets.UTF_8).length > MAX_RESPONSE) {
            throw new IOException("ChatGPT bridge response exceeds size limit");
        }
        // Gson is already present inside Minecraft, but is not shipped in this overlay.
        Object parsed = Class.forName("com.google.gson.JsonParser")
                .getMethod("parseString", String.class).invoke(null, result.body());
        Object object = parsed.getClass().getMethod("getAsJsonObject").invoke(parsed);
        Method entrySet = object.getClass().getMethod("entrySet");
        Map<String, Object> response = new LinkedHashMap<>();
        for (Object value : (Iterable<?>) entrySet.invoke(object)) {
            Map.Entry<?, ?> entry = (Map.Entry<?, ?>) value;
            response.put(String.valueOf(entry.getKey()), entry.getValue());
        }
        if (!response.containsKey("choices")) throw new IOException("Malformed ChatGPT bridge response: missing choices");
        report("CHATGPT_INTERPRETER_RESPONSE", "task="+kind+" result=received");
        return response;
    }

    private static Properties loadConfig() throws IOException {
        Properties result = new Properties();
        Path config = configPath();
        if (!Files.isRegularFile(config)) return result;
        try (var in = Files.newInputStream(config)) { result.load(in); }
        catch (IOException e) { throw new IOException("Ellie ChatGPT provider config exists but cannot be read; refusing silent fallback", e); }
        return result;
    }

    public static Path configPath() {
        try {
            // Avoid linking Fabric types into a portable helper/test class.
            Class<?> fabric = Class.forName("net.fabricmc.loader.api.FabricLoader");
            Object loader = fabric.getMethod("getInstance").invoke(null);
            Object dir = fabric.getMethod("getConfigDir").invoke(loader);
            return ((Path) dir).resolve(CONFIG_NAME);
        } catch (Exception e) {
            return Path.of(System.getProperty("user.dir", "."), "config", CONFIG_NAME);
        }
    }

    private static void report(String event, String detail) {
        try {
            Class<?> telemetry = Class.forName("com.player2.playerengine.observatory.ObservatoryTelemetry");
            telemetry.getMethod("event", String.class, String.class).invoke(null, event, detail);
        } catch (Throwable ignored) { /* Telemetry must never change interpreter behavior. */ }
        System.out.println("[EllieChatGPT] " + event + ": " + detail);
    }
}
