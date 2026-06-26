# MGTS S/MIME Email Signing Implementation

## Overview

This project implements S/MIME (Secure/Multipurpose Internet Mail Extensions) email signing functionality for the MGTS system as part of cyber risk countermeasures. The implementation focuses on **Email Signing (Digital Signature)** to provide sender authenticity verification and content integrity protection for emails sent to external customer email boxes.

## Key Features

### 🔐 Security Benefits
- **Sender Authenticity**: Verifies that emails genuinely originate from MGTS
- **Content Integrity**: Ensures email content hasn't been tampered with during transmission
- **Non-repudiation**: Provides legal proof that MGTS sent the email
- **Customer Trust**: Demonstrates commitment to cybersecurity best practices

### ✉️ Email Functionality
- Digital signature application for all outbound emails
- Support for email attachments with signature
- Batch email processing capabilities
- Multiple environment configurations (dev, test, production)

## Architecture

```
┌─────────────────┐    ┌──────────────────┐    ┌─────────────────┐
│   MGTS AP       │    │   S/MIME Email   │    │   External      │
│   Server        │───▶│   Signer         │───▶│   Customer      │
│                 │    │                  │    │   Email Box     │
└─────────────────┘    └──────────────────┘    └─────────────────┘
                              │
                              ▼
                       ┌─────────────────┐
                       │   Digital       │
                       │   Certificate   │
                       │   & Private Key │
                       └─────────────────┘
```

## Prerequisites

### System Requirements
- Java 11 or higher
- Maven 3.6+
- Access to SMTP server for email sending
- Digital certificate for signing (X.509 format)
- Private key corresponding to the certificate

### Dependencies
The project uses the following key libraries:
- **BouncyCastle**: For S/MIME cryptographic operations
- **JavaMail API**: For email composition and sending
- **JUnit 5**: For testing

## Installation & Setup

### 1. Clone and Build
```bash
git clone <repository-url>
cd mgts-smime-email
mvn clean install
```

### 2. Certificate Setup

#### Option A: Generate Self-Signed Certificate (for testing)
```bash
# Generate private key
openssl genrsa -out mgts-private-key.pem 2048

# Generate certificate signing request
openssl req -new -key mgts-private-key.pem -out mgts.csr

# Generate self-signed certificate
openssl x509 -req -days 365 -in mgts.csr -signkey mgts-private-key.pem -out mgts-signing-cert.pem
```

#### Option B: Use Corporate Certificate
Place your corporate certificate and private key files in the `certificates/` directory:
- `certificates/mgts-signing-cert.pem`
- `certificates/mgts-private-key.pem`

### 3. SMTP Configuration
Update the email server settings in your configuration:

```java
Properties smtpProps = SMimeEmailSigner.createSMTPProperties(
    "smtp.your-company.com",  // SMTP server
    587,                      // Port
    "mgts@your-company.com",  // Username
    "your-password",          // Password
    true                      // Enable TLS
);
```

## Usage Examples

### Basic Email Signing
```java
// Initialize the signer
SMimeEmailSigner emailSigner = new SMimeEmailSigner(
    "certificates/mgts-signing-cert.pem",
    "certificates/mgts-private-key.pem",
    smtpProperties
);

// Send signed email
boolean success = emailSigner.sendSignedEmail(
    "mgts@your-company.com",           // From
    "customer@external-company.com",   // To
    "System Notification",             // Subject
    "Your system is running normally"  // Body
);
```

### Email with Attachments
```java
emailSigner.sendSignedEmail(
    "mgts-alerts@your-company.com",
    "security@external-company.com",
    "Security Report",
    "Please find the security report attached.",
    "reports/security-report.pdf",
    "reports/incident-log.txt"
);
```

### Batch Email Processing
```java
String[] recipients = {
    "customer1@external-company.com",
    "customer2@external-company.com",
    "customer3@external-company.com"
};

for (String recipient : recipients) {
    emailSigner.sendSignedEmail(
        "mgts-reports@your-company.com",
        recipient,
        "Monthly Report",
        buildMonthlyReportContent(recipient)
    );
}
```

## Configuration

### Environment-Specific Configuration

#### Production Environment
```java
Properties prodConfig = SMimeEmailSigner.createSMTPProperties(
    "smtp.production.company.com",
    587,
    "mgts-prod@company.com",
    System.getenv("MGTS_EMAIL_PASSWORD"),
    true
);
```

#### Test Environment
```java
Properties testConfig = SMimeEmailSigner.createSMTPProperties(
    "smtp.test.company.com",
    587,
    "mgts-test@company.com",
    "test-password",
    true
);
```

#### Development Environment
```java
Properties devConfig = SMimeEmailSigner.createSMTPProperties(
    "localhost",
    1025,
    "dev@localhost",
    "",
    false
);
```

### Environment Variables
Set the following environment variables for production:
```bash
export MGTS_EMAIL_PASSWORD=your-production-email-password
export MGTS_CERT_PATH=/path/to/production/certificate.pem
export MGTS_KEY_PATH=/path/to/production/private-key.pem
```

## Security Considerations

### Certificate Management
1. **Secure Storage**: Store certificates and private keys in secure locations
2. **Access Control**: Limit access to certificate files using file permissions
3. **Certificate Rotation**: Regularly renew certificates before expiration
4. **Backup**: Maintain secure backups of certificates and keys

### Email Security
1. **TLS Encryption**: Always use TLS for SMTP connections
2. **Authentication**: Use strong authentication for SMTP servers
3. **Content Validation**: Validate email content before signing
4. **Logging**: Log all signing operations for audit purposes

### Production Deployment
1. **Environment Isolation**: Use separate certificates for different environments
2. **Secret Management**: Use secure secret management systems for passwords
3. **Monitoring**: Monitor certificate expiration dates
4. **Error Handling**: Implement proper error handling and alerting

## Testing

### Run Unit Tests
```bash
mvn test
```

### Run Integration Tests
```bash
mvn verify
```

### Test S/MIME Signature Verification
The project includes utilities to verify S/MIME signatures:

```java
SMimeEmailSigner signer = new SMimeEmailSigner(certPath, keyPath, smtpProps);
MimeMessage signedMessage = /* your signed message */;
boolean isValid = signer.verifySignature(signedMessage);
```

## Deployment

### Build for Production
```bash
mvn clean package -Pprod
```

### Create Distribution
```bash
mvn clean package assembly:single
```

This creates:
- `target/mgts-smime-email-1.0.0.jar` - Main JAR file
- `target/mgts-smime-email-1.0.0-jar-with-dependencies.jar` - Standalone JAR
- `target/lib/` - Dependency libraries

### Integration with MGTS System

#### 1. Add as Dependency
Add the JAR file to your MGTS application classpath:
```xml
<dependency>
    <groupId>com.company.mgts</groupId>
    <artifactId>mgts-smime-email</artifactId>
    <version>1.0.0</version>
</dependency>
```

#### 2. Initialize in Application
```java
@Component
public class MGTSEmailService {
    
    private SMimeEmailSigner emailSigner;
    
    @PostConstruct
    public void init() throws Exception {
        Properties smtpProps = loadSMTPConfiguration();
        this.emailSigner = new SMimeEmailSigner(
            certificatePath,
            privateKeyPath,
            smtpProps
        );
    }
    
    public void sendNotification(String recipient, String subject, String body) {
        emailSigner.sendSignedEmail(
            "mgts@company.com",
            recipient,
            subject,
            body
        );
    }
}
```

## Troubleshooting

### Common Issues

#### Certificate Loading Issues
```
Error: Unable to load certificate
Solution: Verify certificate file path and format (PEM expected)
```

#### SMTP Connection Issues
```
Error: Connection refused to SMTP server
Solution: Check SMTP server settings, firewall, and authentication
```

#### Signature Verification Failures
```
Error: S/MIME signature verification failed
Solution: Ensure certificate is valid and properly configured
```

### Debug Mode
Enable debug logging by adding:
```java
System.setProperty("javax.net.debug", "ssl");
System.setProperty("mail.debug", "true");
```

### Log Analysis
Check application logs for:
- Certificate loading status
- SMTP connection attempts
- Signature generation success/failure
- Email delivery confirmation

## Compliance & Standards

### S/MIME Standards
- **RFC 8551**: Secure/Multipurpose Internet Mail Extensions (S/MIME) Version 4.0
- **RFC 5652**: Cryptographic Message Syntax (CMS)
- **RFC 3370**: Cryptographic Message Syntax (CMS) Algorithms

### Security Standards
- Uses industry-standard cryptographic algorithms
- Complies with enterprise security requirements
- Supports certificate-based authentication

## Support & Maintenance

### Regular Maintenance Tasks
1. **Certificate Renewal**: Monitor and renew certificates before expiration
2. **Security Updates**: Keep BouncyCastle and JavaMail dependencies updated
3. **Log Monitoring**: Review logs for signature failures or security issues
4. **Performance Monitoring**: Monitor email sending performance and throughput

### Contact Information
For technical support and questions:
- Technical Team: mgts-support@company.com
- Security Team: security@company.com
- Documentation: confluence.company.com/mgts-smime

## Version History

### v1.0.0 (Current)
- Initial S/MIME email signing implementation
- Support for digital signatures on outbound emails
- Multi-environment configuration support
- Comprehensive testing framework
- Integration-ready design for MGTS system

---

**Note**: This implementation focuses on email signing only, as per the requirement that email encryption is not feasible for external customer email boxes. The digital signature functionality provides sufficient security benefits for sender authentication and content integrity verification.
