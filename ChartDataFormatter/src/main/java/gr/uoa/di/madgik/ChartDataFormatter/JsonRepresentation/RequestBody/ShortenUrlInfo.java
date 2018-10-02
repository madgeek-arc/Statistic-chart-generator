package gr.uoa.di.madgik.ChartDataFormatter.JsonRepresentation.RequestBody;

import com.fasterxml.jackson.annotation.JsonProperty;

public class ShortenUrlInfo {

    @JsonProperty(value = "url")
    private String urlToShorten;

    public ShortenUrlInfo() {}

    public ShortenUrlInfo(String urlToShorten) { this.urlToShorten = urlToShorten; }

    public String getUrlToShorten() { return urlToShorten; }

    public void setUrlToShorten(String urlToShorten) { this.urlToShorten = urlToShorten; }
}
