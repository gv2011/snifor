package com.github.gv2011.snifor.blocking;

import static com.github.gv2011.util.Verify.verifyEqual;
import static com.github.gv2011.util.ex.Exceptions.call;
import static org.slf4j.LoggerFactory.getLogger;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashSet;
import java.util.Set;

import org.slf4j.Logger;

import com.github.gv2011.snifor.ForwardAddressSelector;
import com.github.gv2011.snifor.ForwardAddressSelectorImp;
import com.github.gv2011.snifor.SniAnalyser;
import com.github.gv2011.snifor.TlsExplorer;
import com.github.gv2011.snifor.conf.PortConfig;
import com.github.gv2011.snifor.conf.SocketAddress;
import com.github.gv2011.util.AutoCloseableNt;


public final class PortHandler implements AutoCloseableNt, com.github.gv2011.snifor.PortHandler{

  private static final Logger LOG = getLogger(PortHandler.class);

  private final ServerSocket serverSocket;
  private final SniAnalyser sniAnalyser;
  private ForwardAddressSelector forwardAddressSelector;

  private final Object lock = new Object();
  private final Set<SocketHandler> socketHandlers = new HashSet<>();

  private boolean closing;

  private final Thread thread;

  private final SocketAddress serverSocketAddress;

  PortHandler(final PortConfig portConfig) {
    forwardAddressSelector = new ForwardAddressSelectorImp(portConfig.forwards(), portConfig.defaultAddress());
    sniAnalyser = new TlsExplorer();
    serverSocket = call(()->new ServerSocket());
    serverSocketAddress = portConfig.serverSocket();
    final InetSocketAddress address = serverSocketAddress.toInetSocketAddress();
    call(()->serverSocket.bind(address));
    thread = new Thread(this::run, address.toString());
    thread.start();
  }

  void changeConfig(final PortConfig portConfig) {
    verifyEqual(portConfig.serverSocket(), serverSocketAddress);
    synchronized(lock) {
      forwardAddressSelector = new ForwardAddressSelectorImp(portConfig.forwards(), portConfig.defaultAddress());
    }
  }

  private void run() {
    boolean shouldRun = true;
    while(shouldRun) {
      try {
        final Socket socket = serverSocket.accept();
        LOG.info("Accepted.");
        synchronized(lock) {
          socketHandlers.add(
            new SocketHandler(
              socket, sniAnalyser, forwardAddressSelector,
              h->{
                synchronized(lock) {
                  socketHandlers.remove(h);
                  lock.notifyAll();
                }
              }
            )
          );
          if(closing) shouldRun = false;
        }
      } catch (final IOException e) {
        synchronized(lock) {
          if(closing) shouldRun = false;
        }
        if(shouldRun) LOG.error("Accept failed.", e);
      }
    }
    synchronized(lock) {
      while(!socketHandlers.isEmpty()) call(()->lock.wait());
    }
  }


  public InetSocketAddress getSocketAdress() {
    return (InetSocketAddress) call(serverSocket::getLocalSocketAddress);
  }

  @Override
  public void close() {
    boolean doClose;
    synchronized(lock) {
      doClose = !closing;
      closing = true;
    }
    if(doClose) {
      call(serverSocket::close);
      LOG.info("Closing.");
    }
    call(()->thread.join());
  }

}
