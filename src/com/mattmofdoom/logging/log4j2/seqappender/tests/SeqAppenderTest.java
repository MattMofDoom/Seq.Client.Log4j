package com.mattmofdoom.logging.log4j2.seqappender.tests;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.ThreadContext;
import org.junit.After;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.Arrays;
import java.util.UUID;

public class SeqAppenderTest {
    private static Logger Log = null;

    @BeforeClass
    public static void setUp() throws Exception {
        System.setProperty("log4j.configurationFile", "log4j2.xml");
        Log = LogManager.getLogger();

    }

    @After
    public void tearDown() throws Exception {
    }

    @Test
    public void DebugLog() {
        var l = Log();
        var x = new Thread() {
            public void run() {
                Log();
            }
        };
        try {
            x.start();
            x.join();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }


        assert l;
        assert x.isAlive() == false;
    }

    public static boolean Log() {
        ThreadContext.put("correlationId", UUID.randomUUID().toString());
        System.out.println("Thread Id for reference is " + Thread.currentThread().getId());
        Log.debug("test-" + Thread.currentThread().getId());
        Log.debug("argh-" + Thread.currentThread().getId());
        Log.error("eeek-" + Thread.currentThread().getId(), new Exception("Argh"));
        return true;
    }
}