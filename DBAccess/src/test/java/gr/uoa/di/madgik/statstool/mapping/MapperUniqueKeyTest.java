package gr.uoa.di.madgik.statstool.mapping;

import com.fasterxml.jackson.databind.ObjectMapper;
import gr.uoa.di.madgik.statstool.domain.Query;
import gr.uoa.di.madgik.statstool.domain.Select;
import gr.uoa.di.madgik.statstool.mapping.domain.Mapping;
import gr.uoa.di.madgik.statstool.mapping.domain.MappingProfile;
import gr.uoa.di.madgik.statstool.repositories.ConfigRepository;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Rule R1 is enabled per entity by the profile flag "uniqueKey" (default false). */
class MapperUniqueKeyTest {

    private static String profileJson(String flag) {
        return "{\"entities\":[{\"name\":\"result\",\"from\":\"result\",\"key\":\"id\"," + flag
                + "\"fields\":[{\"name\":\"year\",\"column\":\"year\",\"datatype\":\"int\"}],\"relations\":[]}],\"relations\":[]}";
    }

    /** p1 flags result.id unique, p2 sets it false, p3 has no flag. */
    private Mapper mapper() throws Exception {
        List<MappingProfile> profiles = new ArrayList<>();
        String[][] defs = {{"p1", "\"uniqueKey\":true,"}, {"p2", "\"uniqueKey\":false,"}, {"p3", ""}};
        for (String[] d : defs) {
            MappingProfile mp = new MappingProfile();
            mp.setName(d[0]);
            mp.setContent(new ObjectMapper().readValue(profileJson(d[1]), Mapping.class));
            profiles.add(mp);
        }
        ConfigRepository repo = mock(ConfigRepository.class);
        when(repo.loadProfiles()).thenReturn(profiles);
        Mapper m = new Mapper();
        java.lang.reflect.Field f = Mapper.class.getDeclaredField("configRepository");
        f.setAccessible(true);
        f.set(m, repo);
        m.reload();
        return m;
    }

    private static String countSql(Mapper m, String profile) {
        Query q = new Query(null, null, new ArrayList<>(),
                Arrays.asList(new Select("result", "count", 1), new Select("result.year", null, 2)),
                "result", profile, 0, null, false);
        return m.map(q, new ArrayList<>(), null);
    }

    @Test
    void flaggedEntity_dropsDistinct_unflaggedKeepsIt() throws Exception {
        Mapper m = mapper();
        assertTrue(countSql(m, "p1").startsWith("SELECT COUNT(r0.id), "));
        assertTrue(countSql(m, "p2").startsWith("SELECT COUNT(DISTINCT r0.id), "));
        assertTrue(countSql(m, "p3").startsWith("SELECT COUNT(DISTINCT r0.id), "));
    }
}
