package com.github.gv2011.snifor;

import static org.slf4j.LoggerFactory.getLogger;

import java.io.IOException;

import org.junit.Test;
import org.slf4j.Logger;

import com.github.gv2011.snifor.nonblocking.SniforNonblocking;

public class SniforBlockingTlsTest extends SniforTlsTest{

  @SuppressWarnings("unused")
  private static final Logger LOG = getLogger(SniforBlockingTlsTest.class);

  public SniforBlockingTlsTest() {
    super(SniforNonblocking::new);
  }

  @Test
  public void testSetup() throws IOException {
  }

  @Test
  public void testTest() throws IOException {
    doTestTest();
  }

  @Test
  public void test() throws IOException {
    doTest(EXPECT_CONN_ON_SNI_PORT);
  }

}
