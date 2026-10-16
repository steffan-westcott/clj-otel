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
import java.util.stream.Collectors;

/**
 * Appends log events as OpenTelemetry log records for export.
 */
@Plugin(name = "CljOtel", category = Core.CATEGORY_NAME, elementType = Appender.ELEMENT_TYPE)
public class CljOtelAppender extends AbstractAppender {

    /**
     * If true, include <code>:source</code> location where log record occurred (default: false)
     */
    public final boolean captureCodeAttributes;

    /**
     * If true, include thread data (default: false).
     */
    public final boolean captureExperimentalAttributes;

    /**
     * Predicate for keys of <code>MapMessage</code> events to add to log record attributes, or default <code>null</code> where no attributes are added.
     */
    public final IFn matchMapMessageAttribute;

    /**
     * If true, include Log4j marker as attribute (default: false).
     */
    public final boolean captureMarkerAttribute;

    /**
     * If true, include message template as an attribute
     */
    public final boolean captureTemplate;

    /**
     * If true, include message arguments as an attribute
     */
    public final boolean captureArguments;

    /**
     * Predicate for keys of Log4j context data to add to log record attributes, or default <code>null</code> where no attributes are added.
     */
    public final IFn matchContextDataAttribute;

    private static final String ADAPTER_NAMESPACE = "steffan-westcott.clj-otel.adapter.log4j";
    private final IFn append;
    private static final String GLOB_NAMESPACE = "steffan-westcott.clj-otel.instrumentation.api.config.glob";
    private final IFn maybeGlob;

    protected CljOtelAppender(String name, Filter filter, boolean captureCodeAttributes, boolean captureExperimentalAttributes,
                              Set<String> mapMessageAttrsExcluded, Set<String> mapMessageAttrsIncluded,
                              boolean captureMarkerAttribute, boolean captureTemplate, boolean captureArguments,
                              Set<String> contextDataAttrsExcluded, Set<String> contextDataAttrsIncluded) {
        super(name, filter, null, true, Property.EMPTY_ARRAY);

        Clojure.var("clojure.core", "require").invoke(Clojure.read(ADAPTER_NAMESPACE));
        this.append = Clojure.var(ADAPTER_NAMESPACE, "append");
        Clojure.var("clojure.core", "require").invoke(Clojure.read(GLOB_NAMESPACE));
        this.maybeGlob = Clojure.var(GLOB_NAMESPACE, "maybe-glob");

        this.captureCodeAttributes = captureCodeAttributes;
        this.captureExperimentalAttributes = captureExperimentalAttributes;
        this.captureMarkerAttribute = captureMarkerAttribute;
        this.captureTemplate = captureTemplate;
        this.captureArguments = captureArguments;
        this.matchMapMessageAttribute = invokeMaybeGlob(mapMessageAttrsExcluded, mapMessageAttrsIncluded);
        this.matchContextDataAttribute = invokeMaybeGlob(contextDataAttrsExcluded, contextDataAttrsIncluded);
    }

    private static Set<String> trimmedSet(String string) {
        return (string == null) ? Collections.emptySet() :
                Arrays.stream(string.split(",")).map(String::trim).filter(s -> !s.isEmpty()).collect(Collectors.toSet());
    }

    private IFn invokeMaybeGlob(Collection<String> excluded, Collection<String> included) {
        HashMap<Object, Object> opts = new HashMap<>();
        opts.put(Clojure.read(":excluded"), excluded);
        opts.put(Clojure.read(":included"), included);
        return (IFn) maybeGlob.invoke(opts);
    }

    @PluginFactory
    public static CljOtelAppender createAppender(@PluginAttribute("name") String name,
                                                 @PluginElement("Filter") Filter filter,
                                                 @PluginAttribute("captureCodeAttributes") boolean codeAttrs,
                                                 @PluginAttribute("captureExperimentalAttributes") boolean experimentalAttrs,
                                                 @PluginAttribute("mapMessageAttributesExcluded") String mapMessageAttrsExcludedString,
                                                 @PluginAttribute("mapMessageAttributesIncluded") String mapMessageAttrsIncludedString,
                                                 @PluginAttribute("captureMarkerAttribute") boolean markerAttr,
                                                 @PluginAttribute("captureTemplate") boolean captureTemplate,
                                                 @PluginAttribute("captureArguments") boolean captureArguments,
                                                 @PluginAttribute("contextDataAttributesExcluded") String contextDataAttrsExcludedString,
                                                 @PluginAttribute("contextDataAttributesIncluded") String contextDataAttrsIncludedString,
                                                 @PluginAttribute("numLogsCapturedBeforeOtelInstall") int _numLogsCapturedBeforeOtelInstall) {
        return new CljOtelAppender(name, filter, codeAttrs, experimentalAttrs, trimmedSet(mapMessageAttrsExcludedString),
                trimmedSet(mapMessageAttrsIncludedString), markerAttr, captureTemplate, captureArguments,
                trimmedSet(contextDataAttrsExcludedString), trimmedSet(contextDataAttrsIncludedString));
    }

    /**
     * Appends a <code>LogEvent</code> by emitting a log record. If
     * <code>steffan-westcott.clj-otel.adapter.log4j/initialize</code> has
     * been evaluated, the log record is emitted immediately (but not necessarily
     * exported). Otherwise, the log record is added to a queue of delayed emits.
     *
     * @param event LogEvent to append
     */
    @Override
    public void append(LogEvent event) {
        append.invoke(this, event);
    }
}
