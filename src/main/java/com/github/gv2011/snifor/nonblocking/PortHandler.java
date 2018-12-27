package com.github.gv2011.snifor.nonblocking;

import static com.github.gv2011.util.Verify.verify;
import static com.github.gv2011.util.Verify.verifyEqual;
import static com.github.gv2011.util.ex.Exceptions.call;
import static org.slf4j.LoggerFactory.getLogger;

import java.net.InetSocketAddress;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.HashSet;
import java.util.Set;

import org.slf4j.Logger;

import com.github.gv2011.snifor.ForwardAddressSelector;
import com.github.gv2011.snifor.ForwardAddressSelectorImp;
import com.github.gv2011.snifor.SniAnalyser;
import com.github.gv2011.snifor.TlsExplorer;
import com.github.gv2011.snifor.conf.PortConfig;
import com.github.gv2011.util.AutoCloseableNt;
import com.github.gv2011.util.icol.Opt;


final class PortHandler implements AutoCloseableNt, com.github.gv2011.snifor.PortHandler{

  private static final Logger LOG = getLogger(PortHandler.class);

  private final ServerSocketChannel serverSocketChannel;
  private final SelectionKey key;
  private final Selector selector;
  private final SniAnalyser sniAnalyser;
  private final ForwardAddressSelector forwardAddressSelector;

  private final Set<SocketHandler> socketHandlers = new HashSet<>();

  private boolean closing;

  PortHandler(final Selector selector, final PortConfig portConfig) {
    this.selector = selector;
    forwardAddressSelector = new ForwardAddressSelectorImp(portConfig.forwards(), portConfig.defaultAddress());
    sniAnalyser = new TlsExplorer();
    serverSocketChannel = call(ServerSocketChannel::open);
    call(()->serverSocketChannel.bind(portConfig.serverSocket().toInetSocketAddress()));
    call(()->serverSocketChannel.configureBlocking(false));
    key = call(()->serverSocketChannel.register(
      selector,
      SelectionKey.OP_ACCEPT,
      SelectionHandler.named(this::handle, "PortHandler")
    ));
  }

  private void handle(final SelectionKey key) {
    verifyEqual(key, this.key);
    verifyEqual(key.channel(), serverSocketChannel);
    verify(key.isAcceptable());
    final Opt<SocketChannel> socket = Opt.ofNullable(call(serverSocketChannel::accept));
    if(socket.isPresent()) {
      LOG.info("Accepted.");
      socketHandlers.add(
        new SocketHandler(selector, socket.get(), sniAnalyser, forwardAddressSelector, socketHandlers::remove)
      );
    }
    else LOG.warn("No socket received.");
  }

  @Override
  public InetSocketAddress getSocketAdress() {
    return (InetSocketAddress) call(serverSocketChannel::getLocalAddress);
  }

  @Override
  public void close() {
    if(!closing) {
      closing = true;
      call(serverSocketChannel::close);
      key.cancel();
      LOG.info("Closing.");
    }
  }

  boolean isFinished() {
    return closing && socketHandlers.isEmpty();
  }
}
