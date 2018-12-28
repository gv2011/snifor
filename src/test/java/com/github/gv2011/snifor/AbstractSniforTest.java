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
import java.net.ServerSocket;
import java.net.Socket;
import java.util.function.Supplier;

import javax.net.ServerSocketFactory;
import javax.net.SocketFactory;

import org.slf4j.Logger;

import com.github.gv2011.snifor.conf.Configuration;
import com.github.gv2011.snifor.conf.Host;
import com.github.gv2011.snifor.conf.Host.Type;
import com.github.gv2011.snifor.conf.Hostname;
import com.github.gv2011.snifor.conf.PortConfig;
import com.github.gv2011.snifor.conf.SocketAddress;
import com.github.gv2011.util.bytes.ByteUtils;
import com.github.gv2011.util.bytes.Bytes;
import com.github.gv2011.util.icol.Opt;

abstract class AbstractSniforTest {

  private static final String HOSTNAME_1 = "letero.com";

  private static final Logger LOG = getLogger(AbstractSniforTest.class);

  static final boolean EXPECT_CONN_ON_DEFAULT_PORT = true;
  static final boolean EXPECT_CONN_ON_SNI_PORT = !EXPECT_CONN_ON_DEFAULT_PORT;

  static final Bytes DATA_FORWARD = ByteUtils.newRandomBytes(128);
  static final Bytes DATA_BACK = ByteUtils.newRandomBytes(128);

  final ServerSocketFactory serverSocketFactory;
  final SocketFactory socketFactory;

  private final Supplier<Snifor> sniforSupplier;

  AbstractSniforTest(
    final ServerSocketFactory serverSocketFactory,
    final SocketFactory socketFactory,
    final Supplier<Snifor> sniforSupplier
  ) {
    this.serverSocketFactory = serverSocketFactory;
    this.socketFactory = socketFactory;
    this.sniforSupplier = sniforSupplier;
  }

  protected Thread handleTarget(final ServerSocket target) {
    final int port = target.getLocalPort();
    final Thread thread = new Thread(
      ()->call(()->{
        LOG.info("{} waiting.", port);
        final Socket socket = target.accept();
        LOG.info("{} accepted connection.", port);
        final InputStream in = socket.getInputStream();
        final Bytes bytes = ByteUtils.copyFromStream(in, DATA_FORWARD.size());
        verifyEqual(bytes, DATA_FORWARD);
        LOG.info("{} read expected input.", port);
        final OutputStream out = socket.getOutputStream();
        DATA_BACK.write(out);
        out.flush();
        LOG.info("{} sent output.", port);
        verifyEqual(in.read(), -1);
        LOG.info("{} detected end of input.", port);
        socket.shutdownOutput();
        LOG.info("{} closed output.", port);
      }),
      "target-"+port
    );
    thread.start();
    return thread;
  }

  protected Thread handleWrongTarget(final ServerSocket target) {
    final int port = target.getLocalPort();
    final Thread thread = new Thread(
      ()->call(()->{
        LOG.info("{} waiting.", port);
        try {
          target.accept();
          LOG.error("{} request received.", port);
        } catch (final IOException e) {
          LOG.info("{} finished ({}).", port, e.toString());
        }
      }),
      "target-"+port
    );
    thread.start();
    return thread;
  }

  protected Configuration configuration(final Hostname targetHost, final int targetPort, final int target2Port) {
    final Host loopback = beanBuilder(Host.class).set(Host::type).to(Type.LOOPBACK).build();
    return beanBuilder(Configuration.class)
      .set(Configuration::ports).to(setOf(
        beanBuilder(PortConfig.class)
        .set(PortConfig::serverSocket).to(
          beanBuilder(SocketAddress.class)
          .set(SocketAddress::host).to(loopback)
          .set(SocketAddress::port).to(0)
          .build()
        )
        .set(PortConfig::defaultAddress).to(
          beanBuilder(SocketAddress.class)
          .set(SocketAddress::host).to(loopback)
          .set(SocketAddress::port).to(target2Port)
          .build()
        )
        .set(PortConfig::forwards).to(sortedMapOf(
          targetHost,
          beanBuilder(SocketAddress.class)
          .set(SocketAddress::host).to(loopback)
          .set(SocketAddress::port).to(targetPort)
          .build()
        ))
        .build()
      ))
      .build()
    ;
  }

  void doTestTest() throws IOException {
    Opt<Thread> targetThread = Opt.empty();
    try {
      try(ServerSocket target = serverSocketFactory.createServerSocket()){
        target.bind(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0));
        targetThread = Opt.of(handleTarget(target));
        final int targetPort = target.getLocalPort();
        try(final Socket client = socketFactory.createSocket(InetAddress.getLoopbackAddress(), targetPort)){
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
    }finally {
      targetThread.ifPresent(t->call(()->t.join()));
    }
  }

  void doTest(final boolean expectConnectionOnDefaultPort) throws IOException {
    Opt<Thread> targetThread = Opt.empty();
    Opt<Thread> defaultTargetThread = Opt.empty();
    try {
      try(ServerSocket target = serverSocketFactory.createServerSocket(0)){
        targetThread = Opt.of(expectConnectionOnDefaultPort ? handleWrongTarget(target): handleTarget(target));
        final int targetPort = target.getLocalPort();
        LOG.info("Target port: {}", targetPort);
        try(ServerSocket defaultTarget = serverSocketFactory.createServerSocket()){
          defaultTarget.bind(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0));
          defaultTargetThread = Opt.of(expectConnectionOnDefaultPort
            ? handleTarget(defaultTarget)
            : handleWrongTarget(defaultTarget)
          );
          final int target2Port = defaultTarget.getLocalPort();
          LOG.info("Default target port: {}", target2Port);
          try(Snifor snifor = sniforSupplier.get()){
            final Hostname targetHost = Hostname.create(HOSTNAME_1);
            LOG.info("Target host: {}", targetHost);
            snifor.configure(configuration(targetHost, targetPort, target2Port));
            final int sourcePort = snifor.ports().single().getSocketAdress().getPort();
            LOG.info("Source port: {}", sourcePort);
            try(final Socket client = socketFactory.createSocket(
              InetAddress.getByName(targetHost.toString()),
              sourcePort
            )){
              try(OutputStream out = client.getOutputStream()){
                DATA_FORWARD.write(out);
                client.shutdownOutput();
                try(InputStream in = client.getInputStream()){
                  final Bytes bytes = ByteUtils.fromStream(in);
                  verifyEqual(bytes, DATA_BACK);
                }
              }
            }
          }
        }
      }
    }finally {
      targetThread.ifPresent(t->call(()->t.join()));
      defaultTargetThread.ifPresent(t->call(()->t.join()));
    }
  }

}
