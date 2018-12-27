package com.github.gv2011.snifor.nonblocking;

import java.nio.channels.SelectionKey;

@FunctionalInterface
public interface SelectionHandler {

  public static final SelectionHandler NOOP = named(k->{}, "NOOP");

  public static SelectionHandler named(final SelectionHandler h, final String name) {
    return new SelectionHandler() {
      @Override
      public void handle(final SelectionKey key) {
        h.handle(key);
      }
      @Override
      public String toString() {
        return name;
      }
    };
  }

  void handle(SelectionKey key);

}
