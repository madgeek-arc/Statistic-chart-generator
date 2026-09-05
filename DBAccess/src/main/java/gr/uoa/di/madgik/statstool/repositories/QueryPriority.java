package gr.uoa.di.madgik.statstool.repositories;

public enum QueryPriority {
    USER(0), CACHE_UPDATE(1), TRICKLE(2);

    public final int value;

    QueryPriority(int value) {
        this.value = value;
    }
}
