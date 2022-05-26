package gr.uoa.di.madgik.ChartDataFormatter.DataFormatter.SupportedDiagrams.POJOs;

import java.util.List;

import gr.uoa.di.madgik.ChartDataFormatter.Handlers.SupportedLibraries;

public class SupportedDiagram {
    
    public String name;
    public int diagramId;
    public String description;
    public String imageURL;
    public boolean isPolar; 
    public boolean isHidden;
    public List<SupportedLibraries> supportedLibraries;

    public void setName(String name) { this.name = name; }
    public String getName() { return this.name; }

    public void setDiagramId(int diagramId) { this.diagramId = diagramId; }
    public int getdiagramId() { return this.diagramId; }
    
    public void setDescription(String description) { this.description = description; }
    public String getDescription() { return this.description; }

    public void setImageURL(String imageURL) { this.imageURL = imageURL; }
    public String getImageURL() { return this.imageURL; }

    public void setIsPolar(boolean isPolar) { this.isPolar = isPolar; }
    public boolean getIsPolar() { return this.isPolar; }
    
    public void setIsHidden(boolean isHidden) { this.isHidden = isHidden; }
    public boolean getIsHidden() { return this.isHidden; }

    public void setSupportedLibraries(List<SupportedLibraries> supportedLibraries) { this.supportedLibraries = supportedLibraries; }
    public List<SupportedLibraries> getSupportedLibraries() { return this.supportedLibraries; }
}