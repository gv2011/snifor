package com.github.gv2011.snifor;

import com.github.gv2011.snifor.conf.Configuration;
import com.github.gv2011.util.AutoCloseableNt;
import com.github.gv2011.util.icol.ISet;

public interface Snifor extends AutoCloseableNt{

  public void configure(final Configuration configuration);

  public ISet<? extends PortHandler> ports();

}