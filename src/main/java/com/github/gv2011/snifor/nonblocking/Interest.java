package com.github.gv2011.snifor.nonblocking;

import java.nio.channels.SelectionKey;
import java.util.Arrays;

import com.github.gv2011.util.icol.ICollections;
import com.github.gv2011.util.icol.ISortedSet;
import com.github.gv2011.util.icol.ISortedSet.Builder;

public enum Interest {

  ACCEPT(SelectionKey.OP_ACCEPT),
  CONNECT(SelectionKey.OP_CONNECT),
  READ(SelectionKey.OP_READ),
  WRITE(SelectionKey.OP_WRITE);

  private final int code;

  private Interest(final int code) {
    this.code = code;
  }

  public void set(final SelectionKey key) {
    key.interestOpsOr(code);
  }

  public void clear(final SelectionKey key) {
    key.interestOpsAnd(~code);
  }

  public static ISortedSet<Interest> asSet(final int code) {
    final Builder<Interest> result = ICollections.<Interest>sortedSetBuilder();
    for(final Interest i: Interest.values()) {
      if((i.code & code) !=0) result.add(i);
    }
    return result.build();
  }

  public static Interest forCode(final int code) {
    return Arrays.stream(Interest.values()).filter(i->i.code==code).findAny().get();
  }

}
