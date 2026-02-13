package org.qortal.utils;

import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.BasicConstraints;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.KeyUsage;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.bouncycastle.util.io.pem.PemObject;
import org.bouncycastle.util.io.pem.PemReader;
import org.bouncycastle.util.io.pem.PemWriter;
import org.qortal.settings.Settings;

import java.io.*;
import java.math.BigInteger;
import java.security.*;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Date;

public class SslUtils {

    private static final String CA_CERT_PATH = "ca.crt";
    private static final String CA_KEY_PATH = "ca.key";
    private static final String SERVER_CERT_PATH = "server.crt";
    private static final String SERVER_KEY_PATH = "server.key";

    static {
        Security.addProvider(new BouncyCastleProvider());
        Security.addProvider(new org.bouncycastle.jsse.provider.BouncyCastleJsseProvider());
    }

    public static void generateSsl() {
        try {
            // Generate key pair
            KeyPairGenerator kpGen = KeyPairGenerator.getInstance("RSA", "BC");
            kpGen.initialize(2048, new SecureRandom());
            KeyPair keyPair = kpGen.generateKeyPair();

            // Create self-signed certificate
            X500Name subject = new X500Name("CN=qortal.org");
            BigInteger serial = BigInteger.valueOf(System.currentTimeMillis());
            Date notBefore = new Date();
            Date notAfter = new Date(System.currentTimeMillis() + (10L * 365 * 24 * 60 * 60 * 1000));

            JcaX509v3CertificateBuilder certBuilder = new JcaX509v3CertificateBuilder(subject, serial, notBefore, notAfter, subject, keyPair.getPublic());
            certBuilder.addExtension(Extension.basicConstraints, true, new BasicConstraints(true));
            certBuilder.addExtension(Extension.keyUsage, true, new KeyUsage(KeyUsage.keyCertSign | KeyUsage.cRLSign));

            JcaContentSignerBuilder signerBuilder = new JcaContentSignerBuilder("SHA256WithRSAEncryption").setProvider("BC");
            X509Certificate cert = new JcaX509CertificateConverter().setProvider("BC").getCertificate(certBuilder.build(signerBuilder.build(keyPair.getPrivate())));

            // Save CA certificate and key
            saveCert(cert, CA_CERT_PATH);
            saveKey(keyPair.getPrivate(), CA_KEY_PATH);

            // Generate server certificate signed by the CA
            createServerCertificate(keyPair);

            // Create keystore
            createKeystore();
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate SSL certificates", e);
        }
    }

    private static void createServerCertificate(KeyPair caKeyPair) throws Exception {
        // Generate server key pair
        KeyPairGenerator kpGen = KeyPairGenerator.getInstance("RSA", "BC");
        kpGen.initialize(2048, new SecureRandom());
        KeyPair serverKeyPair = kpGen.generateKeyPair();

        // Create server certificate
        X500Name subject = new X500Name("CN=localhost");
        BigInteger serial = BigInteger.valueOf(System.currentTimeMillis());
        Date notBefore = new Date();
        Date notAfter = new Date(System.currentTimeMillis() + (365 * 24 * 60 * 60 * 1000));

        JcaX509v3CertificateBuilder certBuilder = new JcaX509v3CertificateBuilder(new X500Name("CN=qortal.org"), serial, notBefore, notAfter, subject, serverKeyPair.getPublic());
        certBuilder.addExtension(Extension.basicConstraints, false, new BasicConstraints(false));
        certBuilder.addExtension(Extension.keyUsage, true, new KeyUsage(KeyUsage.digitalSignature | KeyUsage.keyEncipherment));

        JcaContentSignerBuilder signerBuilder = new JcaContentSignerBuilder("SHA256WithRSAEncryption").setProvider("BC");
        X509Certificate cert = new JcaX509CertificateConverter().setProvider("BC").getCertificate(certBuilder.build(signerBuilder.build(caKeyPair.getPrivate())));

        // Save server certificate and key
        saveCert(cert, SERVER_CERT_PATH);
        saveKey(serverKeyPair.getPrivate(), SERVER_KEY_PATH);
    }

    private static void saveCert(X509Certificate cert, String path) throws Exception {
        StringWriter sw = new StringWriter();
        try (PemWriter pw = new PemWriter(sw)) {
            pw.writeObject(new PemObject("CERTIFICATE", cert.getEncoded()));
        }
        try (FileOutputStream fos = new FileOutputStream(path)) {
            fos.write(sw.toString().getBytes());
        }
        setFilePermissions(path);
    }

    private static void saveKey(PrivateKey key, String path) throws Exception {
        StringWriter sw = new StringWriter();
        try (PemWriter pw = new PemWriter(sw)) {
            pw.writeObject(new PemObject("PRIVATE KEY", key.getEncoded()));
        }
        try (FileOutputStream fos = new FileOutputStream(path)) {
            fos.write(sw.toString().getBytes());
        }
        setFilePermissions(path);
    }

    private static void setFilePermissions(String path) {
        File file = new File(path);
        file.setReadable(false, false);
        file.setWritable(false, false);
        file.setExecutable(false, false);
        file.setReadable(true, true);
        file.setWritable(true, true);
    }

    private static void createKeystore() throws Exception {
        // Load CA certificate
        CertificateFactory cf = CertificateFactory.getInstance("X.509", "BC");
        X509Certificate caCert = (X509Certificate) cf.generateCertificate(new FileInputStream(CA_CERT_PATH));

        // Load server certificate
        X509Certificate serverCert = (X509Certificate) cf.generateCertificate(new FileInputStream(SERVER_CERT_PATH));

        // Load server key
        PrivateKey serverKey;
        try (FileReader keyReader = new FileReader(SERVER_KEY_PATH); PemReader pemReader = new PemReader(keyReader)) {
            PemObject pemObject = pemReader.readPemObject();
            PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(pemObject.getContent());
            KeyFactory kf = KeyFactory.getInstance("RSA", "BC");
            serverKey = kf.generatePrivate(keySpec);
        }

        // Create keystore
        KeyStore keyStore = KeyStore.getInstance("PKCS12", "BC");
        keyStore.load(null, null);

        // Add server certificate and key
        keyStore.setKeyEntry("server", serverKey, Settings.getInstance().getSslKeystorePassword().toCharArray(), new java.security.cert.Certificate[]{serverCert, caCert});

        // Save keystore
        try (FileOutputStream fos = new FileOutputStream(Settings.getInstance().getSslKeystorePathname())) {
            keyStore.store(fos, Settings.getInstance().getSslKeystorePassword().toCharArray());
        }
    }
}
