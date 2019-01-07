package com.github.gv2011.snifor.blocking;

import static com.github.gv2011.util.Verify.verify;
import static com.github.gv2011.util.ex.Exceptions.call;
import static org.slf4j.LoggerFactory.getLogger;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.util.concurrent.ThreadFactory;
import java.util.function.Consumer;

import org.slf4j.Logger;

import com.github.gv2011.snifor.ForwardAddressSelector;
import com.github.gv2011.snifor.SniAnalyser;
import com.github.gv2011.snifor.SniAnalyser.Result;
import com.github.gv2011.snifor.SniAnalyser.ResultType;
import com.github.gv2011.snifor.conf.Hostname;
import com.github.gv2011.snifor.conf.SocketAddress;
import com.github.gv2011.util.icol.Opt;

class SocketHandler {

  private static final Logger LOG = getLogger(SocketHandler.class);

  private final Consumer<SocketHandler> finishedCallback;

  private final Socket sourceSocket;

  private final SniAnalyser sniAnalyser;

  private final ForwardAddressSelector forwardAddressSelector;

  SocketHandler(
    final Socket sourceSocket,
    final SniAnalyser sniAnalyser,
    final ForwardAddressSelector forwardAddressSelector,
    final Consumer<SocketHandler> finishedCallback,
    final ThreadFactory threadFactory
  ) {
    this.sourceSocket = sourceSocket;
    this.sniAnalyser = sniAnalyser;
    this.forwardAddressSelector = forwardAddressSelector;
    this.finishedCallback = finishedCallback;
    threadFactory.newThread(this::run).start();
  }

  private void run() {
    try {
      final InputStream in = call(sourceSocket::getInputStream);
      Opt<Result> result = Opt.empty();
      boolean analysing = true;
      int validBytes = 0;
      byte[] buffer = new byte[1024];
      while(analysing) {
        verify(validBytes<buffer.length);
        final int count;
        {
          final byte[] b = buffer;
          final int v = validBytes;
          count = call(()->in.read(b, v, b.length - v));
        }
        if(count==-1) {
          LOG.debug("End of input - no analysis possible.");
          analysing = false;
        }
        else {
          verify(count>0);
          validBytes+=count;
          final Result r = sniAnalyser.analyse(ByteBuffer.wrap(buffer, 0, validBytes));
          if(r.type().equals(ResultType.MORE_DATA_NEEDED)) {
            if(validBytes==buffer.length) buffer = increaseBuffer(buffer);
          }
          else {
            result = Opt.of(r);
            analysing = false;
          }
        }
      }
      final Opt<Hostname> hostname =
        result.flatMap(r->r.type().equals(ResultType.FOUND_NAME) ? Opt.of(r.name()) : Opt.empty())
      ;
      final SocketAddress targetAddress =
        hostname.map(forwardAddressSelector::getAddress).orElseGet(forwardAddressSelector::getDefaultAddress)
      ;
      LOG.info("Analysis: {} {} {}", result, hostname, targetAddress);
      final Socket target = new Socket();
      call(()->target.connect(targetAddress.toInetSocketAddress()));
      final Thread back = new Thread(()->copyBack(call(target::getInputStream), sourceSocket));
      back.start();
      final OutputStream out = call(target::getOutputStream);
      {
        final byte[] b = buffer;
        final int v = validBytes;
        call(()->out.write(b, 0, v));
      }
      call(()->in.transferTo(out));
      call(target::shutdownOutput);
      call(()->back.join());
      call(()->target.close());
    }
    finally {finishedCallback.accept(this);}
  }

  private void copyBack(final InputStream in, final Socket sourceSocket) {
    call(()->in.transferTo(sourceSocket.getOutputStream()));
    call(sourceSocket::shutdownOutput);
  }

  private byte[] increaseBuffer(final byte[] buffer) {
    final byte[] newBuffer = new byte[buffer.length+1024];
    System.arraycopy(buffer, 0, newBuffer, 0, buffer.length);
    return newBuffer;
  }


}
