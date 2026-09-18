package dev.tylerpac.backend.dayz.dto;

import java.util.List;
import java.util.Map;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class XpBatchRequest extends DayzAuthenticatedRequest {

    @NotEmpty(message = "entries_required")
    @Size(max = 100, message = "too_many_entries")
    @Valid
    private List<Entry> entries;

    public List<Entry> getEntries() {
        return entries;
    }

    public void setEntries(List<Entry> entries) {
        this.entries = entries;
    }

    public static class Entry {

        @NotBlank(message = "invalid_steam_id")
        @Pattern(regexp = "^\\d{17}$", message = "invalid_steam_id")
        private String steamId;

        /** category -> XP to add (validated in the service: 1..1_000_000, category name pattern). */
        @NotEmpty(message = "deltas_required")
        @Size(max = 16, message = "too_many_categories")
        private Map<String, Integer> deltas;

        public String getSteamId() {
            return steamId;
        }

        public void setSteamId(String steamId) {
            this.steamId = steamId;
        }

        public Map<String, Integer> getDeltas() {
            return deltas;
        }

        public void setDeltas(Map<String, Integer> deltas) {
            this.deltas = deltas;
        }
    }
}
