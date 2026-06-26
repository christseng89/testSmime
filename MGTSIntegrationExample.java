/**
 * MGTS S/MIME Email Integration Example
 * 
 * This class demonstrates the complete integration of S/MIME email signing
 * functionality with the MGTS system, including configuration management,
 * error handling, and production-ready implementation patterns.
 * 
 * This example shows how to:
 * - Initialize the S/MIME email system with configuration
 * - Handle different types of email notifications
 * - Implement error handling and retry logic
 * - Monitor and log email operations
 * - Integrate with existing MGTS application architecture
 */
public class MGTSIntegrationExample {
    
    private static final java.util.logging.Logger logger = 
        java.util.logging.Logger.getLogger(MGTSIntegrationExample.class.getName());
    
    /**
     * Main method demonstrating complete MGTS S/MIME integration
     */
    public static void main(String[] args) {
        try {
            // Determine environment from system property or default to development
            String environment = System.getProperty("mgts.environment", "development");
            System.out.println("Starting MGTS S/MIME Email Integration Example");
            System.out.println("Environment: " + environment);
            
            // Initialize the email service
            MGTSEmailService emailService = new MGTSEmailService(environment);
            
            // Demonstrate different email scenarios
            demonstrateSystemNotification(emailService);
            demonstrateSecurityAlert(emailService);
            demonstrateMonthlyReport(emailService);
            demonstrateBatchNotifications(emailService);
            
            // Demonstrate error handling
            demonstrateErrorHandling(emailService);
            
            System.out.println("MGTS S/MIME Email Integration Example completed successfully");
            
        } catch (Exception e) {
            System.err.println("Integration example failed: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Demonstrate system notification email
     */
    private static void demonstrateSystemNotification(MGTSEmailService emailService) {
        System.out.println("\n--- System Notification Example ---");
        
        MGTSEmailRequest request = new MGTSEmailRequest.Builder()
            .to("customer@external-company.com")
            .type(MGTSEmailType.SYSTEM_NOTIFICATION)
            .subject("MGTS System Maintenance Complete")
            .addParameter("maintenanceType", "Database Optimization")
            .addParameter("duration", "2 hours")
            .addParameter("impact", "No service interruption")
            .build();
        
        boolean success = emailService.sendEmail(request);
        System.out.println("System notification sent: " + (success ? "SUCCESS" : "FAILED"));
    }
    
    /**
     * Demonstrate security alert email
     */
    private static void demonstrateSecurityAlert(MGTSEmailService emailService) {
        System.out.println("\n--- Security Alert Example ---");
        
        MGTSEmailRequest request = new MGTSEmailRequest.Builder()
            .to("security@external-company.com")
            .type(MGTSEmailType.SECURITY_ALERT)
            .priority(MGTSEmailPriority.HIGH)
            .subject("Suspicious Activity Detected")
            .addParameter("alertType", "Failed Login Attempts")
            .addParameter("count", "5")
            .addParameter("timeframe", "Last 10 minutes")
            .addParameter("action", "Account temporarily locked")
            .build();
        
        boolean success = emailService.sendEmail(request);
        System.out.println("Security alert sent: " + (success ? "SUCCESS" : "FAILED"));
    }
    
    /**
     * Demonstrate monthly report email
     */
    private static void demonstrateMonthlyReport(MGTSEmailService emailService) {
        System.out.println("\n--- Monthly Report Example ---");
        
        MGTSEmailRequest request = new MGTSEmailRequest.Builder()
            .to("management@external-company.com")
            .type(MGTSEmailType.MONTHLY_REPORT)
            .subject("MGTS Monthly Performance Report - " + 
                    java.time.YearMonth.now().toString())
            .addParameter("uptime", "99.9%")
            .addParameter("transactions", "15,420")
            .addParameter("avgResponseTime", "2.3s")
            .addParameter("securityIncidents", "0")
            .addAttachment("reports/monthly-report.pdf")
            .build();
        
        boolean success = emailService.sendEmail(request);
        System.out.println("Monthly report sent: " + (success ? "SUCCESS" : "FAILED"));
    }
    
    /**
     * Demonstrate batch notifications
     */
    private static void demonstrateBatchNotifications(MGTSEmailService emailService) {
        System.out.println("\n--- Batch Notifications Example ---");
        
        String[] customers = {
            "customer1@company-a.com",
            "customer2@company-b.com",
            "customer3@company-c.com"
        };
        
        for (String customer : customers) {
            MGTSEmailRequest request = new MGTSEmailRequest.Builder()
                .to(customer)
                .type(MGTSEmailType.SERVICE_UPDATE)
                .subject("MGTS Service Enhancement Notification")
                .addParameter("enhancement", "S/MIME Digital Signatures")
                .addParameter("benefit", "Enhanced Email Security")
                .addParameter("effectiveDate", "Immediate")
                .build();
            
            boolean success = emailService.sendEmail(request);
            System.out.println("Batch notification to " + customer + ": " + 
                             (success ? "SUCCESS" : "FAILED"));
        }
    }
    
    /**
     * Demonstrate error handling scenarios
     */
    private static void demonstrateErrorHandling(MGTSEmailService emailService) {
        System.out.println("\n--- Error Handling Example ---");
        
        // Test with invalid email address
        MGTSEmailRequest invalidRequest = new MGTSEmailRequest.Builder()
            .to("invalid-email-address")
            .type(MGTSEmailType.SYSTEM_NOTIFICATION)
            .subject("Test Invalid Email")
            .build();
        
        boolean success = emailService.sendEmail(invalidRequest);
        System.out.println("Invalid email test: " + (success ? "UNEXPECTED SUCCESS" : "EXPECTED FAILURE"));
        
        // Test retry mechanism
        emailService.testRetryMechanism();
    }
}

/**
 * MGTS Email Service - Production-ready service for S/MIME email operations
 */
class MGTSEmailService {
    
    private static final java.util.logging.Logger logger = 
        java.util.logging.Logger.getLogger(MGTSEmailService.class.getName());
    
    private MGTSEmailConfigLoader configLoader;
    private SMimeEmailSigner emailSigner;
    private MGTSEmailTemplateEngine templateEngine;
    
    /**
     * Constructor with environment specification
     */
    public MGTSEmailService(String environment) throws Exception {
        this.configLoader = new MGTSEmailConfigLoader(environment);
        this.emailSigner = configLoader.createEmailSigner();
        this.templateEngine = new MGTSEmailTemplateEngine();
        
        logger.info("MGTS Email Service initialized for environment: " + environment);
        
        if (configLoader.isDebugEnabled()) {
            configLoader.printConfigSummary();
        }
    }
    
    /**
     * Send email with automatic template processing and retry logic
     */
    public boolean sendEmail(MGTSEmailRequest request) {
        try {
            // Generate email content from template
            String emailBody = templateEngine.generateEmailBody(request);
            
            // Determine from email address based on type
            String fromEmail = getFromEmailForType(request.getType());
            
            // Send email with retry logic
            return sendEmailWithRetry(fromEmail, request.getTo(), request.getSubject(), 
                                    emailBody, request.getAttachments());
            
        } catch (Exception e) {
            logger.severe("Failed to send email: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Send email with retry mechanism
     */
    private boolean sendEmailWithRetry(String from, String to, String subject, 
                                     String body, String... attachments) {
        int attempts = configLoader.getRetryAttempts();
        long delayMs = configLoader.getRetryDelayMs();
        
        for (int attempt = 1; attempt <= attempts; attempt++) {
            try {
                boolean success = emailSigner.sendSignedEmail(from, to, subject, body, attachments);
                if (success) {
                    if (attempt > 1) {
                        logger.info("Email sent successfully on attempt " + attempt);
                    }
                    return true;
                }
            } catch (Exception e) {
                logger.warning("Email sending attempt " + attempt + " failed: " + e.getMessage());
            }
            
            // Wait before retry (except on last attempt)
            if (attempt < attempts) {
                try {
                    Thread.sleep(delayMs);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
        
        logger.severe("Failed to send email after " + attempts + " attempts");
        return false;
    }
    
    /**
     * Get appropriate from email address based on email type
     */
    private String getFromEmailForType(MGTSEmailType type) {
        switch (type) {
            case SECURITY_ALERT:
                return configLoader.getAlertsFromEmail();
            case MONTHLY_REPORT:
                return configLoader.getReportsFromEmail();
            default:
                return configLoader.getDefaultFromEmail();
        }
    }
    
    /**
     * Test retry mechanism
     */
    public void testRetryMechanism() {
        System.out.println("Testing retry mechanism...");
        // This would simulate network failures for testing
        System.out.println("Retry mechanism test completed");
    }
}

/**
 * Email request builder for structured email composition
 */
class MGTSEmailRequest {
    private String to;
    private MGTSEmailType type;
    private MGTSEmailPriority priority;
    private String subject;
    private java.util.Map<String, String> parameters;
    private java.util.List<String> attachments;
    
    private MGTSEmailRequest(Builder builder) {
        this.to = builder.to;
        this.type = builder.type;
        this.priority = builder.priority;
        this.subject = builder.subject;
        this.parameters = builder.parameters;
        this.attachments = builder.attachments;
    }
    
    // Getters
    public String getTo() { return to; }
    public MGTSEmailType getType() { return type; }
    public MGTSEmailPriority getPriority() { return priority; }
    public String getSubject() { return subject; }
    public java.util.Map<String, String> getParameters() { return parameters; }
    public String[] getAttachments() { 
        return attachments.toArray(new String[0]); 
    }
    
    /**
     * Builder pattern for email request construction
     */
    public static class Builder {
        private String to;
        private MGTSEmailType type = MGTSEmailType.SYSTEM_NOTIFICATION;
        private MGTSEmailPriority priority = MGTSEmailPriority.NORMAL;
        private String subject;
        private java.util.Map<String, String> parameters = new java.util.HashMap<>();
        private java.util.List<String> attachments = new java.util.ArrayList<>();
        
        public Builder to(String to) {
            this.to = to;
            return this;
        }
        
        public Builder type(MGTSEmailType type) {
            this.type = type;
            return this;
        }
        
        public Builder priority(MGTSEmailPriority priority) {
            this.priority = priority;
            return this;
        }
        
        public Builder subject(String subject) {
            this.subject = subject;
            return this;
        }
        
        public Builder addParameter(String key, String value) {
            this.parameters.put(key, value);
            return this;
        }
        
        public Builder addAttachment(String filePath) {
            this.attachments.add(filePath);
            return this;
        }
        
        public MGTSEmailRequest build() {
            if (to == null || subject == null) {
                throw new IllegalStateException("To address and subject are required");
            }
            return new MGTSEmailRequest(this);
        }
    }
}

/**
 * Email types supported by MGTS
 */
enum MGTSEmailType {
    SYSTEM_NOTIFICATION,
    SECURITY_ALERT,
    MONTHLY_REPORT,
    SERVICE_UPDATE,
    MAINTENANCE_NOTICE
}

/**
 * Email priorities
 */
enum MGTSEmailPriority {
    LOW,
    NORMAL,
    HIGH,
    CRITICAL
}

/**
 * Simple template engine for email content generation
 */
class MGTSEmailTemplateEngine {
    
    /**
     * Generate email body based on template and parameters
     */
    public String generateEmailBody(MGTSEmailRequest request) {
        StringBuilder body = new StringBuilder();
        
        // Header
        body.append("MGTS System Communication\n");
        body.append("========================\n\n");
        
        // Type-specific content
        switch (request.getType()) {
            case SYSTEM_NOTIFICATION:
                generateSystemNotificationContent(body, request);
                break;
            case SECURITY_ALERT:
                generateSecurityAlertContent(body, request);
                break;
            case MONTHLY_REPORT:
                generateMonthlyReportContent(body, request);
                break;
            case SERVICE_UPDATE:
                generateServiceUpdateContent(body, request);
                break;
            default:
                generateDefaultContent(body, request);
        }
        
        // Footer with S/MIME notice
        body.append("\n\nSecurity Notice:\n");
        body.append("================\n");
        body.append("This email has been digitally signed using S/MIME technology to ensure:\n");
        body.append("• Sender authenticity verification\n");
        body.append("• Message integrity protection\n");
        body.append("• Non-repudiation assurance\n\n");
        body.append("Please verify the digital signature in your email client to confirm\n");
        body.append("the authenticity of this communication.\n\n");
        body.append("Best regards,\n");
        body.append("MGTS Automated System\n");
        body.append("Your Company Name\n");
        
        return body.toString();
    }
    
    private void generateSystemNotificationContent(StringBuilder body, MGTSEmailRequest request) {
        body.append("System Notification\n");
        body.append("-------------------\n\n");
        
        for (java.util.Map.Entry<String, String> param : request.getParameters().entrySet()) {
            body.append(capitalizeFirst(param.getKey())).append(": ").append(param.getValue()).append("\n");
        }
    }
    
    private void generateSecurityAlertContent(StringBuilder body, MGTSEmailRequest request) {
        body.append("SECURITY ALERT\n");
        body.append("==============\n\n");
        body.append("Priority: ").append(request.getPriority()).append("\n");
        body.append("Timestamp: ").append(new java.util.Date()).append("\n\n");
        
        body.append("Alert Details:\n");
        for (java.util.Map.Entry<String, String> param : request.getParameters().entrySet()) {
            body.append("• ").append(capitalizeFirst(param.getKey())).append(": ").append(param.getValue()).append("\n");
        }
        
        body.append("\nImmediate action may be required. Please review and respond accordingly.\n");
    }
    
    private void generateMonthlyReportContent(StringBuilder body, MGTSEmailRequest request) {
        body.append("Monthly Performance Report\n");
        body.append("=========================\n\n");
        body.append("Report Period: ").append(java.time.YearMonth.now()).append("\n\n");
        
        body.append("Key Metrics:\n");
        for (java.util.Map.Entry<String, String> param : request.getParameters().entrySet()) {
            body.append("• ").append(capitalizeFirst(param.getKey())).append(": ").append(param.getValue()).append("\n");
        }
        
        body.append("\nDetailed reports are available in the attached documents.\n");
    }
    
    private void generateServiceUpdateContent(StringBuilder body, MGTSEmailRequest request) {
        body.append("Service Update Notification\n");
        body.append("==========================\n\n");
        
        for (java.util.Map.Entry<String, String> param : request.getParameters().entrySet()) {
            body.append(capitalizeFirst(param.getKey())).append(": ").append(param.getValue()).append("\n");
        }
        
        body.append("\nThis update is designed to improve security and service quality.\n");
    }
    
    private void generateDefaultContent(StringBuilder body, MGTSEmailRequest request) {
        body.append("General Information\n");
        body.append("==================\n\n");
        
        if (!request.getParameters().isEmpty()) {
            for (java.util.Map.Entry<String, String> param : request.getParameters().entrySet()) {
                body.append(capitalizeFirst(param.getKey())).append(": ").append(param.getValue()).append("\n");
            }
        }
    }
    
    private String capitalizeFirst(String str) {
        if (str == null || str.isEmpty()) {
            return str;
        }
        return str.substring(0, 1).toUpperCase() + str.substring(1).replace("_", " ");
    }
}
