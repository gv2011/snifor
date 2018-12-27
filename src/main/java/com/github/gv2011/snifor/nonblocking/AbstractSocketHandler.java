package com.github.gv2011.snifor.nonblocking;

import static com.github.gv2011.util.Verify.verify;
import static com.github.gv2011.util.ex.Exceptions.call;
import static org.slf4j.LoggerFactory.getLogger;

import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;

import org.slf4j.Logger;

import com.github.gv2011.util.AutoCloseableNt;
import com.github.gv2011.util.icol.Opt;

abstract class AbstractSocketHandler implements SelectionHandler, AutoCloseableNt{

  private static final Logger LOG = getLogger(AbstractSocketHandler.class);

  protected final SocketHandler connectionHandler;
  protected final SocketChannel socket;
  protected final SelectionKey key;
  protected ByteBuffer readBuffer;

  protected boolean eos = false;
  protected int validBytesCount = 0;

  protected boolean eosBack = false;
  protected boolean outputClosed = false;
  protected Opt<ByteBuffer> writeBuffer = Opt.empty();

  protected boolean connected;

  protected boolean analysing;



  AbstractSocketHandler(
    final SocketHandler connectionHandler,
    final SocketChannel socket
  ) {
    this.connectionHandler = connectionHandler;
    this.socket = socket;
    readBuffer = ByteBuffer.allocate(4096);
    readBuffer.limit(0);
    verify(!readBuffer.hasRemaining());
    key = call(()->socket.register(connectionHandler.selector, 0, this));
  }

  abstract AbstractSocketHandler other();

  /**
   * There is data available for writing to this socket.
   */
  final void dataAvailable(final ByteBuffer buffer) {
    writeBuffer = Opt.of(buffer);
    if(connected && buffer.hasRemaining()) {
      Interest.WRITE.set(key);
    }
  }

  protected final void readFromSocket() {
    if(!canRead()) {
      verify(!analysing);
      if(eos) LOG.info("{}: EOS reached before - not reading.", this);
      else if(readBufferIsFull()) LOG.info("{}: Cannot read, buffer is full.", this);
      Interest.READ.clear(key);
    }
    else {
      final int oldPosition = getOldPosition(readBuffer);
      readBuffer.limit(readBuffer.capacity());
      readBuffer.position(validBytesCount);
      verify(readBuffer.hasRemaining());
      final int count = call(()->socket.read(readBuffer));
      if(count==-1) {
        LOG.info("{}: End of input.", this);
        readBuffer.limit(validBytesCount);
        readBuffer.position(oldPosition);
        eos = true;
        if(analysing) {
          analysing = false;
          eosWhileAnalysing();
        }
        other().eos();
        Interest.READ.clear(key);
        if(outputClosed) finished();
      }
      else if (count==0){
        readBuffer.limit(validBytesCount);
        readBuffer.position(oldPosition);
        LOG.info("{}: Nothing read.", this);
      }
      else {
        validBytesCount += count;
        verify(validBytesCount<=readBuffer.capacity());
        readBuffer.limit(validBytesCount);
        readBuffer.position(oldPosition);
        LOG.info("{}: {} bytes read.", this, count);
        if(readBufferIsFull()) {
          Interest.READ.clear(key);
          LOG.info("{}: buffer full.", this);
        }
      }
      if(analysing) tryAnalyse(oldPosition);
      if(!analysing && count!=0) other().dataAvailable(readBuffer);
    }
  }

  protected void tryAnalyse(final int oldPosition) {};

  protected void eosWhileAnalysing() {}

  /**
   * The end of data for writing to this socket has been reached.
   */
  final void eos() {
    eosBack = true;
    if(connected) Interest.WRITE.set(key);
  }

  protected final int getOldPosition(final ByteBuffer buffer) {
    return buffer.hasRemaining() ? buffer.position() : 0;
  }

  final boolean readBufferIsFull() {
    return validBytesCount == readBuffer.capacity();
  }

  final boolean canRead() {
    return !eos && !readBufferIsFull();
  }

  final void writeToSocket() {
    if(outputClosed) {
      LOG.warn("{}: Not writing, output is closed.", this);
    }
    else {
      if(!writeBuffer.isPresent() && !eosBack) {
        LOG.info("Not writing, no data is available.");
        Interest.WRITE.clear(key);
      }
      else {
        writeBuffer.ifPresent(b->{
          final int count = call(()->socket.write(b));
          LOG.info("{}: {} bytes written.", this, count);
          if(count>0)other().bufferDataWritten();
        });
        if(eosBack) {
          call(()->socket.shutdownOutput());
          outputClosed = true;
          Interest.WRITE.clear(key);
          LOG.info("{}: output closed.", this);
          if(eos) finished();
        }
      }
    }
  }

  protected abstract void finished();

  /**
   * Data from this readBuffer has been written by the other handler.
   */
  final void bufferDataWritten() {
    if(!readBuffer.hasRemaining()) {
      validBytesCount = 0;
      LOG.info("{}: buffer has been fully written and is available for reading again.", this);
    }
    if(!eos) Interest.READ.set(key);
  }

  @Override
  public final String toString() {
    return getClass().getSimpleName();
  }

}
