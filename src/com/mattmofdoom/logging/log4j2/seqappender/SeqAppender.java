package com.mattmofdoom.logging.log4j2.seqappender;

import com.google.gson.Gson;
import org.apache.logging.log4j.ThreadContext;
import org.apache.logging.log4j.core.Filter;
import org.apache.logging.log4j.core.Layout;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.plugins.*;
import org.apache.logging.log4j.core.config.plugins.validation.constraints.Required;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.URL;
import java.net.UnknownHostException;
import java.sql.Timestamp;
import java.util.*;

@SuppressWarnings("unused")
@Plugin(name = "SeqAppender", category = "Core", elementType = "appender", printObject = true)
public class SeqAppender extends AbstractAppender {
    private String Host = null;
    private String Url = "";
    private String ApiKey = "";
    private String AppName = "";

    protected SeqAppender(String name, Filter filter,
                          Layout<? extends Serializable> layout, String seqUrl, String seqApiKey, String appName) {
        super(name, filter, layout);
        Url = seqUrl;
        ApiKey = seqApiKey;
        AppName = appName;
    }

    @PluginBuilderFactory
    public static SeqAppenderBuilder newBuilder() {
        return new SeqAppenderBuilder();
    }


    public static class SeqAppenderBuilder
            implements org.apache.logging.log4j.core.util.Builder<SeqAppender> {
        @PluginBuilderAttribute("name")
        @Required(message = "SeqAppender: no name provided")
        private String name;

        @PluginElement("Layout")
        private Layout<String> layout;

        @PluginElement("Filter")
        private Filter filter;

        @PluginBuilderAttribute("SeqUrl")
        private String seqUrl;

        @PluginBuilderAttribute("SeqApiKey")
        private String seqApiKey;

        @PluginBuilderAttribute("AppName")
        private String appName;

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

        @Override
        public SeqAppender build() {
            return new SeqAppender(name, filter, layout, seqUrl, seqApiKey, appName);
        }
    }

    @PluginFactory
    public static SeqAppender createAppender(@PluginAttribute("name") String name,
                                             @PluginElement("Layout") Layout<? extends Serializable> layout,
                                             @PluginElement("Filters") Filter filter, @PluginAttribute("seqUrl") String seqUrl, @PluginAttribute("seqApiKey") String seqApiKey, @PluginAttribute("appName") String appName) {

        return new SeqAppender(name, filter, layout, seqUrl, seqApiKey, appName);
    }

    @Override
    public void append(LogEvent logEvent) {
        try {
            URL seqUrl = new URL(Url + "/api/events/raw");
            String seqApiKey = ApiKey;
            HttpURLConnection conn = (HttpURLConnection) seqUrl.openConnection();
            conn.setDoOutput(true);
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("X-Seq-ApiKey", seqApiKey);

            Gson jsonObject = new Gson();
            OutputStream os = conn.getOutputStream();
            os.write(jsonObject.toJson(getLog(logEvent)).getBytes());
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

    private SeqLog getLog(LogEvent logEvent)
    {
        SeqLog seqLog = new SeqLog();
        SeqEvents x = new SeqEvents();
        x.Level = logEvent.getLevel().toString();
        x.Timestamp = new Timestamp(logEvent.getTimeMillis());
        Map<String, Object> kv = new Hashtable<>();
        kv.put("MachineName", getHostname());
        x.MessageTemplate = logEvent.getMessage().getFormattedMessage();
        if (logEvent.getThrown() != null) {
            StringBuilder stack = new StringBuilder();
            var thrown = logEvent.getThrown();
            var stackTrace = thrown.getStackTrace();
            stack.append(thrown + "\r\n");
            for (int i = 1; i < stackTrace.length; i++)
                stack.append("at " + stackTrace[i].toString() + "\r\n");
            x.Exception = stack.toString();
        }

        x.Properties = kv;
        x.Properties.put("MachineName", getHostname().toUpperCase());
        x.Properties.put("ThreadId", logEvent.getThreadId());
        x.Properties.put("MethodName", logEvent.getThreadName());
        x.Properties.put("Class", logEvent.getLoggerFqcn());
        x.Properties.put("LoggerName", logEvent.getLoggerName());

        var context =logEvent.getContextData().toMap();
        Iterator contextEntries = context.entrySet().iterator();
        while (contextEntries.hasNext()) {
            Map.Entry pair = (Map.Entry)contextEntries.next();
            x.Properties.put(toPascalCase(pair.getKey().toString()), pair.getValue());
        }

        seqLog.Events.add(x);

        return seqLog;
    }

    private String getHostname() {
        if (Host == null) {
            try {
                InetAddress addr = InetAddress.getLocalHost();
                this.Host = addr.getHostName();
            } catch (UnknownHostException e) {
                this.Host = "localhost";
            }
        }
        return Host;
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

    class SeqLog {
        List<SeqEvents> Events;

        SeqLog() {
            Events = new ArrayList<>();
        }
    }

    class SeqEvents {

        Timestamp Timestamp;
        String MessageTemplate;
        String Level;
        String Exception;
        String EventId;
        String Message;

        Map<String, Object> Properties = new Hashtable<>();

        SeqEvents() {
        }

    }
}