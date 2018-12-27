package com.github.gv2011.snifor;

import com.github.gv2011.snifor.conf.Hostname;
import com.github.gv2011.snifor.conf.SocketAddress;
import com.github.gv2011.util.icol.ISortedMap;

public class ForwardAddressSelectorImp implements ForwardAddressSelector{

  private final ISortedMap<Hostname, SocketAddress> forwards;
  private final SocketAddress defaultAddress;

  public ForwardAddressSelectorImp(final ISortedMap<Hostname, SocketAddress> forwards, final SocketAddress defaultAddress) {
    this.forwards = forwards;
    this.defaultAddress = defaultAddress;
  }

  @Override
  public SocketAddress getAddress(final Hostname hostname) {
    return forwards.tryGet(hostname).orElse(defaultAddress);
  }

  @Override
  public SocketAddress getDefaultAddress() {
    return defaultAddress;
  }

}
