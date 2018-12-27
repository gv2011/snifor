package com.github.gv2011.snifor;

import com.github.gv2011.snifor.conf.Hostname;
import com.github.gv2011.snifor.conf.SocketAddress;

public interface ForwardAddressSelector {
  SocketAddress getDefaultAddress();
  SocketAddress getAddress(Hostname hostname);
}
