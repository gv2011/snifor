package com.github.gv2011.snifor;

import static com.github.gv2011.util.ex.Exceptions.call;
import static com.github.gv2011.util.icol.ICollections.listOf;
import static org.slf4j.LoggerFactory.getLogger;

import java.security.cert.X509Certificate;

import javax.naming.ldap.LdapName;
import javax.net.ServerSocketFactory;
import javax.net.SocketFactory;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLServerSocketFactory;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManagerFactory;

import org.junit.After;
import org.slf4j.Logger;

import com.github.gv2011.util.CollectionUtils;
import com.github.gv2011.util.Pair;
import com.github.gv2011.util.ResourceUtils;
import com.github.gv2011.util.bytes.Bytes;
import com.github.gv2011.util.sec.CertificateBuilder;
import com.github.gv2011.util.sec.RsaKeyPair;
import com.github.gv2011.util.sec.SecUtils;

abstract class SniforTlsTest extends AbstractSniforTest{

  private static final Logger LOG = getLogger(SniforTlsTest.class);

  SniforTlsTest() {
    super(socketFactories());
  }

  @After
  public void close() {
    threadFactory().close();
  }

  static final Pair<ServerSocketFactory,SocketFactory> socketFactories(){
    LOG.info("Creating socket factories.");
    final X509Certificate cert;
    final SSLServerSocketFactory ssf;
    final SSLSocketFactory csf;
    {
      final RsaKeyPair rsaKeyPair = RsaKeyPair.parse(ResourceUtils.getBinaryResource(SniforTlsTest.class, "key.rsa"));
      final LdapName subject = call(()->new LdapName("CN=test"));
      cert = CertificateBuilder.create()
        .setSubject(subject )
        .build(rsaKeyPair)
      ;
      final Bytes ks = SecUtils.createJKSKeyStore(rsaKeyPair, listOf(cert));
      final KeyManagerFactory kmf = call(()->KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm()));
      call(()->kmf.init(SecUtils.readKeyStore(ks::openStream), SecUtils.JKS_DEFAULT_PASSWORD.toCharArray()));
      final SSLContext ctx = call(()->SSLContext.getInstance(SecUtils.TLSV12));
      call(()->ctx.init(kmf.getKeyManagers() , null, null));
      ssf = ctx.getServerSocketFactory();
      LOG.info("Created SSLServerSocketFactory.");
    }
    {
      final Bytes ks = SecUtils.createJKSKeyStore(cert);
      final TrustManagerFactory tmf = call(()->TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm()));
      call(()->tmf.init(SecUtils.readKeyStore(ks::openStream)));
      final SSLContext ctx = call(()->SSLContext.getInstance(SecUtils.TLSV12));
      call(()->ctx.init(null, tmf.getTrustManagers(), null));
      csf = ctx.getSocketFactory();
      LOG.info("Created SSLSocketFactory.");
    }
    return CollectionUtils.pair(ssf, csf);
  }
}
