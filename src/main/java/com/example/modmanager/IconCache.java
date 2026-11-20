package com.example.modmanager;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/** Downloads project icons from Modrinth in the background and turns them into textures. */
public final class IconCache {
    public static final int SIZE = 20;

    private static final Map<String, Identifier> READY = new ConcurrentHashMap<>();
    private static final Set<String> REQUESTED = ConcurrentHashMap.newKeySet();
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    private IconCache() {}

    /** Returns the icon texture if it has finished loading, otherwise null. */
    public static Identifier get(String key) {
        return READY.get(key);
    }

    public static void request(String key, String url) {
        if (key == null || url == null || !REQUESTED.add(key)) return;

        CompletableFuture.runAsync(() -> {
            try {
                HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                        .header("User-Agent", "modmanager/1.0 (Fabric client mod)")
                        .GET().build();
                HttpResponse<byte[]> res = HTTP.send(req, HttpResponse.BodyHandlers.ofByteArray());
                if (res.statusCode() != 200) return;

                // Decode (png/jpg/gif), scale to the icon size and re-encode as PNG for Minecraft.
                BufferedImage src = ImageIO.read(new ByteArrayInputStream(res.body()));
                if (src == null) return; // unsupported format such as webp: just show no icon

                BufferedImage scaled = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_ARGB);
                Graphics2D g = scaled.createGraphics();
                g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                g.drawImage(src, 0, 0, SIZE, SIZE, null);
                g.dispose();

                ByteArrayOutputStream png = new ByteArrayOutputStream();
                ImageIO.write(scaled, "png", png);
                NativeImage image = NativeImage.read(new ByteArrayInputStream(png.toByteArray()));

                Minecraft mc = Minecraft.getInstance();
                mc.execute(() -> {
                    Identifier id = Identifier.fromNamespaceAndPath("modmanager", "icon/" + key.toLowerCase());
                    mc.getTextureManager().register(id, new DynamicTexture(() -> "modmanager icon " + key, image));
                    READY.put(key, id);
                });
            } catch (Exception ignored) {
                // No icon is not a problem.
            }
        });
    }
}
