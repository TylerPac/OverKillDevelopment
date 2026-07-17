package dev.tylerpac.backend.service;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;

/**
 * Converts a CrateSettings LootMaster payload into Google Sheets row arrays
 * for the LootTables, ItemProfiles, and AttachmentProfiles tabs.
 *
 * <p>The output format matches the block-layout expected by
 * {@link SheetsParsingService} so that data uploaded here can be downloaded
 * and re-parsed correctly via the existing import flow.</p>
 */
@Service
public class CrateSettingsSheetWriter {

    private final ObjectMapper objectMapper;

    public CrateSettingsSheetWriter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    // ── LootTables tab ────────────────────────────────────────────────────────

    /**
     * Builds rows for the LootTables tab using the same horizontal column-block layout
     * as ItemProfiles and AttachmentProfiles, so that values land in the correct cell
     * positions of the formatted template (preserving merged cells, colors, etc.).
     * <pre>
     *   TableName  (4 empty)  TableName2  …
     *   Min  &lt;MinProfileSpawned&gt;  Max  &lt;MaxProfileSpawned&gt;  (empty)  Min  …
     *   (blank spacer row)
     *   Profile  Min  Max  Chance  (empty)  Profile  Min  Max  Chance  …
     *   &lt;profile&gt;  &lt;minSpawn&gt;  &lt;maxLoot&gt;  &lt;percent&gt;  …
     * </pre>
     * Each table occupies 5 columns (4 data columns + 1 blank separator).
     */
    public ArrayNode buildLootTablesRows(JsonNode lootTablesNode) {
        if (lootTablesNode == null || !lootTablesNode.isArray()) return objectMapper.createArrayNode();

        List<JsonNode> tables = new ArrayList<>();
        for (JsonNode t : lootTablesNode) tables.add(t);
        if (tables.isEmpty()) return objectMapper.createArrayNode();

        final int COLS = 5; // Profile, Min, Max, Chance + 1 blank separator column

        int maxDataRows = 0;
        for (JsonNode table : tables) {
            JsonNode lt = table.path("LootTable");
            int r = lt.isArray() ? lt.size() : 0;
            maxDataRows = Math.max(maxDataRows, r);
        }
        // row 0: table name, row 1: min/max spawn counts, row 2: blank spacer,
        // row 3: column headers, rows 4+: data entries
        int totalRows = 4 + maxDataRows;
        int totalCols = tables.size() * COLS;

        List<List<String>> grid = newGrid(totalRows, totalCols);

        for (int ti = 0; ti < tables.size(); ti++) {
            JsonNode table = tables.get(ti);
            int sc = ti * COLS;

            String tableName = table.path("TableName").asText("");
            int minSpawned = table.path("MinProfileSpawned").asInt(1);
            int maxSpawned = table.path("MaxProfileSpawned").asInt(1);
            JsonNode lootTable = table.path("LootTable");

            // Row 0: table name
            grid.get(0).set(sc, tableName);

            // Row 1: min/max profile spawn counts
            grid.get(1).set(sc,     "Min");
            grid.get(1).set(sc + 1, String.valueOf(minSpawned));
            grid.get(1).set(sc + 2, "Max");
            grid.get(1).set(sc + 3, String.valueOf(maxSpawned));

            // Row 2: blank spacer (already blank from newGrid)

            // Row 3: column headers
            grid.get(3).set(sc,     "Profile");
            grid.get(3).set(sc + 1, "Min");
            grid.get(3).set(sc + 2, "Max");
            grid.get(3).set(sc + 3, "Chance");

            // Rows 4+: loot table entries
            if (lootTable.isArray()) {
                int row = 4;
                for (JsonNode entry : lootTable) {
                    grid.get(row).set(sc,     entry.path("Profile").asText(""));
                    grid.get(row).set(sc + 1, String.valueOf(entry.path("MinSpawn").asInt(1)));
                    grid.get(row).set(sc + 2, String.valueOf(entry.path("MaxLoot").asInt(1)));
                    grid.get(row).set(sc + 3, String.valueOf(entry.path("PercentToSpawn").asDouble(1.0)));
                    row++;
                }
            }
        }

        return gridToArrayNode(grid);
    }

    // ── ItemProfiles tab ──────────────────────────────────────────────────────

    /**
     * Builds rows for the ItemProfiles tab using the column-block layout expected by
     * {@code parseItemProfilesBlocks}:
     * <pre>
     *   ProfileName  (6 empty)  ProfileName2  …
     *   ItemName  SpawnProb  QuantityMin  QuantityMax  HealthMin  HealthMax  Profile  SpawnProb  ItemName  …
     *   item1     0.6         0.5          1.0          0.5        1.0        SlotProf  1.0       …
     * </pre>
     * Each profile occupies 8 columns (6 item columns + 2 slot columns).
     */
    public ArrayNode buildItemProfilesRows(JsonNode itemProfilesNode) {
        if (itemProfilesNode == null || !itemProfilesNode.isArray()) return objectMapper.createArrayNode();

        List<JsonNode> profiles = new ArrayList<>();
        for (JsonNode p : itemProfilesNode) profiles.add(p);
        if (profiles.isEmpty()) return objectMapper.createArrayNode();

        final int COLS = 8; // ItemName, SpawnProb, QMin, QMax, HMin, HMax, SlotProfile, SlotProb

        // Calculate max rows required
        int maxDataRows = 0;
        for (JsonNode profile : profiles) {
            int r = countItemRows(profile.path("Items"));
            maxDataRows = Math.max(maxDataRows, r);
        }
        int totalRows = 2 + maxDataRows; // row 0: names, row 1: headers, row 2+: data
        int totalCols = profiles.size() * COLS;

        List<List<String>> grid = newGrid(totalRows, totalCols);

        for (int pi = 0; pi < profiles.size(); pi++) {
            JsonNode profile = profiles.get(pi);
            int sc = pi * COLS;

            grid.get(0).set(sc, profile.path("Profile").asText(""));
            grid.get(1).set(sc,     "ItemName");
            grid.get(1).set(sc + 1, "SpawnProb");
            grid.get(1).set(sc + 2, "QuantityMin");
            grid.get(1).set(sc + 3, "QuantityMax");
            grid.get(1).set(sc + 4, "HealthMin");
            grid.get(1).set(sc + 5, "HealthMax");
            grid.get(1).set(sc + 6, "Profile");
            grid.get(1).set(sc + 7, "SpawnProb");

            int row = 2;
            JsonNode items = profile.path("Items");
            if (items.isArray()) {
                for (JsonNode item : items) {
                    JsonNode slots = item.path("Slots");
                    grid.get(row).set(sc,     item.path("ItemName").asText(""));
                    grid.get(row).set(sc + 1, fmt(item.path("SpawnProb").asDouble(0)));
                    grid.get(row).set(sc + 2, fmt(item.path("ItemQuantityMin").asDouble(0)));
                    grid.get(row).set(sc + 3, fmt(item.path("ItemQuantityMax").asDouble(0)));
                    grid.get(row).set(sc + 4, fmt(item.path("ItemHealthMin").asDouble(0)));
                    grid.get(row).set(sc + 5, fmt(item.path("ItemHealthMax").asDouble(0)));

                    // First slot on the item row
                    if (slots.isArray() && !slots.isEmpty()) {
                        grid.get(row).set(sc + 6, slots.get(0).path("Profile").asText(""));
                        grid.get(row).set(sc + 7, fmt(slots.get(0).path("SpawnProb").asDouble(0)));
                    }
                    row++;

                    // Additional slots on blank-itemName rows
                    if (slots.isArray()) {
                        for (int si = 1; si < slots.size(); si++) {
                            grid.get(row).set(sc + 6, slots.get(si).path("Profile").asText(""));
                            grid.get(row).set(sc + 7, fmt(slots.get(si).path("SpawnProb").asDouble(0)));
                            row++;
                        }
                    }
                }
            }
        }

        return gridToArrayNode(grid);
    }

    // ── AttachmentProfiles tab ────────────────────────────────────────────────

    /**
     * Builds rows for the AttachmentProfiles tab using the column-block layout expected by
     * {@code parseAttachmentProfilesBlocks}:
     * <pre>
     *   ProfileName  (5 empty)  ProfileName2  …
     *   ItemName  AttachProb  QuantityMin  QuantityMax  HealthMin  HealthMax  ItemName  …
     *   attach1   1.0          0.5          1.0          0.5        1.0       …
     * </pre>
     * Each profile occupies 6 columns.
     */
    public ArrayNode buildAttachmentProfilesRows(JsonNode attachProfilesNode) {
        if (attachProfilesNode == null || !attachProfilesNode.isArray()) return objectMapper.createArrayNode();

        List<JsonNode> profiles = new ArrayList<>();
        for (JsonNode p : attachProfilesNode) profiles.add(p);
        if (profiles.isEmpty()) return objectMapper.createArrayNode();

        final int COLS = 6; // ItemName, AttachProb, QMin, QMax, HMin, HMax

        int maxDataRows = 0;
        for (JsonNode profile : profiles) {
            JsonNode items = profile.path("Items");
            int r = items.isArray() ? items.size() : 0;
            maxDataRows = Math.max(maxDataRows, r);
        }
        int totalRows = 2 + maxDataRows;
        int totalCols = profiles.size() * COLS;

        List<List<String>> grid = newGrid(totalRows, totalCols);

        for (int pi = 0; pi < profiles.size(); pi++) {
            JsonNode profile = profiles.get(pi);
            int sc = pi * COLS;

            grid.get(0).set(sc, profile.path("Profile").asText(""));
            grid.get(1).set(sc,     "ItemName");
            grid.get(1).set(sc + 1, "AttachProb");
            grid.get(1).set(sc + 2, "QuantityMin");
            grid.get(1).set(sc + 3, "QuantityMax");
            grid.get(1).set(sc + 4, "HealthMin");
            grid.get(1).set(sc + 5, "HealthMax");

            int row = 2;
            JsonNode items = profile.path("Items");
            if (items.isArray()) {
                for (JsonNode item : items) {
                    grid.get(row).set(sc,     item.path("AttachName").asText(""));
                    grid.get(row).set(sc + 1, fmt(item.path("AttachProb").asDouble(0)));
                    grid.get(row).set(sc + 2, fmt(item.path("AttachQuantityMin").asDouble(0)));
                    grid.get(row).set(sc + 3, fmt(item.path("AttachQuantityMax").asDouble(0)));
                    grid.get(row).set(sc + 4, fmt(item.path("AttachHealthMin").asDouble(0)));
                    grid.get(row).set(sc + 5, fmt(item.path("AttachHealthMax").asDouble(0)));
                    row++;
                }
            }
        }

        return gridToArrayNode(grid);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** Count the number of grid rows needed for an Items array (items + extra slot rows). */
    private int countItemRows(JsonNode items) {
        if (items == null || !items.isArray()) return 0;
        int count = 0;
        for (JsonNode item : items) {
            count++; // item row
            JsonNode slots = item.path("Slots");
            if (slots.isArray() && slots.size() > 1) {
                count += slots.size() - 1; // extra slot rows (first slot is on the item row)
            }
        }
        return count;
    }

    private List<List<String>> newGrid(int rows, int cols) {
        List<List<String>> grid = new ArrayList<>(rows);
        for (int r = 0; r < rows; r++) {
            List<String> row = new ArrayList<>(cols);
            for (int c = 0; c < cols; c++) row.add("");
            grid.add(row);
        }
        return grid;
    }

    /** Convert 2D string grid to an ArrayNode, trimming trailing empty cells in each row. */
    private ArrayNode gridToArrayNode(List<List<String>> grid) {
        ArrayNode result = objectMapper.createArrayNode();
        for (List<String> gridRow : grid) {
            int lastNonEmpty = -1;
            for (int c = gridRow.size() - 1; c >= 0; c--) {
                if (!gridRow.get(c).isEmpty()) { lastNonEmpty = c; break; }
            }
            ArrayNode jsonRow = objectMapper.createArrayNode();
            for (int c = 0; c <= Math.max(lastNonEmpty, 0); c++) {
                jsonRow.add(gridRow.get(c));
            }
            result.add(jsonRow);
        }
        return result;
    }

    /** Format a double — use integer representation when it is a whole number. */
    private String fmt(double v) {
        if (v == Math.floor(v) && !Double.isInfinite(v)) {
            return String.valueOf((long) v);
        }
        return String.valueOf(v);
    }
}
