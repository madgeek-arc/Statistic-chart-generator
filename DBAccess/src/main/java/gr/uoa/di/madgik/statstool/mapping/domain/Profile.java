package gr.uoa.di.madgik.statstool.mapping.domain;

import java.util.List;

public class Profile {
    private String name;
    private String description;
    private String usage;
    private List<String> shareholders;
    private int complexity;

    public Profile(String name, String description, String usage, List<String> shareholders, int complexity) {
        this.name = name;
        this.description = description;
        this.usage = usage;
        this.shareholders = shareholders;
        this.complexity = complexity;
    }

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
}
