package com.example.modmanager;

import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/** Lists the mods folder and enables/disables jars by renaming them (.jar <-> .jar.disabled). */
public final class ModFiles {
    private static final String DISABLED = ".disabled";

    public record Entry(String fileName, boolean enabled) {
        public String displayName() {
            return enabled ? fileName : fileName.substring(0, fileName.length() - DISABLED.length());
        }
    }

    private ModFiles() {}

    public static Path modsDir() {
        return FabricLoader.getInstance().getGameDir().resolve("mods");
    }

    public static List<Entry> list() throws IOException {
        List<Entry> out = new ArrayList<>();
        Path dir = modsDir();
        if (!Files.isDirectory(dir)) return out;
        try (Stream<Path> s = Files.list(dir)) {
            s.map(p -> p.getFileName().toString())
                    .filter(n -> n.endsWith(".jar") || n.endsWith(".jar" + DISABLED))
                    .filter(n -> !n.startsWith("modmanager")) // never let it disable itself
                    .sorted(String.CASE_INSENSITIVE_ORDER)
                    .forEach(n -> out.add(new Entry(n, n.endsWith(".jar"))));
        }
        return out;
    }

    /** Returns the new file name. */
    public static String toggle(Entry e) throws IOException {
        String newName = e.enabled()
                ? e.fileName() + DISABLED
                : e.fileName().substring(0, e.fileName().length() - DISABLED.length());
        Files.move(modsDir().resolve(e.fileName()), modsDir().resolve(newName));
        return newName;
    }
}
