package steffan_westcott.clj_otel.adapter.logback;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.Appender;
import ch.qos.logback.core.UnsynchronizedAppenderBase;
import ch.qos.logback.core.spi.AppenderAttachable;
import ch.qos.logback.core.spi.AppenderAttachableImpl;
import clojure.java.api.Clojure;
import clojure.lang.IFn;

import java.util.Iterator;

/**
 * Adds clj-otel bound context aware data to Logback MDC for nested appenders.
 * Provides trace ID, span ID, trace flags and baggage data.
 */
public class CljOtelMdcAppender extends UnsynchronizedAppenderBase<ILoggingEvent> implements AppenderAttachable<ILoggingEvent> {

    private static final String NAMESPACE = "steffan-westcott.clj-otel.adapter.logback";

    private final AppenderAttachableImpl<ILoggingEvent> aai = new AppenderAttachableImpl<>();
    private final IFn assoc_context_data;

    public CljOtelMdcAppender() {
        Clojure.var("clojure.core", "require").invoke(Clojure.read(NAMESPACE));
        assoc_context_data = Clojure.var(NAMESPACE, "assoc-context-data!");
    }

    @Override
    protected void append(ILoggingEvent event) {
        assoc_context_data.invoke(event);
        aai.appendLoopOnAppenders(event);
    }

    @Override
    public void addAppender(Appender<ILoggingEvent> appender) {
        aai.addAppender(appender);
    }

    @Override
    public Iterator<Appender<ILoggingEvent>> iteratorForAppenders() {
        return aai.iteratorForAppenders();
    }

    @Override
    public Appender<ILoggingEvent> getAppender(String name) {
        return aai.getAppender(name);
    }

    @Override
    public boolean isAttached(Appender<ILoggingEvent> appender) {
        return aai.isAttached(appender);
    }

    @Override
    public void detachAndStopAllAppenders() {
        aai.detachAndStopAllAppenders();
    }

    @Override
    public boolean detachAppender(Appender<ILoggingEvent> appender) {
        return aai.detachAppender(appender);
    }

    @Override
    public boolean detachAppender(String name) {
        return aai.detachAppender(name);
    }
}
