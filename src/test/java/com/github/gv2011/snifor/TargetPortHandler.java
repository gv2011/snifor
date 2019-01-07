package com.github.gv2011.snifor;

import static com.github.gv2011.util.Verify.verify;
import static com.github.gv2011.util.ex.Exceptions.call;
import static com.github.gv2011.util.ex.Exceptions.format;

import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.net.ServerSocketFactory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.gv2011.util.AutoCloseableNt;

abstract class TargetPortHandler implements AutoCloseableNt{


  private static final Logger LOG = LoggerFactory.getLogger(TargetPortHandler.class);

  final ServerSocket socket;
  final int port;
  private final Thread thread;
  final AtomicBoolean closing = new AtomicBoolean();

  TargetPortHandler(final ServerSocketFactory serverSocketFactory, final ThreadFactory threadFactory){
    socket = call(()->serverSocketFactory.createServerSocket());
    call(()->socket.bind(new InetSocketAddress(0)));
    port = socket.getLocalPort();
    LOG.info(
      "{} bound to IP {} and port {}.",
      getClass().getSimpleName(), socket.getInetAddress().getHostAddress(), port
    );
    thread = threadFactory.newThread(this::run);
    thread.setName("target-"+port);
  }

  final void start() {
    thread.start();
  }

  abstract void run();

  @Override
  public final void close() {
    verify(!closing.getAndSet(true));
    LOG.info("{}: closing.", this);
    call(()->socket.close());
    call(()->thread.join());
    LOG.info("{}: closed.", this);
  }

  @Override
  public String toString() {
    return format("{}({})", getClass().getSimpleName(), port);
  }

  final InetSocketAddress getAddress() {
    return new InetSocketAddress(socket.getInetAddress(), port);
  }




}
