package gr.uoa.di.madgik.statstool.mapping.domain;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public class MappingProfile {
    private String name;
    private String description;
    private String usage;
    private List<String> shareholders;
    private int complexity;
    private boolean primary;
    private boolean hidden;
    private String file;

    /**
     * Populated when loaded from DB; not present in file-based mappings.json. Serialized as "mappingContent", the
     * name the PUT/POST /config/profiles body uses, so GET /config/profiles/{name} returns a body that can be sent back.
     */
    @JsonProperty("mappingContent")
    private Mapping content;

    public MappingProfile() {}

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public boolean isPrimary() {
        return primary;
    }

    public void setPrimary(boolean primary) {
        this.primary = primary;
    }

    public boolean isHidden() {
        return hidden;
    }

    public void setHidden(boolean hidden) {
        this.hidden = hidden;
    }

    public String getFile() {
        return file;
    }

    public void setFile(String file) {
        this.file = file;
    }

    public String getUsage() {
        return usage;
    }

    public void setUsage(String usage) {
        this.usage = usage;
    }

    public List<String> getShareholders() {
        return shareholders;
    }

    public void setShareholders(List<String> shareholders) {
        this.shareholders = shareholders;
    }

    public int getComplexity() {
        return complexity;
    }

    public void setComplexity(int complexity) {
        this.complexity = complexity;
    }

    public Mapping getContent() {
        return content;
    }

    public void setContent(Mapping content) {
        this.content = content;
    }
}
