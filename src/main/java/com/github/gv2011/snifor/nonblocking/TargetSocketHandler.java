package com.github.gv2011.snifor.nonblocking;

import static com.github.gv2011.util.Verify.verifyEqual;
import static com.github.gv2011.util.ex.Exceptions.call;
import static org.slf4j.LoggerFactory.getLogger;

import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;

import org.slf4j.Logger;

import com.github.gv2011.snifor.conf.SocketAddress;

final class TargetSocketHandler extends AbstractSocketHandler{

  private static final Logger LOG = getLogger(TargetSocketHandler.class);

  private final AbstractSocketHandler sourceSocketHandler;

  public TargetSocketHandler(
    final SocketHandler connectionHandler,
    final AbstractSocketHandler sourceSocketHandler
  ) {
    super(connectionHandler, createSocket());
    this.sourceSocketHandler = sourceSocketHandler;
    analysing = false;
  }

  private static SocketChannel createSocket() {
    final SocketChannel socket = call(()->SocketChannel.open());
    call(()->socket.configureBlocking(false));
    return socket;
  }

  @Override
  AbstractSocketHandler other() {
    return sourceSocketHandler;
  }

  @Override
  public void handle(final SelectionKey key) {
    verifyEqual(key, this.key);
    if(key.isConnectable()) finishConnect();
    if(key.isReadable()) readFromSocket();
    if(key.isWritable()) writeToSocket();
  }

  void connect(final SocketAddress targetAddress) {
    final boolean connected = call(()->socket.connect(targetAddress.toInetSocketAddress()));
    if(connected) {
      LOG.info("Immediately connected.");
      connected();
    }
    else{
      Interest.CONNECT.set(key);
      LOG.info("Connecting.");
    }
  }

  private void finishConnect() {
    final boolean connected = call(()->socket.finishConnect());
    if(connected) {
      Interest.CONNECT.clear(key);
      if(this.connected) {
        LOG.info("Already connected.");
      }
      else {
        LOG.info("Connected.");
        connected();
      }
    }
  }

  private void connected() {
    connected = true;
    Interest.READ.set(key);
    if(eosBack || writeBuffer.isPresent()) Interest.WRITE.set(key);
  }

  @Override
  protected final void finished() {
    connectionHandler.finishedTarget();
  }

  @Override
  public void close() {
    call(()->socket.close());
    key.cancel();
  }


}
