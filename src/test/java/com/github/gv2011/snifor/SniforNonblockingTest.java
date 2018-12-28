package com.github.gv2011.snifor;

import java.io.IOException;

import javax.net.ServerSocketFactory;
import javax.net.SocketFactory;

import org.junit.Test;

import com.github.gv2011.snifor.nonblocking.SniforNonblocking;

public class SniforNonblockingTest extends AbstractSniforTest{

  public SniforNonblockingTest() {
    super(ServerSocketFactory.getDefault(), SocketFactory.getDefault(), SniforNonblocking::new);
  }

  @Test
  public void testTest() throws IOException {
    doTestTest();
  }

  @Test
  public void test() throws IOException {
    doTest(EXPECT_CONN_ON_DEFAULT_PORT);
  }

}