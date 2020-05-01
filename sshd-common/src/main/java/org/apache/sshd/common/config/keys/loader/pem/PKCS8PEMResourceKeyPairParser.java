/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.sshd.common.config.keys.loader.pem;

import java.io.IOException;
import java.io.InputStream;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.apache.sshd.common.NamedResource;
import org.apache.sshd.common.config.keys.FilePasswordProvider;
import org.apache.sshd.common.config.keys.KeyUtils;
import org.apache.sshd.common.session.SessionContext;
import org.apache.sshd.common.util.GenericUtils;
import org.apache.sshd.common.util.ValidateUtils;
import org.apache.sshd.common.util.io.IoUtils;
import org.apache.sshd.common.util.security.SecurityUtils;

/**
 * @author <a href="mailto:dev@mina.apache.org">Apache MINA SSHD Project</a>
 * @see <a href="https://tools.ietf.org/html/rfc5208">RFC 5208</A>
 */
public class PKCS8PEMResourceKeyPairParser extends AbstractPEMResourceKeyPairParser {
    // Not exactly according to standard but good enough
    public static final String BEGIN_MARKER = "BEGIN PRIVATE KEY";
    public static final List<String> BEGINNERS = Collections.unmodifiableList(Collections.singletonList(BEGIN_MARKER));

    public static final String END_MARKER = "END PRIVATE KEY";
    public static final List<String> ENDERS = Collections.unmodifiableList(Collections.singletonList(END_MARKER));

    public static final String PKCS8_FORMAT = "PKCS#8";

    public static final PKCS8PEMResourceKeyPairParser INSTANCE = new PKCS8PEMResourceKeyPairParser();

    public PKCS8PEMResourceKeyPairParser() {
        super(PKCS8_FORMAT, PKCS8_FORMAT, BEGINNERS, ENDERS);
    }

    @Override
    public Collection<KeyPair> extractKeyPairs(
            SessionContext session, NamedResource resourceKey,
            String beginMarker, String endMarker,
            FilePasswordProvider passwordProvider,
            InputStream stream, Map<String, String> headers)
            throws IOException, GeneralSecurityException {
        // Save the data before getting the algorithm OID since we will need it
        byte[] encBytes = IoUtils.toByteArray(stream);
        PKCS8PrivateKeyInfo pkcs8Info = new PKCS8PrivateKeyInfo(encBytes);
        return extractKeyPairs(
            session, resourceKey, beginMarker, endMarker,
            passwordProvider, encBytes, pkcs8Info, headers);
    }

    public Collection<KeyPair> extractKeyPairs(
            SessionContext session, NamedResource resourceKey,
            String beginMarker, String endMarker,
            FilePasswordProvider passwordProvider, byte[] encBytes,
            PKCS8PrivateKeyInfo pkcs8Info, Map<String, String> headers)
            throws IOException, GeneralSecurityException {
        List<Integer> oidAlgorithm = pkcs8Info.getAlgorithmIdentifier();
        PrivateKey prvKey = decodePEMPrivateKeyPKCS8(oidAlgorithm, encBytes);
        PublicKey pubKey = recoverPublicKey(
            session, resourceKey, beginMarker, endMarker,
            passwordProvider, headers, encBytes, pkcs8Info, prvKey);
        ValidateUtils.checkNotNull(pubKey,
                "Failed to recover public key of OID=%s", oidAlgorithm);
        KeyPair kp = new KeyPair(pubKey, prvKey);
        return Collections.singletonList(kp);

    }

    @SuppressWarnings("checkstyle:ParameterNumber")
    protected PublicKey recoverPublicKey(
            SessionContext session, NamedResource resourceKey,
            String beginMarker, String endMarker,
            FilePasswordProvider passwordProvider,
            Map<String, String> headers, byte[] encBytes,
            PKCS8PrivateKeyInfo pkcs8Info, PrivateKey privateKey)
            throws IOException, GeneralSecurityException {
        if (privateKey instanceof ECPrivateKey) {
            return recoverECPublicKey(
                session, resourceKey, beginMarker, endMarker,
                passwordProvider, headers, encBytes, pkcs8Info,
                (ECPrivateKey) privateKey);
        }

        return KeyUtils.recoverPublicKey(privateKey);
    }

    @SuppressWarnings("checkstyle:ParameterNumber")
    protected ECPublicKey recoverECPublicKey(
            SessionContext session, NamedResource resourceKey,
            String beginMarker, String endMarker,
            FilePasswordProvider passwordProvider,
            Map<String, String> headers, byte[] encBytes,
            PKCS8PrivateKeyInfo pkcs8Info, ECPrivateKey privateKey)
            throws IOException, GeneralSecurityException {
        throw new NoSuchAlgorithmException("TODO: SSHD-976");
    }

    public static PrivateKey decodePEMPrivateKeyPKCS8(List<Integer> oidAlgorithm, byte[] keyBytes)
            throws GeneralSecurityException {
        ValidateUtils.checkNotNullAndNotEmpty(oidAlgorithm, "No PKCS8 algorithm OID");
        return decodePEMPrivateKeyPKCS8(GenericUtils.join(oidAlgorithm, '.'), keyBytes);
    }

    public static PrivateKey decodePEMPrivateKeyPKCS8(String oid, byte[] keyBytes)
            throws GeneralSecurityException {
        KeyPairPEMResourceParser parser = PEMResourceParserUtils.getPEMResourceParserByOid(
                ValidateUtils.checkNotNullAndNotEmpty(oid, "No PKCS8 algorithm OID"));
        if (parser == null) {
            throw new NoSuchAlgorithmException("decodePEMPrivateKeyPKCS8(" + oid + ") unknown algorithm identifier");
        }

        String algorithm = ValidateUtils.checkNotNullAndNotEmpty(parser.getAlgorithm(), "No parser algorithm");
        KeyFactory factory = SecurityUtils.getKeyFactory(algorithm);
        PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(keyBytes);
        return factory.generatePrivate(keySpec);
    }
}
