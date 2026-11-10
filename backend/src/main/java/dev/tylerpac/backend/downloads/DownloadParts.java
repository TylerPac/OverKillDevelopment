package dev.tylerpac.backend.downloads;

import java.util.List;

// A mod has a client part (what players load, and what may go on the Steam Workshop) and a server part (what only
// the game server loads). In the download service each part is its own file and each product has one download per
// part: file "core" is stored as "core-client" and "core-server", and product "keycard-crates" has the downloads
// "keycard-crates-client" and "keycard-crates-server". The service itself knows nothing about parts.
public final class DownloadParts {

    public static final String CLIENT = "client";
    public static final String SERVER = "server";
    public static final List<String> ALL = List.of(CLIENT, SERVER);

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
        return ALL.stream().anyMatch(part -> key.endsWith("-" + part));
    }
}
