package com.github.gv2011.snifor.nonblocking;

import static com.github.gv2011.util.Verify.verify;
import static com.github.gv2011.util.ex.Exceptions.call;
import static com.github.gv2011.util.icol.ICollections.toISet;
import static java.util.stream.Collectors.joining;
import static org.slf4j.LoggerFactory.getLogger;

import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.slf4j.Logger;

import com.github.gv2011.snifor.Snifor;
import com.github.gv2011.snifor.conf.Configuration;
import com.github.gv2011.util.icol.ICollections;
import com.github.gv2011.util.icol.ISet;
import com.github.gv2011.util.icol.Opt;

public final class SniforNonblocking implements Snifor{

  private static final Logger LOG = getLogger(SniforNonblocking.class);

  private final Selector selector;

  private final AtomicReference<Configuration> conf = new AtomicReference<>();
  private final Object lock = new Object();
  private final Semaphore confBack = new Semaphore(0);

  private final Thread thread;

  private final AtomicBoolean closeRequested = new AtomicBoolean(false);

  private volatile ISet<PortHandler> ports = ICollections.emptySet();

  public SniforNonblocking() {
    selector = call(Selector::open);
    final CountDownLatch started = new CountDownLatch(1);
    thread = new Thread(()->run(started), SniforNonblocking.class.getSimpleName());
    thread.start();
    call(()->started.await());
  }

  @Override
  public void configure(final Configuration configuration) {
    synchronized(lock) {
      verify(!closeRequested.get());
      conf.set(configuration);
      selector.wakeup();
      call(()->confBack.acquire());
    }
  }

  private void run(final CountDownLatch started) {
    boolean shouldRun = true;
    started.countDown();
    boolean closeRequested = false;
    while(shouldRun) {
      if(closeRequested) call(()->selector.selectNow());
      else call(()->selector.select());
      if(this.closeRequested.get()) closeRequested = true;
      if(closeRequested) {
        ports.forEach(PortHandler::close);
        shouldRun = ports.stream().anyMatch(h->!h.isFinished());
        if(shouldRun) LOG.info("Close requested, but connections pending.");
        else LOG.info("Close requested, terminating.");
      }
      final Opt<Configuration> config = Opt.ofNullable(conf.getAndSet(null));
      verify(!(config.isPresent() && closeRequested));
      config.ifPresent(this::doConfigure);
      log(selector);
      selector.selectedKeys().stream().filter(SelectionKey::isValid).forEach(k->{
        final SelectionHandler selectionHandler = (SelectionHandler) k.attachment();
        if(k.isValid()) {
          LOG.info("Calling handler {}.", selectionHandler);
          selectionHandler.handle(k);
        }
        else LOG.warn("Key {} has been invalidated, skipping.", toString(k));
      });
      Thread.yield();
      call(()->Thread.sleep(10));
    }
    LOG.info("Closing selector.");
    call(selector::close);
  }

  private void doConfigure(final Configuration configuration) {
    LOG.info("Configuring.");
    ports = configuration.ports().stream()
      .map(c->new PortHandler(selector, c))
      .collect(toISet())
    ;
    confBack.release();
  }



  private void log(final Selector selector) {
    LOG.debug(
      "Interests: {}",
      selector.keys().stream().map(this::toString).collect(joining(", "))
    );
    LOG.debug(
      "Selected: {}",
      selector.selectedKeys().stream().map(this::toStringReady).collect(joining(", "))
    );
  }

  private String toString(final SelectionKey key) {
    return String.valueOf(key.attachment()) +
      ( key.isValid()
        ?  "("+toString(key.interestOps())+")"
        :  "(invalid)"
      )
    ;
  }

  private String toStringReady(final SelectionKey key) {
    return String.valueOf(key.attachment()) +
      ( key.isValid()
        ? "("+toString(key.readyOps())+")"
        : "(invalid)"
      )
    ;
  }

  private String toString(final int ops) {
    return Interest.asSet(ops).toString();
  }

  @Override
  public ISet<PortHandler> ports(){
    return ports;
  }

  @Override
  public void close() {
    synchronized(lock) {
      final boolean requestedBefore = closeRequested.getAndSet(true);
      if(requestedBefore) {
        LOG.warn("{} is already closed - ignoring.", SniforNonblocking.class.getSimpleName());
      }else {
        LOG.info("Closing {}.", SniforNonblocking.class.getSimpleName());
        selector.wakeup();
        call(()->thread.join());
        LOG.info("Closed {}.", SniforNonblocking.class.getSimpleName());
      }
    }
  }
}