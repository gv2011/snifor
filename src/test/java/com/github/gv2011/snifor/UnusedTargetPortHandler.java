package com.github.gv2011.snifor;

import static com.github.gv2011.util.ex.Exceptions.format;
import static com.github.gv2011.util.ex.Exceptions.wrap;
import static org.junit.Assert.fail;
import static org.slf4j.LoggerFactory.getLogger;

import java.io.IOException;
import java.util.concurrent.ThreadFactory;

import javax.net.ServerSocketFactory;

import org.slf4j.Logger;

final class UnusedTargetPortHandler extends TargetPortHandler{

  private static final Logger LOG = getLogger(UnusedTargetPortHandler.class);

  final static TargetPortHandler create(
    final ServerSocketFactory serverSocketFactory, final ThreadFactory threadFactory
  ) {
    final TargetPortHandler h = new UnusedTargetPortHandler(serverSocketFactory, threadFactory);
    h.start();
    return h;
  }

  private UnusedTargetPortHandler(final ServerSocketFactory serverSocketFactory, final ThreadFactory threadFactory) {
    super(serverSocketFactory, threadFactory);
  }

  @Override
  void run() {
    LOG.info("{} waiting.", port);
    try {
      socket.accept();
      fail(format("{} request received.", port));
    } catch (final IOException e) {
      if(closing.get()) LOG.info("{} closing (ignoring exception: {}).", port, e.toString());
      else throw wrap(e);
    }
    finally {
      LOG.info("{} finished.", port);
    }
  }


}
