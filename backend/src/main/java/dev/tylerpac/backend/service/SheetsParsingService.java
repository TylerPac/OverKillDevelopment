package dev.tylerpac.backend.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import tools.jackson.databind.JsonNode;

@Service
public class SheetsParsingService {

    // Standard tab names matching the template (same as Python reference parser)
    public static final String TAB_LOOT_TABLES = "LootTables";
    public static final String TAB_ITEM_PROFILES = "ItemProfiles";
    public static final String TAB_ATTACHMENT_PROFILES = "AttachmentProfiles";

    /**
     * Parse from three separate tab value responses — exactly how the Python reference
     * parser works. Each tab is parsed with its dedicated block parser.
     */
    public Object parseAsCrateSettingsFromTabs(JsonNode lootTabValues, JsonNode itemTabValues, JsonNode attachTabValues) {
        List<List<String>> lootRows = extractRows(lootTabValues);
        List<List<String>> itemRows = extractRows(itemTabValues);
        List<List<String>> attachRows = extractRows(attachTabValues);

        List<Map<String, Object>> lootTables = parseLootTablesBlocks(lootRows);
        List<Map<String, Object>> itemProfiles = parseItemProfilesBlocks(itemRows);
        List<Map<String, Object>> attachmentProfiles = parseAttachmentProfilesBlocks(attachRows);

        Map<String, Object> master = new LinkedHashMap<>();
        master.put("AttachmentProfiles", attachmentProfiles);
        master.put("LootTables", lootTables);
        master.put("ItemProfiles", itemProfiles);

        List<Map<String, Object>> lootMaster = new ArrayList<>();
        lootMaster.add(master);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("LootMaster", lootMaster);
        return out;
    }

    /**
     * Parse a Google Sheets "values" response into a CrateSettings-like POJO structure.
     * Returns a plain Java object (Map/List) to avoid coupling to a specific Jackson
     * implementation in the web layer.
     */
    public Object parseAsCrateSettings(JsonNode sheetsValuesResponse) {
        List<Map<String, Object>> lootMaster = new ArrayList<>();

        List<Map<String, Object>> attachmentProfiles = new ArrayList<>();
        List<Map<String, Object>> lootTables = new ArrayList<>();
        List<Map<String, Object>> itemProfiles = new ArrayList<>();

        List<List<String>> rows = extractRows(sheetsValuesResponse);
        int i = 0;
        while (i < rows.size()) {
            List<String> row = rows.get(i);
            String first = row.size() > 0 ? safeTrim(row.get(0)) : "";
            if (first.equalsIgnoreCase("AttachmentProfiles") || first.equalsIgnoreCase("Attachment Profiles")) {
                i++;
                i = parseSectionGroupedByProfile(rows, i, attachmentProfiles);
                continue;
            }
            if (first.equalsIgnoreCase("LootTables") || first.equalsIgnoreCase("Loot Tables")) {
                i++;
                i = parseSectionAsFlatObjects(rows, i, lootTables);
                continue;
            }
            if (first.equalsIgnoreCase("ItemProfiles") || first.equalsIgnoreCase("Item Profiles")) {
                i++;
                i = parseSectionGroupedByProfile(rows, i, itemProfiles);
                continue;
            }
            i++;
        }

        Map<String, Object> master = new LinkedHashMap<>();
        // If the simple section-based parsing produced no results, try the block-layout fallback
        if ((attachmentProfiles == null || attachmentProfiles.isEmpty())
            && (lootTables == null || lootTables.isEmpty())
            && (itemProfiles == null || itemProfiles.isEmpty())) {
            rows = extractRows(sheetsValuesResponse);
            List<Map<String, Object>> lt = parseLootTablesBlocks(rows);
            List<Map<String, Object>> ip = parseItemProfilesBlocks(rows);
            List<Map<String, Object>> ap = parseAttachmentProfilesBlocks(rows);
            attachmentProfiles = ap;
            lootTables = lt;
            itemProfiles = ip;
        }

        master.put("AttachmentProfiles", attachmentProfiles);
        master.put("LootTables", lootTables);
        master.put("ItemProfiles", itemProfiles);
        lootMaster.add(master);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("LootMaster", lootMaster);
        return out;
    }

    // --- Block-layout parsing fallback (left-to-right blocks similar to the original Python parser) ---
    private List<List<String>> rectangularizeRows(List<List<String>> rows) {
        int max = 0;
        for (List<String> r : rows) if (r != null && r.size() > max) max = r.size();
        List<List<String>> rect = new ArrayList<>();
        for (List<String> r : rows) {
            List<String> copy = new ArrayList<>();
            if (r != null) copy.addAll(r);
            while (copy.size() < max) copy.add("");
            rect.add(copy);
        }
        return rect;
    }

    private String normHeader(String s) {
        if (s == null) return "";
        return s.trim().toLowerCase().replaceAll("[^a-z0-9]+", "");
    }

    private boolean isBlankCell(String s) {
        return s == null || s.trim().isEmpty();
    }

    private boolean rowIsBlank(List<String> row) {
        if (row == null) return true;
        for (String c : row) if (!isBlankCell(c)) return false;
        return true;
    }

    private String getCell(List<List<String>> grid, int r, int c) {
        if (r < 0 || r >= grid.size()) return "";
        List<String> row = grid.get(r);
        if (row == null) return "";
        if (c < 0 || c >= row.size()) return "";
        String v = row.get(c);
        return v == null ? "" : v;
    }

    private Integer toIntOrNull(String s) {
        if (s == null) return null;
        String t = s.trim();
        if (t.isEmpty()) return null;
        try {
            double d = Double.parseDouble(t);
            return (int) d;
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private Double toDoubleOrNull(String s) {
        if (s == null) return null;
        String t = s.trim();
        if (t.isEmpty()) return null;
        try {
            return Double.parseDouble(t);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private String findBestTitleNear(List<List<String>> grid, int headerRow, int colCenter, int maxUpRows, int colRadius) {
        String best = null;
        int bestScore = -1;
        for (int r = Math.max(0, headerRow - maxUpRows); r < headerRow; r++) {
            for (int c = Math.max(0, colCenter - colRadius); c <= Math.min(colCenter + colRadius, grid.get(r).size() - 1); c++) {
                String v = getCell(grid, r, c);
                if (isBlankCell(v)) continue;
                String nv = normHeader(v);
                if (nv.equals("profile") || nv.equals("min") || nv.equals("max") || nv.equals("chance") || nv.equals("itemname") || nv.equals("spawnprob")) continue;
                int score = (maxUpRows - (headerRow - r)) + (colRadius - Math.abs(c - colCenter));
                if (score > bestScore) { bestScore = score; best = v; }
            }
        }
        return best;
    }

    private List<Map<String, Object>> parseLootTablesBlocks(List<List<String>> rows) {
        List<Map<String, Object>> tables = new ArrayList<>();
        List<List<String>> rect = rectangularizeRows(rows);
        if (rect.isEmpty()) return tables;
        int blankRunLimit = 25;

        for (int r = 0; r < rect.size(); r++) {
            List<String> row = rect.get(r);
            boolean hasProfile = false;
            for (String c : row) if (normHeader(c).equals("profile")) { hasProfile = true; break; }
            if (!hasProfile) continue;
            int profileCol = -1;
            for (int ci = 0; ci < row.size(); ci++) if (normHeader(row.get(ci)).equals("profile")) { profileCol = ci; break; }
            if (profileCol == -1) continue;

            // find nearby min/max/chance
            int minCol = -1, maxCol = -1, chanceCol = -1;
            for (int ci = profileCol; ci < Math.min(row.size(), profileCol + 12); ci++) {
                String nh = normHeader(row.get(ci));
                if (nh.equals("min") && minCol == -1) minCol = ci;
                if (nh.equals("max") && maxCol == -1) maxCol = ci;
                if (nh.equals("chance") && chanceCol == -1) chanceCol = ci;
            }
            if (minCol == -1 || maxCol == -1 || chanceCol == -1) continue;

            String title = findBestTitleNear(rect, r, profileCol, 6, 8);
            if (title == null) continue;

            // find min/max profiles in rows above
            Integer minProfiles = null, maxProfiles = null;
            for (int rr = Math.max(0, r - 8); rr < r; rr++) {
                for (int cc = Math.max(0, profileCol - 10); cc < Math.min(rect.get(rr).size(), profileCol + 10); cc++) {
                    String v = getCell(rect, rr, cc);
                    String nh = normHeader(v);
                    if (nh.equals("min") && minProfiles == null) minProfiles = toIntOrNull(getCell(rect, rr, cc + 1));
                    if (nh.equals("max") && maxProfiles == null) maxProfiles = toIntOrNull(getCell(rect, rr, cc + 1));
                }
                if (minProfiles != null && maxProfiles != null) break;
            }
            if (minProfiles == null) minProfiles = 1;
            if (maxProfiles == null) maxProfiles = 1;

            List<Map<String, Object>> lootRows = new ArrayList<>();
            int blanks = 0;
            for (int rr = r + 1; rr < rect.size(); rr++) {
                List<String> prow = rect.get(rr);
                String profile = getCell(rect, rr, profileCol);
                if (isBlankCell(profile)) {
                    if (rowIsBlank(prow)) {
                        blanks++;
                        if (blanks >= blankRunLimit) break;
                    }
                    continue;
                }
                blanks = 0;
                Integer maxLoot = toIntOrNull(getCell(rect, rr, maxCol));
                Integer minSpawn = toIntOrNull(getCell(rect, rr, minCol));
                Double pct = toDoubleOrNull(getCell(rect, rr, chanceCol));
                if (maxLoot == null || minSpawn == null || pct == null) continue;
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("Profile", profile.trim());
                item.put("MaxLoot", maxLoot);
                item.put("MinSpawn", minSpawn);
                item.put("PercentToSpawn", pct);
                lootRows.add(item);
            }

            if (!lootRows.isEmpty()) {
                Map<String, Object> t = new LinkedHashMap<>();
                t.put("TableName", title.trim());
                t.put("MaxProfileSpawned", maxProfiles);
                t.put("MinProfileSpawned", minProfiles);
                t.put("LootTable", lootRows);
                tables.add(t);
            }
        }

        // dedup by TableName (keep most complete)
        Map<String, Map<String, Object>> dedup = new LinkedHashMap<>();
        for (Map<String, Object> t : tables) {
            String name = (String) t.get("TableName");
            if (name == null) continue;
            Map<String, Object> cur = dedup.get(name);
            int len = ((List<?>) t.getOrDefault("LootTable", List.of())).size();
            int curLen = cur == null ? -1 : ((List<?>) cur.getOrDefault("LootTable", List.of())).size();
            if (cur == null || len > curLen) dedup.put(name, t);
        }
        return new ArrayList<>(dedup.values());
    }

    private List<Map<String, Object>> parseItemProfilesBlocks(List<List<String>> rows) {
        List<Map<String, Object>> profiles = new ArrayList<>();
        List<List<String>> rect = rectangularizeRows(rows);
        if (rect.isEmpty()) return profiles;
        int maxCols = rect.get(0).size();
        int blankRunLimit = 20;

        for (int startCol = 0; startCol < maxCols; startCol++) {
            Integer itemHdrRow = null;
            for (int r = 0; r < Math.min(rect.size(), 30); r++) {
                String v = getCell(rect, r, startCol);
                String nh = normHeader(v);
                if (nh.equals("itemname") || nh.equals("item")) { itemHdrRow = r; break; }
            }
            if (itemHdrRow == null) continue;

            String profileName = getCell(rect, 0, startCol);
            if (isBlankCell(profileName)) profileName = findBestTitleNear(rect, itemHdrRow, startCol, 3, 4);
            if (isBlankCell(profileName)) continue;
            profileName = profileName.trim();

            int itemCol = startCol;
            int spawnCol = startCol + 1;
            int qminCol = startCol + 2;
            int qmaxCol = startCol + 3;
            int hminCol = startCol + 4;
            int hmaxCol = startCol + 5;
            Integer slotProfileCol = startCol + 6;
            Integer slotProbCol = startCol + 7;

            String slotHeader = normHeader(getCell(rect, itemHdrRow, slotProfileCol));
            if (!slotHeader.equals("profile")) { slotProfileCol = null; slotProbCol = null; }

            int dataStart = itemHdrRow + 1;
            List<Map<String, Object>> items = new ArrayList<>();
            Map<String, Object> currentItem = null;
            int blanks = 0;

            for (int r = dataStart; r < rect.size(); r++) {
                String itemName = getCell(rect, r, itemCol);
                if (isBlankCell(itemName) && currentItem != null && slotProfileCol != null) {
                    String sp = getCell(rect, r, slotProfileCol);
                    if (!isBlankCell(sp)) {
                        Double prob = toDoubleOrNull(getCell(rect, r, slotProbCol));
                        if (prob != null) {
                            @SuppressWarnings("unchecked")
                            List<Map<String, Object>> slots = (List<Map<String, Object>>) currentItem.get("Slots");
                            Map<String, Object> s = new LinkedHashMap<>(); s.put("Profile", sp.trim()); s.put("SpawnProb", prob);
                            slots.add(s);
                        }
                        continue;
                    }
                    if (rowIsBlank(rect.get(r))) { blanks++; if (blanks >= blankRunLimit) break; }
                    continue;
                }

                if (isBlankCell(itemName)) { if (rowIsBlank(rect.get(r))) { blanks++; if (blanks >= blankRunLimit) break; } continue; }
                blanks = 0;
                if (currentItem != null) items.add(currentItem);

                Double spawnProb = toDoubleOrNull(getCell(rect, r, spawnCol));
                Double qmin = toDoubleOrNull(getCell(rect, r, qminCol));
                Double qmax = toDoubleOrNull(getCell(rect, r, qmaxCol));
                Double hmin = toDoubleOrNull(getCell(rect, r, hminCol));
                Double hmax = toDoubleOrNull(getCell(rect, r, hmaxCol));
                if (spawnProb == null || qmin == null || qmax == null || hmin == null || hmax == null) { currentItem = null; continue; }

                currentItem = new LinkedHashMap<>();
                currentItem.put("ItemName", itemName.trim());
                currentItem.put("SpawnProb", spawnProb);
                currentItem.put("ItemQuantityMin", qmin);
                currentItem.put("ItemQuantityMax", qmax);
                currentItem.put("ItemHealthMin", hmin);
                currentItem.put("ItemHealthMax", hmax);
                currentItem.put("Slots", new ArrayList<Map<String,Object>>());

                if (slotProfileCol != null) {
                    String sp = getCell(rect, r, slotProfileCol);
                    if (!isBlankCell(sp)) {
                        Double prob = toDoubleOrNull(getCell(rect, r, slotProbCol));
                        if (prob != null) {
                            @SuppressWarnings("unchecked")
                            List<Map<String,Object>> slots = (List<Map<String,Object>>) currentItem.get("Slots");
                            Map<String,Object> s = new LinkedHashMap<>(); s.put("Profile", sp.trim()); s.put("SpawnProb", prob);
                            slots.add(s);
                        }
                    }
                }
            }
            if (currentItem != null) items.add(currentItem);
            if (!items.isEmpty()) {
                Map<String, Object> obj = new LinkedHashMap<>(); obj.put("Profile", profileName); obj.put("Items", items); profiles.add(obj);
            }
        }

        // dedup by Profile
        Map<String, Map<String,Object>> dedup = new LinkedHashMap<>();
        for (Map<String,Object> p : profiles) {
            String name = (String) p.get("Profile"); if (name == null) continue;
            Map<String,Object> cur = dedup.get(name); int len = ((List<?>) p.getOrDefault("Items", List.of())).size(); int curLen = cur == null ? -1 : ((List<?>) cur.getOrDefault("Items", List.of())).size();
            if (cur == null || len > curLen) dedup.put(name, p);
        }
        return new ArrayList<>(dedup.values());
    }

    private List<Map<String, Object>> parseAttachmentProfilesBlocks(List<List<String>> rows) {
        List<Map<String, Object>> profiles = new ArrayList<>();
        List<List<String>> rect = rectangularizeRows(rows);
        if (rect.isEmpty()) return profiles;
        int maxCols = rect.get(0).size();
        int blankRunLimit = 20;

        for (int startCol = 0; startCol < maxCols; startCol++) {
            Integer itemHdrRow = null;
            for (int r = 0; r < Math.min(rect.size(), 30); r++) {
                String v = getCell(rect, r, startCol);
                String nh = normHeader(v);
                if (nh.equals("itemname") || nh.equals("item")) { itemHdrRow = r; break; }
            }
            if (itemHdrRow == null) continue;

            String profileName = getCell(rect, 0, startCol);
            if (isBlankCell(profileName)) profileName = findBestTitleNear(rect, itemHdrRow, startCol, 3, 4);
            if (isBlankCell(profileName)) continue;
            profileName = profileName.trim();

            int itemCol = startCol;
            int probCol = startCol + 1;
            int qminCol = startCol + 2;
            int qmaxCol = startCol + 3;
            int hminCol = startCol + 4;
            int hmaxCol = startCol + 5;

            int dataStart = itemHdrRow + 1;
            List<Map<String,Object>> items = new ArrayList<>();
            int blanks = 0;
            for (int r = dataStart; r < rect.size(); r++) {
                String name = getCell(rect, r, itemCol);
                if (isBlankCell(name)) { if (rowIsBlank(rect.get(r))) { blanks++; if (blanks >= blankRunLimit) break; } continue; }
                blanks = 0;
                Double prob = toDoubleOrNull(getCell(rect, r, probCol));
                Double qmin = toDoubleOrNull(getCell(rect, r, qminCol));
                Double qmax = toDoubleOrNull(getCell(rect, r, qmaxCol));
                Double hmin = toDoubleOrNull(getCell(rect, r, hminCol));
                Double hmax = toDoubleOrNull(getCell(rect, r, hmaxCol));
                if (prob == null || qmin == null || qmax == null || hmin == null || hmax == null) continue;
                Map<String,Object> it = new LinkedHashMap<>();
                it.put("AttachName", name.trim());
                it.put("AttachProb", prob);
                it.put("AttachQuantityMin", qmin);
                it.put("AttachQuantityMax", qmax);
                it.put("AttachHealthMin", hmin);
                it.put("AttachHealthMax", hmax);
                items.add(it);
            }
            if (!items.isEmpty()) { Map<String,Object> obj = new LinkedHashMap<>(); obj.put("Profile", profileName); obj.put("Items", items); profiles.add(obj); }
        }

        Map<String, Map<String,Object>> dedup = new LinkedHashMap<>();
        for (Map<String,Object> p : profiles) { String name = (String) p.get("Profile"); if (name == null) continue; Map<String,Object> cur = dedup.get(name); int len = ((List<?>) p.getOrDefault("Items", List.of())).size(); int curLen = cur == null ? -1 : ((List<?>) cur.getOrDefault("Items", List.of())).size(); if (cur == null || len > curLen) dedup.put(name, p); }
        return new ArrayList<>(dedup.values());
    }

    /**
     * Parse a sheet tab into Tier `Zones` by expecting a header row containing at least
     * the columns: tier, x and y (case-insensitive). Rows after the header produce points
     * grouped by tier.
     */
    public Object parseAsTierZones(JsonNode sheetsValuesResponse) {
        List<List<String>> rows = extractRows(sheetsValuesResponse);
        int headerIdx = -1;
        List<String> header = null;
        for (int r = 0; r < rows.size(); r++) {
            List<String> row = rows.get(r);
            if (row.stream().anyMatch(c -> c != null && c.toLowerCase().contains("tier"))
                && row.stream().anyMatch(c -> c != null && (c.toLowerCase().equals("x") || c.toLowerCase().contains("x")))
                && row.stream().anyMatch(c -> c != null && (c.toLowerCase().equals("y") || c.toLowerCase().contains("y")))) {
                headerIdx = r;
                header = row;
                break;
            }
        }

        List<Map<String, Object>> zones = new ArrayList<>();
        if (headerIdx == -1) {
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("Zones", zones);
            return out;
        }

        int idxTier = findColumnIndex(header, "tier");
        int idxX = findColumnIndex(header, "x");
        int idxY = findColumnIndex(header, "y");
        if (idxTier == -1 || idxX == -1 || idxY == -1) {
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("Zones", zones);
            return out;
        }

        Map<Integer, List<List<Double>>> groups = new LinkedHashMap<>();
        for (int r = headerIdx + 1; r < rows.size(); r++) {
            List<String> row = rows.get(r);
            if (row == null || row.stream().allMatch(c -> c == null || c.trim().isEmpty())) break;
            String sTier = idxTier < row.size() ? safeTrim(row.get(idxTier)) : "";
            String sX = idxX < row.size() ? safeTrim(row.get(idxX)) : "";
            String sY = idxY < row.size() ? safeTrim(row.get(idxY)) : "";
            if (sTier.isEmpty() || sX.isEmpty() || sY.isEmpty()) continue;
            try {
                int tier = (int) Double.parseDouble(sTier);
                double x = Double.parseDouble(sX);
                double y = Double.parseDouble(sY);
                groups.computeIfAbsent(tier, k -> new ArrayList<>()).add(List.of(x, y));
            } catch (NumberFormatException ex) {
                // skip invalid rows
            }
        }

        for (Map.Entry<Integer, List<List<Double>>> e : groups.entrySet()) {
            Map<String, Object> zone = new LinkedHashMap<>();
            zone.put("tier", e.getKey());
            zone.put("points", e.getValue());
            zones.add(zone);
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("Zones", zones);
        return out;
    }

    // --- Helpers ---
    private List<List<String>> extractRows(JsonNode sheetsValuesResponse) {
        List<List<String>> rows = new ArrayList<>();
        JsonNode values = sheetsValuesResponse == null ? null : sheetsValuesResponse.path("values");
        if (values == null || !values.isArray()) return rows;
        for (JsonNode r : values) {
            List<String> row = new ArrayList<>();
            for (JsonNode c : r) row.add(c.isNull() ? "" : c.asText());
            rows.add(row);
        }
        return rows;
    }

    private int parseSectionAsFlatObjects(List<List<String>> rows, int startIndex, List<Map<String, Object>> outList) {
        if (startIndex >= rows.size()) return startIndex;
        List<String> header = rows.get(startIndex);
        int i = startIndex + 1;
        while (i < rows.size()) {
            List<String> row = rows.get(i);
            if (row == null || row.stream().allMatch(c -> c == null || c.trim().isEmpty())) break;
            String first = row.size() > 0 ? safeTrim(row.get(0)) : "";
            if (isSectionHeading(first)) break;
            Map<String, Object> obj = new LinkedHashMap<>();
            for (int c = 0; c < header.size(); c++) {
                String key = header.get(c) == null ? "col" + c : safeTrim(header.get(c));
                String val = c < row.size() ? row.get(c) : "";
                obj.put(key, toTypedValue(val));
            }
            outList.add(obj);
            i++;
        }
        return i;
    }

    private int parseSectionGroupedByProfile(List<List<String>> rows, int startIndex, List<Map<String, Object>> outList) {
        if (startIndex >= rows.size()) return startIndex;
        List<String> header = rows.get(startIndex);
        int i = startIndex + 1;
        Map<String, Map<String, Object>> profileMap = new LinkedHashMap<>();
        while (i < rows.size()) {
            List<String> row = rows.get(i);
            if (row == null || row.stream().allMatch(c -> c == null || c.trim().isEmpty())) break;
            String first = row.size() > 0 ? safeTrim(row.get(0)) : "";
            if (isSectionHeading(first)) break;

            Map<String, String> flat = new LinkedHashMap<>();
            for (int c = 0; c < header.size(); c++) {
                String key = header.get(c) == null ? "col" + c : safeTrim(header.get(c));
                String val = c < row.size() ? row.get(c) : "";
                flat.put(key, val == null ? "" : val);
            }

            String profileName = flat.containsKey("Profile") ? flat.get("Profile") : (flat.containsKey("profile") ? flat.get("profile") : "");
            if (profileName == null) profileName = "";
            Map<String, Object> profileObj = profileMap.computeIfAbsent(profileName, k -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("Profile", k);
                m.put("Items", new ArrayList<Map<String, Object>>());
                return m;
            });

            Map<String, Object> item = new LinkedHashMap<>();
            for (Map.Entry<String, String> e : flat.entrySet()) {
                String key = e.getKey();
                if ("Profile".equalsIgnoreCase(key)) continue;
                item.put(key, toTypedValue(e.getValue()));
            }
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> items = (List<Map<String, Object>>) profileObj.get("Items");
            items.add(item);
            i++;
        }

        for (Map<String, Object> v : profileMap.values()) outList.add(v);
        return i;
    }

    private boolean isSectionHeading(String firstCell) {
        if (firstCell == null) return false;
        String t = firstCell.trim().toLowerCase();
        return t.equals("attachmentprofiles") || t.equals("attachment profiles") || t.equals("loottables") || t.equals("loot tables") || t.equals("itemprofiles") || t.equals("item profiles");
    }

    private int findColumnIndex(List<String> header, String name) {
        if (header == null) return -1;
        for (int i = 0; i < header.size(); i++) {
            String v = header.get(i);
            if (v == null) continue;
            if (v.trim().equalsIgnoreCase(name)) return i;
            if (v.trim().toLowerCase().contains(name)) return i;
        }
        return -1;
    }

    private Object toTypedValue(String raw) {
        String s = safeTrim(raw);
        if (s.isEmpty()) return null;
        if (s.equalsIgnoreCase("true") || s.equalsIgnoreCase("false")) return Boolean.parseBoolean(s);
        try {
            if (s.matches("-?\\d+")) {
                long l = Long.parseLong(s);
                if (l <= Integer.MAX_VALUE && l >= Integer.MIN_VALUE) return (int) l;
                return l;
            }
            if (s.matches("-?\\d*\\.\\d+")) {
                return Double.parseDouble(s);
            }
        } catch (NumberFormatException ex) {
            // fall through
        }
        return s;
    }

    private String safeTrim(String s) {
        return s == null ? "" : s.trim();
    }
}
