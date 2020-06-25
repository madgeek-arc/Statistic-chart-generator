package gr.uoa.di.madgik.statstool.domain.cache;

import gr.uoa.di.madgik.statstool.domain.Result;

import java.util.Date;
import java.util.List;

public class CacheEntry {
    private String key;
    private String query;
    private Result result;
    private Result shadowResult;
    private Date created = new Date();
    private Date updated = new Date();
    private int totalHits = 0;
    private int sessionHits = 0;
    private boolean pinned = false;

    public CacheEntry(String key, String query, Result result) {
        this.key = key;
        this.query = query;
        this.result = result;
    }

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public String getQuery() {
        return query;
    }

    public void setQuery(String query) {
        this.query = query;
    }

    public Result getResult() {
        return result;
    }

    public void setResult(Result result) {
        this.result = result;
    }

    public Result getShadowResult() {
        return shadowResult;
    }

    public void setShadowResult(Result shadowResult) {
        this.shadowResult = shadowResult;
    }

    public Date getCreated() {
        return created;
    }

    public void setCreated(Date created) {
        this.created = created;
    }

    public Date getUpdated() {
        return updated;
    }

    public void setUpdated(Date updated) {
        this.updated = updated;
    }

    public int getTotalHits() {
        return totalHits;
    }

    public void setTotalHits(int totalHits) {
        this.totalHits = totalHits;
    }

    public int getSessionHits() {
        return sessionHits;
    }

    public void setSessionHits(int sessionHits) {
        this.sessionHits = sessionHits;
    }

    public boolean isPinned() {
        return pinned;
    }

    public void setPinned(boolean pinned) {
        this.pinned = pinned;
    }
}