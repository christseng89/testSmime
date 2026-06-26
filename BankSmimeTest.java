import javax.mail.Authenticator;
import javax.mail.Message;
import javax.mail.PasswordAuthentication;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;
import javax.activation.CommandMap;
import javax.activation.MailcapCommandMap;

import java.io.FileInputStream;
import java.io.InputStream;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.Security;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

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
 * Standalone S/MIME tester for the BANK environment. Fully independent of
 * GreenMailSmimeTest — its own keystore folder, its own config, so the bank
 * setup never interferes with the GreenMail/Gmail tests.
 *
 * Signs with your private key and (by default) encrypts to the bank's public
 * certificate (sign-then-encrypt, AES-256), then sends via the bank SMTP gateway.
 *
 * Configuration (all via environment variables; test-bank.sh loads .env.bank):
 *   KEYSTORE_PATH      your signing PKCS#12      (default bank-certs/our-signing.p12)
 *   KEYSTORE_PASS      keystore password         (default "changeit")
 *   KEYSTORE_ALIAS     key alias                 (default "our-alias")
 *   BANK_CERT          bank PUBLIC cert .cer/.crt/.pem (required when encrypting)
 *   BANK_ENCRYPT       true=sign+encrypt (default) | false=sign only
 *   BANK_SMTP_HOST     required
 *   BANK_SMTP_PORT     default 587
 *   BANK_SMTP_STARTTLS default true
 *   BANK_SMTP_AUTH     default true
 *   BANK_SMTP_USER     required if auth
 *   BANK_SMTP_PASSWORD required if auth
 *   FROM_EMAIL         required
 *   TO_EMAIL           required (comma-separated allowed)
 *
 * Stack: javax.mail + BouncyCastle 1.70 (same as the existing project pom).
 */
public class BankSmimeTest {

    static {
        Security.addProvider(new BouncyCastleProvider());
        registerSmimeHandlers();
    }

    public static void main(String[] args) throws Exception {
        final String ksPath  = envOr("KEYSTORE_PATH", "bank-certs/our-signing.p12");
        final char[] ksPass  = envOr("KEYSTORE_PASS", "changeit").toCharArray();
        final String alias   = envOr("KEYSTORE_ALIAS", "our-alias");
        final boolean encrypt = Boolean.parseBoolean(envOr("BANK_ENCRYPT", "true"));
        final String from = requireEnv("FROM_EMAIL");
        final String to   = requireEnv("TO_EMAIL");

        // 1) Load signing key + chain.
        Signer signer = loadSigner(ksPath, ksPass, alias);

        // 2) Build the bank SMTP session.
        Session session = buildBankSession();

        // 3) Build signed (or signed+encrypted) message.
        MimeMessage msg;
        if (encrypt) {
            X509Certificate bankCert = loadRecipientCert(requireEnv("BANK_CERT"));
            msg = buildSignedEncrypted(session, signer, bankCert, from, to,
                    "S/MIME signed+encrypted test (Bank)");
            System.out.println("Mode: SIGN + ENCRYPT (AES-256) -> " + to);
        } else {
            msg = buildSignedOnly(session, signer, from, to,
                    "S/MIME signed test (Bank)");
            System.out.println("Mode: SIGN only -> " + to);
        }

        // 4) Send.
        Transport.send(msg);
        System.out.println("Sent to bank SMTP " + session.getProperty("mail.smtp.host")
                + ":" + session.getProperty("mail.smtp.port"));
    }

    // ----------------------------------------------------------------- SMTP
    private static Session buildBankSession() {
        String host = requireEnv("BANK_SMTP_HOST");
        String port = envOr("BANK_SMTP_PORT", "587");
        boolean auth = Boolean.parseBoolean(envOr("BANK_SMTP_AUTH", "true"));
        boolean tls  = Boolean.parseBoolean(envOr("BANK_SMTP_STARTTLS", "true"));

        Properties props = new Properties();
        props.put("mail.smtp.host", host);
        props.put("mail.smtp.port", port);
        props.put("mail.smtp.auth", String.valueOf(auth));
        props.put("mail.smtp.starttls.enable", String.valueOf(tls));
        props.put("mail.smtp.connectiontimeout", "15000");
        props.put("mail.smtp.timeout", "15000");

        if (auth) {
            final String u = requireEnv("BANK_SMTP_USER");
            final String p = requireEnv("BANK_SMTP_PASSWORD");
            return Session.getInstance(props, new Authenticator() {
                @Override
                protected PasswordAuthentication getPasswordAuthentication() {
                    return new PasswordAuthentication(u, p);
                }
            });
        }
        return Session.getInstance(props);
    }

    // --------------------------------------------------------------- signing
    private static MimeMessage buildSignedOnly(Session session, Signer signer,
            String from, String to, String subject) throws Exception {
        MimeMultipart signed = sign(signer, plainBody());
        MimeMessage msg = newMessage(session, from, to, subject);
        msg.setContent(signed);
        msg.saveChanges();
        return msg;
    }

    private static MimeMessage buildSignedEncrypted(Session session, Signer signer,
            X509Certificate recipientCert, String from, String to, String subject)
            throws Exception {
        MimeBodyPart signedPart = new MimeBodyPart();
        signedPart.setContent(sign(signer, plainBody()));

        SMIMEEnvelopedGenerator encGen = new SMIMEEnvelopedGenerator();
        encGen.addRecipientInfoGenerator(
                new JceKeyTransRecipientInfoGenerator(recipientCert).setProvider("BC"));
        MimeBodyPart encrypted = encGen.generate(signedPart,
                new JceCMSContentEncryptorBuilder(CMSAlgorithm.AES256_CBC)
                        .setProvider("BC").build());

        MimeMessage msg = newMessage(session, from, to, subject);
        msg.setContent(encrypted.getContent(), encrypted.getContentType());
        msg.saveChanges();
        return msg;
    }

    /** Detached multipart/signed, SHA-256. */
    private static MimeMultipart sign(Signer signer, MimeBodyPart body) throws Exception {
        SMIMESignedGenerator gen = new SMIMESignedGenerator();
        gen.addSignerInfoGenerator(
                new JcaSimpleSignerInfoGeneratorBuilder()
                        .setProvider("BC")
                        .build("SHA256withRSA", signer.key, signer.chain.get(0)));
        gen.addCertificates(new JcaCertStore(signer.chain));
        return gen.generate(body);
    }

    private static MimeBodyPart plainBody() throws Exception {
        MimeBodyPart body = new MimeBodyPart();
        body.setText("Hello — this is a real S/MIME test message to the bank.", "UTF-8");
        return body;
    }

    private static MimeMessage newMessage(Session session, String from, String to, String subject)
            throws Exception {
        MimeMessage msg = new MimeMessage(session);
        msg.setFrom(new InternetAddress(from));
        msg.setRecipients(Message.RecipientType.TO, InternetAddress.parse(to, false));
        msg.setSubject(subject);
        return msg;
    }

    // --------------------------------------------------------------- loading
    private static Signer loadSigner(String p12Path, char[] pass, String alias) throws Exception {
        KeyStore ks = KeyStore.getInstance("PKCS12");
        try (InputStream in = new FileInputStream(p12Path)) {
            ks.load(in, pass);
        }
        PrivateKey key = (PrivateKey) ks.getKey(alias, pass);
        if (key == null) throw new IllegalStateException("No key for alias: " + alias);
        List<X509Certificate> chain = new ArrayList<>();
        for (Certificate c : ks.getCertificateChain(alias)) {
            chain.add((X509Certificate) c);
        }
        chain.get(0).checkValidity();
        return new Signer(key, chain);
    }

    private static X509Certificate loadRecipientCert(String path) throws Exception {
        try (InputStream in = new FileInputStream(path)) {
            return (X509Certificate) CertificateFactory.getInstance("X.509")
                    .generateCertificate(in);
        }
    }

    // --------------------------------------------------------------- plumbing
    private static void registerSmimeHandlers() {
        MailcapCommandMap mc = (MailcapCommandMap) CommandMap.getDefaultCommandMap();
        mc.addMailcap("application/pkcs7-signature;; x-java-content-handler=org.bouncycastle.mail.smime.handlers.pkcs7_signature");
        mc.addMailcap("application/pkcs7-mime;; x-java-content-handler=org.bouncycastle.mail.smime.handlers.pkcs7_mime");
        mc.addMailcap("application/x-pkcs7-signature;; x-java-content-handler=org.bouncycastle.mail.smime.handlers.x_pkcs7_signature");
        mc.addMailcap("application/x-pkcs7-mime;; x-java-content-handler=org.bouncycastle.mail.smime.handlers.x_pkcs7_mime");
        mc.addMailcap("multipart/signed;; x-java-content-handler=org.bouncycastle.mail.smime.handlers.multipart_signed");
        CommandMap.setDefaultCommandMap(mc);
    }

    private static String requireEnv(String n) {
        String v = System.getenv(n);
        if (v == null || v.trim().isEmpty()) throw new IllegalStateException("Missing env var: " + n);
        return v;
    }

    private static String envOr(String n, String def) {
        String v = System.getenv(n);
        return (v == null || v.trim().isEmpty()) ? def : v;
    }

    private static final class Signer {
        final PrivateKey key;
        final List<X509Certificate> chain;
        Signer(PrivateKey key, List<X509Certificate> chain) { this.key = key; this.chain = chain; }
    }
}
