package dev.streamline.client.moonshot;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class MoonshotClientsTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    private HttpServer server;
    private URI baseUrl;
    private final AtomicReference<JsonNode> lastBody = new AtomicReference<>();
    private final AtomicReference<String> lastPath = new AtomicReference<>();
    private final AtomicReference<String> lastMethod = new AtomicReference<>();

    private void startWith(int status, String body) throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", (HttpExchange ex) -> {
            lastMethod.set(ex.getRequestMethod());
            lastPath.set(ex.getRequestURI().getPath());
            byte[] reqBytes = ex.getRequestBody().readAllBytes();
            if (reqBytes.length > 0) {
                try {
                    lastBody.set(JSON.readTree(reqBytes));
                } catch (Exception ignored) {
                    lastBody.set(null);
                }
            }
            byte[] resp = body == null ? new byte[0] : body.getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().add("Content-Type", "application/json");
            ex.sendResponseHeaders(status, resp.length);
            ex.getResponseBody().write(resp);
            ex.close();
        });
        server.start();
        baseUrl = URI.create("http://127.0.0.1:" + server.getAddress().getPort());
    }

    @AfterEach
    void stop() {
        if (server != null) server.stop(0);
        server = null;
    }

    @BeforeEach
    void reset() {
        lastBody.set(null);
        lastPath.set(null);
        lastMethod.set(null);
    }

    private MoonshotClientOptions opts() {
        return new MoonshotClientOptions(baseUrl.toString());
    }

    @Test
    void branches_create_returnsView() throws Exception {
        startWith(200, "{\"id\":\"orders/exp-a\",\"created_at_ms\":1000,\"message_count\":0}");
        BranchAdminClient c = new BranchAdminClient(opts());
        BranchAdminClient.BranchView v =
                c.create("orders", "exp-a", BranchAdminClient.CreateOptions.empty());
        assertEquals("orders/exp-a", v.id());
        assertEquals(1000L, v.createdAtMs());
        assertEquals("POST", lastMethod.get());
        assertEquals("/api/v1/branches", lastPath.get());
    }

    @Test
    void branches_get_404_throwsHttpException() throws Exception {
        startWith(404, "{\"error\":\"missing\"}");
        BranchAdminClient c = new BranchAdminClient(opts());
        MoonshotHttpClient.HttpException ex =
                assertThrows(MoonshotHttpClient.HttpException.class, () -> c.get("orders/x"));
        assertEquals(404, ex.status());
    }

    @Test
    void branches_create_rejectsEmpty() throws Exception {
        startWith(200, "{}");
        BranchAdminClient c = new BranchAdminClient(opts());
        assertThrows(IllegalArgumentException.class,
                () -> c.create("", "x", BranchAdminClient.CreateOptions.empty()));
    }

    @Test
    void contracts_validate_200_valid() throws Exception {
        startWith(200, "{\"schema_id\":7}");
        ContractsClient c = new ContractsClient(opts());
        ContractsClient.ValidationResult r = c.validate(Map.of("name", "c"), Map.of("id", "x"));
        assertTrue(r.valid());
        assertEquals(7, r.schemaId());
    }

    @Test
    void contracts_validate_400_failures() throws Exception {
        startWith(400, "{\"schema_id\":7,\"errors\":[{\"field_path\":\"id\",\"expected\":\"string\",\"actual\":\"int\"}]}");
        ContractsClient c = new ContractsClient(opts());
        ContractsClient.ValidationResult r = c.validate(Map.of("name", "c"), Map.of("id", 1));
        assertFalse(r.valid());
        assertEquals(1, r.errors().size());
        assertEquals("id", r.errors().get(0).fieldPath());
    }

    @Test
    void contracts_validate_bytes_sentAsValueString() throws Exception {
        startWith(200, "{}");
        ContractsClient c = new ContractsClient(opts());
        c.validate(Map.of("name", "c"), "hi".getBytes(StandardCharsets.UTF_8));
        assertEquals("hi", lastBody.get().path("value_string").asText());
    }

    @Test
    void attest_sign_returnsEnvelope() throws Exception {
        startWith(200,
                "{\"key_id\":\"k0\",\"algorithm\":\"ed25519\",\"timestamp_ms\":42,"
                        + "\"payload_sha256\":\"aa\",\"signature_b64\":\"bb\","
                        + "\"header_name\":\"streamline-attest\",\"header_value\":\"v\"}");
        AttestationClient a = new AttestationClient(opts());
        AttestationClient.SignedAttestation s = a.sign(new AttestationClient.SignParams(
                "t", 0, 1L, null, "hi", 0, 0L, null));
        assertEquals("bb", s.signatureB64());
        assertEquals("streamline-attest", s.headerName());
    }

    @Test
    void attest_verify_returnsValid() throws Exception {
        startWith(200, "{\"valid\":true}");
        AttestationClient a = new AttestationClient(opts());
        boolean ok = a.verify(new AttestationClient.VerifyParams(
                "t", 0, 1L, null, "hi", 0, 42L, "bb", null, null));
        assertTrue(ok);
    }

    @Test
    void attest_sign_rejectsBothValueForms() throws Exception {
        startWith(200, "{}");
        AttestationClient a = new AttestationClient(opts());
        assertThrows(IllegalArgumentException.class,
                () -> a.sign(new AttestationClient.SignParams(
                        "t", 0, 1L, "x".getBytes(), "y", 0, 0L, null)));
    }

    @Test
    void attest_sign_bytesEncoded() throws Exception {
        startWith(200,
                "{\"key_id\":\"k\",\"algorithm\":\"ed25519\",\"timestamp_ms\":1,"
                        + "\"payload_sha256\":\"x\",\"signature_b64\":\"y\","
                        + "\"header_name\":\"streamline-attest\",\"header_value\":\"v\"}");
        AttestationClient a = new AttestationClient(opts());
        a.sign(new AttestationClient.SignParams(
                "t", 0, 1L, "hi".getBytes(StandardCharsets.UTF_8), null, 0, 0L, null));
        assertEquals("aGk=", lastBody.get().path("value_b64").asText());
    }

    @Test
    void search_parsesHitsAndTookMs() throws Exception {
        startWith(200,
                "{\"hits\":[{\"partition\":1,\"offset\":5,\"score\":0.9}],\"took_ms\":12}");
        SemanticSearchClient c = new SemanticSearchClient(opts());
        SemanticSearchClient.SearchResult r = c.search("logs", "payment failure",
                new SemanticSearchClient.SearchOptions(5, null));
        assertEquals(12, r.tookMs());
        assertEquals(1, r.hits().size());
        assertEquals(0.9, r.hits().get(0).score(), 1e-9);
        assertTrue(lastPath.get().endsWith("/topics/logs/search"));
    }

    @Test
    void search_validation() throws Exception {
        startWith(200, "{}");
        SemanticSearchClient c = new SemanticSearchClient(opts());
        assertThrows(IllegalArgumentException.class,
                () -> c.search("t", "", SemanticSearchClient.SearchOptions.defaults()));
        assertThrows(IllegalArgumentException.class,
                () -> c.search("t", "q", new SemanticSearchClient.SearchOptions(1001, null)));
    }

    @Test
    void memory_remember_returnsEntries() throws Exception {
        startWith(200, "{\"written\":[{\"topic\":\"a-ep\",\"offset\":1},{\"topic\":\"a-sem\",\"offset\":2}]}");
        MemoryClient c = new MemoryClient(opts());
        List<MemoryClient.WrittenEntry> out = c.remember(new MemoryClient.RememberParams(
                "a", MemoryClient.Kind.FACT, "x", 0.8, null, null));
        assertEquals(2, out.size());
        assertEquals(2L, out.get(1).offset());
    }

    @Test
    void memory_procedure_requiresSkill() throws Exception {
        startWith(200, "{}");
        MemoryClient c = new MemoryClient(opts());
        assertThrows(IllegalArgumentException.class,
                () -> c.remember(new MemoryClient.RememberParams(
                        "a", MemoryClient.Kind.PROCEDURE, "x", 0.5, null, null)));
    }

    @Test
    void memory_recall_mapsTier() throws Exception {
        startWith(200, "{\"hits\":[{\"tier\":\"semantic\",\"topic\":\"a\",\"offset\":5,\"content\":\"hi\",\"score\":0.7}]}");
        MemoryClient c = new MemoryClient(opts());
        List<MemoryClient.RecalledMemory> hits = c.recall(new MemoryClient.RecallParams("a", "q", 0, 0));
        assertEquals(1, hits.size());
        assertEquals("semantic", hits.get(0).tier());
        assertEquals(0.7, hits.get(0).score(), 1e-9);
    }
}
