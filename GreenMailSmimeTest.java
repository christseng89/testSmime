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
import java.util.Collection;
import java.util.List;
import java.util.Properties;

import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.jcajce.JcaCertStore;
import org.bouncycastle.cms.SignerInformation;
import org.bouncycastle.cms.SignerInformationStore;
import org.bouncycastle.cms.SignerInformationVerifier;
import org.bouncycastle.cms.jcajce.JcaSimpleSignerInfoGeneratorBuilder;
import org.bouncycastle.cms.jcajce.JcaSimpleSignerInfoVerifierBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.mail.smime.SMIMESigned;
import org.bouncycastle.mail.smime.SMIMESignedGenerator;
import org.bouncycastle.util.Store;

import com.icegreen.greenmail.util.GreenMail;
import com.icegreen.greenmail.util.ServerSetupTest;

/**
 * S/MIME signed-email test that runs against TWO environments, selected by the
 * MAIL_ENV environment variable:
 *
 *   MAIL_ENV=greenmail   (default) -> embedded local SMTP, no network, no account.
 *                                     Sends, receives, and VERIFIES the signature
 *                                     entirely offline. Best for CI / dev.
 *
 *   MAIL_ENV=gmail                 -> real send via smtp.gmail.com:587 (STARTTLS).
 *                                     Needs GMAIL_USER + GMAIL_APP_PASSWORD
 *                                     (account must have 2-Step Verification + App
 *                                     Password). Delivery only; no local verify.
 *
 * Shared across both: PKCS#12 loading + SMIMESignedGenerator signing, so you test
 * the exact same signing code path in either environment.
 *
 * Stack: javax.mail + BouncyCastle 1.70 + GreenMail (already in your pom).
 *
 * Common env vars:
 *   KEYSTORE_PASS   p12 password (default "changeit")
 *   FROM_EMAIL      sender   (default samfire5200@gmail.com)
 *   TO_EMAIL        recipient(default samfire5201@gmail.com)
 */
public class GreenMailSmimeTest {

    static {
        Security.addProvider(new BouncyCastleProvider());
    }

    private static final String P12_PATH = "smime-test-certs/our-signing.p12";
    private static final String ALIAS    = "our-alias";

    public static void main(String[] args) throws Exception {
        String env  = envOr("MAIL_ENV", "greenmail").toLowerCase();
        String from = envOr("FROM_EMAIL", "samfire5200@gmail.com");
        String to   = envOr("TO_EMAIL", "samfire5201@gmail.com");

        // ---- Shared: load signing material + build the signed message ----
        Signer signer = loadSigner();
        switch (env) {
            case "greenmail":
                runGreenMail(signer, from, to);
                break;
            case "gmail":
                runGmail(signer, from, to);
                break;
            default:
                throw new IllegalArgumentException("Unknown MAIL_ENV: " + env
                        + " (use 'greenmail' or 'gmail')");
        }
    }

    // ---------------------------------------------------------------- GreenMail
    private static void runGreenMail(Signer signer, String from, String to) throws Exception {
        // Embedded SMTP on 127.0.0.1:3025, no auth, no TLS.
        GreenMail greenMail = new GreenMail(ServerSetupTest.SMTP);
        greenMail.start();
        try {
            Properties props = new Properties();
            props.put("mail.smtp.host", "127.0.0.1");
            props.put("mail.smtp.port", String.valueOf(ServerSetupTest.SMTP.getPort()));
            props.put("mail.smtp.auth", "false");
            Session session = Session.getInstance(props);

            MimeMessage msg = buildSignedMessage(session, signer, from, to,
                    "S/MIME signed test (GreenMail)");
            Transport.send(msg);

            // Pull it back out of the in-memory server and verify the signature.
            greenMail.waitForIncomingEmail(5000, 1);
            MimeMessage[] received = greenMail.getReceivedMessages();
            System.out.println("GreenMail received " + received.length + " message(s).");

            boolean ok = verifySignature(received[0]);
            System.out.println(ok
                    ? "SIGNATURE VERIFIED — offline round-trip succeeded."
                    : "SIGNATURE INVALID — check certs / signing code.");
        } finally {
            greenMail.stop();
        }
    }

    // --------------------------------------------------------------------- Gmail
    private static void runGmail(Signer signer, String from, String to) throws Exception {
        final String user = requireEnv("GMAIL_USER");
        final String pass = requireEnv("GMAIL_APP_PASSWORD");

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

        MimeMessage msg = buildSignedMessage(session, signer, from, to,
                "S/MIME signed test (Gmail)");
        Transport.send(msg);
        System.out.println("Sent signed mail via Gmail from " + from + " to " + to);
    }

    // ------------------------------------------------------------ shared helpers
    private static Signer loadSigner() throws Exception {
        char[] ksPass = envOr("KEYSTORE_PASS", "changeit").toCharArray();
        KeyStore ks = KeyStore.getInstance("PKCS12");
        try (InputStream in = new FileInputStream(P12_PATH)) {
            ks.load(in, ksPass);
        }
        PrivateKey key = (PrivateKey) ks.getKey(ALIAS, ksPass);
        if (key == null) throw new IllegalStateException("No key for alias: " + ALIAS);
        List<X509Certificate> chain = new ArrayList<>();
        for (Certificate c : ks.getCertificateChain(ALIAS)) {
            chain.add((X509Certificate) c);
        }
        chain.get(0).checkValidity();
        return new Signer(key, chain);
    }

    /** Build a detached multipart/signed message (SHA-256). Shared by both envs. */
    private static MimeMessage buildSignedMessage(Session session, Signer signer,
                                                  String from, String to, String subject)
            throws Exception {
        MimeBodyPart body = new MimeBodyPart();
        body.setText("Hello — this is a real S/MIME signed test message.", "UTF-8");

        SMIMESignedGenerator gen = new SMIMESignedGenerator();
        gen.addSignerInfoGenerator(
                new JcaSimpleSignerInfoGeneratorBuilder()
                        .setProvider("BC")
                        .build("SHA256withRSA", signer.key, signer.chain.get(0)));
        Store certs = new JcaCertStore(signer.chain);
        gen.addCertificates(certs);
        MimeMultipart signed = gen.generate(body);

        MimeMessage msg = new MimeMessage(session);
        msg.setFrom(new InternetAddress(from));
        // 'to' may be a comma-separated list, e.g. "a@x.com,b@y.com".
        msg.setRecipients(Message.RecipientType.TO, InternetAddress.parse(to, false));
        msg.setSubject(subject);
        msg.setContent(signed);
        msg.saveChanges();
        return msg;
    }

    /** Real S/MIME signature verification using BouncyCastle. */
    @SuppressWarnings("unchecked")
    private static boolean verifySignature(MimeMessage msg) throws Exception {
        Object content = msg.getContent();
        if (!(content instanceof MimeMultipart)) {
            System.out.println("Not a multipart/signed message.");
            return false;
        }
        SMIMESigned signed = new SMIMESigned((MimeMultipart) content);
        Store<X509CertificateHolder> certStore = signed.getCertificates();
        SignerInformationStore signers = signed.getSignerInfos();

        boolean allOk = !signers.getSigners().isEmpty();
        for (SignerInformation si : signers.getSigners()) {
            Collection<X509CertificateHolder> matches = certStore.getMatches(si.getSID());
            if (matches.isEmpty()) { allOk = false; continue; }
            X509CertificateHolder certHolder = matches.iterator().next();
            SignerInformationVerifier verifier = new JcaSimpleSignerInfoVerifierBuilder()
                    .setProvider("BC").build(certHolder);
            if (!si.verify(verifier)) allOk = false;
        }
        return allOk;
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

    /** Holds private key + certificate chain. */
    private static final class Signer {
        final PrivateKey key;
        final List<X509Certificate> chain;
        Signer(PrivateKey key, List<X509Certificate> chain) { this.key = key; this.chain = chain; }
    }
}
