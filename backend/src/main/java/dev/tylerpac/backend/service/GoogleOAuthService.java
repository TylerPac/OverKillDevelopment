package dev.tylerpac.backend.service;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import dev.tylerpac.backend.util.JsonUtils;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

@Service
public class GoogleOAuthService {

    private static final Logger logger = LoggerFactory.getLogger(GoogleOAuthService.class);

    private static final String AUTH_URL = "https://accounts.google.com/o/oauth2/v2/auth";
    private static final String TOKEN_URL = "https://oauth2.googleapis.com/token";
    private static final String DRIVE_COPY_URL_TEMPLATE = "https://www.googleapis.com/drive/v3/files/%s/copy";
    private static final String SHEETS_VALUES_URL_TEMPLATE = "https://sheets.googleapis.com/v4/spreadsheets/%s/values/%s";
    private static final String SHEETS_METADATA_URL_TEMPLATE = "https://sheets.googleapis.com/v4/spreadsheets/%s?fields=spreadsheetId,properties.title,sheets.properties";

    private final String clientId;
    private final String clientSecret;
    private final String redirectUri;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public GoogleOAuthService(
        @Value("${GOOGLE_CLIENT_ID:}") String clientId,
        @Value("${GOOGLE_CLIENT_SECRET:}") String clientSecret,
        @Value("${GOOGLE_REDIRECT_URI:http://localhost:8080/api/google/callback}") String redirectUri,
        ObjectMapper objectMapper
    ) {
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.redirectUri = redirectUri;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();
    }

    public String buildLoginUrl(String state) {
        Map<String, String> q = new LinkedHashMap<>();
        q.put("client_id", clientId);
        q.put("redirect_uri", redirectUri);
        q.put("response_type", "code");
        // Use least-privilege Drive scope: access to files created or opened by the app
        q.put("scope", "https://www.googleapis.com/auth/spreadsheets https://www.googleapis.com/auth/drive");
        q.put("access_type", "offline");
        q.put("include_granted_scopes", "true");
        q.put("state", state);
        q.put("prompt", "consent");
        return AUTH_URL + "?" + encodeForm(q);
    }

    public Tokens exchangeCodeForTokens(String code) {
        Map<String, String> form = new LinkedHashMap<>();
        form.put("code", code);
        form.put("client_id", clientId);
        form.put("client_secret", clientSecret);
        form.put("redirect_uri", redirectUri);
        form.put("grant_type", "authorization_code");

        HttpRequest req = HttpRequest.newBuilder()
            .uri(URI.create(TOKEN_URL))
            .timeout(Duration.ofSeconds(15))
            .header("Content-Type", "application/x-www-form-urlencoded")
            .POST(HttpRequest.BodyPublishers.ofString(encodeForm(form)))
            .build();

        try {
            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 200) {
                throw new IllegalArgumentException("google_token_exchange_failed");
            }
            JsonNode json = objectMapper.readTree(resp.body());
            String accessToken = JsonUtils.textOrNull(json.path("access_token"));
            if (accessToken == null) accessToken = "";
            String refreshToken = JsonUtils.textOrNull(json.path("refresh_token"));
            if (refreshToken == null) refreshToken = "";
            long expiresIn = json.path("expires_in").asLong(0L);
            return new Tokens(accessToken, refreshToken, expiresIn);
        } catch (IOException | InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("google_token_exchange_failed", e);
        }
    }

    public String accessTokenFromRefreshToken(String refreshToken) {
        Map<String, String> form = new LinkedHashMap<>();
        form.put("refresh_token", refreshToken);
        form.put("client_id", clientId);
        form.put("client_secret", clientSecret);
        form.put("grant_type", "refresh_token");

        HttpRequest req = HttpRequest.newBuilder()
            .uri(URI.create(TOKEN_URL))
            .timeout(Duration.ofSeconds(15))
            .header("Content-Type", "application/x-www-form-urlencoded")
            .POST(HttpRequest.BodyPublishers.ofString(encodeForm(form)))
            .build();

        try {
            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 200) {
                throw new IllegalArgumentException("google_refresh_failed");
            }
            JsonNode json = objectMapper.readTree(resp.body());
            String at = JsonUtils.textOrNull(json.path("access_token"));
            return at == null ? "" : at;
        } catch (IOException | InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("google_refresh_failed", e);
        }
    }

    public JsonNode fetchSheetValues(String accessToken, String spreadsheetId, String range) {
        String url = String.format(SHEETS_VALUES_URL_TEMPLATE, urlEncode(spreadsheetId), urlEncode(range));
        HttpRequest req = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .timeout(Duration.ofSeconds(20))
            .header("Authorization", "Bearer " + accessToken)
            .GET()
            .build();

        try {
            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 200) {
                throw new IllegalArgumentException("sheets_fetch_failed");
            }
            return objectMapper.readTree(resp.body());
        } catch (IOException | InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("sheets_fetch_failed", e);
        }
    }

    /**
     * Fetch sheet values using a public API key. Useful for publicly-shared templates
     * where no per-user OAuth is required.
     */
    public JsonNode fetchSheetValuesWithApiKey(String apiKey, String spreadsheetId, String range) {
        String url = String.format(SHEETS_VALUES_URL_TEMPLATE, urlEncode(spreadsheetId), urlEncode(range))
            + "?key=" + urlEncode(apiKey);
        HttpRequest req = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .timeout(Duration.ofSeconds(20))
            .GET()
            .build();

        try {
            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 200) {
                throw new IllegalArgumentException("sheets_fetch_failed");
            }
            return objectMapper.readTree(resp.body());
        } catch (IOException | InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("sheets_fetch_failed", e);
        }
    }

    public JsonNode fetchSpreadsheetMetadata(String accessToken, String spreadsheetId) {
        String url = String.format(SHEETS_METADATA_URL_TEMPLATE, urlEncode(spreadsheetId));
        HttpRequest req = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .timeout(Duration.ofSeconds(20))
            .header("Authorization", "Bearer " + accessToken)
            .GET()
            .build();

        try {
            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 200) {
                throw new IllegalArgumentException("sheets_meta_failed");
            }
            return objectMapper.readTree(resp.body());
        } catch (IOException | InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("sheets_meta_failed", e);
        }
    }

    public JsonNode copyFileAs(String accessToken, String fileId, String newName) {
        String url = String.format(DRIVE_COPY_URL_TEMPLATE, urlEncode(fileId));
        String body = "{\"name\":\"" + escapeJson(newName) + "\"}";
        HttpRequest req = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .timeout(Duration.ofSeconds(20))
            .header("Authorization", "Bearer " + accessToken)
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build();

        try {
            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            int status = resp.statusCode();
            String respBody = resp.body() == null ? "" : resp.body();
            if (status != 200 && status != 201) {
                String msg = "drive_copy_failed: status=" + status + " body=" + respBody;
                throw new IllegalArgumentException(msg);
            }
            try {
                return objectMapper.readTree(respBody);
            } catch (JacksonException e) {
                throw new IllegalStateException("drive_copy_failed: invalid_json: " + respBody, e);
            }
        } catch (IOException | InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("drive_copy_failed", e);
        }
    }

    /**
     * Copy a spreadsheet using the Sheets API sheets.copyTo endpoint, which preserves
     * ALL formatting (colors, merged cells, fonts, etc.). Works with the 'spreadsheets'
     * scope as long as the source is accessible (e.g. publicly shared).
     * Returns Sheets metadata for the newly created spreadsheet.
     */
    public JsonNode copySpreadsheetWithFormatting(String accessToken, String templateSpreadsheetId, String newName) {
        // 1. Fetch template metadata to get sheetId + title for each sheet
        JsonNode templateMeta = fetchSpreadsheetMetadata(accessToken, templateSpreadsheetId);
        JsonNode sourceSheets = templateMeta.path("sheets");
        if (!sourceSheets.isArray() || sourceSheets.isEmpty()) {
            throw new IllegalStateException("copy_failed: template has no sheets or is not accessible");
        }

        // 2. Create blank destination spreadsheet
        ObjectNode createBody = objectMapper.createObjectNode();
        createBody.putObject("properties").put("title", newName == null ? "Copy" : newName);

        HttpRequest createReq = HttpRequest.newBuilder()
            .uri(URI.create("https://sheets.googleapis.com/v4/spreadsheets"))
            .timeout(Duration.ofSeconds(20))
            .header("Authorization", "Bearer " + accessToken)
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(createBody.toString()))
            .build();

        try {
            HttpResponse<String> createResp = httpClient.send(createReq, HttpResponse.BodyHandlers.ofString());
            if (createResp.statusCode() != 200 && createResp.statusCode() != 201) {
                throw new IllegalArgumentException("sheets_create_failed: status=" + createResp.statusCode() + " body=" + createResp.body());
            }
            JsonNode created = objectMapper.readTree(createResp.body());
            String destId = JsonUtils.textOrNull(created.path("spreadsheetId"));
            if (destId == null || destId.isEmpty()) throw new IllegalStateException("sheets_create_failed: missing_id");

            // Get the auto-created default sheet's sheetId so we can delete it later
            long defaultSheetId = -1;
            JsonNode destSheets = created.path("sheets");
            if (destSheets.isArray() && !destSheets.isEmpty()) {
                defaultSheetId = destSheets.get(0).path("properties").path("sheetId").asLong(-1);
            }

            // 3. For each source sheet, call sheets.copyTo — preserves formatting
            List<Long> copiedSheetIds = new ArrayList<>();
            List<String> originalTitles = new ArrayList<>();

            for (JsonNode sheet : sourceSheets) {
                long srcSheetId = sheet.path("properties").path("sheetId").asLong(-1);
                String title = JsonUtils.textOrNull(sheet.path("properties").path("title"));
                if (title == null) title = "";
                if (srcSheetId < 0 || title.isEmpty()) continue;

                String copyToUrl = "https://sheets.googleapis.com/v4/spreadsheets/"
                    + urlEncode(templateSpreadsheetId) + "/sheets/" + srcSheetId + ":copyTo";
                String copyToBody = "{\"destinationSpreadsheetId\":\"" + escapeJson(destId) + "\"}";

                HttpRequest copyToReq = HttpRequest.newBuilder()
                    .uri(URI.create(copyToUrl))
                    .timeout(Duration.ofSeconds(30))
                    .header("Authorization", "Bearer " + accessToken)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(copyToBody))
                    .build();

                HttpResponse<String> copyToResp = httpClient.send(copyToReq, HttpResponse.BodyHandlers.ofString());
                if (copyToResp.statusCode() != 200 && copyToResp.statusCode() != 201) {
                    logger.warn("sheets.copyTo failed for sheet '{}' (id {}): status={} body={}",
                        title, srcSheetId, copyToResp.statusCode(), copyToResp.body());
                    continue;
                }
                JsonNode copyToResult = objectMapper.readTree(copyToResp.body());
                long newSheetId = copyToResult.path("sheetId").asLong(-1);
                if (newSheetId >= 0) {
                    copiedSheetIds.add(newSheetId);
                    originalTitles.add(title);
                    logger.info("Copied sheet '{}' with full formatting into dest={}", title, destId);
                }
            }

            if (copiedSheetIds.isEmpty()) {
                throw new IllegalStateException("copy_failed: no sheets were copied from template");
            }

            // 4. batchUpdate: rename copied sheets back to originals, delete default sheet
            ArrayNode requests = objectMapper.createArrayNode();

            for (int i = 0; i < copiedSheetIds.size(); i++) {
                ObjectNode updateReq = objectMapper.createObjectNode();
                ObjectNode updateSheet = updateReq.putObject("updateSheetProperties");
                ObjectNode props = updateSheet.putObject("properties");
                props.put("sheetId", copiedSheetIds.get(i));
                props.put("title", originalTitles.get(i));
                updateSheet.put("fields", "title");
                requests.add(updateReq);
            }

            if (defaultSheetId >= 0) {
                ObjectNode deleteReq = objectMapper.createObjectNode();
                deleteReq.putObject("deleteSheet").put("sheetId", defaultSheetId);
                requests.add(deleteReq);
            }

            ObjectNode batchBody = objectMapper.createObjectNode();
            batchBody.set("requests", requests);
            String batchUrl = "https://sheets.googleapis.com/v4/spreadsheets/" + urlEncode(destId) + ":batchUpdate";
            HttpRequest batchReq = HttpRequest.newBuilder()
                .uri(URI.create(batchUrl))
                .timeout(Duration.ofSeconds(20))
                .header("Authorization", "Bearer " + accessToken)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(batchBody.toString()))
                .build();
            HttpResponse<String> batchResp = httpClient.send(batchReq, HttpResponse.BodyHandlers.ofString());
            if (batchResp.statusCode() != 200) {
                logger.warn("batchUpdate rename/delete failed: status={} body={}", batchResp.statusCode(), batchResp.body());
            }

            // Return up-to-date metadata for the new spreadsheet
            return fetchSpreadsheetMetadata(accessToken, destId);

        } catch (IOException | InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("sheets_copy_failed", e);
        }
    }

    /**
     * Create a new spreadsheet for the user by copying sheet titles and values
     * from the template spreadsheet. Returns the created spreadsheet's metadata.
     */
    public JsonNode createSpreadsheetFromTemplate(String accessToken, String templateSpreadsheetId, String newName) {
        // Fetch template metadata to obtain sheet titles
        JsonNode meta = fetchSpreadsheetMetadata(accessToken, templateSpreadsheetId);
        ArrayNode sheetTitles = objectMapper.createArrayNode();
        JsonNode sheets = meta.path("sheets");
        if (sheets.isArray()) {
            for (JsonNode s : sheets) {
                String title = JsonUtils.textOrNull(s.path("properties").path("title"));
                if (title != null && !title.isEmpty()) sheetTitles.add(title);
            }
        }

        // Build create spreadsheet request with sheet titles
        ObjectNode createBody = objectMapper.createObjectNode();
        createBody.putObject("properties").put("title", newName == null ? "Copy" : newName);
        ArrayNode sheetsArray = createBody.putArray("sheets");
        if (sheetTitles.size() == 0) {
            // Ensure at least a single sheet
            ObjectNode s = objectMapper.createObjectNode();
            s.putObject("properties").put("title", "Sheet1");
            sheetsArray.add(s);
        } else {
            for (JsonNode t : sheetTitles) {
                String tTitle = JsonUtils.textOrNull(t);
                if (tTitle == null) tTitle = "";
                ObjectNode s = objectMapper.createObjectNode();
                s.putObject("properties").put("title", tTitle);
                sheetsArray.add(s);
            }
        }

        String createUrl = "https://sheets.googleapis.com/v4/spreadsheets";
        HttpRequest createReq = HttpRequest.newBuilder()
            .uri(URI.create(createUrl))
            .timeout(Duration.ofSeconds(20))
            .header("Authorization", "Bearer " + accessToken)
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(createBody.toString()))
            .build();

        try {
            HttpResponse<String> createResp = httpClient.send(createReq, HttpResponse.BodyHandlers.ofString());
            int status = createResp.statusCode();
            String body = createResp.body() == null ? "" : createResp.body();
            if (status != 200 && status != 201) {
                throw new IllegalArgumentException("sheets_create_failed: status=" + status + " body=" + body);
            }
            JsonNode created = objectMapper.readTree(body);
            String newId = JsonUtils.textOrNull(created.path("spreadsheetId"));
            if (newId == null || newId.isEmpty()) throw new IllegalStateException("sheets_create_failed: missing_id");

            // Prepare batchUpdate data by copying values from each template sheet
            ArrayNode dataArray = objectMapper.createArrayNode();
            if (sheetTitles.size() > 0) {
                String apiKey = System.getenv("GOOGLE_API_KEY");
                for (JsonNode t : sheetTitles) {
                    String title = JsonUtils.textOrNull(t);
                    if (title == null) title = "";
                    JsonNode valuesResp = null;
                    // Try OAuth fetch first (works when the token has file access)
                    try {
                        valuesResp = fetchSheetValues(accessToken, templateSpreadsheetId, title + "!A:Z");
                    } catch (Exception ex) {
                        logger.warn("Failed to fetch values via OAuth for sheet '{}': {}", title, ex.getMessage());
                        // Try API key if available
                        if (apiKey != null && !apiKey.isEmpty()) {
                            try {
                                valuesResp = fetchSheetValuesWithApiKey(apiKey, templateSpreadsheetId, title + "!A:Z");
                                logger.info("Fetched values for sheet '{}' via API key", title);
                            } catch (Exception ex2) {
                                logger.warn("Failed to fetch values via API key for sheet '{}': {}", title, ex2.getMessage());
                            }
                        }
                    }
                    // If OAuth/API-key returned nothing or empty values, always try public CSV as last resort
                    JsonNode earlyCheck = valuesResp != null ? valuesResp.path("values") : null;
                    if (earlyCheck == null || !earlyCheck.isArray() || earlyCheck.isEmpty()) {
                        try {
                            JsonNode csvResp = fetchPublicSheetCsvAsValues(templateSpreadsheetId, title);
                            if (csvResp != null && csvResp.path("values").isArray() && !csvResp.path("values").isEmpty()) {
                                valuesResp = csvResp;
                                logger.info("Fetched values for sheet '{}' via public CSV export", title);
                            }
                        } catch (Exception ex3) {
                            logger.warn("Failed to fetch values via public CSV for sheet '{}': {}", title, ex3.getMessage());
                        }
                    }

                    if (valuesResp != null) {
                        JsonNode values = valuesResp.path("values");
                        if (values != null && values.isArray() && values.size() > 0) {
                            ObjectNode item = objectMapper.createObjectNode();
                            item.put("range", title + "!A1");
                            item.set("values", values);
                            dataArray.add(item);
                        } else {
                            logger.debug("No values found for sheet '{}'", title);
                        }
                    } else {
                        logger.debug("No values response for sheet '{}'", title);
                    }
                }
            }

            if (dataArray.size() > 0) {
                ObjectNode batchBody = objectMapper.createObjectNode();
                batchBody.put("valueInputOption", "USER_ENTERED");
                batchBody.set("data", dataArray);
                String batchUrl = String.format("https://sheets.googleapis.com/v4/spreadsheets/%s/values:batchUpdate", urlEncode(newId));
                HttpRequest batchReq = HttpRequest.newBuilder()
                    .uri(URI.create(batchUrl))
                    .timeout(Duration.ofSeconds(20))
                    .header("Authorization", "Bearer " + accessToken)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(batchBody.toString()))
                    .build();

                HttpResponse<String> batchResp = httpClient.send(batchReq, HttpResponse.BodyHandlers.ofString());
                int bstatus = batchResp.statusCode();
                String bbody = batchResp.body() == null ? "" : batchResp.body();
                if (bstatus != 200) {
                    throw new IllegalArgumentException("sheets_batch_update_failed: status=" + bstatus + " body=" + bbody);
                }
            } else {
                logger.warn("No sheet values were available to copy from template {}. Created spreadsheet {} will have empty sheets. Consider sharing the template, making it public, or using Drive copy (drive.files.copy) which preserves formatting if available.", templateSpreadsheetId, newId);
            }

            return created;
        } catch (IOException | InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("sheets_create_failed", e);
        }
    }

    private String encodeForm(Map<String, String> values) {
        StringBuilder b = new StringBuilder();
        boolean first = true;
        for (Map.Entry<String, String> e : values.entrySet()) {
            if (!first) b.append('&');
            first = false;
            b.append(URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8));
            b.append('=');
            b.append(URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8));
        }
        return b.toString();
    }

    private String urlEncode(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    private String escapeJson(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    /**
     * Fetch a publicly-shared sheet as CSV from the docs.google.com export endpoint
     * and convert it into a Sheets "values" shaped JsonNode: { "values": [ [..], ... ] }
     */
    private JsonNode fetchPublicSheetCsvAsValues(String spreadsheetId, String sheetTitle) {
        try {
            String url = "https://docs.google.com/spreadsheets/d/" + urlEncode(spreadsheetId)
                + "/gviz/tq?tqx=out:csv";
            if (sheetTitle != null && !sheetTitle.isBlank()) url += "&sheet=" + urlEncode(sheetTitle);
            HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(20))
                .GET()
                .build();

            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 200) return null;
            String body = resp.body() == null ? "" : resp.body();
            ArrayNode values = parseCsvToArrayNode(body);
            ObjectNode out = objectMapper.createObjectNode();
            out.set("values", values);
            return out;
        } catch (IOException | InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("public_csv_fetch_failed", ex);
        }
    }

    private ArrayNode parseCsvToArrayNode(String csv) {
        ArrayNode rows = objectMapper.createArrayNode();
        if (csv == null || csv.isEmpty()) return rows;
        String[] lines = csv.split("\r?\n", -1);
        for (String line : lines) {
            ArrayNode row = objectMapper.createArrayNode();
            StringBuilder cell = new StringBuilder();
            boolean inQuotes = false;
            for (int i = 0; i < line.length(); i++) {
                char ch = line.charAt(i);
                if (ch == '"') {
                    if (inQuotes && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                        cell.append('"');
                        i++; // skip escaped quote
                    } else {
                        inQuotes = !inQuotes;
                    }
                } else if (ch == ',' && !inQuotes) {
                    row.add(cell.toString());
                    cell.setLength(0);
                } else {
                    cell.append(ch);
                }
            }
            // add last cell
            row.add(cell.toString());
            rows.add(row);
        }
        return rows;
    }

    // ── Position-aware tab sync (preserves cell formatting) ────────────────────

    /** Reads the current values of a sheet tab into a mutable 2-D string list. */
    private List<List<String>> getTabValuesList(String accessToken, String spreadsheetId, String tabName) throws IOException, InterruptedException {
        String url = "https://sheets.googleapis.com/v4/spreadsheets/"
            + urlEncode(spreadsheetId) + "/values/" + urlEncode(tabName);
        HttpRequest req = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .timeout(Duration.ofSeconds(20))
            .header("Authorization", "Bearer " + accessToken)
            .GET()
            .build();
        HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() != 200) return new ArrayList<>();
        JsonNode root = objectMapper.readTree(resp.body());
        List<List<String>> rows = new ArrayList<>();
        JsonNode valNode = root.path("values");
        if (valNode.isArray()) {
            for (JsonNode rowNode : valNode) {
                List<String> row = new ArrayList<>();
                if (rowNode.isArray()) {
                    for (JsonNode cell : rowNode) row.add(JsonUtils.textOrEmpty(cell));
                }
                rows.add(row);
            }
        }
        return rows;
    }

    /** Converts a 0-based column index to a spreadsheet column letter (A, B, … Z, AA, …). */
    private String colToLetter(int col) {
        StringBuilder sb = new StringBuilder();
        col++;
        while (col > 0) {
            int rem = (col - 1) % 26;
            sb.insert(0, (char) ('A' + rem));
            col = (col - 1) / 26;
        }
        return sb.toString();
    }

    private String normHdr(String s) {
        if (s == null) return "";
        return s.trim().toLowerCase().replaceAll("[^a-z0-9]+", "");
    }

    private String getCellSafe(List<List<String>> rows, int r, int c) {
        if (r < 0 || r >= rows.size()) return "";
        List<String> row = rows.get(r);
        if (row == null || c < 0 || c >= row.size()) return "";
        String v = row.get(c);
        return v == null ? "" : v;
    }

    /** Executes a Sheets values.batchUpdate call with multiple range-value pairs. */
    private void batchUpdateValues(String accessToken, String spreadsheetId,
                                   List<Map<String, Object>> data) throws IOException, InterruptedException {
        if (data.isEmpty()) return;
        ObjectNode body = objectMapper.createObjectNode();
        body.put("valueInputOption", "USER_ENTERED");
        body.set("data", objectMapper.valueToTree(data));
        String url = "https://sheets.googleapis.com/v4/spreadsheets/"
            + urlEncode(spreadsheetId) + "/values:batchUpdate";
        HttpRequest req = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .timeout(Duration.ofSeconds(30))
            .header("Authorization", "Bearer " + accessToken)
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
            .build();
        HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() != 200) {
            throw new IllegalArgumentException("batchUpdate_failed: status=" + resp.statusCode()
                + " body=" + resp.body());
        }
    }

    private void addSingleCell(List<Map<String, Object>> batch, String tabName,
                               int col, int row0Based, String value) {
        Map<String, Object> d = new LinkedHashMap<>();
        d.put("range", tabName + "!" + colToLetter(col) + (row0Based + 1));
        d.put("majorDimension", "ROWS");
        d.put("values", List.of(List.of(value)));
        batch.add(d);
    }

    private String fmtDouble(double v) {
        if (v == Math.floor(v) && !Double.isInfinite(v)) return String.valueOf((long) v);
        return String.valueOf(v);
    }

    /**
     * Syncs LootTables data to the sheet while preserving cell formatting.
     * Discovers block anchor positions ("Profile" header cells), then writes incoming
     * tables positionally (1st incoming → 1st block, etc.) regardless of name match.
     * Also writes the table name and min/max spawned values into their respective cells.
     * No values.clear is performed — formatting (merged cells, colors, fonts) is untouched.
     */
    public void syncLootTablesPreservingFormat(String accessToken, String spreadsheetId,
                                               String tabName, JsonNode incomingTables) {
        try {
            List<List<String>> current = getTabValuesList(accessToken, spreadsheetId, tabName);
            if (current.isEmpty()) return;

            int maxCols = 0;
            for (List<String> r : current) if (r.size() > maxCols) maxCols = r.size();
            for (List<String> r : current) { while (r.size() < maxCols) r.add(""); }

            List<JsonNode> incoming = new ArrayList<>();
            if (incomingTables.isArray()) {
                for (JsonNode t : incomingTables) incoming.add(t);
            }
            logger.info("[sync-loot] tab='{}' currentRows={} incomingTables={}",
                tabName, current.size(), incoming.size());
            if (incoming.isEmpty()) return;

            // Detect loot table block start columns from row 0 (table name row).
            // The template layout is:
            //   row 0: <TableName>  (at startCol, e.g. col 1, 7, 13, ...)
            //   row 1: "Min" <minSpawnedValue>  (at startCol, startCol+1)
            //   row 2: "Max" <maxSpawnedValue>  (at startCol, startCol+1)
            //   row 3: "" "Min" "Max" "Chance"  (column headers for data)
            //   row 4: "Profile"                (profile label)
            //   row 5+: data rows
            // Row 0 non-empty cells that aren't structural headers = block start columns.
            Set<String> knownHdrs = Set.of(
                "profile", "min", "max", "chance", "tospawn", "percenttospawn",
                "minspawned", "maxspawned", "loot", "loottable"
            );
            List<Integer> blockStarts = new ArrayList<>();
            List<String> row0 = current.get(0);
            for (int c = 0; c < row0.size(); c++) {
                String val = row0.get(c).trim();
                if (val.isEmpty() || knownHdrs.contains(normHdr(val))) continue;
                blockStarts.add(c);
            }
            logger.info("[sync-loot] tab='{}' blocks found={} row0Names={}",
                tabName, blockStarts.size(),
                blockStarts.stream().map(c -> c + ":" + row0.get(c)).toList());

            if (blockStarts.isEmpty()) {
                logger.warn("[sync-loot] tab='{}' no table name blocks found in row 0", tabName);
                return;
            }

            List<Map<String, Object>> batchData = new ArrayList<>();
            int tableCount = Math.min(incoming.size(), blockStarts.size());

            for (int bi = 0; bi < blockStarts.size(); bi++) {
                int startCol = blockStarts.get(bi);

                if (bi >= tableCount) {
                    // Extra template block with no incoming table: clear table name cell
                    addSingleCell(batchData, tabName, startCol, 0, "");
                    continue;
                }

                JsonNode tableNode = incoming.get(bi);
                String tableName  = JsonUtils.textOrEmpty(tableNode.path("TableName")).trim();
                int minSpawned    = tableNode.path("MinProfileSpawned").asInt(1);
                int maxSpawned    = tableNode.path("MaxProfileSpawned").asInt(1);

                // Find the "Profile" anchor row by scanning down startCol (rows 1-14)
                int profileHdrRow = -1;
                for (int r = 1; r < Math.min(current.size(), 15); r++) {
                    if (normHdr(getCellSafe(current, r, startCol)).equals("profile")) {
                        profileHdrRow = r;
                        break;
                    }
                }
                if (profileHdrRow == -1) {
                    logger.warn("[sync-loot] tab='{}' block[{}] startCol={} — no 'Profile' header found, skipping",
                        tabName, bi, startCol);
                    continue;
                }
                int dataStartRow = profileHdrRow + 1;

                logger.info("[sync-loot] tab='{}' block[{}] tableName='{}' startCol={} profileHdrRow={} dataStartRow={}",
                    tabName, bi, tableName, startCol, profileHdrRow, dataStartRow);

                // Write table name to row 0
                if (!tableName.isEmpty()) {
                    addSingleCell(batchData, tabName, startCol, 0, tableName);
                }

                // Write MinProfileSpawned / MaxProfileSpawned:
                // scan rows 1..profileHdrRow-1 at startCol for "Min"/"Max" label cells
                for (int r = 1; r < profileHdrRow; r++) {
                    String nh = normHdr(getCellSafe(current, r, startCol));
                    if (nh.equals("min")) {
                        addSingleCell(batchData, tabName, startCol + 1, r, String.valueOf(minSpawned));
                    } else if (nh.equals("max")) {
                        addSingleCell(batchData, tabName, startCol + 1, r, String.valueOf(maxSpawned));
                    }
                }

                // Determine Min/Max/Chance column offsets from the column-header row
                // (exactly the row just above the "Profile" anchor row).
                // Limit scan to off < 6 to avoid picking up the next block's headers.
                int minOff = 1, maxOff = 2, chanceOff = 3;
                if (profileHdrRow > 0) {
                    for (int off = 1; off < 6; off++) {
                        String nh = normHdr(getCellSafe(current, profileHdrRow - 1, startCol + off));
                        if (nh.equals("min"))    minOff    = off;
                        if (nh.equals("max"))    maxOff    = off;
                        if (nh.equals("chance")) chanceOff = off;
                    }
                }
                int rowWidth = chanceOff + 1;

                // Calculate rows to overwrite / clear
                int lastDataRow = dataStartRow;
                for (int rr = dataStartRow; rr < Math.min(current.size(), dataStartRow + 200); rr++) {
                    if (!getCellSafe(current, rr, startCol).trim().isEmpty()) {
                        lastDataRow = rr;
                    } else if (rr > dataStartRow + 5 && lastDataRow < rr - 5) {
                        break;
                    }
                }
                JsonNode lootTable = tableNode.path("LootTable");
                int newCount   = lootTable.isArray() ? lootTable.size() : 0;
                int clearCount = Math.max(newCount + 3, lastDataRow - dataStartRow + 2);

                List<List<Object>> dataRows = new ArrayList<>();
                if (lootTable.isArray()) {
                    for (JsonNode entry : lootTable) {
                        List<Object> row = new ArrayList<>(Collections.nCopies(rowWidth, ""));
                        row.set(0,         JsonUtils.textOrEmpty(entry.path("Profile")));
                        row.set(minOff,    fmtDouble(entry.path("MinSpawn").asDouble(1)));
                        row.set(maxOff,    fmtDouble(entry.path("MaxLoot").asDouble(1)));
                        row.set(chanceOff, fmtDouble(entry.path("PercentToSpawn").asDouble(1.0)));
                        dataRows.add(row);
                    }
                }
                while (dataRows.size() < clearCount) {
                    dataRows.add(new ArrayList<>(Collections.nCopies(rowWidth, "")));
                }

                Map<String, Object> rangeData = new LinkedHashMap<>();
                rangeData.put("range", tabName + "!" + colToLetter(startCol) + (dataStartRow + 1)
                    + ":" + colToLetter(startCol + rowWidth - 1) + (dataStartRow + clearCount));
                rangeData.put("majorDimension", "ROWS");
                rangeData.put("values", dataRows);
                batchData.add(rangeData);
            }

            if (!batchData.isEmpty()) {
                logger.info("[sync-loot] tab='{}' batchUpdate ranges={}", tabName, batchData.size());
                batchUpdateValues(accessToken, spreadsheetId, batchData);
            } else {
                logger.warn("[sync-loot] tab='{}' batchData is empty — no blocks found in sheet", tabName);
            }
        } catch (IOException | InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("sync_loot_tables_failed: " + e.getMessage(), e);
        }
    }

    /**
     * Syncs ItemProfiles or AttachmentProfiles data while preserving cell formatting.
     * Discovers profile block positions from row 0 positionally (1st incoming → 1st block, etc.),
     * finds the "ItemName" / "AttachName" header row dynamically in each block column,
     * and writes item data at fixed column offsets matching the parser's layout.
     * Also writes incoming profile names into row 0.
     *
     * @param isItemProfiles {@code true} for ItemProfiles (includes Slot sub-rows),
     *                       {@code false} for AttachmentProfiles
     */
    public void syncColumnProfilesPreservingFormat(String accessToken, String spreadsheetId,
                                                   String tabName, JsonNode incomingProfiles,
                                                   boolean isItemProfiles) {
        try {
            List<List<String>> current = getTabValuesList(accessToken, spreadsheetId, tabName);
            if (current.isEmpty()) return;

            int maxCols = 0;
            for (List<String> r : current) if (r.size() > maxCols) maxCols = r.size();
            for (List<String> r : current) { while (r.size() < maxCols) r.add(""); }

            // Build ordered list of incoming profiles
            List<JsonNode> incoming = new ArrayList<>();
            if (incomingProfiles.isArray()) {
                for (JsonNode p : incomingProfiles) incoming.add(p);
            }
            logger.info("[sync-profiles] tab='{}' currentRows={} incomingProfiles={}",
                tabName, current.size(), incoming.size());
            if (incoming.isEmpty()) return;

            // Structural headers — cells in row 0 matching these are column headers, not profile names
            Set<String> knownHdrs = Set.of(
                "itemname", "item", "attachname", "spawnprob", "spawnpro",
                "quantitymin", "quantitymax", "quantmin", "quantmax",
                "healthmin", "healthmax", "profile", "attachprob",
                "attachquantitymin", "attachquantitymax", "attachhealthmin", "attachhealthmax",
                "min", "max", "chance"
            );

            // Find profile block start columns from row 0 (non-structural, non-empty cells)
            List<Integer> blockStarts = new ArrayList<>();
            List<String> row0 = current.get(0);
            for (int c = 0; c < row0.size(); c++) {
                String val = row0.get(c).trim();
                if (val.isEmpty() || knownHdrs.contains(normHdr(val))) continue;
                blockStarts.add(c);
            }
            int fixedWidth = isItemProfiles ? 8 : 6;
            logger.info("[sync-profiles] tab='{}' blockStarts={} sheetRow0Names={}",
                tabName, blockStarts.size(),
                blockStarts.stream().map(c -> c + ":" + row0.get(c)).toList());
            if (blockStarts.isEmpty()) return;

            List<Map<String, Object>> batchData = new ArrayList<>();
            int profileCount = Math.min(incoming.size(), blockStarts.size());

            for (int bi = 0; bi < profileCount; bi++) {
                int startCol = blockStarts.get(bi);
                JsonNode profileNode = incoming.get(bi);
                String incomingName = JsonUtils.textOrEmpty(profileNode.path("Profile")).trim();

                // Write profile name into row 0 (preserves format, updates value only)
                if (!incomingName.isEmpty()) {
                    addSingleCell(batchData, tabName, startCol, 0, incomingName);
                }

                // Find the "itemname" / "attachname" header row by scanning down this column
                int itemHdrRow = -1;
                for (int r = 1; r < Math.min(current.size(), 15); r++) {
                    String nh = normHdr(getCellSafe(current, r, startCol));
                    if (nh.equals("itemname") || nh.equals("item") || nh.equals("attachname")) {
                        itemHdrRow = r;
                        break;
                    }
                }
                if (itemHdrRow == -1) {
                    logger.warn("[sync-profiles] tab='{}' block[{}] col={} — no itemname header found, skipping",
                        tabName, bi, startCol);
                    continue;
                }
                int dataStartRow = itemHdrRow + 1; // 0-indexed; sheet row = dataStartRow + 1

                logger.info("[sync-profiles] tab='{}' block[{}] name='{}' startCol={} itemHdrRow={} dataStartRow={}",
                    tabName, bi, incomingName, startCol, itemHdrRow, dataStartRow);

                // Calculate rows to overwrite
                int lastDataRow = dataStartRow;
                for (int rr = dataStartRow; rr < Math.min(current.size(), dataStartRow + 200); rr++) {
                    if (!getCellSafe(current, rr, startCol).trim().isEmpty()) {
                        lastDataRow = rr;
                    } else if (rr > dataStartRow + 3 && lastDataRow < rr - 5) {
                        break;
                    }
                }
                JsonNode items = profileNode.path("Items");
                int newCount = items.isArray() ? items.size() : 0;
                int clearCount = Math.max(newCount + 3, lastDataRow - dataStartRow + 2);

                // Build data rows using FIXED column offsets matching the parser's layout:
                // ItemProfiles:   col 0=ItemName, 1=SpawnProb, 2=QtyMin, 3=QtyMax, 4=HltMin, 5=HltMax, 6=SlotProfile, 7=SlotProb
                // AttachProfiles: col 0=AttachName, 1=AttachProb, 2=QtyMin, 3=QtyMax, 4=HltMin, 5=HltMax
                List<List<Object>> dataRows = new ArrayList<>();
                if (items.isArray()) {
                    for (JsonNode item : items) {
                        List<Object> row = new ArrayList<>(Collections.nCopies(fixedWidth, ""));
                        if (isItemProfiles) {
                            row.set(0, JsonUtils.textOrEmpty(item.path("ItemName")));
                            row.set(1, fmtDouble(item.path("SpawnProb").asDouble(0)));
                            row.set(2, fmtDouble(item.path("ItemQuantityMin").asDouble(0)));
                            row.set(3, fmtDouble(item.path("ItemQuantityMax").asDouble(0)));
                            row.set(4, fmtDouble(item.path("ItemHealthMin").asDouble(0)));
                            row.set(5, fmtDouble(item.path("ItemHealthMax").asDouble(0)));
                            JsonNode slots = item.path("Slots");
                            if (slots.isArray() && !slots.isEmpty()) {
                                row.set(6, JsonUtils.textOrEmpty(slots.get(0).path("Profile")));
                                row.set(7, fmtDouble(slots.get(0).path("SpawnProb").asDouble(0)));
                            }
                            dataRows.add(row);
                            if (slots.isArray()) {
                                for (int si = 1; si < slots.size(); si++) {
                                    List<Object> slotRow = new ArrayList<>(Collections.nCopies(fixedWidth, ""));
                                    slotRow.set(6, JsonUtils.textOrEmpty(slots.get(si).path("Profile")));
                                    slotRow.set(7, fmtDouble(slots.get(si).path("SpawnProb").asDouble(0)));
                                    dataRows.add(slotRow);
                                }
                            }
                        } else {
                            row.set(0, JsonUtils.textOrEmpty(item.path("AttachName")));
                            row.set(1, fmtDouble(item.path("AttachProb").asDouble(0)));
                            row.set(2, fmtDouble(item.path("AttachQuantityMin").asDouble(0)));
                            row.set(3, fmtDouble(item.path("AttachQuantityMax").asDouble(0)));
                            row.set(4, fmtDouble(item.path("AttachHealthMin").asDouble(0)));
                            row.set(5, fmtDouble(item.path("AttachHealthMax").asDouble(0)));
                            dataRows.add(row);
                        }
                    }
                }
                while (dataRows.size() < clearCount) {
                    dataRows.add(new ArrayList<>(Collections.nCopies(fixedWidth, "")));
                }

                // dataStartRow is 0-indexed; sheet notation = dataStartRow + 1
                Map<String, Object> rangeData = new LinkedHashMap<>();
                rangeData.put("range", tabName + "!"
                    + colToLetter(startCol) + (dataStartRow + 1)
                    + ":" + colToLetter(startCol + fixedWidth - 1) + (dataStartRow + clearCount));
                rangeData.put("majorDimension", "ROWS");
                rangeData.put("values", dataRows);
                batchData.add(rangeData);
            }

            // Clear extra template blocks that have no incoming profile
            for (int bi = profileCount; bi < blockStarts.size(); bi++) {
                int startCol = blockStarts.get(bi);
                addSingleCell(batchData, tabName, startCol, 0, "");
                // Find and clear any existing data rows in this extra block
                int extraHdrRow = -1;
                for (int r = 1; r < Math.min(current.size(), 15); r++) {
                    String nh = normHdr(getCellSafe(current, r, startCol));
                    if (nh.equals("itemname") || nh.equals("item") || nh.equals("attachname")) {
                        extraHdrRow = r;
                        break;
                    }
                }
                if (extraHdrRow != -1) {
                    int dataStart = extraHdrRow + 1;
                    int lastRow = dataStart;
                    for (int rr = dataStart; rr < Math.min(current.size(), dataStart + 100); rr++) {
                        if (!getCellSafe(current, rr, startCol).trim().isEmpty()) lastRow = rr;
                        else if (rr > dataStart + 3 && lastRow < rr - 5) break;
                    }
                    int clearRows = lastRow - dataStart + 1;
                    List<List<Object>> emptyRows = new ArrayList<>();
                    for (int i = 0; i < clearRows; i++) {
                        emptyRows.add(new ArrayList<>(Collections.nCopies(fixedWidth, "")));
                    }
                    Map<String, Object> clearRange = new LinkedHashMap<>();
                    clearRange.put("range", tabName + "!"
                        + colToLetter(startCol) + (dataStart + 1)
                        + ":" + colToLetter(startCol + fixedWidth - 1) + (dataStart + clearRows));
                    clearRange.put("majorDimension", "ROWS");
                    clearRange.put("values", emptyRows);
                    batchData.add(clearRange);
                }
            }

            if (!batchData.isEmpty()) {
                logger.info("[sync-profiles] tab='{}' batchUpdate ranges={}", tabName, batchData.size());
                batchUpdateValues(accessToken, spreadsheetId, batchData);
            } else {
                logger.warn("[sync-profiles] tab='{}' batchData is empty", tabName);
            }
        } catch (IOException | InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("sync_profiles_failed: " + tabName + ": " + e.getMessage(), e);
        }
    }

    // ── Legacy: clear-and-rewrite (used for first-time setup when no template exists) ──

    /**
     * Clears the content of a sheet tab and then writes new row data to it.
     *
     * @param accessToken   valid Google OAuth access token
     * @param spreadsheetId target spreadsheet
     * @param tabName       name of the sheet tab (e.g. "LootTables")
     * @param rows          ArrayNode of row arrays to write; may be empty (will only clear)
     * @throws IllegalArgumentException if the clear or update API call fails
     */
    public void clearAndUpdateSheetTab(String accessToken, String spreadsheetId, String tabName, ArrayNode rows) {
        // 1. Clear existing content
        String clearUrl = "https://sheets.googleapis.com/v4/spreadsheets/"
            + urlEncode(spreadsheetId) + "/values/" + urlEncode(tabName + "!A:ZZ") + ":clear";
        HttpRequest clearReq = HttpRequest.newBuilder()
            .uri(URI.create(clearUrl))
            .timeout(Duration.ofSeconds(20))
            .header("Authorization", "Bearer " + accessToken)
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString("{}"))
            .build();

        try {
            HttpResponse<String> clearResp = httpClient.send(clearReq, HttpResponse.BodyHandlers.ofString());
            if (clearResp.statusCode() != 200) {
                throw new IllegalArgumentException("sheets_clear_failed: status=" + clearResp.statusCode()
                    + " tab=" + tabName + " body=" + clearResp.body());
            }

            if (rows == null || rows.isEmpty()) return;

            // 2. Write new values
            ObjectNode body = objectMapper.createObjectNode();
            body.put("range", tabName + "!A1");
            body.put("majorDimension", "ROWS");
            body.set("values", rows);

            String updateUrl = "https://sheets.googleapis.com/v4/spreadsheets/"
                + urlEncode(spreadsheetId) + "/values/"
                + urlEncode(tabName + "!A1") + "?valueInputOption=USER_ENTERED";
            HttpRequest updateReq = HttpRequest.newBuilder()
                .uri(URI.create(updateUrl))
                .timeout(Duration.ofSeconds(30))
                .header("Authorization", "Bearer " + accessToken)
                .header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString(body.toString()))
                .build();

            HttpResponse<String> updateResp = httpClient.send(updateReq, HttpResponse.BodyHandlers.ofString());
            if (updateResp.statusCode() != 200) {
                throw new IllegalArgumentException("sheets_update_failed: status=" + updateResp.statusCode()
                    + " tab=" + tabName + " body=" + updateResp.body());
            }
        } catch (IOException | InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("sheets_write_failed: tab=" + tabName, e);
        }
    }

    public static record Tokens(String accessToken, String refreshToken, long expiresInSeconds) {}
}
