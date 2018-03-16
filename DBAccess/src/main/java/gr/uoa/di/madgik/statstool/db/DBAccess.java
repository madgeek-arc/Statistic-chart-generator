package gr.uoa.di.madgik.statstool.db;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;

import gr.uoa.di.madgik.statstool.mapping.Mapper;
import gr.uoa.di.madgik.statstool.query.Query;

public class DBAccess{

    Mapper mapper = new Mapper();

    public List<Result> query(List<Query> queryList) {
        for(Query query : queryList) {
            System.out.println("lol");
        }
        return null;
    }

    public List<Result> queryTest(List<Query> queryList) {
        List<Result> customResult = new ArrayList<Result>();
        ObjectMapper objectMapper  = new ObjectMapper();
        try {
            /*
            ArrayList<String> row= new ArrayList<String>();
            row.add("EPLANET");
            row.add("598");
            Result result = new Result();
            result.addRow(row);
            result.addRow(row);
            result.addRow(row);
            result.addRow(row);
            result.addRow(row);
            result.addRow(row);
            result.addRow(row);
            result.addRow(row);
            result.addRow(row);
            result.addRow(row);
            */
            Result result = objectMapper.readValue(getClass().getClassLoader().getResource("result1.json"), Result.class);
            customResult.add(result);
            result = objectMapper.readValue(getClass().getClassLoader().getResource("result2.json"), Result.class);
            customResult.add(result);
            result = objectMapper.readValue(getClass().getClassLoader().getResource("result3.json"), Result.class);
            customResult.add(result);
        } catch (Exception e) {
            e.printStackTrace();
        }
        List<Result> results = new ArrayList<Result>();
        int count = 0;
        for(Query query: queryList) {
            results.add(customResult.get(count % customResult.size()));
            count++;
        }
        return results;
    }
}
