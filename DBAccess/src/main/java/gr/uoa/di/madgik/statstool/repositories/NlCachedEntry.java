package gr.uoa.di.madgik.statstool.repositories;

import gr.uoa.di.madgik.statstool.domain.QueryWithParameters;

public record NlCachedEntry(QueryWithParameters qwp, String description) {

    public NlCachedEntry(QueryWithParameters qwp) {
        this(qwp, "");
    }
}
