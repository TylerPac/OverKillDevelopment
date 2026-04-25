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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

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
        q.put("scope", "https://www.googleapis.com/auth/spreadsheets https://www.googleapis.com/auth/drive.file");
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
            String accessToken = json.path("access_token").asText("");
            String refreshToken = json.path("refresh_token").asText("");
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
            return json.path("access_token").asText("");
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
            } catch (Exception e) {
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
            String destId = created.path("spreadsheetId").asText("");
            if (destId.isEmpty()) throw new IllegalStateException("sheets_create_failed: missing_id");

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
                String title = sheet.path("properties").path("title").asText("");
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
                String title = s.path("properties").path("title").asText(null);
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
                ObjectNode s = objectMapper.createObjectNode();
                s.putObject("properties").put("title", t.asText());
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
            String newId = created.path("spreadsheetId").asText("");
            if (newId.isEmpty()) throw new IllegalStateException("sheets_create_failed: missing_id");

            // Prepare batchUpdate data by copying values from each template sheet
            ArrayNode dataArray = objectMapper.createArrayNode();
            if (sheetTitles.size() > 0) {
                String apiKey = System.getenv("GOOGLE_API_KEY");
                for (JsonNode t : sheetTitles) {
                    String title = t.asText();
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

    public static record Tokens(String accessToken, String refreshToken, long expiresInSeconds) {}
}
