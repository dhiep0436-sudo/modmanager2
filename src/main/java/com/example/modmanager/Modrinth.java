package com.example.modmanager;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

/** Minimal Modrinth API client: search and install. */
public final class Modrinth {
    public static final String MC_VERSION = "1.21.11";
    private static final String API = "https://api.modrinth.com/v2";
    private static final String USER_AGENT = "modmanager/1.0 (Fabric client mod)";
    private static final String FABRIC_API_PROJECT = "P7dR8mSH";
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    public enum Kind {
        MOD("mod", "mods", "Mods"),
        RESOURCEPACK("resourcepack", "resourcepacks", "Resource Packs"),
        SHADER("shader", "shaderpacks", "Shaders");

        public final String apiType;
        public final String folder;
        public final String label;

        Kind(String apiType, String folder, String label) {
            this.apiType = apiType;
            this.folder = folder;
            this.label = label;
        }
    }

    public record Hit(String id, String title, String description, String iconUrl) {}

    private Modrinth() {}

    public static List<Hit> search(Kind kind, String query, int page, int pageSize) throws IOException, InterruptedException {
        String facets = "[[\"project_type:" + kind.apiType + "\"],[\"versions:" + MC_VERSION + "\"]"
                + (kind == Kind.MOD ? ",[\"categories:fabric\"]" : "") + "]";
        String url = API + "/search?query=" + enc(query)
                + "&limit=" + pageSize
                + "&offset=" + (page * pageSize)
                + "&facets=" + enc(facets);

        JsonArray hits = get(url).getAsJsonObject().getAsJsonArray("hits");
        List<Hit> out = new ArrayList<>();
        for (JsonElement e : hits) {
            JsonObject o = e.getAsJsonObject();
            out.add(new Hit(
                    o.get("project_id").getAsString(),
                    o.get("title").getAsString(),
                    o.has("description") ? o.get("description").getAsString() : "",
                    str(o, "icon_url")));
        }
        return out;
    }

    /** Downloads the newest 1.21.11 file of a project (plus required mod dependencies). Returns the saved file names. */
    public static List<String> install(Kind kind, String projectId, Path gameDir) throws IOException, InterruptedException {
        List<String> saved = new ArrayList<>();
        install(kind, projectId, gameDir, saved, 0);
        return saved;
    }

    private static void install(Kind kind, String projectId, Path gameDir, List<String> saved, int depth)
            throws IOException, InterruptedException {
        String url = API + "/project/" + projectId + "/version?game_versions=" + enc("[\"" + MC_VERSION + "\"]")
                + (kind == Kind.MOD ? "&loaders=" + enc("[\"fabric\"]") : "");
        JsonArray versions = get(url).getAsJsonArray();
        if (versions.isEmpty()) {
            throw new IOException("no " + MC_VERSION + " version available");
        }

        JsonObject version = versions.get(0).getAsJsonObject();

        // Required dependencies (mods only, one level deep chain limited to 3).
        if (kind == Kind.MOD && depth < 3 && version.has("dependencies")) {
            for (JsonElement d : version.getAsJsonArray("dependencies")) {
                JsonObject dep = d.getAsJsonObject();
                if (!"required".equals(str(dep, "dependency_type"))) continue;
                String depId = str(dep, "project_id");
                if (depId == null || depId.equals(FABRIC_API_PROJECT)) continue;
                install(Kind.MOD, depId, gameDir, saved, depth + 1);
            }
        }

        JsonArray files = version.getAsJsonArray("files");
        JsonObject file = files.get(0).getAsJsonObject();
        for (JsonElement f : files) {
            JsonObject fo = f.getAsJsonObject();
            if (fo.has("primary") && fo.get("primary").getAsBoolean()) {
                file = fo;
                break;
            }
        }

        Path dir = gameDir.resolve(kind.folder);
        Files.createDirectories(dir);
        saved.add(download(file, dir));
    }

    private static String download(JsonObject file, Path dir) throws IOException, InterruptedException {
        String fileName = file.get("filename").getAsString();
        // Never trust a remote file name to stay inside the target folder.
        if (!Path.of(fileName).getFileName().toString().equals(fileName)) {
            throw new IOException("unsafe file name: " + fileName);
        }

        Path target = dir.resolve(fileName);
        if (Files.exists(target)) {
            return fileName;
        }

        Path tmp = dir.resolve(fileName + ".part");
        HttpRequest req = HttpRequest.newBuilder(URI.create(file.get("url").getAsString()))
                .header("User-Agent", USER_AGENT).GET().build();
        HttpResponse<InputStream> res = HTTP.send(req, HttpResponse.BodyHandlers.ofInputStream());
        if (res.statusCode() != 200) {
            throw new IOException("download failed: HTTP " + res.statusCode());
        }

        try {
            MessageDigest md = MessageDigest.getInstance("SHA-512");
            try (InputStream in = new DigestInputStream(res.body(), md)) {
                Files.copy(in, tmp, StandardCopyOption.REPLACE_EXISTING);
            }
            String expected = file.getAsJsonObject("hashes").get("sha512").getAsString();
            if (!HexFormat.of().formatHex(md.digest()).equalsIgnoreCase(expected)) {
                Files.deleteIfExists(tmp);
                throw new IOException("checksum mismatch for " + fileName);
            }
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IOException(e);
        }
        return fileName;
    }

    private static JsonElement get(String url) throws IOException, InterruptedException {
        HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                .header("User-Agent", USER_AGENT).GET().build();
        HttpResponse<String> res = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() != 200) {
            throw new IOException("Modrinth returned HTTP " + res.statusCode());
        }
        return JsonParser.parseString(res.body());
    }

    private static String str(JsonObject o, String key) {
        return o.has(key) && !o.get(key).isJsonNull() ? o.get(key).getAsString() : null;
    }

    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }
}
