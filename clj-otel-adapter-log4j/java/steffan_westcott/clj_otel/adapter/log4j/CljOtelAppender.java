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

    private static final String NAMESPACE = "steffan-westcott.clj-otel.adapter.log4j";
    private final Map<Object, Object> opts;
    private final IFn emit;

    protected CljOtelAppender(String name, Filter filter, boolean codeAttrs, boolean experimentalAttrs,
                              boolean mapMessageAttrs, boolean markerAttr, Set<String> cdataAttrs, boolean eventName) {
        super(name, filter, null, true, Property.EMPTY_ARRAY);
        Map<Object, Object> opts = new HashMap<>();
        opts.put(Clojure.read(":code-attrs?"), codeAttrs);
        opts.put(Clojure.read(":experimental-attrs?"), experimentalAttrs);
        opts.put(Clojure.read(":map-message-attrs?"), mapMessageAttrs);
        opts.put(Clojure.read(":marker-attr?"), markerAttr);
        opts.put(Clojure.read(":all-cdata-attrs?"), cdataAttrs.size() == 1 && cdataAttrs.contains("*"));
        opts.put(Clojure.read(":cdata-attrs"), cdataAttrs);
        opts.put(Clojure.read(":event-name?"), eventName);
        this.opts = opts;
        Clojure.var("clojure.core", "require").invoke(Clojure.read(NAMESPACE));
        emit = Clojure.var(NAMESPACE, "emit");
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

    @Override
    public void append(LogEvent event) {
        emit.invoke(event, opts);
    }
}
