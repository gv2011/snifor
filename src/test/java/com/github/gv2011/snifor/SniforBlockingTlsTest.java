package com.github.gv2011.snifor;

import static org.slf4j.LoggerFactory.getLogger;

import java.io.IOException;

import org.junit.After;
import org.junit.Test;
import org.slf4j.Logger;

import com.github.gv2011.snifor.blocking.SniforBlocking;

public class SniforBlockingTlsTest extends SniforTlsTest{

  private static final Logger LOG = getLogger(SniforBlockingTlsTest.class);

  public SniforBlockingTlsTest() {
    LOG.info("Created.");
  }

  @Override
  @After
  public void close() {
    threadFactory().close();
  }

  @Test
  public void testSetup() throws IOException {
  }

  @Test
  public void testHostName() throws IOException {
    doTestHostName();
  }

  @Test(timeout=10000)
  public void testTest() throws IOException {
    doTestTest();
  }

  @Test(timeout=10000)
  public void test() throws IOException {
    doTest(EXPECT_CONN_ON_SNI_PORT);
  }

  @Override
  Snifor createSnifor() {
    return new SniforBlocking(threadFactory());
  }

}
