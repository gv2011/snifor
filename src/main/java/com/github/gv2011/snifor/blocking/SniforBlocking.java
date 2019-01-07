package com.github.gv2011.snifor.blocking;

import static com.github.gv2011.util.Verify.verify;
import static com.github.gv2011.util.icol.ICollections.iCollections;
import static com.github.gv2011.util.icol.ICollections.setFrom;
import static com.github.gv2011.util.icol.ICollections.toIMap;
import static org.slf4j.LoggerFactory.getLogger;

import java.util.Map.Entry;
import java.util.concurrent.ThreadFactory;

import org.slf4j.Logger;

import com.github.gv2011.snifor.Snifor;
import com.github.gv2011.snifor.conf.Configuration;
import com.github.gv2011.snifor.conf.PortConfig;
import com.github.gv2011.snifor.conf.SocketAddress;
import com.github.gv2011.util.icol.ICollections;
import com.github.gv2011.util.icol.IMap;
import com.github.gv2011.util.icol.IMap.Builder;
import com.github.gv2011.util.icol.ISet;

public final class SniforBlocking implements Snifor{

  @SuppressWarnings("unused")
  private static final Logger LOG = getLogger(SniforBlocking.class);

  private final Object lock = new Object();

  private IMap<SocketAddress, PortHandler> ports = ICollections.emptyMap();

  private boolean closed;

  private final ThreadFactory threadFactory;

  public SniforBlocking(final ThreadFactory threadFactory) {
    this.threadFactory = threadFactory;
  }

  @Override
  public void configure(final Configuration configuration) {
    synchronized(lock) {
      verify(!closed);
      final IMap<SocketAddress, PortConfig> bySocket =
        configuration.ports().stream().collect(toIMap(PortConfig::serverSocket, p->p))
      ;
      ports.entrySet().parallelStream()
        .filter(e->!bySocket.containsKey(e.getKey()))
        .map(Entry::getValue)
        .forEach(PortHandler::close)
      ;
      final Builder<SocketAddress, PortHandler> modPorts = iCollections().mapBuilder();
      ports.entrySet().parallelStream()
        .filter(e->bySocket.containsKey(e.getKey()))
        .forEach(e->{
          e.getValue().changeConfig(bySocket.get(e.getKey()));
          modPorts.put(e.getKey(), e.getValue());
        })
      ;
      bySocket.entrySet().parallelStream()
        .filter(e->!ports.containsKey(e.getKey()))
        .forEach(e->{
          modPorts.put(e.getKey(), new PortHandler(e.getValue(), threadFactory));
        });
      ;
      ports = modPorts.build();
    }
  }

  @Override
  public ISet<PortHandler> ports(){
    synchronized(lock) {
      return setFrom(ports.values());
    }
  }


  @Override
  public void close() {
    synchronized(lock) {
      ports.values().parallelStream().forEach(PortHandler::close);
      ports = ICollections.emptyMap();
    }
  }

}