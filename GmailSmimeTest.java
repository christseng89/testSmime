import javax.mail.Authenticator;
import javax.mail.Message;
import javax.mail.PasswordAuthentication;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;

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

import org.bouncycastle.cert.jcajce.JcaCertStore;
import org.bouncycastle.cms.jcajce.JcaSimpleSignerInfoGeneratorBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.mail.smime.SMIMESignedGenerator;
import org.bouncycastle.util.Store;

/**
 * Real, working S/MIME signed-email test via Gmail SMTP.
 * Matches the existing project stack: javax.mail + BouncyCastle 1.70, Java 11.
 *
 * Unlike the placeholder in SMimeEmailSigner.signContent(), this actually calls
 * SMIMESignedGenerator.generate(...) to produce a valid multipart/signed message.
 *
 * Prerequisites:
 *   1. Gmail account samfire5200@gmail.com has 2-Step Verification ON and an
 *      App Password created (Google Account > Security > App passwords).
 *   2. Test certs generated with email = samfire5200@gmail.com, e.g.:
 *        smime-test-certs/our-signing.p12   (alias: our-alias)
 *   3. Env vars set:
 *        GMAIL_USER          = samfire5200@gmail.com
 *        GMAIL_APP_PASSWORD  = the 16-char app password (no spaces)
 *        KEYSTORE_PASS       = p12 password (optional; defaults to "changeit")
 */
public class GmailSmimeTest {

    static {
        Security.addProvider(new BouncyCastleProvider());
    }

    public static void main(String[] args) throws Exception {
        final String from  = "samfire5200@gmail.com";
        final String to    = "samfire5201@gmail.com";
        final String user  = requireEnv("GMAIL_USER");
        final String pass  = requireEnv("GMAIL_APP_PASSWORD");
        final String p12   = "smime-test-certs/our-signing.p12";
        final String alias = "our-alias";
        final char[] ksPass = envOr("KEYSTORE_PASS", "changeit").toCharArray();

        // 1) Load signing private key + certificate chain from PKCS#12.
        KeyStore ks = KeyStore.getInstance("PKCS12");
        try (InputStream in = new FileInputStream(p12)) {
            ks.load(in, ksPass);
        }
        PrivateKey key = (PrivateKey) ks.getKey(alias, ksPass);
        if (key == null) throw new IllegalStateException("No key for alias: " + alias);
        List<X509Certificate> chain = new ArrayList<>();
        for (Certificate c : ks.getCertificateChain(alias)) {
            chain.add((X509Certificate) c);
        }
        X509Certificate signerCert = chain.get(0);
        signerCert.checkValidity(); // fail fast if expired

        // 2) Gmail SMTP session: smtp.gmail.com:587, STARTTLS, authenticated.
        Properties props = new Properties();
        props.put("mail.smtp.host", "smtp.gmail.com");
        props.put("mail.smtp.port", "587");
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.starttls.enable", "true");
        props.put("mail.smtp.connectiontimeout", "10000");
        props.put("mail.smtp.timeout", "10000");
        Session session = Session.getInstance(props, new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(user, pass);
            }
        });

        // 3) The content to be signed.
        MimeBodyPart body = new MimeBodyPart();
        body.setText("Hello — this is a real S/MIME signed test message.", "UTF-8");

        // 4) Produce a detached S/MIME signature (multipart/signed, SHA-256).
        SMIMESignedGenerator gen = new SMIMESignedGenerator();
        gen.addSignerInfoGenerator(
                new JcaSimpleSignerInfoGeneratorBuilder()
                        .setProvider("BC")
                        .build("SHA256withRSA", key, signerCert));
        Store certs = new JcaCertStore(chain); // include chain so verifier can build trust path
        gen.addCertificates(certs);
        MimeMultipart signedMultipart = gen.generate(body);

        // 5) Assemble and send.
        MimeMessage msg = new MimeMessage(session);
        msg.setFrom(new InternetAddress(from));
        msg.setRecipient(Message.RecipientType.TO, new InternetAddress(to));
        msg.setSubject("S/MIME signed test");
        msg.setContent(signedMultipart);   // preserves the multipart/signed content type
        msg.saveChanges();

        Transport.send(msg);
        System.out.println("Sent signed mail from " + from + " to " + to);
    }

    private static String requireEnv(String n) {
        String v = System.getenv(n);
        if (v == null || v.trim().isEmpty()) {
            throw new IllegalStateException("Missing env var: " + n);
        }
        return v;
    }

    private static String envOr(String n, String def) {
        String v = System.getenv(n);
        return (v == null || v.trim().isEmpty()) ? def : v;
    }
}
