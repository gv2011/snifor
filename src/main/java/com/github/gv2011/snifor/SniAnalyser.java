package com.github.gv2011.snifor;

import java.nio.ByteBuffer;

import com.github.gv2011.snifor.conf.Hostname;

public interface SniAnalyser {

  public static enum ResultType{MORE_DATA_NEEDED, NOT_ANALYSABLE, FOUND_NAME}

  public static interface Result{
    ResultType type();
    Hostname name();
  }

  Result analyse(ByteBuffer buffer);

}
