package com.example.mail;

import java.io.FileInputStream;
import java.io.InputStream;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.Security;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import jakarta.mail.Message;
import jakarta.mail.Session;
import jakarta.mail.Transport;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeBodyPart;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;

import org.bouncycastle.asn1.ASN1EncodableVector;
import org.bouncycastle.asn1.cms.AttributeTable;
import org.bouncycastle.asn1.smime.SMIMECapabilitiesAttribute;
import org.bouncycastle.asn1.smime.SMIMECapability;
import org.bouncycastle.asn1.smime.SMIMECapabilityVector;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.cert.jcajce.JcaCertStore;
import org.bouncycastle.cms.CMSAlgorithm;
import org.bouncycastle.cms.jcajce.JcaSimpleSignerInfoGeneratorBuilder;
import org.bouncycastle.cms.jcajce.JceCMSContentEncryptorBuilder;
import org.bouncycastle.cms.jcajce.JceKeyTransRecipientInfoGenerator;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.mail.smime.SMIMEEnvelopedGenerator;
import org.bouncycastle.mail.smime.SMIMESignedGenerator;
import org.bouncycastle.util.Store;

/**
 * S/MIME (Secure MIME) mail sender reference for Java EE / Jakarta EE.
 *
 * Capabilities:
 *   - Sign only        (multipart/signed, detached signature)
 *   - Sign + encrypt   (sign first, then encrypt the signed content)
 *
 * Dependencies (Maven):
 *   org.eclipse.angus:angus-mail                (Jakarta Mail impl)   2.0.x
 *   jakarta.mail:jakarta.mail-api                                      2.1.x
 *   org.bouncycastle:bcprov-jdk18on                                    1.78+
 *   org.bouncycastle:bcpkix-jdk18on                                    1.78+
 *   org.bouncycastle:bcmail-jdk18on                                    1.78+
 *
 * NOTE: This is a reference skeleton. Externalize all secrets (keystore
 * password, SMTP credentials) to JNDI / env vars / a secrets vault.
 * Do NOT hardcode them or commit them to version control.
 */
public class SMIMEMailSender {

    static {
        // Register Bouncy Castle once at class load.
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }

    // ----- SMTP settings (inject from config, do not hardcode in prod) -----
    private final String smtpHost;
    private final int smtpPort;
    private final boolean smtpAuth;
    private final boolean startTls;
    private final String smtpUser;
    private final String smtpPassword;

    public SMIMEMailSender(String smtpHost, int smtpPort, boolean startTls,
                           boolean smtpAuth, String smtpUser, String smtpPassword) {
        this.smtpHost = smtpHost;
        this.smtpPort = smtpPort;
        this.startTls = startTls;
        this.smtpAuth = smtpAuth;
        this.smtpUser = smtpUser;
        this.smtpPassword = smtpPassword;
    }

    /**
     * Load our own signing private key + certificate chain from a PKCS#12 keystore.
     */
    public static SignerMaterial loadSigner(String p12Path, char[] p12Password, String alias)
            throws Exception {
        KeyStore ks = KeyStore.getInstance("PKCS12");
        try (InputStream in = new FileInputStream(p12Path)) {
            ks.load(in, p12Password);
        }
        PrivateKey privateKey = (PrivateKey) ks.getKey(alias, p12Password);
        if (privateKey == null) {
            throw new IllegalStateException("No private key found for alias: " + alias);
        }
        Certificate[] chain = ks.getCertificateChain(alias);
        List<X509Certificate> certChain = new ArrayList<>();
        for (Certificate c : chain) {
            certChain.add((X509Certificate) c);
        }
        // Fail fast on expired certificates.
        certChain.get(0).checkValidity();
        return new SignerMaterial(privateKey, certChain);
    }

    /**
     * Build a digitally SIGNED MimeMessage (detached signature, multipart/signed).
     * Signature digest: SHA-256.
     */
    public MimeMessage buildSignedMessage(Session session, SignerMaterial signer,
                                          String from, String to, String subject,
                                          MimeBodyPart contentPart) throws Exception {

        // Advertise our S/MIME capabilities (preferred algorithms).
        ASN1EncodableVector signedAttrs = new ASN1EncodableVector();
        SMIMECapabilityVector caps = new SMIMECapabilityVector();
        caps.addCapability(SMIMECapability.aES256_CBC);
        caps.addCapability(SMIMECapability.aES128_CBC);
        signedAttrs.add(new SMIMECapabilitiesAttribute(caps));

        X509Certificate signerCert = signer.chain.get(0);

        SMIMESignedGenerator gen = new SMIMESignedGenerator();
        gen.addSignerInfoGenerator(
                new JcaSimpleSignerInfoGeneratorBuilder()
                        .setProvider(BouncyCastleProvider.PROVIDER_NAME)
                        .setSignedAttributeGenerator(new AttributeTable(signedAttrs))
                        .build("SHA256withRSA", signer.privateKey, signerCert));

        // Attach the full certificate chain so the bank can build a trust path.
        Store<?> certs = new JcaCertStore(signer.chain);
        gen.addCertificates(certs);

        MimeMultipart signedMultipart = gen.generate(contentPart);

        MimeMessage message = new MimeMessage(session);
        message.setFrom(new InternetAddress(from));
        message.setRecipient(Message.RecipientType.TO, new InternetAddress(to));
        message.setSubject(subject);
        message.setContent(signedMultipart);
        message.saveChanges();
        return message;
    }

    /**
     * Build a SIGNED-then-ENCRYPTED MimeMessage.
     * Encryption: AES-256, recipient key transport via the bank's public certificate.
     */
    public MimeMessage buildSignedEncryptedMessage(Session session, SignerMaterial signer,
                                                   X509Certificate recipientCert,
                                                   String from, String to, String subject,
                                                   MimeBodyPart contentPart) throws Exception {
        // 1) Sign.
        MimeMessage signed = buildSignedMessage(session, signer, from, to, subject, contentPart);

        // 2) Encrypt the signed content for the recipient (the bank).
        SMIMEEnvelopedGenerator encGen = new SMIMEEnvelopedGenerator();
        encGen.addRecipientInfoGenerator(
                new JceKeyTransRecipientInfoGenerator(recipientCert)
                        .setProvider(BouncyCastleProvider.PROVIDER_NAME));

        MimeBodyPart encryptedPart = encGen.generate(signed,
                new JceCMSContentEncryptorBuilder(CMSAlgorithm.AES256_CBC)
                        .setProvider(BouncyCastleProvider.PROVIDER_NAME)
                        .build());

        MimeMessage message = new MimeMessage(session);
        message.setFrom(new InternetAddress(from));
        message.setRecipient(Message.RecipientType.TO, new InternetAddress(to));
        message.setSubject(subject);
        message.setContent(encryptedPart.getContent(), encryptedPart.getContentType());
        message.saveChanges();
        return message;
    }

    /** Create a Jakarta Mail Session using the configured SMTP settings. */
    public Session createSession() {
        Properties props = new Properties();
        props.put("mail.smtp.host", smtpHost);
        props.put("mail.smtp.port", String.valueOf(smtpPort));
        props.put("mail.smtp.auth", String.valueOf(smtpAuth));
        props.put("mail.smtp.starttls.enable", String.valueOf(startTls));
        // Reasonable timeouts so a hung SMTP server doesn't block threads.
        props.put("mail.smtp.connectiontimeout", "10000");
        props.put("mail.smtp.timeout", "10000");
        props.put("mail.smtp.writetimeout", "10000");
        return Session.getInstance(props);
    }

    /** Load the recipient (bank) public certificate from a .cer / .crt / .pem file. */
    public static X509Certificate loadRecipientCert(String certPath) throws Exception {
        try (InputStream in = new FileInputStream(certPath)) {
            return (X509Certificate) java.security.cert.CertificateFactory
                    .getInstance("X.509").generateCertificate(in);
        }
    }

    /** Send the message via SMTP. */
    public void send(MimeMessage message) throws Exception {
        try (Transport transport = message.getSession().getTransport("smtp")) {
            if (smtpAuth) {
                transport.connect(smtpHost, smtpPort, smtpUser, smtpPassword);
            } else {
                transport.connect();
            }
            transport.sendMessage(message, message.getAllRecipients());
        }
    }

    /** Holds our private key + certificate chain. */
    public static class SignerMaterial {
        final PrivateKey privateKey;
        final List<X509Certificate> chain;
        SignerMaterial(PrivateKey privateKey, List<X509Certificate> chain) {
            this.privateKey = privateKey;
            this.chain = chain;
        }
    }

    // --------------------------- Example usage ---------------------------
    public static void main(String[] args) throws Exception {
        // In production: read these from JNDI / env vars / vault, never hardcode.
        SMIMEMailSender sender = new SMIMEMailSender(
                "smtp.yourbank-gateway.example", 587, true, true,
                System.getenv("SMTP_USER"), System.getenv("SMTP_PASS"));

        SignerMaterial signer = loadSigner(
                "/secure/our-signing.p12",
                System.getenv("KEYSTORE_PASS").toCharArray(),
                "our-alias");

        X509Certificate bankCert = loadRecipientCert("/secure/bank-public.cer");

        // The actual message body.
        MimeBodyPart body = new MimeBodyPart();
        body.setText("Payment instruction attached.", "UTF-8");

        Session session = sender.createSession();

        // Choose one:
        MimeMessage signedOnly = sender.buildSignedMessage(
                session, signer,
                "ops@yourcompany.example", "host2host@bank.example",
                "Daily Instruction", body);

        MimeMessage signedEncrypted = sender.buildSignedEncryptedMessage(
                session, signer, bankCert,
                "ops@yourcompany.example", "host2host@bank.example",
                "Daily Instruction (Encrypted)", body);

        sender.send(signedEncrypted); // or sender.send(signedOnly);
        System.out.println("Sent.");
    }
}
