/*-
 * The MIT License (MIT)
 *
 * Modified version of tlschannel.impl.TlsExplorer
 *
 * Copyright (C) 2018 Vinz (https://github.com/gv2011)
 *
 * Original version from Mariano Barrios:
 * https://github.com/marianobarrios/tls-channel/blob/5e3c04287eccf46ea365cc96264cd517ac7aea28/
 * src/main/java/tlschannel/impl/TlsExplorer.java (MIT License)
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 * #L%
 */
package com.github.gv2011.snifor;

import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;

import javax.net.ssl.SNIHostName;
import javax.net.ssl.SNIServerName;
import javax.net.ssl.SSLProtocolException;
import javax.net.ssl.StandardConstants;

import com.github.gv2011.snifor.conf.Hostname;
import com.github.gv2011.util.BeanUtils;
import com.github.gv2011.util.beans.BeanBuilder;
import com.github.gv2011.util.icol.Opt;
import com.github.gv2011.util.tstr.TypedString;

public final class TlsExplorer implements SniAnalyser{

  @Override
  public Result analyse(final ByteBuffer buffer) {
    final BeanBuilder<Result> result = BeanUtils.beanBuilder(Result.class);
    if(buffer.remaining()<TlsExplorer.RECORD_HEADER_SIZE) {
      result.set(Result::type).to(ResultType.MORE_DATA_NEEDED);
    }
    else {
      final int required = TlsExplorer.getRequiredSize(buffer);
      if(buffer.remaining()<required) {
        result.set(Result::type).to(ResultType.MORE_DATA_NEEDED);
      }
      else {
        try {
          final Map<Integer, SNIServerName> names = TlsExplorer.explore(buffer);
          final Opt<SNIHostName> hostName = Opt.ofNullable((SNIHostName)names.get(StandardConstants.SNI_HOST_NAME));
          if(hostName.isPresent()) {
            result.set(Result::type).to(ResultType.FOUND_NAME);
            result.set(Result::name).to(TypedString.create(Hostname.class, hostName.get().getAsciiName()));
          }
          else {
            result.set(Result::type).to(ResultType.NOT_ANALYSABLE);
          }
        } catch (final SSLProtocolException e) {
          result.set(Result::type).to(ResultType.NOT_ANALYSABLE);
        }
      }
    }
    return result.build();
  }


	private final static int RECORD_HEADER_SIZE = 5;

	/**
	 * Returns the required number of bytesProduced in the {@code source}
	 * {@link ByteBuffer} necessary to explore SSL/TLS connection.
	 * <P>
	 * This method tries to parse as few bytesProduced as possible from {@code source}
	 * byte buffer to get the length of an SSL/TLS record.
	 * <P>
	 */
	private final static int getRequiredSize(final ByteBuffer source) {
		if (source.remaining() < RECORD_HEADER_SIZE)
			throw new BufferUnderflowException();
		source.mark();
		try {
			final byte firstByte = source.get();
			source.get(); // second byte discarded
			final byte thirdByte = source.get();
			if ((firstByte & 0x80) != 0 && thirdByte == 0x01) {
				// looks like a V2ClientHello
				return RECORD_HEADER_SIZE; // Only need the header fields
			} else {
				return (((source.get() & 0xFF) << 8) | (source.get() & 0xFF)) + 5;
			}
		} finally {
			source.reset();
		}
	}

	private final static Map<Integer, SNIServerName> explore(final ByteBuffer source) throws SSLProtocolException {
		if (source.remaining() < RECORD_HEADER_SIZE)
			throw new BufferUnderflowException();
		source.mark();
		try {
			final byte firstByte = source.get();
			ignore(source, 1); // ignore second byte
			final byte thirdByte = source.get();
			if ((firstByte & 0x80) != 0 && thirdByte == 0x01) {
				// looks like a V2ClientHello
				return new HashMap<>();
			} else if (firstByte == 22) {
				// 22: handshake record
				return exploreTLSRecord(source, firstByte);
			} else {
				throw new SSLProtocolException("Not handshake record");
			}
		} finally {
			source.reset();
		}
	}

	/*
	 * struct { uint8 major; uint8 minor; } ProtocolVersion;
	 *
	 * enum { change_cipher_spec(20), alert(21), handshake(22),
	 * application_data(23), (255) } ContentType;
	 *
	 * struct { ContentType type; ProtocolVersion version; uint16 length; opaque
	 * fragment[TLSPlaintext.length]; } TLSPlaintext;
	 */
	private static Map<Integer, SNIServerName> exploreTLSRecord(final ByteBuffer input, final byte firstByte)
			throws SSLProtocolException {
		// Is it a handshake message?
		if (firstByte != 22) // 22: handshake record
			throw new SSLProtocolException("Not handshake record");
		// Is there enough data for a full record?
		final int recordLength = getInt16(input);
		if (recordLength > input.remaining())
			throw new BufferUnderflowException();
		return exploreHandshake(input, recordLength);
	}

	/*
	 * enum { hello_request(0), client_hello(1), server_hello(2),
	 * certificate(11), server_key_exchange (12), certificate_request(13),
	 * server_hello_done(14), certificate_verify(15), client_key_exchange(16),
	 * finished(20) (255) } HandshakeType;
	 *
	 * struct { HandshakeType msg_type; uint24 length; select (HandshakeType) {
	 * case hello_request: HelloRequest; case client_hello: ClientHello; case
	 * server_hello: ServerHello; case certificate: Certificate; case
	 * server_key_exchange: ServerKeyExchange; case certificate_request:
	 * CertificateRequest; case server_hello_done: ServerHelloDone; case
	 * certificate_verify: CertificateVerify; case client_key_exchange:
	 * ClientKeyExchange; case finished: Finished; } body; } Handshake;
	 */
	private static Map<Integer, SNIServerName> exploreHandshake(final ByteBuffer input, final int recordLength)
			throws SSLProtocolException {
		// What is the handshake type?
		final byte handshakeType = input.get();
		if (handshakeType != 0x01) // 0x01: client_hello message
			throw new SSLProtocolException("Not initial handshaking");
		// What is the handshake body length?
		final int handshakeLength = getInt24(input);
		// Theoretically, a single handshake message might span multiple
		// records, but in practice this does not occur.
		if (handshakeLength > recordLength - 4) // 4: handshake header size
			throw new SSLProtocolException("Handshake message spans multiple records");
		input.limit(handshakeLength + input.position());
		return exploreClientHello(input);
	}

	/*
	 * struct { uint32 gmt_unix_time; opaque random_bytes[28]; } Random;
	 *
	 * opaque SessionID<0..32>;
	 *
	 * uint8 CipherSuite[2];
	 *
	 * enum { null(0), (255) } CompressionMethod;
	 *
	 * struct { ProtocolVersion client_version; Random random; SessionID
	 * session_id; CipherSuite cipher_suites<2..2^16-2>; CompressionMethod
	 * compression_methods<1..2^8-1>; select (extensions_present) { case false:
	 * struct {}; case true: Extension extensions<0..2^16-1>; }; } ClientHello;
	 */
	private static Map<Integer, SNIServerName> exploreClientHello(final ByteBuffer input) throws SSLProtocolException {
		ignore(input, 2); // ignore version
		ignore(input, 32); // ignore random; 32: the length of Random
		ignoreByteVector8(input); // ignore session id
		ignoreByteVector16(input); // ignore cipher_suites
		ignoreByteVector8(input); // ignore compression methods
		if (input.remaining() > 0)
			return exploreExtensions(input);
		else
			return new HashMap<>();
	}

	/*
	 * struct { ExtensionType extension_type; opaque extension_data<0..2^16-1>;
	 * } Extension;
	 *
	 * enum { server_name(0), max_fragment_length(1), client_certificate_url(2),
	 * trusted_ca_keys(3), truncated_hmac(4), status_request(5), (65535) }
	 * ExtensionType;
	 */
	private static Map<Integer, SNIServerName> exploreExtensions(final ByteBuffer input) throws SSLProtocolException {
		int length = getInt16(input); // length of extensions
		while (length > 0) {
			final int extType = getInt16(input); // extension type
			final int extLen = getInt16(input); // length of extension data
			if (extType == 0x00) { // 0x00: type of server name indication
				return exploreSNIExt(input, extLen);
			} else { // ignore other extensions
				ignore(input, extLen);
			}
			length -= extLen + 4;
		}
		return new HashMap<>();
	}

	/*
	 * struct { NameType name_type; select (name_type) { case host_name:
	 * HostName; } name; } ServerName;
	 *
	 * enum { host_name(0), (255) } NameType;
	 *
	 * opaque HostName<1..2^16-1>;
	 *
	 * struct { ServerName server_name_list<1..2^16-1> } ServerNameList;
	 */
	private static Map<Integer, SNIServerName> exploreSNIExt(final ByteBuffer input, final int extLen) throws SSLProtocolException {
		final Map<Integer, SNIServerName> sniMap = new HashMap<>();
		int remains = extLen;
		if (extLen >= 2) { // "server_name" extension in ClientHello
			final int listLen = getInt16(input); // length of server_name_list
			if (listLen == 0 || listLen + 2 != extLen)
				throw new SSLProtocolException("Invalid server name indication extension");
			remains -= 2; // 2: the length field of server_name_list
			while (remains > 0) {
				final int code = getInt8(input); // name_type
				final int snLen = getInt16(input); // length field of server name
				if (snLen > remains)
					throw new SSLProtocolException("Not enough data to fill declared vector size");
				final byte[] encoded = new byte[snLen];
				input.get(encoded);
				SNIServerName serverName;
				switch (code) {
				case StandardConstants.SNI_HOST_NAME:
					if (encoded.length == 0)
						throw new SSLProtocolException("Empty HostName in server name indication");
					serverName = new SNIHostName(encoded);
					break;
				default:
					serverName = new UnknownServerName(code, encoded);
				}
				// check for duplicated server name type
				if (sniMap.put(serverName.getType(), serverName) != null)
					throw new SSLProtocolException("Duplicated server name of type " + serverName.getType());
				remains -= encoded.length + 3; // NameType: 1 byte; HostName;
												// length: 2 bytesProduced
			}
		} else if (extLen == 0) { // "server_name" extension in ServerHello
			throw new SSLProtocolException("Not server name indication extension in client");
		}
		if (remains != 0)
			throw new SSLProtocolException("Invalid server name indication extension");
		return sniMap;
	}

	private static int getInt8(final ByteBuffer input) {
		return input.get();
	}

	private static int getInt16(final ByteBuffer input) {
		return ((input.get() & 0xFF) << 8) | (input.get() & 0xFF);
	}

	private static int getInt24(final ByteBuffer input) {
		return ((input.get() & 0xFF) << 16) | ((input.get() & 0xFF) << 8) | (input.get() & 0xFF);
	}

	private static void ignoreByteVector8(final ByteBuffer input) {
		ignore(input, getInt8(input));
	}

	private static void ignoreByteVector16(final ByteBuffer input) {
		ignore(input, getInt16(input));
	}

	private static void ignore(final ByteBuffer input, final int length) {
		if (length != 0)
			input.position(input.position() + length);
	}

	// For some reason, SNIServerName is abstract
	private static class UnknownServerName extends SNIServerName {
		UnknownServerName(final int code, final byte[] encoded) {
			super(code, encoded);
		}
	}
}
