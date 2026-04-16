# Prac 7 - POP3/SMTP Email Programs

## Requirements
- Ubuntu/Linux with Java installed
- Postfix (SMTP server) running
- Dovecot (POP3 server) running

## Setup - Run these commands first
```bash
# Start the mail servers
service postfix start
service dovecot start

# Create a test user
useradd -m -s /bin/bash testuser
passwd testuser
# When asked, set password to: test123

# Send test emails
echo "Test email" | mail -s "prac7" testuser@localhost
```

## Part 1 - Vacation Auto Responder
```bash
cd ~/Prac7
javac Prac7.java
java Prac7
```
This program checks testuser's mailbox for emails with subject "prac7" and sends a vacation reply.

## Part 2 - Mail Manager GUI
```bash
cd ~/Prac7
javac MailManager.java
java MailManager
```
This opens a GUI window showing all emails. Tick any email and click Delete Selected to remove it.
