package gr.uoa.di.madgik.statstool;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import gr.uoa.di.madgik.statstool.db.Result;
import gr.uoa.di.madgik.statstool.db.DBAccess;
import gr.uoa.di.madgik.statstool.query.Query;

public class DBAccessTest {

    @Test
    public void testDB() {
        DBAccess dbAccess = new DBAccess();
        ObjectMapper mapper = new ObjectMapper();
        List<Query> queryList = new ArrayList<Query>();
        try {
            Query query = mapper.readValue(getClass().getClassLoader().getResource("query_test.json"), Query.class);
            queryList.add(query);
            queryList.add(query);
            queryList.add(query);
            queryList.add(query);
            queryList.add(query);
            queryList.add(query);

            List<Result> results = dbAccess.queryTest(queryList);
            /*
            for(Result result : results) {
                System.out.println(mapper.writerWithDefaultPrettyPrinter().writeValueAsString(result));
            }
            */
            System.out.println(mapper.writerWithDefaultPrettyPrinter().writeValueAsString(results));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
