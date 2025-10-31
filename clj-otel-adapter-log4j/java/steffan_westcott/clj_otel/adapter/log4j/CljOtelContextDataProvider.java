package steffan_westcott.clj_otel.adapter.log4j;

import clojure.java.api.Clojure;
import clojure.lang.IFn;
import org.apache.logging.log4j.core.util.ContextDataProvider;

import java.util.Map;

/**
 * Adds clj-otel bound context aware data to Log4j context data. Provides trace ID, span ID, trace
 * flags and baggage data.
 */
public class CljOtelContextDataProvider implements ContextDataProvider {

    private static final String NAMESPACE = "steffan-westcott.clj-otel.adapter.log4j";
    private final IFn context_data;

    public CljOtelContextDataProvider() {
        super();
        Clojure.var("clojure.core", "require").invoke(Clojure.read(NAMESPACE));
        context_data = Clojure.var(NAMESPACE, "context-data");
    }

    @Override
    public Map<String, String> supplyContextData() {
        return (Map<String, String>) context_data.invoke();
    }
}
