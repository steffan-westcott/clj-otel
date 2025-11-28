package steffan_westcott.clj_otel.adapter.logback;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.UnsynchronizedAppenderBase;
import clojure.java.api.Clojure;
import clojure.lang.IFn;

import java.util.Arrays;
import java.util.Collections;
import java.util.Set;
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
     * If true, include Logback KeyValuePairs as attributes (default: false).
     */
    public boolean captureKeyValuePairAttributes;

    /**
     * If true, include logger context properties as attributes (default: false)
     */
    public boolean captureLoggerContext;

    /**
     * If true, include message arguments as attributes (default: false)
     */
    public boolean captureArguments;

    /**
     * If true, include Logstash markers as attributes (default: false)
     */
    public boolean captureLogstashMarkerAttributes;

    /**
     * If true, include structured Logstash markers as attributes (default: false)
     */
    public boolean captureLogstashStructuredArguments;

    /**
     * if true, include all MDC as attributes (default: false)
     */
    public boolean captureAllMdcAttributes;

    /**
     * Set of keys of MDC to include as attributes, if <code>allMdcAttrs</code> is false (default: no keys)
     */
    public Set<String> captureMdcAttributes = Collections.emptySet();

    /**
     * If true, set log record event name as value of <code>event.name</code> attribute (default: false)
     */
    public boolean captureEventName = false;

    private static final String NAMESPACE = "steffan-westcott.clj-otel.adapter.logback";
    private final IFn append;

    public CljOtelAppender() {
        Clojure.var("clojure.core", "require").invoke(Clojure.read(NAMESPACE));
        append = Clojure.var(NAMESPACE, "append");
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

    public void setCaptureKeyValuePairAttributes(boolean captureKeyValuePairAttributes) {
        this.captureKeyValuePairAttributes = captureKeyValuePairAttributes;
    }

    public void setCaptureLoggerContext(boolean captureLoggerContext) {
        this.captureLoggerContext = captureLoggerContext;
    }

    public void setCaptureArguments(boolean captureArguments) {
        this.captureArguments = captureArguments;
    }

    public void setCaptureLogstashMarkerAttributes(boolean captureLogstashMarkerAttributes) {
        this.captureLogstashMarkerAttributes = captureLogstashMarkerAttributes;
    }

    public void setCaptureLogstashStructuredArguments(boolean captureLogstashStructuredArguments) {
        this.captureLogstashStructuredArguments = captureLogstashStructuredArguments;
    }

    public void setCaptureMdcAttributes(String attributes) {
        captureMdcAttributes = trimmedSet(attributes);
        captureAllMdcAttributes = captureMdcAttributes.size() == 1 && captureMdcAttributes.contains("*");
    }

    public void setCaptureEventName(boolean captureEventName) {
        this.captureEventName = captureEventName;
    }
}
