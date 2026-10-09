package steffan_westcott.clj_otel.adapter.logback;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.UnsynchronizedAppenderBase;
import clojure.java.api.Clojure;
import clojure.lang.IFn;

import java.util.*;
import java.util.stream.Collectors;

public class CljOtelAppender extends UnsynchronizedAppenderBase<ILoggingEvent> {

    /**
     * If true, include <code>:source</code> location where log record occurred (default: false)
     */
    public boolean captureCodeAttributes;

    /**
     * If true, include thread data (default: false).
     */
    public boolean captureExperimentalAttributes;

    /**
     * If true, include Logback markers as attributes (default: false).
     */
    public boolean captureMarkerAttribute;

    /**
     * Set of globs for keys of Logback KeyValuePairs to exclude as log record attributes, or default <code>null</code> if none defined.
     * Takes precedence over <code>keyValuePairAttributesIncluded</code>.
     */
    public Set<String> keyValuePairAttributesExcluded;

    /**
     * Set of globs for keys of Logback KeyValuePairs to include as log record attributes, or default <code>null</code> if none defined.
     */
    public Set<String> keyValuePairAttributesIncluded;

    /**
     * Predicate for keys of Logback KeyValuePairs to include as log record attributes, or default <code>null</code> if none defined.
     */
    public IFn matchKeyValuePairAttribute;

    /**
     * Set of globs for keys of Logback context properties to exclude as log record attributes, or default <code>null</code> if none defined.
     * Takes precedence over <code>loggerContextAttributesIncluded</code>.
     */
    public Set<String> loggerContextAttributesExcluded;

    /**
     * Set of globs for keys of Logback context properties to include as log record attributes, or default <code>null</code> if none defined.
     */
    public Set<String> loggerContextAttributesIncluded;

    /**
     * Predicate for keys of Logback context properties to include as log record attributes, or default <code>null</code> if none defined.
     */
    public IFn matchLoggerContextAttribute;

    /**
     * If true, include message template as an attribute (default: false)
     */
    public boolean captureTemplate;

    /**
     * If true, include message arguments as an attribute (default: false)
     */
    public boolean captureArguments;

    /**
     * Set of globs for keys of Logstash marker attribute to exclude as log record attributes, or default <code>null</code> if none defined.
     * Takes precedence over <code>logstashMarkerAttributesIncluded</code>.
     */
    public Set<String> logstashMarkerAttributesExcluded;

    /**
     * Set of globs for keys of Logstash marker attributes to include as log record attributes, or default <code>null</code> if none defined.
     */
    public Set<String> logstashMarkerAttributesIncluded;

    /**
     * Predicate for keys of Logstash marker attributes to include as log record attributes, or default <code>null</code> if none defined.
     */
    public IFn matchLogstashMarkerAttribute;

    /**
     * Set of globs for keys of Logstash structured arguments to exclude as log record attributes, or default <code>null</code> if none defined.
     * Takes precedence over <code>logstashStructuredArgumentAttributesIncluded</code>.
     */
    public Set<String> logstashStructuredArgumentAttributesExcluded;

    /**
     * Set of globs for keys of Logstash structured arguments to include as log record attributes, or default <code>null</code> if none defined.
     */
    public Set<String> logstashStructuredArgumentAttributesIncluded;

    /**
     * Predicate for keys of Logstash structured arguments to include as log record attributes, or default <code>null</code> if none defined.
     */
    public IFn matchLogstashStructuredArgumentsAttribute;

    /**
     * Set of globs for keys of MDC attributes to exclude as log record attributes, or default <code>null</code> if none defined.
     * Takes precedence over <code>mdcAttributesIncluded</code>.
     */
    public Set<String> mdcAttributesExcluded;

    /**
     * Set of globs for keys of MDC attributes to include as log record attributes, or default <code>null</code> if none defined.
     */
    public Set<String> mdcAttributesIncluded;

    /**
     * Predicate for keys of MDC attributes to include as log record attributes, or default <code>null</code> if none defined.
     */
    public IFn matchMdcAttribute;

    private static final String ADAPTER_NAMESPACE = "steffan-westcott.clj-otel.adapter.logback";
    private final IFn append;
    private static final String GLOB_NAMESPACE = "steffan-westcott.clj-otel.instrumentation.api.config.glob";
    private final IFn maybeGlob;

    public CljOtelAppender() {
        Clojure.var("clojure.core", "require").invoke(Clojure.read(ADAPTER_NAMESPACE));
        append = Clojure.var(ADAPTER_NAMESPACE, "append");
        Clojure.var("clojure.core", "require").invoke(Clojure.read(GLOB_NAMESPACE));
        this.maybeGlob = Clojure.var(GLOB_NAMESPACE, "maybe-glob");
    }

    @Override
    public void start()
    {
        this.matchKeyValuePairAttribute = invokeMaybeGlob(keyValuePairAttributesExcluded, keyValuePairAttributesIncluded);
        this.matchLoggerContextAttribute = invokeMaybeGlob(loggerContextAttributesExcluded, loggerContextAttributesIncluded);
        this.matchLogstashMarkerAttribute = invokeMaybeGlob(logstashMarkerAttributesExcluded, logstashMarkerAttributesIncluded);
        this.matchLogstashStructuredArgumentsAttribute = invokeMaybeGlob(logstashStructuredArgumentAttributesExcluded, logstashStructuredArgumentAttributesIncluded);
        this.matchMdcAttribute = invokeMaybeGlob(mdcAttributesExcluded, mdcAttributesIncluded);
        super.start();
    }

    /**
     * Appends a <code>ILoggingEvent</code> by emitting a log record. If
     * <code>steffan-westcott.clj-otel.adapter.logback/initialize</code> has
     * been evaluated, the log record is emitted immediately (but not necessarily
     * exported). Otherwise, the log record is added to a queue of delayed emits.
     *
     * @param event ILoggingEvent to append
     */
    @Override
    protected void append(ILoggingEvent event) {
        append.invoke(this, event);
    }

    private static Set<String> trimmedSet(String string) {
        return (string == null) ? Collections.emptySet() :
                Arrays.stream(string.split(",")).map(String::trim).filter(s -> !s.isEmpty()).collect(Collectors.toSet());
    }

    public void setCaptureExperimentalAttributes(boolean captureExperimentalAttributes) {
        this.captureExperimentalAttributes = captureExperimentalAttributes;
    }

    public void setCaptureCodeAttributes(boolean captureCodeAttributes) {
        this.captureCodeAttributes = captureCodeAttributes;
    }

    public void setCaptureMarkerAttribute(boolean captureMarkerAttribute) {
        this.captureMarkerAttribute = captureMarkerAttribute;
    }

    public void setKeyValuePairAttributesExcluded(String keyValuePairAttrsExcludedString) {
        this.keyValuePairAttributesExcluded = trimmedSet(keyValuePairAttrsExcludedString);
    }

    public void setKeyValuePairAttributesIncluded(String keyValuePairAttrsIncludedString) {
        this.keyValuePairAttributesIncluded = trimmedSet(keyValuePairAttrsIncludedString);
    }

    public void setLoggerContextAttributesExcluded(String loggerContextAttrsExcludedString) {
        this.loggerContextAttributesExcluded = trimmedSet(loggerContextAttrsExcludedString);
    }

    public void setLoggerContextAttributesIncluded(String loggerContextAttrsIncludedString) {
        this.loggerContextAttributesIncluded = trimmedSet(loggerContextAttrsIncludedString);
    }

    public void setCaptureTemplate(boolean captureTemplate) {
        this.captureTemplate = captureTemplate;
    }

    public void setCaptureArguments(boolean captureArguments) {
        this.captureArguments = captureArguments;
    }

    public void setLogstashMarkerAttributesExcluded(String logstashMarkerAttrsExcludedString) {
        this.logstashMarkerAttributesExcluded = trimmedSet(logstashMarkerAttrsExcludedString);
    }

    public void setLogstashMarkerAttributesIncluded(String logstashMarkerAttrsIncludedString) {
        this.logstashMarkerAttributesIncluded = trimmedSet(logstashMarkerAttrsIncludedString);
    }

    public void setLogstashStructuredArgumentAttributesExcluded(String logstashStructuredArgumentAttrsExcludedString) {
        this.logstashStructuredArgumentAttributesExcluded = trimmedSet(logstashStructuredArgumentAttrsExcludedString);
    }

    public void setLogstashStructuredArgumentAttributesIncluded(String logstashStructuredArgumentAttrsIncludedString) {
        this.logstashStructuredArgumentAttributesIncluded = trimmedSet(logstashStructuredArgumentAttrsIncludedString);
    }

    public void setMdcAttributesExcluded(String mdcAttrsExcludedString) {
        this.mdcAttributesExcluded = trimmedSet(mdcAttrsExcludedString);
    }

    public void setMdcAttributesIncluded(String mdcAttrsIncludedString) {
        this.mdcAttributesIncluded = trimmedSet(mdcAttrsIncludedString);
    }

    public void setNumLogsCapturedBeforeOtelInstall(int _numLogsCapturedBeforeOtelInstall) {
        // ignored
    }

    private IFn invokeMaybeGlob(Collection<String> excluded, Collection<String> included) {
        HashMap<Object, Object> opts = new HashMap<>();
        opts.put(Clojure.read(":excluded"), excluded);
        opts.put(Clojure.read(":included"), included);
        return (IFn) maybeGlob.invoke(opts);
    }

}
