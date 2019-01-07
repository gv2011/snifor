package com.github.gv2011.snifor.conf;

import static com.github.gv2011.util.Verify.verifyEqual;
import static com.github.gv2011.util.ex.Exceptions.call;

import java.net.InetAddress;

import com.github.gv2011.util.beans.Computed;
import com.github.gv2011.util.icol.Opt;

public interface Host {

  public static enum Type{LOCALHOST, LOOPBACK, EXPLICIT, WILDCARD}

  Type type();

  Opt<Hostname> name();

  @Computed
  InetAddress toInetAddress();

  public static InetAddress toInetAddress(final Host host) {
    final Type type = host.type();
    if(type.equals(Type.LOCALHOST)) return call(InetAddress::getLocalHost);
    else if(type.equals(Type.LOOPBACK)) return call(InetAddress::getLoopbackAddress);
    else if(type.equals(Type.WILDCARD)) return call(()->InetAddress.getByAddress(new byte[] {0,0,0,0}));
    else {
      verifyEqual(type, Type.EXPLICIT);
      return call(()->InetAddress.getByName(host.name().toString()));
    }
  }

//  default Predicate<Host> emptyName() {
//    return h-> h.name().isEmpty() != h.type().equals(Type.EXPLICIT);
//  }
//
//  default Predicate<Host> trimmed() {
//    return h-> h.name().equals(h.name().trim());
//  }
//
//  default Predicate<Host> lowercase() {
//    return h-> h.name().equals(h.name().toLowerCase(Locale.ENGLISH));
//  }

}
