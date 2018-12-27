package com.github.gv2011.snifor.nonblocking;

import static com.github.gv2011.util.Verify.notNull;
import static com.github.gv2011.util.Verify.verify;
import static com.github.gv2011.util.Verify.verifyEqual;
import static org.slf4j.LoggerFactory.getLogger;

import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;

import org.slf4j.Logger;

import com.github.gv2011.snifor.ForwardAddressSelector;
import com.github.gv2011.snifor.SniAnalyser;
import com.github.gv2011.snifor.SniAnalyser.Result;
import com.github.gv2011.snifor.SniAnalyser.ResultType;
import com.github.gv2011.snifor.conf.Hostname;
import com.github.gv2011.snifor.conf.SocketAddress;
import com.github.gv2011.util.ann.Nullable;
import com.github.gv2011.util.icol.Opt;

final class SourceSocketHandler extends AbstractSocketHandler{

  private static final Logger LOG = getLogger(SourceSocketHandler.class);

  private final SniAnalyser sniAnalyser;
  private final ForwardAddressSelector forwardAddressSelector;
  private @Nullable TargetSocketHandler targetSocketHandler;



  SourceSocketHandler(
    final SocketHandler connectionHandler,
    final SocketChannel sourceSocket,
    final SniAnalyser sniAnalyser,
    final ForwardAddressSelector forwardAddressSelector
  ) {
    super(connectionHandler, sourceSocket);
    this.sniAnalyser = sniAnalyser;
    this.forwardAddressSelector = forwardAddressSelector;
    connected = true;
    analysing = true;
  }

  void setTargetSocketHandler(final TargetSocketHandler targetSocketHandler) {
    verify(this.targetSocketHandler==null);
    this.targetSocketHandler = targetSocketHandler;
  }

  @Override
  TargetSocketHandler other() {
    return notNull(targetSocketHandler);
  }

  @Override
  public void handle(final SelectionKey key) {
    verifyEqual(key, this.key);
    verify(!key.isConnectable());
    if(key.isReadable()) readFromSocket();
    if(key.isWritable()) writeToSocket();
  }

  @Override
  protected void eosWhileAnalysing() {
    other().connect(forwardAddressSelector.getDefaultAddress());
  }

  @Override
  protected void tryAnalyse(final int oldPosition) {
    readBuffer.position(0);
    readBuffer.limit(validBytesCount);
    final Result result = sniAnalyser.analyse(readBuffer);
    readBuffer.limit(validBytesCount);
    readBuffer.position(oldPosition);
    if(result.type().equals(ResultType.MORE_DATA_NEEDED)) {
      LOG.info("Analysis: more data needed (available: {}).", validBytesCount);
      if(readBufferIsFull()) {
        increaseReadBufferSize();
        Interest.READ.set(key);
      }
    }
    else {
      analysing = false;
      final Opt<Hostname> hostname = result.type().equals(ResultType.FOUND_NAME)
        ? Opt.of(result.name())
        : Opt.empty()
      ;
      final SocketAddress targetAddress =
        hostname.map(forwardAddressSelector::getAddress).orElseGet(forwardAddressSelector::getDefaultAddress)
      ;
      LOG.info(
        "Analysis: hostname: {}, target address: {}.",
        hostname.map(Hostname::toString).orElse("n/a"),
        targetAddress
      );
      other().connect(targetAddress);
    }
  }

  private void increaseReadBufferSize() {
    final ByteBuffer old = readBuffer;
    readBuffer = ByteBuffer.allocate(old.capacity()+4096);
    old.position(0); old.limit(validBytesCount);
    readBuffer.put(old);
  }

  @Override
  protected final void finished() {
    key.cancel();
    connectionHandler.finishedSource();
  }

  void startReading() {
    Interest.READ.set(key);
  }

  @Override
  public void close() {
    key.cancel();
  }

}
