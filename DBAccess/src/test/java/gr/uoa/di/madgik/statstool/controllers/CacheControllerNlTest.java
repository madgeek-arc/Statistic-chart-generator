package gr.uoa.di.madgik.statstool.controllers;

import gr.uoa.di.madgik.statstool.services.CacheService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

public class CacheControllerNlTest {

    private MockMvc mockMvc;
    private CacheService cacheService;

    @BeforeEach
    void setup() {
        cacheService = mock(CacheService.class);
        CacheController controller = new CacheController();
        // inject mock via the @Autowired field
        org.springframework.test.util.ReflectionTestUtils.setField(controller, "cacheService", cacheService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void dropNlCache_noProfile_callsDropWithNull() throws Exception {
        mockMvc.perform(get("/cache/dropNlCache"))
                .andExpect(status().isOk());
        verify(cacheService).dropNlCache(null);
    }

    @Test
    void dropNlCache_withProfile_callsDropWithProfile() throws Exception {
        mockMvc.perform(get("/cache/dropNlCache").param("profile", "openaire_stats"))
                .andExpect(status().isOk());
        verify(cacheService).dropNlCache("openaire_stats");
    }

    @Test
    void evictNlCache_callsEvictWithProfileAndNl() throws Exception {
        mockMvc.perform(get("/cache/evictNlCache")
                        .param("profile", "openaire_stats")
                        .param("nl", "Number of publications per year"))
                .andExpect(status().isOk());
        verify(cacheService).evictNlCache("openaire_stats", "Number of publications per year");
    }

    @Test
    void evictNlCache_missingProfile_returnsBadRequest() throws Exception {
        mockMvc.perform(get("/cache/evictNlCache")
                        .param("nl", "Number of publications per year"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void evictNlCache_missingNl_returnsBadRequest() throws Exception {
        mockMvc.perform(get("/cache/evictNlCache")
                        .param("profile", "openaire_stats"))
                .andExpect(status().isBadRequest());
    }
}
