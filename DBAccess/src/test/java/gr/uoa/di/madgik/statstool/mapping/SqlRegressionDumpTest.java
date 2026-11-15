package gr.uoa.di.madgik.statstool.mapping;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import gr.uoa.di.madgik.statstool.domain.Query;
import gr.uoa.di.madgik.statstool.mapping.domain.Mapping;
import gr.uoa.di.madgik.statstool.mapping.domain.MappingProfile;
import gr.uoa.di.madgik.statstool.repositories.ConfigRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.io.BufferedReader;
import java.io.PrintWriter;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Regression harness, not a unit test: runs only with -Dregression.corpus=... .
 * Loads the profiles of a config dir (mappings.json + profile files) into the real Mapper,
 * generates SQL + bound parameters for every query of a JSONL corpus
 * ({"id":..,"query":{..},"orderBy":..}) and writes one JSON line per query to regression.out.
 * Used to diff generator output before/after a change.
 */
@EnabledIfSystemProperty(named = "regression.corpus", matches = ".+")
class SqlRegressionDumpTest {

    private static final ObjectMapper JSON = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    @Test
    void dump() throws Exception {
        Path config = Path.of(System.getProperty("regression.config"));
        Path corpus = Path.of(System.getProperty("regression.corpus"));
        Path out = Path.of(System.getProperty("regression.out"));

        // Profiles exactly as load_stats_config.py loads them (duplicate names: last wins)
        Map<String, MappingProfile> byName = new LinkedHashMap<>();
        for (JsonNode p : JSON.readTree(config.resolve("mappings.json").toFile())) {
            MappingProfile mp = new MappingProfile();
            mp.setName(p.get("name").asText());
            String file = Path.of(p.get("file").asText()).getFileName().toString();
            mp.setContent(JSON.readValue(config.resolve(file).toFile(), Mapping.class));
            byName.put(mp.getName(), mp);
        }
        ConfigRepository repo = mock(ConfigRepository.class);
        when(repo.loadProfiles()).thenReturn(new ArrayList<>(byName.values()));
        Mapper mapper = new Mapper();
        Field f = Mapper.class.getDeclaredField("configRepository");
        f.setAccessible(true);
        f.set(mapper, repo);
        mapper.reload();

        int ok = 0, err = 0, skipped = 0;
        try (BufferedReader in = Files.newBufferedReader(corpus); PrintWriter w = new PrintWriter(Files.newBufferedWriter(out))) {
            String line;
            while ((line = in.readLine()) != null) {
                JsonNode rec = JSON.readTree(line);
                ObjectNode res = JSON.createObjectNode();
                res.put("id", rec.get("id").asInt());
                String profile = rec.path("query").path("profile").asText(null);
                res.put("profile", profile);
                if (profile == null || !byName.containsKey(profile)) {
                    res.put("skip", "profile not in config");
                    skipped++;
                } else {
                    try {
                        Query q = JSON.treeToValue(rec.get("query"), Query.class);
                        String orderBy = rec.hasNonNull("orderBy") ? rec.get("orderBy").asText() : null;
                        List<Object> params = new ArrayList<>();
                        String sql = mapper.map(q, params, orderBy);
                        res.put("sql", sql);
                        res.set("params", JSON.valueToTree(params));
                        List<String> types = new ArrayList<>();
                        for (Object o : params) types.add(o == null ? "null" : o.getClass().getSimpleName());
                        res.set("types", JSON.valueToTree(types));
                        ok++;
                    } catch (Exception e) {
                        res.put("error", e.getClass().getSimpleName() + ": " + e.getMessage());
                        err++;
                    }
                }
                w.println(JSON.writeValueAsString(res));
            }
        }
        System.out.printf("REGRESSION ok=%d error=%d skipped=%d -> %s%n", ok, err, skipped, out);
    }
}
