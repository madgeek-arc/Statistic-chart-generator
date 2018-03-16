package gr.uoa.di.madgik.statstool;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.Test;

import gr.uoa.di.madgik.statstool.mapping.Mapper;
import gr.uoa.di.madgik.statstool.query.Query;

public class MapperTest {

    @Test
    public void testMapper() {
        Mapper mapper = new Mapper();
        //mapper.printMapper();
        try {
            ObjectMapper objectMapper = new ObjectMapper();
            Query query = objectMapper.readValue(getClass().getClassLoader().getResource("query_test.json"), Query.class);
            Query mappedQuery = mapper.map(query);
            System.out.println(objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(mappedQuery));

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
