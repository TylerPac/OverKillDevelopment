package dev.tylerpac.backend.dayz.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import dev.tylerpac.backend.dayz.dto.SkinResponses.SkillQueryResponse;
import dev.tylerpac.backend.dayz.dto.SkinResponses.XpBatchResponse;
import dev.tylerpac.backend.dayz.dto.SkinResponses.XpDto;
import dev.tylerpac.backend.dayz.dto.XpBatchRequest;
import dev.tylerpac.backend.dayz.repo.SkillXpRepository;

@Service
@ConditionalOnProperty(name = "app.dayz.enabled", havingValue = "true")
public class DayzSkillService {

    private static final Pattern CATEGORY = Pattern.compile("^[A-Za-z0-9_]{1,32}$");
    private static final int MAX_DELTA = 1_000_000;

    private final SkillXpRepository skillXpRepository;

    public DayzSkillService(SkillXpRepository skillXpRepository) {
        this.skillXpRepository = skillXpRepository;
    }

    /** Validates the whole batch first so a bad entry never leaves a half-applied update. */
    public XpBatchResponse addXpBatch(List<XpBatchRequest.Entry> entries) {
        for (XpBatchRequest.Entry entry : entries) {
            DayzPlayerService.parseSteamId(entry.getSteamId());
            for (Map.Entry<String, Integer> delta : entry.getDeltas().entrySet()) {
                if (!CATEGORY.matcher(delta.getKey()).matches()) {
                    throw new DayzApiException(HttpStatus.BAD_REQUEST, "invalid_category");
                }
                Integer value = delta.getValue();
                if (value == null || value < 1 || value > MAX_DELTA) {
                    throw new DayzApiException(HttpStatus.BAD_REQUEST, "invalid_xp_delta");
                }
            }
        }

        LocalDateTime now = DayzPlayerService.utcNow();
        int updated = 0;
        int skipped = 0;
        for (XpBatchRequest.Entry entry : entries) {
            long steamId = Long.parseLong(entry.getSteamId());
            boolean applied = false;
            for (Map.Entry<String, Integer> delta : entry.getDeltas().entrySet()) {
                if (skillXpRepository.addXp(steamId, delta.getKey(), delta.getValue(), now) > 0) {
                    applied = true;
                }
            }
            if (applied) {
                updated++;
            } else {
                skipped++;
            }
        }
        return new XpBatchResponse(true, updated, skipped);
    }

    public SkillQueryResponse query(String steamId) {
        long id = DayzPlayerService.parseSteamId(steamId);
        List<XpDto> xp = new ArrayList<>();
        for (Map.Entry<String, Long> e : skillXpRepository.findXp(id).entrySet()) {
            xp.add(new XpDto(e.getKey(), e.getValue()));
        }
        return new SkillQueryResponse(xp);
    }
}
