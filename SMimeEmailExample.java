import java.util.Properties;

/**
 * Example usage of S/MIME Email Signing for MGTS System
 * 
 * This class demonstrates how to use the SMimeEmailSigner to send
 * digitally signed emails as part of cyber security countermeasures.
 */
public class SMimeEmailExample {
    
    public static void main(String[] args) {
        try {
            // Example usage of S/MIME email signing
            demonstrateSMimeEmailSigning();
            
        } catch (Exception e) {
            System.err.println("Error in S/MIME email demonstration: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Demonstrate S/MIME email signing functionality
     */
    private static void demonstrateSMimeEmailSigning() throws Exception {
        System.out.println("=== MGTS S/MIME Email Signing Example ===");
        
        // Configure SMTP properties for your mail server
        Properties smtpProps = SMimeEmailSigner.createSMTPProperties(
            "smtp.company.com",    // SMTP server host
            587,                   // SMTP server port
            "mgts@company.com",    // SMTP username
            "password",            // SMTP password
            true                   // Enable TLS
        );
        
        // Certificate and private key paths
        String certificatePath = "certificates/mgts-signing-cert.pem";
        String privateKeyPath = "certificates/mgts-private-key.pem";
        
        // Initialize the S/MIME email signer
        SMimeEmailSigner emailSigner = new SMimeEmailSigner(
            certificatePath, 
            privateKeyPath, 
            smtpProps
        );
        
        // Example 1: Send notification email with digital signature
        sendNotificationEmail(emailSigner);
        
        // Example 2: Send alert email with attachment and signature
        sendAlertEmailWithAttachment(emailSigner);
        
        // Example 3: Send batch notification emails
        sendBatchNotifications(emailSigner);
        
        System.out.println("=== S/MIME Email Signing Examples Completed ===");
    }
    
    /**
     * Send a basic notification email with S/MIME signature
     */
    private static void sendNotificationEmail(SMimeEmailSigner emailSigner) {
        System.out.println("\n--- Sending Basic Notification Email ---");
        
        String subject = "[MGTS] System Status Notification";
        String messageBody = buildNotificationMessage(
            "System Health Check", 
            "All systems are operating normally",
            "No action required"
        );
        
        boolean success = emailSigner.sendSignedEmail(
            "mgts-notifications@company.com",
            "customer@external-company.com",
            subject,
            messageBody
        );
        
        if (success) {
            System.out.println("✓ Notification email sent with S/MIME signature");
        } else {
            System.err.println("✗ Failed to send notification email");
        }
    }
    
    /**
     * Send an alert email with attachment and S/MIME signature
     */
    private static void sendAlertEmailWithAttachment(SMimeEmailSigner emailSigner) {
        System.out.println("\n--- Sending Alert Email with Attachment ---");
        
        String subject = "[MGTS ALERT] Security Incident Report";
        String messageBody = buildAlertMessage(
            "Security Alert",
            "Potential security incident detected",
            "Please review the attached report immediately"
        );
        
        // Example attachment (in real scenario, this would be an actual report file)
        String attachmentPath = "reports/security-incident-report.pdf";
        
        boolean success = emailSigner.sendSignedEmail(
            "mgts-security@company.com",
            "security-team@external-company.com",
            subject,
            messageBody,
            attachmentPath
        );
        
        if (success) {
            System.out.println("✓ Alert email sent with attachment and S/MIME signature");
        } else {
            System.err.println("✗ Failed to send alert email");
        }
    }
    
    /**
     * Send multiple notification emails (batch processing)
     */
    private static void sendBatchNotifications(SMimeEmailSigner emailSigner) {
        System.out.println("\n--- Sending Batch Notifications ---");
        
        String[] recipients = {
            "customer1@external-company.com",
            "customer2@external-company.com",
            "customer3@external-company.com"
        };
        
        for (String recipient : recipients) {
            String subject = "[MGTS] Monthly System Report";
            String messageBody = buildMonthlyReport(recipient);
            
            boolean success = emailSigner.sendSignedEmail(
                "mgts-reports@company.com",
                recipient,
                subject,
                messageBody
            );
            
            if (success) {
                System.out.println("✓ Monthly report sent to: " + recipient);
            } else {
                System.err.println("✗ Failed to send report to: " + recipient);
            }
            
            // Small delay between emails to avoid overwhelming the server
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
    
    /**
     * Build a notification message body
     */
    private static String buildNotificationMessage(String title, String status, String action) {
        StringBuilder message = new StringBuilder();
        message.append("MGTS System Notification\n");
        message.append("========================\n\n");
        message.append("Title: ").append(title).append("\n");
        message.append("Status: ").append(status).append("\n");
        message.append("Action Required: ").append(action).append("\n\n");
        message.append("This email has been digitally signed using S/MIME to ensure:\n");
        message.append("• Sender authenticity verification\n");
        message.append("• Message integrity protection\n");
        message.append("• Non-repudiation assurance\n\n");
        message.append("Please verify the digital signature to confirm the authenticity of this message.\n\n");
        message.append("Best regards,\n");
        message.append("MGTS Automated Notification System\n");
        message.append("Company Name\n");
        return message.toString();
    }
    
    /**
     * Build an alert message body
     */
    private static String buildAlertMessage(String alertType, String description, String instruction) {
        StringBuilder message = new StringBuilder();
        message.append("MGTS SECURITY ALERT\n");
        message.append("===================\n\n");
        message.append("ALERT TYPE: ").append(alertType).append("\n");
        message.append("DESCRIPTION: ").append(description).append("\n");
        message.append("TIMESTAMP: ").append(new java.util.Date()).append("\n\n");
        message.append("INSTRUCTIONS:\n");
        message.append(instruction).append("\n\n");
        message.append("SECURITY NOTICE:\n");
        message.append("This message contains sensitive security information and has been\n");
        message.append("digitally signed using S/MIME for your protection. Please verify\n");
        message.append("the digital signature before taking any action.\n\n");
        message.append("If you have any questions, please contact our security team immediately.\n\n");
        message.append("MGTS Security Team\n");
        message.append("Company Name\n");
        return message.toString();
    }
    
    /**
     * Build a monthly report message
     */
    private static String buildMonthlyReport(String customerEmail) {
        StringBuilder message = new StringBuilder();
        message.append("MGTS Monthly System Report\n");
        message.append("==========================\n\n");
        message.append("Dear Valued Customer,\n\n");
        message.append("Please find below your monthly MGTS system report:\n\n");
        message.append("System Performance Metrics:\n");
        message.append("• Uptime: 99.9%\n");
        message.append("• Response Time: Average 2.3 seconds\n");
        message.append("• Transactions Processed: 15,420\n");
        message.append("• Security Incidents: 0\n\n");
        message.append("Security Enhancements:\n");
        message.append("• S/MIME digital signatures implemented\n");
        message.append("• Enhanced encryption protocols deployed\n");
        message.append("• Regular security audits conducted\n\n");
        message.append("This report has been digitally signed to ensure its authenticity\n");
        message.append("and integrity. The S/MIME signature provides:\n");
        message.append("• Confirmation that this report originates from MGTS\n");
        message.append("• Assurance that the content has not been modified\n");
        message.append("• Legal non-repudiation protection\n\n");
        message.append("Thank you for choosing MGTS services.\n\n");
        message.append("Best regards,\n");
        message.append("MGTS Customer Service Team\n");
        message.append("Company Name\n");
        return message.toString();
    }
    
    /**
     * Configuration class for different environments
     */
    public static class MGTSEmailConfig {
        
        public static Properties getProductionConfig() {
            return SMimeEmailSigner.createSMTPProperties(
                "smtp.production.company.com",
                587,
                "mgts-prod@company.com",
                System.getenv("MGTS_EMAIL_PASSWORD"),
                true
            );
        }
        
        public static Properties getTestConfig() {
            return SMimeEmailSigner.createSMTPProperties(
                "smtp.test.company.com",
                587,
                "mgts-test@company.com",
                "test-password",
                true
            );
        }
        
        public static Properties getDevelopmentConfig() {
            return SMimeEmailSigner.createSMTPProperties(
                "localhost",
                1025,
                "dev@localhost",
                "",
                false
            );
        }
    }
}
