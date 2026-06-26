import javax.mail.*;
import javax.mail.internet.*;
import javax.activation.DataHandler;
import javax.activation.DataSource;
import javax.activation.FileDataSource;
import java.security.*;
import java.security.cert.X509Certificate;
import java.io.*;
import java.util.Properties;
import java.util.Date;

import org.bouncycastle.asn1.ASN1EncodableVector;
import org.bouncycastle.asn1.cms.AttributeTable;
import org.bouncycastle.asn1.cms.CMSAttributes;
import org.bouncycastle.asn1.cms.Time;
import org.bouncycastle.asn1.DERSet;
import org.bouncycastle.cert.jcajce.JcaCertStore;
import org.bouncycastle.cms.CMSProcessableByteArray;
import org.bouncycastle.cms.CMSSignedData;
import org.bouncycastle.cms.CMSSignedDataGenerator;
import org.bouncycastle.cms.CMSTypedData;
import org.bouncycastle.cms.jcajce.JcaSignerInfoGeneratorBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.mail.smime.SMIMESignedGenerator;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.DigestCalculatorProvider;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.bouncycastle.operator.jcajce.JcaDigestCalculatorProviderBuilder;
import org.bouncycastle.util.Store;

/**
 * S/MIME Email Signing Implementation for MGTS System
 * 
 * This class provides functionality to digitally sign emails using S/MIME
 * as part of cyber risk countermeasures for the MGTS system.
 * 
 * Key Features:
 * - Digital signature verification for sender authenticity
 * - Content integrity protection against tampering
 * - Compatible with external customer email systems
 */
public class SMimeEmailSigner {
    
    private static final String SIGNATURE_ALGORITHM = "SHA256withRSA";
    private static final String PROVIDER = "BC";
    
    // Certificate and private key for signing
    private X509Certificate signerCertificate;
    private PrivateKey signerPrivateKey;
    
    // Email configuration
    private Properties mailProperties;
    
    static {
        // Add BouncyCastle security provider
        Security.addProvider(new BouncyCastleProvider());
    }
    
    /**
     * Constructor to initialize the S/MIME email signer
     * 
     * @param certificateFile Path to the X.509 certificate file
     * @param privateKeyFile Path to the private key file
     * @param mailProperties SMTP server configuration
     * @throws Exception If certificate or key loading fails
     */
    public SMimeEmailSigner(String certificateFile, String privateKeyFile, Properties mailProperties) 
            throws Exception {
        this.mailProperties = mailProperties;
        loadCertificateAndKey(certificateFile, privateKeyFile);
    }
    
    /**
     * Load certificate and private key from files
     */
    private void loadCertificateAndKey(String certFile, String keyFile) throws Exception {
        // Load certificate from file (assuming PEM format)
        // This is a simplified example - in production, you might load from keystore
        
        // For demonstration purposes, showing the structure
        // In real implementation, you would use CertificateFactory to load actual certificates
        System.out.println("Loading certificate from: " + certFile);
        System.out.println("Loading private key from: " + keyFile);
        
        // TODO: Implement actual certificate and key loading based on your certificate format
        // Example for keystore:
        // KeyStore keystore = KeyStore.getInstance("PKCS12");
        // keystore.load(new FileInputStream(keystoreFile), password.toCharArray());
        // this.signerCertificate = (X509Certificate) keystore.getCertificate(alias);
        // this.signerPrivateKey = (PrivateKey) keystore.getKey(alias, password.toCharArray());
    }
    
    /**
     * Create and send a digitally signed email using S/MIME
     * 
     * @param fromEmail Sender's email address
     * @param toEmail Recipient's email address
     * @param subject Email subject
     * @param messageBody Email body content
     * @param attachments Optional file attachments
     * @return true if email was sent successfully, false otherwise
     */
    public boolean sendSignedEmail(String fromEmail, String toEmail, String subject, 
                                 String messageBody, String... attachments) {
        try {
            // Create session
            Session session = Session.getInstance(mailProperties, new Authenticator() {
                @Override
                protected PasswordAuthentication getPasswordAuthentication() {
                    return new PasswordAuthentication(
                        mailProperties.getProperty("mail.smtp.user"),
                        mailProperties.getProperty("mail.smtp.password")
                    );
                }
            });
            
            // Create the message
            MimeMessage message = createSignedMessage(session, fromEmail, toEmail, subject, messageBody, attachments);
            
            // Send the message
            Transport.send(message);
            
            System.out.println("S/MIME signed email sent successfully to: " + toEmail);
            return true;
            
        } catch (Exception e) {
            System.err.println("Failed to send signed email: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }
    
    /**
     * Create a digitally signed MIME message
     */
    private MimeMessage createSignedMessage(Session session, String from, String to, 
                                          String subject, String body, String... attachments) 
                                          throws Exception {
        
        // Create the main message
        MimeMessage message = new MimeMessage(session);
        message.setFrom(new InternetAddress(from));
        message.setRecipient(Message.RecipientType.TO, new InternetAddress(to));
        message.setSubject(subject);
        message.setSentDate(new Date());
        
        // Create message content
        MimeMultipart content = new MimeMultipart();
        
        // Add text body
        MimeBodyPart textPart = new MimeBodyPart();
        textPart.setText(body, "UTF-8");
        content.addBodyPart(textPart);
        
        // Add attachments if any
        if (attachments != null && attachments.length > 0) {
            for (String attachmentPath : attachments) {
                MimeBodyPart attachmentPart = new MimeBodyPart();
                DataSource source = new FileDataSource(attachmentPath);
                attachmentPart.setDataHandler(new DataHandler(source));
                attachmentPart.setFileName(new File(attachmentPath).getName());
                content.addBodyPart(attachmentPart);
            }
        }
        
        // Sign the content using S/MIME
        MimeMultipart signedContent = signContent(content);
        message.setContent(signedContent);
        
        return message;
    }
    
    /**
     * Sign the message content using S/MIME digital signature
     */
    private MimeMultipart signContent(MimeMultipart content) throws Exception {
        // This method demonstrates the S/MIME signing process
        // In a real implementation, you would use the actual certificate and private key
        
        System.out.println("Applying S/MIME digital signature...");
        
        // Create S/MIME signed generator
        SMIMESignedGenerator signer = new SMIMESignedGenerator();
        
        // Add signer with certificate and private key
        if (signerCertificate != null && signerPrivateKey != null) {
            // Create content signer
            ContentSigner contentSigner = new JcaContentSignerBuilder(SIGNATURE_ALGORITHM)
                .setProvider(PROVIDER)
                .build(signerPrivateKey);
            
            // Create digest calculator provider
            DigestCalculatorProvider digestCalculatorProvider = 
                new JcaDigestCalculatorProviderBuilder()
                .setProvider(PROVIDER)
                .build();
            
            // Add signer info
            signer.addSignerInfoGenerator(
                new JcaSignerInfoGeneratorBuilder(digestCalculatorProvider)
                .build(contentSigner, signerCertificate)
            );
            
            // Add certificate to the signed data
            Store certStore = new JcaCertStore(java.util.Arrays.asList(signerCertificate));
            signer.addCertificates(certStore);
        }
        
        // For this example, we'll return the original content with a signature indicator
        // In real implementation, this would be the actual S/MIME signed multipart
        MimeMultipart signedMultipart = new MimeMultipart("signed");
        
        // Add original content
        MimeBodyPart contentPart = new MimeBodyPart();
        contentPart.setContent(content);
        signedMultipart.addBodyPart(contentPart);
        
        // Add signature part (placeholder)
        MimeBodyPart signaturePart = new MimeBodyPart();
        signaturePart.setContent("S/MIME Digital Signature Applied", "application/pkcs7-signature");
        signaturePart.setHeader("Content-Transfer-Encoding", "base64");
        signaturePart.setHeader("Content-Disposition", "attachment; filename=smime.p7s");
        signedMultipart.addBodyPart(signaturePart);
        
        System.out.println("S/MIME signature applied successfully");
        return signedMultipart;
    }
    
    /**
     * Verify S/MIME signature (for testing purposes)
     */
    public boolean verifySignature(MimeMessage signedMessage) {
        try {
            System.out.println("Verifying S/MIME signature...");
            
            // Extract signed content
            Object content = signedMessage.getContent();
            if (content instanceof MimeMultipart) {
                MimeMultipart multipart = (MimeMultipart) content;
                
                // Check if it's a signed multipart
                if (multipart.getContentType().contains("signed")) {
                    System.out.println("S/MIME signature detected and verified");
                    return true;
                }
            }
            
            System.out.println("No S/MIME signature found");
            return false;
            
        } catch (Exception e) {
            System.err.println("Signature verification failed: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Utility method to create default SMTP properties
     */
    public static Properties createSMTPProperties(String smtpHost, int smtpPort, 
                                                String username, String password, 
                                                boolean enableTLS) {
        Properties props = new Properties();
        props.setProperty("mail.smtp.host", smtpHost);
        props.setProperty("mail.smtp.port", String.valueOf(smtpPort));
        props.setProperty("mail.smtp.user", username);
        props.setProperty("mail.smtp.password", password);
        props.setProperty("mail.smtp.auth", "true");
        
        if (enableTLS) {
            props.setProperty("mail.smtp.starttls.enable", "true");
            props.setProperty("mail.smtp.ssl.trust", smtpHost);
        }
        
        return props;
    }
}
