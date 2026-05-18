package gr.uoa.di.madgik.ChartDataFormatter;

import gr.uoa.di.madgik.ChartDataFormatter.DataFormatter.SupportedDiagrams.Service.SupportedDiagramsService;
import gr.uoa.di.madgik.ChartDataFormatter.Handlers.RequestBodyHandler;
import gr.uoa.di.madgik.ChartDataFormatter.RestControllers.ChartDataFormatterRestController;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.client.RestTemplate;

import java.net.URI;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ChartDataFormatterRestController.class)
class ShortenChartUrlTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private RequestBodyHandler requestBodyHandler;

    @MockBean
    private SupportedDiagramsService supportedDiagramsService;

    @MockBean
    private RestTemplate restTemplate;

    @Test
    void shortenChartUrl_success_returnsJsonWithShortUrl() throws Exception {
        when(restTemplate.getForEntity(any(URI.class), eq(String.class)))
                .thenReturn(new ResponseEntity<>("https://tinyurl.com/abc123", HttpStatus.OK));

        mockMvc.perform(post("/chart/shorten")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\":\"https://example.com/some/long/url\"}"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.shortUrl").value("https://tinyurl.com/abc123"));
    }

    @Test
    void shortenChartUrl_invalidUrl_returnsBadRequest() throws Exception {
        // Spaces in the URL make new URI(...) throw URISyntaxException → 400
        mockMvc.perform(post("/chart/shorten")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\":\"not a valid url\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.shortUrl").doesNotExist());
    }

    @Test
    void shortenChartUrl_responseContainsOnlyShortUrlKey() throws Exception {
        when(restTemplate.getForEntity(any(URI.class), eq(String.class)))
                .thenReturn(new ResponseEntity<>("https://tinyurl.com/xyz99", HttpStatus.OK));

        mockMvc.perform(post("/chart/shorten")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\":\"https://example.com/chart?foo=bar\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.shortUrl").value("https://tinyurl.com/xyz99"))
                .andExpect(jsonPath("$.url").doesNotExist());
    }
}
