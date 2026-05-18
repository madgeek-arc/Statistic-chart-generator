package gr.uoa.di.madgik.ChartDataFormatter.nl.options;

public interface NlOptionsGenerator {

    /**
     * Generates a library-specific chart options JSON string from a canonical description.
     *
     * @param library               target library (e.g. "HighCharts", "eCharts")
     * @param canonicalDescription  self-contained description of all visual settings
     * @return raw JSON string (no markdown fences)
     */
    String generate(String library, String canonicalDescription);
}
