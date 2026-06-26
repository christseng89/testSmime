import java.io.*;
import java.util.Properties;
import java.util.logging.Logger;
import java.util.logging.Level;

/**
 * Configuration loader for MGTS S/MIME Email System
 * 
 * This utility class loads configuration from properties files
 * and provides environment-specific settings for the S/MIME
 * email signing functionality.
 * 
 * Features:
 * - Environment-based configuration loading (dev, test, prod)
 * - Environment variable substitution
 * - Default value handling
 * - Configuration validation
 */
public class MGTSEmailConfigLoader {
    
    private static final Logger logger = Logger.getLogger(MGTSEmailConfigLoader.class.getName());
    
    private static final String DEFAULT_CONFIG_FILE = "mgts-email-config.properties";
    private static final String ENVIRONMENT_PROPERTY = "mgts.environment";
    private static final String DEFAULT_ENVIRONMENT = "development";
    
    private Properties config;
    private String environment;
    
    /**
     * Default constructor that loads configuration for the current environment
     */
    public MGTSEmailConfigLoader() {
        this(System.getProperty(ENVIRONMENT_PROPERTY, DEFAULT_ENVIRONMENT));
    }
    
    /**
     * Constructor for specific environment
     * 
     * @param environment Target environment (development, test, production)
     */
    public MGTSEmailConfigLoader(String environment) {
        this.environment = environment;
        this.config = new Properties();
        loadConfiguration();
    }
    
    /**
     * Load configuration from properties file
     */
    private void loadConfiguration() {
        try {
            // Load configuration file
            InputStream configStream = getClass().getClassLoader()
                .getResourceAsStream(DEFAULT_CONFIG_FILE);
            
            if (configStream == null) {
                // Try loading from file system
                configStream = new FileInputStream(DEFAULT_CONFIG_FILE);
            }
            
            if (configStream != null) {
                config.load(configStream);
                configStream.close();
                logger.info("Successfully loaded configuration for environment: " + environment);
            } else {
                logger.warning("Configuration file not found, using default settings");
                loadDefaultConfiguration();
            }
            
            // Substitute environment variables
            substituteEnvironmentVariables();
            
            // Validate configuration
            validateConfiguration();
            
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Failed to load configuration", e);
            loadDefaultConfiguration();
        }
    }
    
    /**
     * Load default configuration when config file is not available
     */
    private void loadDefaultConfiguration() {
        logger.info("Loading default configuration for environment: " + environment);
        
        // SMTP settings
        config.setProperty("smtp.host", "localhost");
        config.setProperty("smtp.port", "1025");
        config.setProperty("smtp.username", "mgts@localhost");
        config.setProperty("smtp.password", "");
        config.setProperty("smtp.tls.enabled", "false");
        config.setProperty("smtp.auth.enabled", "false");
        
        // Certificate settings
        config.setProperty("certificate.path", "certificates/default-cert.pem");
        config.setProperty("private.key.path", "certificates/default-key.pem");
        
        // Email settings
        config.setProperty("email.from.default", "mgts@localhost");
        config.setProperty("email.retry.attempts", "3");
        config.setProperty("email.retry.delay.ms", "5000");
        
        // S/MIME settings
        config.setProperty("smime.signature.algorithm", "SHA256withRSA");
        config.setProperty("smime.digest.algorithm", "SHA-256");
    }
    
    /**
     * Substitute environment variables in configuration values
     */
    private void substituteEnvironmentVariables() {
        for (String key : config.stringPropertyNames()) {
            String value = config.getProperty(key);
            if (value != null && value.contains("${")) {
                String substitutedValue = substituteVariables(value);
                config.setProperty(key, substitutedValue);
            }
        }
    }
    
    /**
     * Substitute variables in a string value
     */
    private String substituteVariables(String value) {
        String result = value;
        
        // Pattern: ${VARIABLE_NAME}
        while (result.contains("${")) {
            int start = result.indexOf("${");
            int end = result.indexOf("}", start);
            
            if (end > start) {
                String varName = result.substring(start + 2, end);
                String envValue = System.getenv(varName);
                
                if (envValue != null) {
                    result = result.substring(0, start) + envValue + result.substring(end + 1);
                } else {
                    logger.warning("Environment variable not found: " + varName);
                    // Keep the placeholder if environment variable is not found
                    break;
                }
            } else {
                break;
            }
        }
        
        return result;
    }
    
    /**
     * Validate essential configuration properties
     */
    private void validateConfiguration() throws IllegalStateException {
        // Validate required SMTP settings
        validateRequired("smtp.host", "SMTP host must be configured");
        validateRequired("smtp.port", "SMTP port must be configured");
        
        // Validate certificate paths
        validateRequired("certificate.path", "Certificate path must be configured");
        validateRequired("private.key.path", "Private key path must be configured");
        
        // Validate email settings
        validateRequired("email.from.default", "Default from email must be configured");
        
        logger.info("Configuration validation completed successfully");
    }
    
    /**
     * Validate that a required property exists
     */
    private void validateRequired(String key, String message) {
        String value = config.getProperty(key);
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalStateException(message + " (key: " + key + ")");
        }
    }
    
    /**
     * Get SMTP properties for JavaMail
     * 
     * @return Properties configured for SMTP
     */
    public Properties getSMTPProperties() {
        Properties smtpProps = new Properties();
        
        // Basic SMTP settings
        smtpProps.setProperty("mail.smtp.host", getProperty("smtp.host"));
        smtpProps.setProperty("mail.smtp.port", getProperty("smtp.port"));
        smtpProps.setProperty("mail.smtp.user", getProperty("smtp.username"));
        smtpProps.setProperty("mail.smtp.password", getProperty("smtp.password"));
        
        // Authentication
        boolean authEnabled = getBooleanProperty("smtp.auth.enabled", false);
        smtpProps.setProperty("mail.smtp.auth", String.valueOf(authEnabled));
        
        // TLS settings
        boolean tlsEnabled = getBooleanProperty("smtp.tls.enabled", false);
        if (tlsEnabled) {
            smtpProps.setProperty("mail.smtp.starttls.enable", "true");
            smtpProps.setProperty("mail.smtp.ssl.trust", getProperty("smtp.host"));
        }
        
        // Timeout settings
        String connectionTimeout = getProperty("smtp.connection.timeout", "30000");
        String readTimeout = getProperty("smtp.read.timeout", "60000");
        smtpProps.setProperty("mail.smtp.connectiontimeout", connectionTimeout);
        smtpProps.setProperty("mail.smtp.timeout", readTimeout);
        
        return smtpProps;
    }
    
    /**
     * Get certificate file path
     */
    public String getCertificatePath() {
        return getProperty("certificate.path");
    }
    
    /**
     * Get private key file path
     */
    public String getPrivateKeyPath() {
        return getProperty("private.key.path");
    }
    
    /**
     * Get certificate password
     */
    public String getCertificatePassword() {
        return getProperty("certificate.password", "");
    }
    
    /**
     * Get default from email address
     */
    public String getDefaultFromEmail() {
        return getProperty("email.from.default");
    }
    
    /**
     * Get alerts from email address
     */
    public String getAlertsFromEmail() {
        return getProperty("email.from.alerts", getDefaultFromEmail());
    }
    
    /**
     * Get reports from email address
     */
    public String getReportsFromEmail() {
        return getProperty("email.from.reports", getDefaultFromEmail());
    }
    
    /**
     * Get S/MIME signature algorithm
     */
    public String getSignatureAlgorithm() {
        return getProperty("smime.signature.algorithm", "SHA256withRSA");
    }
    
    /**
     * Get S/MIME digest algorithm
     */
    public String getDigestAlgorithm() {
        return getProperty("smime.digest.algorithm", "SHA-256");
    }
    
    /**
     * Get retry attempts for failed emails
     */
    public int getRetryAttempts() {
        return getIntProperty("email.retry.attempts", 3);
    }
    
    /**
     * Get retry delay in milliseconds
     */
    public long getRetryDelayMs() {
        return getLongProperty("email.retry.delay.ms", 5000L);
    }
    
    /**
     * Check if certificate includes should be included in signature
     */
    public boolean shouldIncludeCertificates() {
        return getBooleanProperty("smime.include.certificates", true);
    }
    
    /**
     * Get property value with optional default
     */
    public String getProperty(String key, String defaultValue) {
        return config.getProperty(key, defaultValue);
    }
    
    /**
     * Get property value (required)
     */
    public String getProperty(String key) {
        String value = config.getProperty(key);
        if (value == null) {
            throw new IllegalStateException("Required property not found: " + key);
        }
        return value;
    }
    
    /**
     * Get boolean property value
     */
    public boolean getBooleanProperty(String key, boolean defaultValue) {
        String value = config.getProperty(key);
        if (value == null) {
            return defaultValue;
        }
        return "true".equalsIgnoreCase(value.trim());
    }
    
    /**
     * Get integer property value
     */
    public int getIntProperty(String key, int defaultValue) {
        String value = config.getProperty(key);
        if (value == null) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            logger.warning("Invalid integer value for property " + key + ": " + value);
            return defaultValue;
        }
    }
    
    /**
     * Get long property value
     */
    public long getLongProperty(String key, long defaultValue) {
        String value = config.getProperty(key);
        if (value == null) {
            return defaultValue;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            logger.warning("Invalid long value for property " + key + ": " + value);
            return defaultValue;
        }
    }
    
    /**
     * Get current environment
     */
    public String getEnvironment() {
        return environment;
    }
    
    /**
     * Check if debug mode is enabled
     */
    public boolean isDebugEnabled() {
        return getBooleanProperty("dev.debug.enabled", false);
    }
    
    /**
     * Get all configuration properties (for debugging)
     */
    public Properties getAllProperties() {
        return (Properties) config.clone();
    }
    
    /**
     * Print configuration summary (for debugging, excludes sensitive data)
     */
    public void printConfigSummary() {
        System.out.println("=== MGTS Email Configuration Summary ===");
        System.out.println("Environment: " + environment);
        System.out.println("SMTP Host: " + getProperty("smtp.host"));
        System.out.println("SMTP Port: " + getProperty("smtp.port"));
        System.out.println("TLS Enabled: " + getBooleanProperty("smtp.tls.enabled", false));
        System.out.println("Certificate Path: " + getCertificatePath());
        System.out.println("Default From Email: " + getDefaultFromEmail());
        System.out.println("Signature Algorithm: " + getSignatureAlgorithm());
        System.out.println("Retry Attempts: " + getRetryAttempts());
        System.out.println("Debug Enabled: " + isDebugEnabled());
        System.out.println("========================================");
    }
    
    /**
     * Create SMimeEmailSigner instance with loaded configuration
     */
    public SMimeEmailSigner createEmailSigner() throws Exception {
        return new SMimeEmailSigner(
            getCertificatePath(),
            getPrivateKeyPath(),
            getSMTPProperties()
        );
    }
    
    /**
     * Factory method to create configuration loader for specific environment
     */
    public static MGTSEmailConfigLoader forEnvironment(String environment) {
        return new MGTSEmailConfigLoader(environment);
    }
    
    /**
     * Factory method to create configuration loader for production
     */
    public static MGTSEmailConfigLoader forProduction() {
        return new MGTSEmailConfigLoader("production");
    }
    
    /**
     * Factory method to create configuration loader for test
     */
    public static MGTSEmailConfigLoader forTest() {
        return new MGTSEmailConfigLoader("test");
    }
    
    /**
     * Factory method to create configuration loader for development
     */
    public static MGTSEmailConfigLoader forDevelopment() {
        return new MGTSEmailConfigLoader("development");
    }
}
