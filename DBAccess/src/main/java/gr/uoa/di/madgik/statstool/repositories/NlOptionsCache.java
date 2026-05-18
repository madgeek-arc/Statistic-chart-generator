package gr.uoa.di.madgik.statstool.repositories;

import com.fasterxml.jackson.databind.JsonNode;

public interface NlOptionsCache {

    String get(String library, String canonicalDescription, String promptVersion);

    void put(String library, String canonicalDescription, String optionsJson, String promptVersion);

    void evict(String library, String canonicalDescription);

    void drop(String library);
}
