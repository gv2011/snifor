package com.github.gv2011.snifor.conf;

import com.github.gv2011.util.tstr.TypedString;

public interface Hostname extends TypedString<Hostname>{

  public static Hostname create(final String name) {
    return TypedString.create(Hostname.class, name);
  }

}
