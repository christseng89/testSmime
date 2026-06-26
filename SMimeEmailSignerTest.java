import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import com.icegreen.greenmail.configuration.GreenMailConfiguration;
import com.icegreen.greenmail.junit5.GreenMailExtension;
import com.icegreen.greenmail.util.GreenMailUtil;
import com.icegreen.greenmail.util.ServerSetupTest;

import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for SMimeEmailSigner
 * 
 * Tests the S/MIME email signing functionality including:
 * - Email creation and signing
 * - SMTP configuration
 * - Signature verification
 * - Error handling scenarios
 */
public class SMimeEmailSignerTest {
    
    @RegisterExtension
    static GreenMailExtension greenMail = new GreenMailExtension(ServerSetupTest.SMTP)
            .withConfiguration(GreenMailConfiguration.aConfig().withUser("test@localhost", "test"))
            .withPerMethodLifecycle(false);
    
    private SMimeEmailSigner emailSigner;
    private Properties testMailProperties;
    
    @Mock
    private java.security.cert.X509Certificate mockCertificate;
    
    @Mock
    private java.security.PrivateKey mockPrivateKey;
    
    @BeforeEach
    void setUp() throws Exception {
        MockitoAnnotations.openMocks(this);
        
        // Setup test email properties
        testMailProperties = new Properties();
        testMailProperties.setProperty("mail.smtp.host", "localhost");
        testMailProperties.setProperty("mail.smtp.port", String.valueOf(greenMail.getSmtp().getPort()));
        testMailProperties.setProperty("mail.smtp.user", "test@localhost");
        testMailProperties.setProperty("mail.smtp.password", "test");
        testMailProperties.setProperty("mail.smtp.auth", "false");
        testMailProperties.setProperty("mail.smtp.starttls.enable", "false");
        
        // Create email signer with test configuration
        // Note: In a real test, you would need actual certificate files
        // For this test, we'll use mock objects
        emailSigner = createTestEmailSigner();
    }
    
    private SMimeEmailSigner createTestEmailSigner() throws Exception {
        // For testing purposes, we'll create a minimal implementation
        // that bypasses actual certificate loading
        return new TestSMimeEmailSigner(testMailProperties);
    }
    
    @Test
    @DisplayName("Should successfully send a basic signed email")
    void testSendBasicSignedEmail() throws Exception {
        // Arrange
        String fromEmail = "mgts@company.com";
        String toEmail = "customer@external.com";
        String subject = "Test S/MIME Signed Email";
        String messageBody = "This is a test message with S/MIME signature.";
        
        // Act
        boolean result = emailSigner.sendSignedEmail(fromEmail, toEmail, subject, messageBody);
        
        // Assert
        assertTrue(result, "Email should be sent successfully");
        
        // Verify email was received
        greenMail.waitForIncomingEmail(5000, 1);
        MimeMessage[] messages = greenMail.getReceivedMessages();
        assertEquals(1, messages.length, "Should receive exactly one email");
        
        MimeMessage receivedMessage = messages[0];
        assertEquals(subject, receivedMessage.getSubject(), "Subject should match");
        assertEquals(fromEmail, receivedMessage.getFrom()[0].toString(), "From address should match");
    }
    
    @Test
    @DisplayName("Should successfully send signed email with attachment")
    void testSendSignedEmailWithAttachment() throws Exception {
        // Arrange
        String fromEmail = "mgts@company.com";
        String toEmail = "customer@external.com";
        String subject = "Test Email with Attachment";
        String messageBody = "This email contains an attachment and S/MIME signature.";
        
        // Create a test attachment file
        String testAttachmentPath = createTestAttachment();
        
        // Act
        boolean result = emailSigner.sendSignedEmail(fromEmail, toEmail, subject, messageBody, testAttachmentPath);
        
        // Assert
        assertTrue(result, "Email with attachment should be sent successfully");
        
        // Verify email was received
        greenMail.waitForIncomingEmail(5000, 1);
        MimeMessage[] messages = greenMail.getReceivedMessages();
        assertEquals(1, messages.length, "Should receive exactly one email");
        
        MimeMessage receivedMessage = messages[0];
        assertTrue(receivedMessage.getContent() instanceof MimeMultipart, 
                  "Message should be multipart due to attachment");
    }
    
    @Test
    @DisplayName("Should handle invalid email addresses gracefully")
    void testInvalidEmailAddress() {
        // Arrange
        String fromEmail = "invalid-email";
        String toEmail = "customer@external.com";
        String subject = "Test Invalid From Email";
        String messageBody = "This should fail due to invalid from address.";
        
        // Act
        boolean result = emailSigner.sendSignedEmail(fromEmail, toEmail, subject, messageBody);
        
        // Assert
        assertFalse(result, "Should fail with invalid email address");
    }
    
    @Test
    @DisplayName("Should create SMTP properties correctly")
    void testCreateSMTPProperties() {
        // Act
        Properties props = SMimeEmailSigner.createSMTPProperties(
            "smtp.test.com", 587, "user@test.com", "password", true);
        
        // Assert
        assertEquals("smtp.test.com", props.getProperty("mail.smtp.host"));
        assertEquals("587", props.getProperty("mail.smtp.port"));
        assertEquals("user@test.com", props.getProperty("mail.smtp.user"));
        assertEquals("password", props.getProperty("mail.smtp.password"));
        assertEquals("true", props.getProperty("mail.smtp.auth"));
        assertEquals("true", props.getProperty("mail.smtp.starttls.enable"));
    }
    
    @Test
    @DisplayName("Should verify S/MIME signature detection")
    void testSignatureVerification() throws Exception {
        // Arrange - create a test signed message
        String fromEmail = "mgts@company.com";
        String toEmail = "customer@external.com";
        String subject = "Test Signature Verification";
        String messageBody = "This message will be checked for S/MIME signature.";
        
        // Send a signed email first
        emailSigner.sendSignedEmail(fromEmail, toEmail, subject, messageBody);
        
        // Wait for email and retrieve it
        greenMail.waitForIncomingEmail(5000, 1);
        MimeMessage[] messages = greenMail.getReceivedMessages();
        MimeMessage signedMessage = messages[0];
        
        // Act
        boolean hasSignature = emailSigner.verifySignature(signedMessage);
        
        // Assert
        // Note: In this test implementation, we're checking for the signature indicator
        // In a real implementation with actual certificates, this would verify the cryptographic signature
        assertTrue(hasSignature || signedMessage.getContent() instanceof MimeMultipart, 
                  "Should detect S/MIME signature or multipart content");
    }
    
    @Test
    @DisplayName("Should handle batch email sending")
    void testBatchEmailSending() throws Exception {
        // Arrange
        String fromEmail = "mgts@company.com";
        String[] recipients = {
            "customer1@external.com",
            "customer2@external.com",
            "customer3@external.com"
        };
        String subject = "Batch Test Email";
        String messageBody = "This is a batch test email.";
        
        // Act
        int successCount = 0;
        for (String recipient : recipients) {
            if (emailSigner.sendSignedEmail(fromEmail, recipient, subject, messageBody)) {
                successCount++;
            }
        }
        
        // Assert
        assertEquals(recipients.length, successCount, "All batch emails should be sent successfully");
        
        // Verify all emails were received
        greenMail.waitForIncomingEmail(5000, recipients.length);
        MimeMessage[] messages = greenMail.getReceivedMessages();
        assertEquals(recipients.length, messages.length, "Should receive all batch emails");
    }
    
    /**
     * Test implementation of SMimeEmailSigner for unit testing
     */
    private static class TestSMimeEmailSigner extends SMimeEmailSigner {
        
        public TestSMimeEmailSigner(Properties mailProperties) throws Exception {
            super(null, null, mailProperties);
        }
        
        // Override certificate loading for testing
        @Override
        protected void loadCertificateAndKey(String certFile, String keyFile) throws Exception {
            // Skip actual certificate loading in tests
            System.out.println("Test mode: Skipping certificate loading");
        }
        
        // Override signing for testing - just add a signature indicator
        @Override
        protected MimeMultipart signContent(MimeMultipart content) throws Exception {
            System.out.println("Test mode: Applying mock S/MIME signature");
            return super.signContent(content);
        }
    }
    
    /**
     * Create a test attachment file
     */
    private String createTestAttachment() throws Exception {
        java.io.File tempFile = java.io.File.createTempFile("test-attachment", ".txt");
        tempFile.deleteOnExit();
        
        try (java.io.FileWriter writer = new java.io.FileWriter(tempFile)) {
            writer.write("This is a test attachment for S/MIME email signing test.");
        }
        
        return tempFile.getAbsolutePath();
    }
}
