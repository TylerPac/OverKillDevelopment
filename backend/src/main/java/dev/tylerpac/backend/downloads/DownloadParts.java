package dev.tylerpac.backend.downloads;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// A mod has a client part (what players load, and what may go on the Steam Workshop) and a server part (what only
// the game server loads), and each part can hold several files. In the download service:
//   "core-client"              the part itself (also holds a file uploaded before parts could hold several)
//   "core-client--dankammo-pbo" one file in that part
// Each product has one download per part: "keycard-crates-client" and "keycard-crates-server". Loose files are
// placed in the standard mod layout under the file group's mod folder. The service knows nothing about any of this.
public final class DownloadParts {

    public static final String CLIENT = "client";
    public static final String SERVER = "server";
    public static final List<String> ALL = List.of(CLIENT, SERVER);

    // The group name is matched as short as possible: "core-client--x-server" is file "x-server" in core's client part.
    // Group names never contain "--" and never end in a part, so this cannot be ambiguous.
    private static final Pattern KEY = Pattern.compile("(.+?)-(client|server)(?:--(.+))?");
    private static final int MAX_KEY = 64;

    /** base "core", part "client", entry "dankammo-pbo" (null for the part itself). */
    public record Parsed(String base, String part, String entry) {
    }

    private DownloadParts() {
    }

    public static boolean isPart(String value) {
        return ALL.contains(value);
    }

    public static String key(String base, String part) {
        return base + "-" + part;
    }

    public static String label(String part) {
        return CLIENT.equals(part) ? "Client" : "Server";
    }

    /** True for "core-client": a base key cannot end in a part, or the two names would clash. */
    public static boolean endsWithPart(String key) {
        return ALL.stream().anyMatch(part -> key.endsWith("-" + part)) || key.contains("--");
    }

    public static Parsed parse(String key) {
        Matcher match = KEY.matcher(key);
        return match.matches() ? new Parsed(match.group(1), match.group(2), match.group(3)) : null;
    }

    /** The key of one uploaded file in a part, the same every time for the same file name. */
    public static String entryKey(String base, String part, String fileName) {
        String lower = fileName.toLowerCase(Locale.ROOT);
        String slug = lower.replaceAll("[^a-z0-9]+", "-").replaceAll("^-+|-+$", "");
        if (slug.isEmpty()) {
            slug = "file";
        }
        String prefix = key(base, part) + "--";
        if (prefix.length() + slug.length() <= MAX_KEY) {
            return prefix + slug;
        }
        // Too long: keep the start of the name and add a short hash so different names stay different
        String hash = shortHash(lower);
        int room = MAX_KEY - prefix.length() - hash.length() - 1;
        if (room < 1) {
            throw new DownloadServiceException(400, "invalid_key");
        }
        return prefix + slug.substring(0, Math.min(room, slug.length())).replaceAll("-+$", "") + "-" + hash;
    }

    /**
     * Where a loose file goes in the standard mod layout: .pbo and .bisign in @Mod/addons, .bikey in @Mod/keys,
     * anything else (mod.cpp, meta.cpp, logo) in @Mod. A .zip is merged as it is, so it has no folder.
     */
    public static String folderFor(String modFolder, String fileName) {
        if (modFolder == null || modFolder.isBlank()) {
            return null;
        }
        String lower = fileName.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".zip")) {
            return null;
        }
        if (lower.endsWith(".pbo") || lower.endsWith(".bisign")) {
            return modFolder + "/addons";
        }
        if (lower.endsWith(".bikey")) {
            return modFolder + "/keys";
        }
        return modFolder;
    }

    /** "@OverKillDevCore" from a name like "OverKill Dev Core": the folder name DayZ server owners expect. */
    public static String defaultModFolder(String displayName) {
        String compact = displayName == null ? "" : displayName.replaceAll("[^A-Za-z0-9_.-]", "");
        return "@" + (compact.isEmpty() ? "Mod" : compact);
    }

    private static String shortHash(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest).substring(0, 6);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
