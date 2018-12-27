package com.github.gv2011.snifor.conf;

import java.net.InetSocketAddress;

import com.github.gv2011.util.beans.Computed;

public interface SocketAddress {

  Host host();

  Integer port();

  @Computed
  InetSocketAddress toInetSocketAddress();

  public static InetSocketAddress toInetSocketAddress(final SocketAddress socketAddress) {
    return new InetSocketAddress(socketAddress.host().toInetAddress(), socketAddress.port());
  }

}
