package com.github.gv2011.snifor.nonblocking;

import static com.github.gv2011.util.Verify.verify;
import static com.github.gv2011.util.ex.Exceptions.call;
import static org.slf4j.LoggerFactory.getLogger;

import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.function.Consumer;

import org.slf4j.Logger;

import com.github.gv2011.snifor.ForwardAddressSelector;
import com.github.gv2011.snifor.SniAnalyser;

class SocketHandler {

  @SuppressWarnings("unused")
  private static final Logger LOG = getLogger(SocketHandler.class);

  private final Consumer<SocketHandler> finishedCallback;

  private boolean finishedForward;
  private boolean finishedBack;
  final Selector selector;

  private final SourceSocketHandler sourceSocketHandler;

  private final TargetSocketHandler targetSocketHandler;

  SocketHandler(
    final Selector selector,
    final SocketChannel sourceSocket,
    final SniAnalyser sniAnalyser,
    final ForwardAddressSelector forwardAddressSelector,
    final Consumer<SocketHandler> finishedCallback
  ) {
    this.selector = selector;
    this.finishedCallback = finishedCallback;
    call(()->sourceSocket.configureBlocking(false));
    verify(sourceSocket.isConnected());

    sourceSocketHandler =
      new SourceSocketHandler(this, sourceSocket, sniAnalyser, forwardAddressSelector)
    ;
    targetSocketHandler = new TargetSocketHandler(this, sourceSocketHandler);
    sourceSocketHandler.setTargetSocketHandler(targetSocketHandler);

    sourceSocketHandler.startReading();
  }

  void finishedSource() {
    finishedForward = true;
    if(finishedBack) finished();
  }

  void finishedTarget() {
    finishedBack = true;
    if(finishedForward) finished();
  }

  private void finished() {
    targetSocketHandler.close();
    sourceSocketHandler.close();
    finishedCallback.accept(this);
  }

}
