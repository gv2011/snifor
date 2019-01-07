package com.github.gv2011.snifor;

import static com.github.gv2011.util.BeanUtils.beanBuilder;
import static com.github.gv2011.util.Verify.verifyEqual;
import static com.github.gv2011.util.ex.Exceptions.call;
import static com.github.gv2011.util.icol.ICollections.setOf;
import static com.github.gv2011.util.icol.ICollections.sortedMapOf;
import static org.slf4j.LoggerFactory.getLogger;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.Locale;

import javax.net.ServerSocketFactory;
import javax.net.SocketFactory;

import org.slf4j.Logger;

import com.github.gv2011.snifor.conf.Configuration;
import com.github.gv2011.snifor.conf.Host;
import com.github.gv2011.snifor.conf.Host.Type;
import com.github.gv2011.snifor.conf.Hostname;
import com.github.gv2011.snifor.conf.PortConfig;
import com.github.gv2011.snifor.conf.SocketAddress;
import com.github.gv2011.testutil.TestThreadFactory;
import com.github.gv2011.util.BeanUtils;
import com.github.gv2011.util.Pair;
import com.github.gv2011.util.bytes.ByteUtils;
import com.github.gv2011.util.bytes.Bytes;

abstract class AbstractSniforTest {

  private static final Logger LOG = getLogger(AbstractSniforTest.class);

  static final boolean EXPECT_CONN_ON_DEFAULT_PORT = true;
  static final boolean EXPECT_CONN_ON_SNI_PORT = !EXPECT_CONN_ON_DEFAULT_PORT;

  static final Bytes DATA_FORWARD = ByteUtils.newRandomBytes(128);
  static final Bytes DATA_BACK = ByteUtils.newRandomBytes(128);

  final ServerSocketFactory serverSocketFactory;
  final SocketFactory socketFactory;

  private final TestThreadFactory threadFactory = new TestThreadFactory();

  AbstractSniforTest(
    final ServerSocketFactory serverSocketFactory,
    final SocketFactory socketFactory
  ) {
    this.serverSocketFactory = serverSocketFactory;
    this.socketFactory = socketFactory;
  }

  AbstractSniforTest(
      final Pair<ServerSocketFactory,SocketFactory> socketFactories
    ) {
      serverSocketFactory = socketFactories.getKey();
      socketFactory = socketFactories.getValue();
    }

  final TestThreadFactory threadFactory() {
    return threadFactory ;
  }

  abstract Snifor createSnifor();

  final void doTestTest() throws IOException {
    try(
      final TargetPortHandler activeTargetPortHandler = ActiveTargetPortHandler.create(
        serverSocketFactory, threadFactory
      )
    ){
      final InetSocketAddress targetAddress = activeTargetPortHandler.getAddress();
      try(final Socket client = socketFactory.createSocket(targetAddress.getAddress(), targetAddress.getPort())){
        LOG.info("Client opened connection to {}.", targetAddress);
        try(OutputStream out = client.getOutputStream()){
          DATA_FORWARD.write(out);
          out.flush();
          LOG.info("Client sent output.");
          //client.shutdownOutput();
          try(InputStream in = client.getInputStream()){
            final Bytes bytes = ByteUtils.copyFromStream(in, DATA_BACK.size());
            verifyEqual(bytes.size(), DATA_BACK.size());
            verifyEqual(bytes, DATA_BACK);
            client.shutdownOutput();
            LOG.info("Client received expected answer and closed output.");
            verifyEqual(in.read(), -1);
            LOG.info("Client received end of input.");
          }
        }
      }
    }
  }

  void doTestHostName() throws UnknownHostException {
    final Hostname hostName = getHostName();
    LOG.info("Hostname is {}.", hostName);
    LOG.info(
      "IP is {}.",
      InetAddress.getByName(hostName.toString()).getHostAddress()
    );
  }

  private Hostname getHostName() throws UnknownHostException {
    return Hostname.create(InetAddress.getLocalHost().getCanonicalHostName().toLowerCase(Locale.ENGLISH));
  }

  void doTest(final boolean expectConnectionOnDefaultPort) throws IOException {
    try(final TargetPortHandler activeTargetPortHandler = ActiveTargetPortHandler.create(
      serverSocketFactory, threadFactory
    )){
      try(final TargetPortHandler unusedTargetPortHandler = UnusedTargetPortHandler.create(
        serverSocketFactory, threadFactory
      )){
        try(Snifor snifor = createSnifor()){
          final Hostname targetHost = getHostName();
          LOG.info("Target host: {}", targetHost);
          final Configuration configuration = expectConnectionOnDefaultPort
            ? configuration(
              targetHost,
              unusedTargetPortHandler.getAddress().getPort(),
              activeTargetPortHandler.getAddress().getPort()
            )
            : configuration(
              targetHost,
              activeTargetPortHandler.getAddress().getPort(),
              unusedTargetPortHandler.getAddress().getPort()
            )
          ;
          LOG.info(
            "Configuration: {}",
            BeanUtils.typeRegistry().beanType(Configuration.class).toJson(configuration).serialize()
          );
          snifor.configure(configuration);
          final InetSocketAddress sniforEntryPort = snifor.ports().single().getSocketAdress();
          LOG.info("Snifor entry: {}", sniforEntryPort);
          final InetAddress host = InetAddress.getByName(targetHost.toString());
          final int port = sniforEntryPort.getPort();
          LOG.info("Connecting to: {}:{}", host, port);
          try(final Socket client = socketFactory.createSocket(host, port)){
            final Thread commThread = threadFactory.newThread(()->communicate(client), client::close);
            commThread.start();
            call(()->commThread.join());
          }
        }
      }
    }
  }



  private void communicate(final Socket client){
    call(()->{
      try(OutputStream out = client.getOutputStream()){
        DATA_FORWARD.write(out);
        client.shutdownOutput();
        try(InputStream in = client.getInputStream()){
          final Bytes bytes = ByteUtils.fromStream(in);
          verifyEqual(bytes, DATA_BACK);
        }
      }
    });
  }

  protected Configuration configuration(final Hostname targetHost, final int targetPort, final int defaultPort) {
    final Host wildcard = beanBuilder(Host.class).set(Host::type).to(Type.WILDCARD).build();
    return beanBuilder(Configuration.class)
      .set(Configuration::ports).to(setOf(
        beanBuilder(PortConfig.class)
        .set(PortConfig::serverSocket).to(
          beanBuilder(SocketAddress.class)
          .set(SocketAddress::host).to(wildcard)
          .set(SocketAddress::port).to(0)
          .build()
        )
        .set(PortConfig::defaultAddress).to(
          beanBuilder(SocketAddress.class)
          .set(SocketAddress::host).to(host(targetHost))
          .set(SocketAddress::port).to(defaultPort)
          .build()
        )
        .set(PortConfig::forwards).to(sortedMapOf(
          targetHost,
          beanBuilder(SocketAddress.class)
          .set(SocketAddress::host).to(host(targetHost))
          .set(SocketAddress::port).to(targetPort)
          .build()
        ))
        .build()
      ))
      .build()
    ;
  }



  private Host host(final Hostname targetHost) {
    return beanBuilder(Host.class)
      .set(Host::type).to(Type.EXPLICIT)
      .setOpt(Host::name).to(targetHost)
      .build()
    ;
  }

}
