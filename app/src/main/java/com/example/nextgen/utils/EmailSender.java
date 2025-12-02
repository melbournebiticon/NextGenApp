package com.example.nextgen.utils;

import android.content.Context;
import android.widget.Toast;

import java.util.Properties;
import javax.mail.Message;
import javax.mail.PasswordAuthentication;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;

public class EmailSender {

    private static final String SENDER_EMAIL = "biticonmr@gmail.com";   // sender email
    private static final String APP_PASSWORD = "wqcy kxxd vkpm eiox";     // Gmail App password
    private static final String APP_NAME = "NextGen App"; // Sender display name

    public static void send(Context context, String toEmail, String subject, String bodyText) {

        new Thread(() -> {
            try {
                Properties props = new Properties();
                props.put("mail.smtp.auth", "true");
                props.put("mail.smtp.starttls.enable", "true");
                props.put("mail.smtp.host", "smtp.gmail.com");
                props.put("mail.smtp.port", "587");

                Session session = Session.getInstance(props,
                        new javax.mail.Authenticator() {
                            protected PasswordAuthentication getPasswordAuthentication() {
                                return new PasswordAuthentication(SENDER_EMAIL, APP_PASSWORD);
                            }
                        });

                Message message = new MimeMessage(session);
                // Set sender email and display name
                message.setFrom(new InternetAddress(SENDER_EMAIL, APP_NAME));
                message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(toEmail));
                message.setSubject(subject);
                message.setText(bodyText);

                Transport.send(message);

                // Show success Toast on UI thread
                android.os.Handler handler = new android.os.Handler(context.getMainLooper());
                handler.post(() ->
                        Toast.makeText(context, "Email sent to " + toEmail, Toast.LENGTH_SHORT).show()
                );

            } catch (Exception e) {
                e.printStackTrace();
                // Show failure Toast on UI thread
                android.os.Handler handler = new android.os.Handler(context.getMainLooper());
                handler.post(() ->
                        Toast.makeText(context, "Email failed: " + e.getMessage(), Toast.LENGTH_LONG).show()
                );
            }
        }).start();
    }
}
