package gr.uoa.di.madgik.statstool.repositories.datasource;

public class DatasourceContext {

    private static ThreadLocal<String> context = new ThreadLocal<>();

    public static String getContext() {
        return context.get();
    }

    public static void setContext(String context) {
        DatasourceContext.context.set(context);
    }

    public static void resetContext() {
        context.remove();
    }
}
