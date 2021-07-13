package com.mattmofdoom.logging.log4j2.seqappender;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.util.StdDateFormat;
import org.apache.logging.log4j.core.Filter;
import org.apache.logging.log4j.core.Layout;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Property;
import org.apache.logging.log4j.core.config.plugins.*;
import org.apache.logging.log4j.core.config.plugins.validation.constraints.Required;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.URL;
import java.net.UnknownHostException;
import java.sql.Timestamp;
import java.util.*;

@SuppressWarnings({"unused", "LocalCanBeFinal", "UnnecessaryThis", "UnqualifiedStaticUsage"})
@Plugin(name = "SeqAppender", category = "Core", elementType = "appender", printObject = true)
public class SeqAppender extends AbstractAppender {
    private String Host;
    private final String Url;
    private final String ApiKey;
    private final String AppName;
    private boolean IsCache;
    private final String CorrelationProperty;
    private final boolean IncludeCorrelation;
    @SuppressWarnings("FieldCanBeLocal")
    private int CacheTime;
    final Map<String, Object> Properties = new HashMap<>();
    private static SeqAppenderCache<String, Object> Cache;
    private static  String CorrelationId;


    protected SeqAppender(String name, Filter filter,
                          Layout<? extends Serializable> layout, String seqUrl, String seqApiKey, String appName, int cacheTime, String correlationProperty, boolean includeCorrelation, Property[] properties) {
        super(name, filter, layout, true, properties);
        this.Url = seqUrl;
        this.ApiKey = seqApiKey;
        this.AppName = appName;
        this.CorrelationProperty = Objects.requireNonNullElse(correlationProperty, "correlationId");
        this.IncludeCorrelation = includeCorrelation;

        if (this.IncludeCorrelation) {
            if (cacheTime < 0) {
                cacheTime = 600;
            }

            if (cacheTime > 0) {
                this.CacheTime = cacheTime;
                Cache = new SeqAppenderCache<>(cacheTime, 5);
                this.IsCache = true;
            }
        }

        for (Property property : properties) {
            this.Properties.put(property.getName(), property.getValue());
        }

        System.out.println("instantiated appender for thread " + Thread.currentThread().getId());

    }

    @PluginBuilderFactory
    public static SeqAppenderBuilder newBuilder() {
        return new SeqAppenderBuilder();
    }


    @SuppressWarnings({"LocalCanBeFinal", "UnnecessaryThis"})
    public static class SeqAppenderBuilder
            implements org.apache.logging.log4j.core.util.Builder<SeqAppender> {
        @PluginBuilderAttribute("name")
        @Required(message = "SeqAppender: no name provided")
        private String name;

        @PluginElement("Layout")
        private Layout<String> layout;

        @PluginElement("Filter")
        private Filter filter;

        @PluginElement("Properties")
        private Property[] properties;

        @PluginBuilderAttribute("SeqUrl")
        private String seqUrl;

        @PluginBuilderAttribute("SeqApiKey")
        private String seqApiKey;

        @PluginBuilderAttribute("AppName")
        private String appName;

        @PluginBuilderAttribute("CacheTime")
        private int cacheTime;

        @PluginBuilderAttribute("CorrelationProperty")
        private String correlationProperty;

        @PluginBuilderAttribute("IncludeCorrelation")
        private boolean includeCorrelation;

        public SeqAppenderBuilder setName(String value) {
            this.name = value;
            return this;
        }

        public SeqAppenderBuilder setLayout(Layout<String> value) {
            this.layout = value;
            return this;
        }

        public SeqAppenderBuilder setFilter(Filter value) {
            this.filter = value;
            return this;
        }

        public SeqAppenderBuilder setProperties(Property[] value) {
            this.properties = value;
            return this;
        }


        public SeqAppenderBuilder setSeqUrl(String value) {
            this.seqUrl = value;
            return this;
        }

        public SeqAppenderBuilder setSeqApiKey(String value) {
            this.seqApiKey = value;
            return this;
        }

        public SeqAppenderBuilder setAppName(String value) {
            this.appName = value;
            return this;
        }

        public SeqAppenderBuilder setCacheTime(int value) {
            this.cacheTime = value;
            return this;
        }

        public SeqAppenderBuilder setCorrelationProperty(String value) {
            this.correlationProperty = value;
            return this;
        }

        public SeqAppenderBuilder setIncludeCorrelation(boolean value) {
            this.includeCorrelation = value;
            return this;
        }

        @Override
        public SeqAppender build() {
            return new SeqAppender(this.name, this.filter, this.layout, this.seqUrl, this.seqApiKey, this.appName, this.cacheTime, this.correlationProperty, this.includeCorrelation, this.properties);
        }
    }

    @PluginFactory
    public static SeqAppender createAppender(@PluginAttribute("name") String name,
                                             @PluginElement("Layout") Layout<? extends Serializable> layout,
                                             @PluginElement("Filters") Filter filter, @PluginElement("IgnoreExceptions") Boolean ignoreExceptions,
                                             @PluginElement("Properties") Property[] properties, @PluginAttribute("seqUrl") String seqUrl,
                                             @PluginAttribute("seqApiKey") String seqApiKey, @PluginAttribute("appName") String appName,
                                             @PluginAttribute("cacheTime") int cacheTime, @PluginAttribute("correlationProperty") String correlationProperty, @PluginAttribute("includeCorrelation") boolean includeCorrelation) {

        return new SeqAppender(name, filter, layout, seqUrl, seqApiKey, appName, cacheTime, correlationProperty, includeCorrelation, properties);
    }

    @Override
    public void append(LogEvent logEvent) {
        try {
            URL seqUrl = new URL(this.Url + "/api/events/raw");
            HttpURLConnection conn = (HttpURLConnection) seqUrl.openConnection();
            conn.setDoOutput(true);
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("X-Seq-ApiKey", this.ApiKey);

            if (this.IncludeCorrelation) {
                var context = logEvent.getContextData().toMap();
                var threadId = String.valueOf(logEvent.getThreadId());
                if (this.HasKey(context, this.CorrelationProperty)) {
                    if (this.IsCache) {
                        if (context.get(this.CorrelationProperty) != Cache.get(threadId)) {
                            Cache.put(threadId, context.get(this.CorrelationProperty));
                        } else {
                            CorrelationId = context.get(this.CorrelationProperty);
                        }
                    }
                } else {
                    if (this.IsCache) {
                        if (Cache.get(threadId) == null) {
                            Cache.put(threadId, UUID.randomUUID().toString());
                        } else {
                            if (CorrelationId == null) {
                                CorrelationId = UUID.randomUUID().toString();
                            }
                        }

                    }
                }
            }

            var jsonObject = new ObjectMapper().disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS).setDateFormat(new StdDateFormat().withColonInTimeZone(true));
            OutputStream os = conn.getOutputStream();
            os.write(jsonObject.writeValueAsBytes(this.getLog(logEvent)));
            os.flush();

            if (conn.getResponseCode() != HttpURLConnection.HTTP_CREATED) {
                throw new RuntimeException("Failed : HTTP error code : "
                        + conn.getResponseCode());
            }

            BufferedReader br = new BufferedReader(new InputStreamReader(
                    (conn.getInputStream())));

            conn.disconnect();

        } catch (IOException e) {

            e.printStackTrace();

        }
    }

    public SeqLog getLog(LogEvent logEvent)
    {
        SeqEvents seqEvents = new SeqEvents();

        //Build the basic log event
        seqEvents.Level = logEvent.getLevel().toString();
        seqEvents.Timestamp = new Timestamp(logEvent.getTimeMillis());
        seqEvents.MessageTemplate = logEvent.getMessage().getFormattedMessage();

        //If an exception was thrown, build the stack trace and pass it
        if (logEvent.getThrown() != null) {
            StringBuilder stack = new StringBuilder();
            var thrown = logEvent.getThrown();
            var stackTrace = thrown.getStackTrace();
            stack.append(thrown).append("\r\n");
            for (int i = 1; i < stackTrace.length; i++)
                stack.append("at ").append(stackTrace[i].toString()).append("\r\n");
            seqEvents.Exception = stack.toString();
        }

        //If any properties were passed in config, add them and then add diagnostic info
        seqEvents.Properties = this.Properties;
        if (this.AppName == null || this.AppName.equals(""))
            seqEvents.Properties.put("AppName", logEvent.getLoggerName());
        else
            seqEvents.Properties.put("AppName", this.AppName);
        seqEvents.Properties.put("MachineName", this.getHostname().toUpperCase());
        seqEvents.Properties.put("ThreadId", logEvent.getThreadId());
        seqEvents.Properties.put("MethodName", logEvent.getThreadName());
        seqEvents.Properties.put("ProcessName", logEvent.getLoggerName());
        if (this.IncludeCorrelation) {
            if (this.IsCache) {
                seqEvents.Properties.put("CorrelationId", Cache.get(String.valueOf(logEvent.getThreadId())));
            } else {
                seqEvents.Properties.put("CorrelationId", CorrelationId);
            }
        }
        //Add any context mappings
        var context =logEvent.getContextData().toMap();
        for (Map.Entry<String, String> pair : context.entrySet()) {
            if (!pair.getKey().equalsIgnoreCase("correlationid"))
            seqEvents.Properties.put(toPascalCase(pair.getKey()), pair.getValue());
        }

        return new SeqLog(seqEvents);
    }

    private boolean HasKey(Map<String,String> entries, String value) {
        for (Map.Entry<String, String> pair : entries.entrySet()) {
            if (pair.getKey().equalsIgnoreCase(value))
                return true;
        }

        return false;
    }

    private String getHostname() {
        if (this.Host == null) {
            try {
                InetAddress addr = InetAddress.getLocalHost();
                this.Host = addr.getHostName();
            } catch (UnknownHostException e) {
                this.Host = "localhost";
            }
        }
        return this.Host;
    }

    static String toPascalCase(String text){

        if (text == null || text.isEmpty()) {
            return text;
        }

        StringBuilder converted = new StringBuilder();

        boolean convertNext = true;
        for (char ch : text.toCharArray()) {
            if (Character.isSpaceChar(ch)) {
                convertNext = true;
            } else if (convertNext) {
                ch = Character.toTitleCase(ch);
                convertNext = false;
            }

            converted.append(ch);
        }

        return converted.toString();
    }

    @SuppressWarnings({"LocalCanBeFinal", "UnnecessaryThis"})
    public static class SeqLog {
        public final List<SeqEvents> Events = new ArrayList<>();

        public SeqLog(SeqEvents events) {
            this.Events.add(events);
        }
    }

    public static class SeqEvents {

        public Timestamp Timestamp;
        public String MessageTemplate;
        public String Level;
        public String Exception;
        public String EventId;
        public String Message;

        public Map<String, Object> Properties = new Hashtable<>();

        SeqEvents() {
        }

    }
}