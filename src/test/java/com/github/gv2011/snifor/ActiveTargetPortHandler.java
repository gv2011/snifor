package com.github.gv2011.snifor;

import static com.github.gv2011.util.Verify.verifyEqual;
import static com.github.gv2011.util.ex.Exceptions.call;
import static org.slf4j.LoggerFactory.getLogger;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.concurrent.ThreadFactory;

import javax.net.ServerSocketFactory;

import org.slf4j.Logger;

import com.github.gv2011.util.bytes.ByteUtils;
import com.github.gv2011.util.bytes.Bytes;

final class ActiveTargetPortHandler extends TargetPortHandler{

  private static final Logger LOG = getLogger(ActiveTargetPortHandler.class);

  final static TargetPortHandler create(
    final ServerSocketFactory serverSocketFactory, final ThreadFactory threadFactory
  ) {
    final TargetPortHandler h = new ActiveTargetPortHandler(serverSocketFactory, threadFactory);
    h.start();
    return h;
  }

  private ActiveTargetPortHandler(final ServerSocketFactory serverSocketFactory, final ThreadFactory threadFactory) {
    super(serverSocketFactory, threadFactory);
  }

  @Override
  void run() {
    LOG.info("{} waiting.", port);
    final Socket socket = call(()->this.socket.accept());
    LOG.info("{} accepted connection.", port);
    final InputStream in = call(()->socket.getInputStream());
    final Bytes bytes = ByteUtils.copyFromStream(in, AbstractSniforTest.DATA_FORWARD.size());
    verifyEqual(bytes, AbstractSniforTest.DATA_FORWARD);
    LOG.info("{} read expected input.", port);
    final OutputStream out = call(()->socket.getOutputStream());
    AbstractSniforTest.DATA_BACK.write(out);
    call(()->out.flush());
    LOG.info("{} sent output.", port);
    verifyEqual(call(()->in.read()), -1);
    LOG.info("{} detected end of input.", port);
    call(()->socket.shutdownOutput());
    LOG.info("{} closed output.", port);
  }

}
