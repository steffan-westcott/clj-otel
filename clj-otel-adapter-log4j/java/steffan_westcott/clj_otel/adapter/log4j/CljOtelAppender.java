package steffan_westcott.clj_otel.adapter.log4j;

import clojure.java.api.Clojure;
import clojure.lang.IFn;
import org.apache.logging.log4j.core.Appender;
import org.apache.logging.log4j.core.Core;
import org.apache.logging.log4j.core.Filter;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Property;
import org.apache.logging.log4j.core.config.plugins.Plugin;
import org.apache.logging.log4j.core.config.plugins.PluginAttribute;
import org.apache.logging.log4j.core.config.plugins.PluginElement;
import org.apache.logging.log4j.core.config.plugins.PluginFactory;

import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Collectors;

/**
 * Appends log events as OpenTelemetry log records for export.
 */
@Plugin(name = "CljOtel", category = Core.CATEGORY_NAME, elementType = Appender.ELEMENT_TYPE)
public class CljOtelAppender extends AbstractAppender {

    /**
     * If true, CljOtelAppender instances are initialized.
     */
    public static volatile boolean initialized = false;

    /**
     * Delayed emits while appenders remain uninitialized.
     */
    public static final ConcurrentLinkedQueue<Object> delayedEmits = new ConcurrentLinkedQueue<>();

    /**
     * Protects access to <code>initialized</code> and <code>delayedEmits</code>.
     */
    public static final ReadWriteLock lock = new ReentrantReadWriteLock();

    /**
     * If true, include <code>:source</code> location where log record occurred (default: false)
     */
    public final boolean codeAttrs;

    /**
     * If true, include thread data (default: false).
     */
    public final boolean experimentalAttrs;

    /**
     * If true and event is a <code>MapMessage</code>, add content to log record attributes and set log record body to <code>message</code> value (default: false).
     */
    public final boolean mapMessageAttrs;

    /**
     * If true, include Log4j marker as attribute (default: false).
     */
    public final boolean markerAttr;

    /**
     * If true, include all Log4j context data as attributes (default: false).
     */
    public final boolean allCdataAttrs;

    /**
     * Set of keys of Log4j context data to include as attributes, if <code>allCdataAttrs</code> is false (default: no keys).
     */
    public final Set<String> cdataAttrs;

    /**
     * If true, set log record event name as value of <code>event.name</code> in Log4j context data (default: false)."
     */
    public final boolean eventName;

    private static final String NAMESPACE = "steffan-westcott.clj-otel.adapter.log4j";
    private final IFn append;

    protected CljOtelAppender(String name, Filter filter, boolean codeAttrs, boolean experimentalAttrs,
                              boolean mapMessageAttrs, boolean markerAttr, Set<String> cdataAttrs, boolean eventName) {
        super(name, filter, null, true, Property.EMPTY_ARRAY);
        this.codeAttrs = codeAttrs;
        this.experimentalAttrs = experimentalAttrs;
        this.mapMessageAttrs = mapMessageAttrs;
        this.markerAttr = markerAttr;
        this.allCdataAttrs = cdataAttrs.size() == 1 && cdataAttrs.contains("*");
        this.cdataAttrs = cdataAttrs;
        this.eventName = eventName;
        Clojure.var("clojure.core", "require").invoke(Clojure.read(NAMESPACE));
        append = Clojure.var(NAMESPACE, "append");
    }

    private static Set<String> trimmedSet(String string) {
        return (string == null) ? Collections.emptySet() :
                Arrays.stream(string.split(",")).map(String::trim).filter(s -> !s.isEmpty()).collect(Collectors.toSet());
    }

    @PluginFactory
    public static CljOtelAppender createAppender(@PluginAttribute("name") String name,
                                                 @PluginElement("Filter") Filter filter,
                                                 @PluginAttribute("captureCodeAttributes") boolean codeAttrs,
                                                 @PluginAttribute("captureExperimentalAttributes") boolean experimentalAttrs,
                                                 @PluginAttribute("captureMapMessageAttributes") boolean mapMessageAttrs,
                                                 @PluginAttribute("captureMarkerAttribute") boolean markerAttr,
                                                 @PluginAttribute("captureContextDataAttributes") String cdataAttrsString,
                                                 @PluginAttribute("captureEventName") boolean eventName) {
        return new CljOtelAppender(name, filter, codeAttrs, experimentalAttrs, mapMessageAttrs, markerAttr,
                trimmedSet(cdataAttrsString), eventName);
    }

    /**
     * Appends a <code>LogEvent</code> by emitting a log record. If `CljOtelAppender`
     * instances have been initialized, the log record is emitted immediately (but
     * not necessarily exported). Otherwise, the log record is added to a queue of
     * delayed emits.
     * @param event LogEvent to append
     */
    @Override
    public void append(LogEvent event) {
        append.invoke(this, event);
    }
}
