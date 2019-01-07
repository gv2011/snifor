package com.github.gv2011.snifor;

import static java.util.stream.Collectors.joining;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.Enumeration;

import org.junit.Test;

public class TestInterfaces {

  @Test
  public void test() throws SocketException, UnknownHostException {
    final Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
    while(interfaces.hasMoreElements()) {
      final NetworkInterface intf = interfaces.nextElement();
      if(!intf.getInterfaceAddresses().isEmpty()) {
        System.out.println(intf.getDisplayName());
        System.out.println(intf.getName());
        intf.getInterfaceAddresses().forEach(a->{
          System.out.println("  IP: "+a.getAddress().getHostAddress());
          System.out.println("  Host Name: "+a.getAddress().getHostAddress());
          System.out.println("  Canonical Host Name: "+a.getAddress().getCanonicalHostName());
          System.out.println("  Broadcast: "+a.getBroadcast());
          System.out.println("  Network Prefix Length: "+a.getNetworkPrefixLength());
          System.out.println();
        });
      }
    }
    final InetAddress localHost = InetAddress.getLocalHost();
    System.out.println("Local Host: "+localHost.getHostAddress()+" ("+localHost.getCanonicalHostName()+")");
    System.out.println(
      "IP addresses: "+
      Arrays.stream(InetAddress.getAllByName("d1.letero.com")).map(InetAddress::getHostAddress).collect(joining(", "))
    );
  }

}
