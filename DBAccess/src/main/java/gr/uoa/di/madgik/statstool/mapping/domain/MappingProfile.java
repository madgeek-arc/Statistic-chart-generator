package gr.uoa.di.madgik.statstool.mapping.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;

public class MappingProfile {
    private String name;
    private String description;
    private boolean primary;
    private String file;

    MappingProfile() {}

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

    public String getFile() {
        return file;
    }

    public void setFile(String file) {
        this.file = file;
    }
}
