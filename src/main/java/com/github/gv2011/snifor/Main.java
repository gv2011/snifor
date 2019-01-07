package com.github.gv2011.snifor;

import static com.github.gv2011.util.ex.Exceptions.call;

import java.util.concurrent.CountDownLatch;

import com.github.gv2011.snifor.blocking.SniforBlocking;
import com.github.gv2011.snifor.conf.Configuration;
import com.github.gv2011.util.BeanUtils;
import com.github.gv2011.util.FileUtils;
import com.github.gv2011.util.json.JsonUtils;

public final class Main {

  private static final CountDownLatch done = new CountDownLatch(1);
  private static Snifor snifor;
  private static Thread main;

  private Main() {}

  public static void main(final String[] args) throws InterruptedException {
    main = Thread.currentThread();
    Runtime.getRuntime().addShutdownHook(new Thread(Main::shutdown, "shutdown"));
    snifor = new SniforBlocking(Thread::new);
    snifor.configure(
      BeanUtils.typeRegistry().beanType(Configuration.class).parse(
        JsonUtils.jsonFactory().deserialize(
          FileUtils.readText("conf.json")
        )
      )
    );
    done.await();
    snifor.close();
  }

  private static void shutdown() {
    done.countDown();
    call(()->main.join());
  }

}
