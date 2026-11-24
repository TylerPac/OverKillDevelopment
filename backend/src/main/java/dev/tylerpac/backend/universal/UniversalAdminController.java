package dev.tylerpac.backend.universal;

import java.security.Principal;
import java.util.Map;
import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import dev.tylerpac.backend.universal.UniversalApiClient.UniversalApiException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

// The admin page's MapLink and Game servers sections: where each player's character is, the characters MapLink
// saved (any can be handed back), and the game servers allowed to use this community's Universal API. Every call
// checks the admin first, then forwards to the Universal API, which owns the logic and records the admin in its
// audit log.
@RestController
@RequestMapping("/shop/admin/universal")
public class UniversalAdminController {

    private static final Set<String> STATES = Set.of("NONE", "ALIVE", "TRANSIT", "DEAD");

    private final UniversalAdminSupport support;
    private final UniversalApiClient universalApi;

    public UniversalAdminController(UniversalAdminSupport support, UniversalApiClient universalApi) {
        this.support = support;
        this.universalApi = universalApi;
    }

    /** Which Universal API the pages talk to: its version, features, and the ApiUrl game servers use. */
    @GetMapping("/platform")
    public JsonNode platform(Principal principal) {
        support.requireAdmin(principal);
        return universalApi.info();
    }

    // ---- MapLink -----------------------------------------------------------------------------------------

    @GetMapping("/maplink/players")
    public JsonNode players(Principal principal, @RequestParam(required = false) String state) {
        support.requireAdmin(principal);
        String filter = state == null ? "" : state.trim();
        if (!filter.isEmpty() && !STATES.contains(filter)) {
            throw new UniversalApiException(HttpStatus.BAD_REQUEST, "invalid_state");
        }
        return universalApi.listMapLinkPlayers(filter);
    }

    @GetMapping("/maplink/players/{steamId}")
    public JsonNode player(Principal principal, @PathVariable String steamId) {
        support.requireAdmin(principal);
        return universalApi.getMapLinkPlayer(UniversalAdminSupport.requireSteamId(steamId));
    }

    /** The saved character's JSON exactly as the Universal API sent it. */
    @GetMapping("/maplink/players/{steamId}/states/{stateId}")
    public ResponseEntity<String> state(Principal principal, @PathVariable String steamId, @PathVariable long stateId) {
        support.requireAdmin(principal);
        String json = universalApi.getMapLinkState(UniversalAdminSupport.requireSteamId(steamId), stateId);
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(json);
    }

    /** Body: {"serverId": "..."}: the game server the player gets the character on. */
    @PostMapping("/maplink/players/{steamId}/states/{stateId}/restore")
    public JsonNode restore(Principal principal, @PathVariable String steamId, @PathVariable long stateId,
                            @RequestBody JsonNode body) {
        String admin = support.requireAdmin(principal);
        if (body == null || !body.path("serverId").isString()) {
            throw new UniversalApiException(HttpStatus.BAD_REQUEST, "server_required");
        }
        String serverId = UniversalAdminSupport.requireServerId(body.path("serverId").asString());
        return universalApi.restoreMapLinkState(admin, UniversalAdminSupport.requireSteamId(steamId), stateId, serverId);
    }

    // ---- Game servers ------------------------------------------------------------------------------------

    @GetMapping("/servers")
    public JsonNode servers(Principal principal) {
        support.requireAdmin(principal);
        return universalApi.listServers();
    }

    /**
     * Body: {"serverId": "...", "apiKey": "..."} (apiKey optional: blank = the Universal API generates one). The
     * response carries the key once; only its hash is stored, so it can never be shown again.
     */
    @PostMapping("/servers")
    @ResponseStatus(HttpStatus.CREATED)
    public JsonNode register(Principal principal, @RequestBody JsonNode body) {
        String admin = support.requireAdmin(principal);
        if (body == null || !body.isObject() || !body.path("serverId").isString()) {
            throw new UniversalApiException(HttpStatus.BAD_REQUEST, "invalid_server_id");
        }
        ObjectNode forward = JsonNodeFactory.instance.objectNode();
        forward.put("serverId", UniversalAdminSupport.requireServerId(body.path("serverId").asString()));
        JsonNode apiKey = body.path("apiKey");
        if (apiKey.isString() && !apiKey.asString().isBlank()) {
            forward.put("apiKey", apiKey.asString());
        }
        return universalApi.registerServer(admin, forward);
    }

    /** Body: {"enabled": true|false}. */
    @PatchMapping("/servers/{serverId}")
    public JsonNode setEnabled(Principal principal, @PathVariable String serverId, @RequestBody JsonNode body) {
        String admin = support.requireAdmin(principal);
        if (body == null || !body.path("enabled").isBoolean()) {
            throw new UniversalApiException(HttpStatus.BAD_REQUEST, "invalid_enabled");
        }
        ObjectNode forward = JsonNodeFactory.instance.objectNode();
        forward.put("enabled", body.path("enabled").booleanValue());
        return universalApi.setServerEnabled(admin, UniversalAdminSupport.requireServerId(serverId), forward);
    }

    // ---- errors ------------------------------------------------------------------------------------------

    // "error" matches the other admin sections; "message" is what the frontend's callJson shows.
    @ExceptionHandler(UniversalApiException.class)
    ResponseEntity<Map<String, String>> universalError(UniversalApiException e) {
        return ResponseEntity.status(e.status()).body(Map.of("error", e.code(), "message", e.code()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<Map<String, String>> refused(IllegalArgumentException e) {
        boolean signedOut = "unauthorized".equals(e.getMessage());
        String code = signedOut ? "unauthorized" : "forbidden";
        return ResponseEntity.status(signedOut ? HttpStatus.UNAUTHORIZED : HttpStatus.FORBIDDEN)
            .body(Map.of("error", code, "message", code));
    }
}
