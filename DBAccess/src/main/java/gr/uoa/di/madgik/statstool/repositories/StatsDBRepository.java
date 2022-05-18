package gr.uoa.di.madgik.statstool.repositories;

import gr.uoa.di.madgik.statstool.domain.QueryWithParameters;
import gr.uoa.di.madgik.statstool.domain.Result;
import gr.uoa.di.madgik.statstool.domain.cache.CacheEntry;
import gr.uoa.di.madgik.statstool.repositories.datasource.DatasourceContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.util.List;

@Repository
public class StatsDBRepository implements StatsCache {

    public static final String CACHE_DB_NAME = "cache";

    private JdbcTemplate jdbcTemplate;

    public StatsDBRepository(DataSource dataSource) {
        this.jdbcTemplate = new JdbcTemplate(dataSource);
    }

    @Override
    public boolean exists(String key) throws Exception {
        DatasourceContext.setContext(CACHE_DB_NAME);

        return jdbcTemplate.queryForObject("select count(*) from cache where key=?",new Object[] {String.class}, Integer.class) == 1;
    }

    @Override
    public Result get(String key) throws Exception {
        return null;
    }

    @Override
    public String save(QueryWithParameters fullSqlQuery, Result result) throws Exception {
        return null;
    }

    @Override
    public void storeEntry(CacheEntry entry) throws Exception {

    }

    @Override
    public List<CacheEntry> getEntries() {
        return null;
    }

    @Override
    public void deleteEntry(String key) {

    }
}
