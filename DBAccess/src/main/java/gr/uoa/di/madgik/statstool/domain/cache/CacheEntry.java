package gr.uoa.di.madgik.statstool.domain.cache;

import gr.uoa.di.madgik.statstool.domain.QueryWithParameters;
import gr.uoa.di.madgik.statstool.domain.Result;

import java.util.Date;

public class CacheEntry {
    private String key;
    private QueryWithParameters query;
    private Result result;
    private Result shadowResult;
    private Date created = new Date();
    private Date updated = new Date();
    private int totalHits = 0;
    private int sessionHits = 0;
    private boolean pinned = false;

    private String profile;

    private long execTime;

    public CacheEntry(String key, QueryWithParameters query, Result result) {
        this.key = key;
        this.query = query;
        this.result = result;
    }

    public CacheEntry(String key, QueryWithParameters query, Result result, long execTime) {
        this.key = key;
        this.query = query;
        this.result = result;
        this.execTime = execTime;
    }

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public QueryWithParameters getQuery() {
        return query;
    }

    public void setQuery(QueryWithParameters query) {
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

    public long getExecTime() {
        return execTime;
    }

    public void setExecTime(long execTime) {
        this.execTime = execTime;
    }

    public String getProfile() {
        return profile;
    }

    public void setProfile(String profile) {
        this.profile = profile;
    }

    @Override
    public String toString() {
        return "CacheEntry{" +
                "key='" + key + '\'' +
                ", query=" + query +
                ", result=" + result +
                ", shadowResult=" + shadowResult +
                ", created=" + created +
                ", updated=" + updated +
                ", totalHits=" + totalHits +
                ", sessionHits=" + sessionHits +
                ", pinned=" + pinned +
                ", execTime= " + execTime +
                ", profile= " + profile +
                '}';
    }
}