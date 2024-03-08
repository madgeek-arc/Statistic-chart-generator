package gr.uoa.di.madgik.statstool.controllers;

import com.fasterxml.jackson.databind.ObjectMapper;
import gr.uoa.di.madgik.statstool.domain.Query;
import gr.uoa.di.madgik.statstool.domain.Result;
import gr.uoa.di.madgik.statstool.services.StatsService;
import gr.uoa.di.madgik.statstool.services.StatsServiceException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;

@RestController
public class QueryController {

    private StatsService statsService;

    private final Logger log = LogManager.getLogger(this.getClass());

    public QueryController(StatsService statsService) {
        this.statsService = statsService;
    }

    @RequestMapping(value = "/queryTest")
    public List<Result> query() {
        try {
            ObjectMapper mapper = new ObjectMapper();
            Query query = mapper.readValue(getClass().getClassLoader().getResource("query_test.json"), Query.class);

            List<Query> queryList = new ArrayList<>();
            queryList.add(query);

            return statsService.query(queryList);
        } catch (Exception e) {
            log.error(e);
            return null;
        }
    }

    @RequestMapping(value = "/query_flat")
    public List<Result> queryFlat() {
        try {
            ObjectMapper mapper = new ObjectMapper();
            Query query = mapper.readValue(getClass().getClassLoader().getResource("query_flat.json"), Query.class);

            List<Query> queryList = new ArrayList<>();
            queryList.add(query);

            return statsService.query(queryList);
        } catch (Exception e) {
            log.error(e);
            return null;
        }
    }

    @RequestMapping(value = "/query", method = RequestMethod.POST)
    public List<Result> query(@RequestBody List<Query> queryList) {
        try {
            return statsService.query(queryList);
        } catch (StatsServiceException e) {
            log.error(e);

            return null;
        }
    }
}
