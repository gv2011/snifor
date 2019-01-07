package com.github.gv2011.snifor.conf;

import java.net.InetSocketAddress;

import com.github.gv2011.snifor.conf.Host.Type;
import com.github.gv2011.util.beans.Computed;

public interface SocketAddress {

  Host host();

  Integer port();

  @Computed
  InetSocketAddress toInetSocketAddress();

  public static InetSocketAddress toInetSocketAddress(final SocketAddress socketAddress) {
    return socketAddress.host().type().equals(Type.WILDCARD)
      ? new InetSocketAddress(socketAddress.port())
      : new InetSocketAddress(socketAddress.host().toInetAddress(), socketAddress.port())
    ;
  }

}
