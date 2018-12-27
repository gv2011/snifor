package com.github.gv2011.snifor.conf;

import com.github.gv2011.util.icol.ISortedMap;

public interface PortConfig {

  SocketAddress serverSocket();

  SocketAddress defaultAddress();

  ISortedMap<Hostname, SocketAddress> forwards();

}
