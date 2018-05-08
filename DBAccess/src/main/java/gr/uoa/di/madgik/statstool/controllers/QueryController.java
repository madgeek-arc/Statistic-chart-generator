package gr.uoa.di.madgik.statstool.controllers;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;

import gr.uoa.di.madgik.statstool.db.DBAccess;
import gr.uoa.di.madgik.statstool.domain.Query;
import gr.uoa.di.madgik.statstool.domain.Result;
import gr.uoa.di.madgik.statstool.services.StatsService;

@RestController
public class QueryController {

    private StatsService statsService;

    public QueryController(StatsService statsService) {
        this.statsService = statsService;
    }

    @RequestMapping(value = "/query_old")
    public List<Result> query_old() {
        try {
            ObjectMapper mapper = new ObjectMapper();
            Query query = mapper.readValue(getClass().getClassLoader().getResource("query_test.json"), Query.class);

            List<Query> queryList = new ArrayList<>();
            queryList.add(query);

            DBAccess dbAccess = new DBAccess();
            List<Result> results = dbAccess.query(queryList);
            return results;
        } catch (Exception e) {
            return null;
        }
    }

    @RequestMapping(value = "/query")
    public List<Result> query() {
        try {
            ObjectMapper mapper = new ObjectMapper();
            Query query = mapper.readValue(getClass().getClassLoader().getResource("query_test.json"), Query.class);

            List<Query> queryList = new ArrayList<>();
            queryList.add(query);

            return statsService.query(queryList);
        } catch (Exception e) {
            return null;
        }
    }

}
