import com.mattmofdoom.logging.log4j2.seqappender.Cache;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@SuppressWarnings({"LocalCanBeFinal", "UnqualifiedStaticUsage", "UnnecessaryThis"})
public class SeqAppenderTest {
    private static Logger Log;
    private final Cache<String,Object> cache = new Cache<>(2,2);

    @BeforeClass
    public static void setUp() {
        System.setProperty("log4j.configurationFile", "log4j2-test.xml");
        Log = LogManager.getLogger();

    }

    @Test
    public void DebugLog() {
        var l = Log();
        var x = new Thread(SeqAppenderTest::Log);
        try {
            x.start();
            x.join();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }


        assert l;
        assert !x.isAlive();
    }

    @Test
    public void TestAddGetRemoveCache() {
        String threadId = String.valueOf(Thread.currentThread().getId());
        String correlationId = UUID.randomUUID().toString();
        this.cache.put(threadId, correlationId);
        var c = this.cache.get(threadId);
        assert correlationId == c;
        this.cache.remove(threadId);
        //noinspection AssertWithSideEffects
        assert this.cache.get(threadId) == null;
    }

    @Test
    public void TestExpireCache() throws InterruptedException {
        String threadId = String.valueOf(Thread.currentThread().getId());
        String correlationId = UUID.randomUUID().toString();
        this.cache.put(threadId, correlationId);
        var c = this.cache.get(threadId);
        assert correlationId == c;
        Thread.sleep(4000);
        //noinspection AssertWithSideEffects
        assert this.cache.get(threadId) == null;

    }

    @Test
    public void LogThreadCacheTest() throws InterruptedException {
        List<Thread> t = new ArrayList<>();
        for (var i = 0; i < 10; i++) {
            int finalI = i;
            t.add(new Thread(() -> LogRunner(finalI)));
            t.get(i).start();
        }

        Thread.sleep(4000);

        for (var i = 0; i < 10; i++) {
            assert !t.get(i).isAlive();
        }
    }

    @SuppressWarnings("SameReturnValue")
    public static boolean Log() {
        System.out.println("Thread Id for reference is " + Thread.currentThread().getId());
        Log.debug("test-" + Thread.currentThread().getId());
        Log.debug("argh-" + Thread.currentThread().getId());
        Log.error("eeek-" + Thread.currentThread().getId(), new Exception("Argh"));

        return true;
    }

    @SuppressWarnings("SameReturnValue")
    public static void LogRunner(int testNo) {
        for (var i = 1; i < 3; i++) {
            Log.debug("Test #" + testNo + ", Loop " + i + ", Thread"  + Thread.currentThread().getId() + ", correlationId {CorrelationId}");
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }
}