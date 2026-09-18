package dev.tylerpac.backend.dayz.config;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import dev.tylerpac.backend.dayz.repo.DayzServerRepository;
import dev.tylerpac.backend.dayz.service.DayzServerAuthService;

/** Seeds the first DayZ server credential from the k8s secret. An existing row is never overwritten. */
@Component
@ConditionalOnProperty(name = "app.dayz.enabled", havingValue = "true")
public class DayzBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DayzBootstrap.class);

    private final DayzServerRepository serverRepository;
    private final String bootstrapServerId;
    private final String bootstrapApiKey;

    public DayzBootstrap(
        DayzServerRepository serverRepository,
        @Value("${app.dayz.bootstrap.server-id:}") String bootstrapServerId,
        @Value("${app.dayz.bootstrap.api-key:}") String bootstrapApiKey
    ) {
        this.serverRepository = serverRepository;
        this.bootstrapServerId = bootstrapServerId;
        this.bootstrapApiKey = bootstrapApiKey;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!StringUtils.hasText(bootstrapServerId) || !StringUtils.hasText(bootstrapApiKey)) {
            return;
        }
        if (serverRepository.findByServerId(bootstrapServerId).isPresent()) {
            return;
        }
        serverRepository.insert(
            bootstrapServerId,
            DayzServerAuthService.sha256Hex(bootstrapApiKey),
            LocalDateTime.now(ZoneOffset.UTC));
        log.info("Seeded DayZ server credential for serverId={}", bootstrapServerId);
    }
}
