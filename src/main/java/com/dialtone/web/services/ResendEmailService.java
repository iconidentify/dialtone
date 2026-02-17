/*
 * Copyright (c) 2025 iconidentify. MIT License. See LICENSE file.
 */

package com.dialtone.web.services;

import com.dialtone.utils.LoggerUtil;
import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Properties;

/**
 * Service for sending transactional emails via the Resend API.
 * 
 * Used for magic link authentication emails.
 * Resend API docs: https://resend.com/docs/api-reference/emails/send-email
 */
public class ResendEmailService {
    
    private static final String RESEND_API_URL = "https://api.resend.com/emails";
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);
    
    private final String apiKey;
    private final String fromAddress;
    private final String fromName;
    private final boolean enabled;
    private final HttpClient httpClient;
    private final Gson gson;
    
    public ResendEmailService(Properties config) {
        this.enabled = Boolean.parseBoolean(config.getProperty("email.enabled", "false"));
        this.apiKey = config.getProperty("email.resend.api.key", "");
        this.fromAddress = config.getProperty("email.from.address", "noreply@dialtone.live");
        this.fromName = config.getProperty("email.from.name", "Dialtone");
        
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(REQUEST_TIMEOUT)
            .build();
        this.gson = new Gson();
        
        if (enabled && (apiKey == null || apiKey.isBlank())) {
            LoggerUtil.warn("Email is enabled but Resend API key is not configured");
        } else if (enabled) {
            LoggerUtil.info("ResendEmailService initialized (from: " + fromAddress + ")");
        } else {
            LoggerUtil.info("ResendEmailService disabled");
        }
    }
    
    /**
     * Check if email service is enabled and properly configured.
     */
    public boolean isEnabled() {
        return enabled && apiKey != null && !apiKey.isBlank();
    }
    
    /**
     * Send a magic link email to the user.
     * 
     * @param toEmail Recipient email address
     * @param magicLinkUrl Full URL with token for authentication
     * @param expiryMinutes How long until the link expires
     * @throws EmailSendException if sending fails
     */
    public void sendMagicLinkEmail(String toEmail, String magicLinkUrl, int expiryMinutes) throws EmailSendException {
        if (!isEnabled()) {
            throw new EmailSendException("Email service is not enabled or configured");
        }
        
        String subject = "Sign in to Dialtone";
        String htmlContent = buildMagicLinkHtml(magicLinkUrl, expiryMinutes);
        String textContent = buildMagicLinkText(magicLinkUrl, expiryMinutes);
        
        sendEmail(toEmail, subject, htmlContent, textContent);
        
        LoggerUtil.info("Magic link email sent to: " + maskEmail(toEmail));
    }
    
    /**
     * Send an email via Resend API.
     */
    private void sendEmail(String to, String subject, String html, String text) throws EmailSendException {
        try {
            EmailRequest request = new EmailRequest(
                fromName + " <" + fromAddress + ">",
                List.of(to),
                subject,
                html,
                text
            );
            
            String jsonBody = gson.toJson(request);
            
            HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(URI.create(RESEND_API_URL))
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .timeout(REQUEST_TIMEOUT)
                .build();
            
            HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                EmailResponse emailResponse = gson.fromJson(response.body(), EmailResponse.class);
                LoggerUtil.debug("Email sent successfully, ID: " + emailResponse.id);
            } else {
                LoggerUtil.error("Resend API error: " + response.statusCode() + " - " + response.body());
                throw new EmailSendException("Failed to send email: " + response.statusCode());
            }
            
        } catch (EmailSendException e) {
            throw e;
        } catch (Exception e) {
            LoggerUtil.error("Failed to send email via Resend: " + e.getMessage());
            throw new EmailSendException("Failed to send email: " + e.getMessage(), e);
        }
    }
    
    /**
     * Build HTML content for magic link email.
     * Uses Dialtone brand colors: Navy (#001e37), Orange (#fc9d2c), Cream (#fffae3)
     */
    private String buildMagicLinkHtml(String magicLinkUrl, int expiryMinutes) {
        return """
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="utf-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>Sign in to Dialtone</title>
            </head>
            <body style="font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif; 
                         background-color: #001e37; margin: 0; padding: 40px 20px;">
                <div style="max-width: 500px; margin: 0 auto; background: #fffae3; border-radius: 12px; 
                            box-shadow: 0 8px 32px rgba(0,0,0,0.3); overflow: hidden;">
                    
                    <!-- Header with Logo -->
                    <div style="background: #001e37; padding: 32px; text-align: center;">
                        <!-- Inline SVG Modem Logo -->
                        <div style="margin-bottom: 16px;">
                            <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 512 512" width="80" height="80">
                                <path fill="#fc9d2c" d="M256 40L380 180H132L256 40z"/>
                                <ellipse fill="#fc9d2c" cx="256" cy="340" rx="180" ry="40"/>
                                <path fill="#001e37" d="M96 180c-20 0-36 16-36 36v120c0 20 16 36 36 36h320c20 0 36-16 36-36V216c0-20-16-36-36-36H96z"/>
                                <rect fill="#fffae3" x="76" y="196" width="360" height="104" rx="16"/>
                                <rect fill="#001e37" x="92" y="212" width="328" height="72" rx="12"/>
                                <circle fill="#fffae3" cx="140" cy="248" r="24"/>
                                <circle fill="#001e37" cx="140" cy="248" r="14"/>
                                <circle fill="#fffae3" cx="320" cy="248" r="10"/>
                                <circle fill="#fffae3" cx="360" cy="248" r="10"/>
                                <circle fill="#fffae3" cx="400" cy="248" r="10"/>
                                <rect fill="#001e37" x="120" y="352" width="40" height="20" rx="4"/>
                                <rect fill="#001e37" x="352" y="352" width="40" height="20" rx="4"/>
                            </svg>
                        </div>
                        <h1 style="color: #fffae3; margin: 0; font-size: 32px; font-weight: 700; letter-spacing: -0.5px;">Dialtone</h1>
                        <p style="color: #fc9d2c; margin: 8px 0 0 0; font-size: 13px; text-transform: uppercase; letter-spacing: 2px;">
                            Bringing back the dial-up days
                        </p>
                    </div>
                    
                    <!-- Content -->
                    <div style="padding: 40px 32px;">
                        <h2 style="color: #001e37; margin: 0 0 16px 0; font-size: 22px; font-weight: 600;">
                            Sign in to your account
                        </h2>
                        
                        <p style="color: #444; font-size: 15px; line-height: 1.7; margin: 0 0 28px 0;">
                            Click the button below to sign in to Dialtone. This link will expire in <strong>%d minutes</strong>.
                        </p>
                        
                        <!-- CTA Button -->
                        <div style="text-align: center; margin: 32px 0;">
                            <a href="%s" 
                               style="display: inline-block; background: #fc9d2c; color: #001e37; 
                                      text-decoration: none; padding: 16px 48px; border-radius: 8px;
                                      font-size: 16px; font-weight: 700; letter-spacing: 0.5px;
                                      box-shadow: 0 4px 12px rgba(252,157,44,0.4);">
                                Sign In to Dialtone
                            </a>
                        </div>
                        
                        <div style="background: #001e37; border-radius: 8px; padding: 16px; margin-top: 28px;">
                            <p style="color: #fffae3; font-size: 12px; margin: 0 0 8px 0; opacity: 0.8;">
                                Or copy and paste this link:
                            </p>
                            <a href="%s" style="color: #fc9d2c; font-size: 13px; word-break: break-all; 
                                               text-decoration: none;">%s</a>
                        </div>
                    </div>
                    
                    <!-- Footer -->
                    <div style="background: #001e37; padding: 20px 32px;">
                        <p style="color: #fffae3; font-size: 11px; margin: 0; text-align: center; opacity: 0.7; line-height: 1.6;">
                            If you didn't request this email, you can safely ignore it.<br>
                            This link expires in %d minutes for security.
                        </p>
                    </div>
                </div>
                
                <!-- Bottom branding -->
                <p style="text-align: center; color: #fffae3; font-size: 11px; margin-top: 24px; opacity: 0.5;">
                    Dialtone Protocol Server
                </p>
            </body>
            </html>
            """.formatted(expiryMinutes, magicLinkUrl, magicLinkUrl, magicLinkUrl, expiryMinutes);
    }
    
    /**
     * Build plain text content for magic link email.
     */
    private String buildMagicLinkText(String magicLinkUrl, int expiryMinutes) {
        return """
            Sign in to Dialtone
            ====================
            
            Click the link below to sign in to your Dialtone account:
            
            %s
            
            This link will expire in %d minutes.
            
            If you didn't request this email, you can safely ignore it.
            
            ---
            Dialtone - Bringing back the dial-up days
            """.formatted(magicLinkUrl, expiryMinutes);
    }
    
    /**
     * Mask email for logging (privacy).
     */
    private String maskEmail(String email) {
        int atIndex = email.indexOf('@');
        if (atIndex <= 1) {
            return "***@" + email.substring(atIndex + 1);
        }
        return email.charAt(0) + "***" + email.substring(atIndex);
    }
    
    // Request/Response DTOs for Resend API
    
    private record EmailRequest(
        String from,
        List<String> to,
        String subject,
        String html,
        String text
    ) {}
    
    private static class EmailResponse {
        @SerializedName("id")
        String id;
    }
    
    /**
     * Exception thrown when email sending fails.
     */
    public static class EmailSendException extends Exception {
        public EmailSendException(String message) {
            super(message);
        }
        
        public EmailSendException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}

